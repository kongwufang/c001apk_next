package com.example.c001apk.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.c001apk.R
import com.example.c001apk.ui.onboarding.OnboardingActivity
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Failed : UpdateState
    data object Current : UpdateState
    data class Manual(val info: UpdateChecker.UpdateInfo) : UpdateState
    data class Waiting(val info: UpdateChecker.UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateChecker.UpdateInfo, val percent: Int) : UpdateState
    data class Ready(val info: UpdateChecker.UpdateInfo) : UpdateState

    val infoOrNull: UpdateChecker.UpdateInfo?
        get() = when (this) {
            is Manual -> info
            is Waiting -> info
            is Downloading -> info
            is Ready -> info
            else -> null
        }
}

/** The first try plus at most five quiet retries. Waiting for Wi-Fi isn't an attempt. */
internal suspend fun retryUpdateDownload(
    backoff: suspend (Int) -> Unit,
    attempt: suspend () -> Unit,
): Boolean {
    for (retry in 0..5) {
        if (retry > 0) backoff(retry)
        try {
            attempt()
            return true
        } catch (_: IOException) {
            // The caller closes the transfer and waits for Wi-Fi again before another try.
        }
    }
    return false
}

internal fun pruneUpdateFiles(
    directory: File,
    keep: String? = null,
    olderThan: Long = Long.MAX_VALUE,
    onlyVersion: Long? = null,
) {
    directory.listFiles()?.forEach { file ->
        val code = file.name.substringBefore('-').toLongOrNull()
        if (file.isFile && file.name != keep && (onlyVersion == null || onlyVersion == code) &&
            (code == null || code < olderThan)
        ) file.delete()
    }
}

