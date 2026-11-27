package com.example.c001apk.util.emoji

import com.example.c001apk.util.NetworkHealth
import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import com.example.c001apk.util.AppLog
import com.example.c001apk.util.LogModule
import com.example.c001apk.util.PrefManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Keeps the downloadable emoji pack current.
 *
 * Policy: check the release manifest on process start. Download automatically on an
 * unmetered network; allow metered downloads only if enabled during onboarding.
 * The installed pack keeps working offline and
 * is only replaced after the archive's size and SHA-256 match and every file extracts cleanly.
 */
internal object EmojiPackUpdater : Application.ActivityLifecycleCallbacks {
    private const val ENDPOINT = "https://service.houlangs.cn/c001apk/emojipack/stable.json"

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class WaitingForWifi(val version: Int) : State
        data class Downloading(val version: Int, val percent: Int) : State
        data class Ready(val version: Int, val updated: Boolean) : State
        data class Failed(val version: Int) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = mutableState.asStateFlow()
    private val client = NetworkHealth.clientBuilder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gate = Mutex()
    private lateinit var app: Context
    private var started = false
    private var pending: EmojiPackRelease? = null
    private var waitingForWifi = false
    private var job: Job? = null

    fun register(application: Application) {
        val process = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000') }.getOrNull()
        }
        if (process != application.packageName) return
        app = application.applicationContext
        EmojiPackStore.load(app)
        application.registerActivityLifecycleCallbacks(this)
        watchNetworks()
    }

    override fun onActivityStarted(activity: Activity) {
        if (started || activity.javaClass.name.contains("Onboarding")) return
        if (!PrefManager.onboarded) return
        started = true
        check()
    }

    /** Looks for a newer release; safe to call repeatedly on the main thread. */
    private fun check() {
        if (!::app.isInitialized || !started || job?.isActive == true) return
        job = scope.launch {
            mutableState.value = State.Checking
            val release = try {
                fetchRelease()
            } catch (error: Throwable) {
                AppLog.w(LogModule.CARD, "EmojiPack", "check failed: ${error.javaClass.simpleName}")
                null
            }
            if (release == null) {
                mutableState.value = State.Failed(pending?.version ?: 0)
                return@launch
            }
            val installed = EmojiPackStore.version()
            if (release.version <= installed) {
                mutableState.value = State.Ready(installed, updated = false)
                return@launch
            }
            pending = release
            if (unmetered() || PrefManager.emojiPackDownloadOnMetered) {
                download(release)
            } else {
                waitingForWifi = true
                mutableState.value = State.WaitingForWifi(release.version)
            }
        }
    }

    /** Called by the panel when it is opened without any installed pack. */
    fun ensureAvailable() {
        if (EmojiPackStore.version() > 0) return
        if (state.value is State.Checking || state.value is State.Downloading) return
        check()
    }

    private suspend fun fetchRelease(): EmojiPackRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$ENDPOINT?ts=${System.currentTimeMillis()}")
            .header("Cache-Control", "no-store")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body ?: return@withContext null
            if (body.contentLength() > 64 * 1024) return@withContext null
            EmojiPackRelease.parse(body.string())
        }
    }

    private suspend fun download(release: EmojiPackRelease) {
        gate.withLock {
            if (!unmetered() && !PrefManager.emojiPackDownloadOnMetered) {
                waitingForWifi = true
                mutableState.value = State.WaitingForWifi(release.version)
                return
            }
            waitingForWifi = false
            mutableState.value = State.Downloading(release.version, 0)
            val folder = EmojiPackStore.root(app)
            folder.mkdirs()
            val archive = File(folder, "download-${release.version}.zip")
            try {
                val ok = withContext(Dispatchers.IO) { downloadArchive(release, archive) }
                if (!ok) {
                    mutableState.value = State.Failed(release.version)
                    return
                }
                val (dir, pack) = withContext(Dispatchers.IO) {
                    EmojiPackInstaller.install(archive, release.version, folder)
                }
                PrefManager.emojiPackVersion = pack.version
                EmojiPackStore.activate(app, dir, pack)
                pending = null
                mutableState.value = State.Ready(pack.version, updated = true)
                AppLog.i(LogModule.CARD, "EmojiPack", "installed v${pack.version} (${pack.codes.size} emoji)")
            } catch (error: Throwable) {
                AppLog.w(LogModule.CARD, "EmojiPack", "install failed: ${error.javaClass.simpleName}")
                mutableState.value = State.Failed(release.version)
            } finally {
                archive.delete()
            }
        }
    }

    private fun downloadArchive(release: EmojiPackRelease, target: File): Boolean {
        val partial = File(target.parentFile, target.name + ".part")
        return try {
            val request = Request.Builder().url(release.url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                if (body.contentLength() > release.sizeBytes) return false
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                partial.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            read += count
                            if (read > release.sizeBytes) return false
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (read != release.sizeBytes) return false
                if (digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } != release.sha256) return false
                if (!partial.renameTo(target)) return false
            }
            true
        } catch (error: Throwable) {
            AppLog.w(LogModule.CARD, "EmojiPack", "download failed: ${error.javaClass.simpleName}")
            false
        } finally {
            partial.delete()
        }
    }

    private fun unmetered(): Boolean = runCatching {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return@runCatching false
        val network = manager.activeNetwork ?: return@runCatching false
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    private fun watchNetworks() {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
                    scope.launch { onNetworkAvailable() }
                }
            })
        }.onFailure { AppLog.w(LogModule.CARD, "EmojiPack", "network callback unavailable", it) }
    }

    private fun onNetworkAvailable() {
        if (!started || job?.isActive == true) return
        val release = pending
        if (waitingForWifi && release != null) {
            if (unmetered() || PrefManager.emojiPackDownloadOnMetered) {
                job = scope.launch { download(release) }
            }
        } else if (state.value is State.Failed) {
            check()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