/** One process-wide update session; no foreground service, alerts, or automatic install. */
internal object SelfUpdates : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = mutableState.asStateFlow()
    private lateinit var context: Context
    private var started = false
    private var work: Job? = null
    private var firstState: CompletableDeferred<UpdateState>? = null
    private var ready: AutoUpdateInfo? = null
    private val files = Mutex() // A cancelled writer must finish before newer files are touched.
    private val client = NetworkHealth.clientBuilder()
        .followRedirects(false)
        .callTimeout(12, TimeUnit.SECONDS)
        .addInterceptor(ServerCookieStore.interceptor())
        .build()
    private val directory get() = File(context.filesDir, WifiUpdateDownload.DIRECTORY)

    fun register(app: Application) {
        val process = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName()
        else runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000') }.getOrNull()
        if (process != app.packageName) return
        context = app.applicationContext
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        // On a fresh install wait for completion of the guide and the user's checkbox choice.
        if (started || activity is OnboardingActivity || !PrefManager.onboarded) return
        started = true
        restart(manual = false)
    }

    fun preferencesChanged() {
        started = true
        restart(manual = false)
    }

    suspend fun checkNow(): UpdateState {
        started = true
        return when (val current = state.value) {
            is UpdateState.Waiting, is UpdateState.Downloading -> current
            UpdateState.Checking -> firstState?.await() ?: state.value
            else -> restart(manual = true).await()
        }
    }

    private fun restart(manual: Boolean): CompletableDeferred<UpdateState> {
        work?.cancel()
        ready = null
        val result = CompletableDeferred<UpdateState>()
        firstState = result
        val enabled = PrefManager.isCheckUpdateStable
        val automatic = enabled && PrefManager.autoDownloadUpdatesOnWifi
        mutableState.value = if (enabled || manual) UpdateState.Checking else UpdateState.Idle
        fun publish(next: UpdateState) {
            mutableState.value = next
            result.complete(next)
        }
        work = scope.launch {
            files.withLock {
                if (!automatic) cleanFiles() else clearPartialFiles()
                if (!enabled && !manual) return@withLock
                if (automatic) {
                    val release = fetchRelease()
                    if (release != null) {
                        val info = release.update
                        if (!info.isNewer || isSkipped(info.versionCode)) {
                            cleanFiles()
                            publish(UpdateState.Current)
                            return@withLock
                        }
                        cleanFiles(keep = release.fileName)
                        val apk = File(directory, release.fileName)
                        val available = try {
                            WifiUpdateDownload.verifyCached(context, apk, release) || download(release, apk, ::publish)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: RuntimeException) {
                            // An unavailable connectivity service or a device-specific storage error
                            // must not crash the app or leave it stuck on an automatic update card.
                            false
                        }
                        if (available) {
                            ready = release
                            publish(UpdateState.Ready(info))
                            return@withLock
                        }
                    }
                }
                val legacy = UpdateChecker.fetchUpdate()
                if (legacy != null) {
                    if (!legacy.isNewer || isSkipped(legacy.versionCode)) cleanFiles()
                    else cleanFiles(olderThan = legacy.versionCode)
                }
                publish(when {
                    legacy == null -> UpdateState.Failed
                    !legacy.isNewer || isSkipped(legacy.versionCode) -> UpdateState.Current
                    else -> UpdateState.Manual(legacy)
                })
            }
        }.also { it.invokeOnCompletion { result.complete(mutableState.value) } }
        return result
    }

    private suspend fun fetchRelease(): AutoUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = UpdateChecker.metadataRequest(AutoUpdateInfo.ENDPOINT)
                .header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: return@use null
                // Bound even a chunked metadata response; its changelog is plain text.
                if (!response.isSuccessful || body.source().request(256L * 1024 + 1)) return@use null
                AutoUpdateInfo.parse(body.string())
            }
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun download(
        release: AutoUpdateInfo,
        file: File,
        publish: (UpdateState) -> Unit,
    ): Boolean = coroutineScope {
        withContext(Dispatchers.IO) { file.delete() }
        val wifi = MutableStateFlow<Network?>(null)
        val connectivity = launch {
            WifiUpdateDownload.availableNetworks(context).collect { wifi.value = it }
        }
        val info = release.update
        try {
            retryUpdateDownload(
                backoff = { retry ->
                    if (wifi.value == null) publish(UpdateState.Waiting(info))
                    delay(1_000L shl (retry - 1))
                },
                attempt = {
                    if (wifi.value == null) publish(UpdateState.Waiting(info))
                    val network = wifi.first { it != null }!!
                    val initial = UpdateState.Downloading(info, 0)
                    publish(initial)
                    WifiUpdateDownload.transfer(context, release, file, network, wifi) { percent ->
                        mutableState.updateProgress(info, percent)
                    }
                },
            )
        } finally {
            connectivity.cancel()
        }
    }

    private fun MutableStateFlow<UpdateState>.updateProgress(info: UpdateChecker.UpdateInfo, percent: Int) {
        val old = value as? UpdateState.Downloading ?: return
        if (old.info == info && percent > old.percent) {
            compareAndSet(old, UpdateState.Downloading(info, percent))
        }
    }

    fun skip(versionCode: Long) {
        if (state.value.infoOrNull?.versionCode != versionCode) return
        PrefManager.skippedUpdateCode = versionCode
        work?.cancel()
        ready = null
        mutableState.value = UpdateState.Current
        scope.launch { files.withLock { cleanFiles(onlyVersion = versionCode) } }
    }

    fun install(activity: Activity, versionCode: Long) {
        val release = ready?.takeIf { it.update.versionCode == versionCode } ?: return
        val file = File(directory, release.fileName)
        if (!file.isFile) {
            restart(manual = false)
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(activity, R.string.download_no_installer, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(activity, R.string.download_no_installer, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isSkipped(versionCode: Long) = versionCode == PrefManager.skippedUpdateCode

    private suspend fun clearPartialFiles() = withContext(Dispatchers.IO) {
        directory.listFiles()?.filter { it.name.endsWith(".part.apk") }?.forEach { it.delete() }
    }

    private suspend fun cleanFiles(
        keep: String? = null,
        olderThan: Long = Long.MAX_VALUE,
        onlyVersion: Long? = null,
    ) = withContext(Dispatchers.IO) {
        pruneUpdateFiles(directory, keep, olderThan, onlyVersion)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
