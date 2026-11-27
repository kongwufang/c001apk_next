package com.example.c001apk.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.Request
import okio.ByteString.Companion.toByteString

internal object WifiUpdateDownload {
    const val DIRECTORY = "self-update"

    private val client = NetworkHealth.clientBuilder()
        .followSslRedirects(false) // Allow HTTPS CDN redirects, never downgrade to HTTP.
        .proxy(Proxy.NO_PROXY) // A system proxy on a different network must not carry APK bytes.
        .retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    fun availableNetworks(context: Context) = callbackFlow {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(null) }
            override fun onLost(network: Network) { trySend(null) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(network.takeIf {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                })
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()

    suspend fun transfer(
        context: Context,
        release: AutoUpdateInfo,
        target: File,
        network: Network,
        wifi: StateFlow<Network?>,
        onProgress: (Int) -> Unit,
    ): Unit = coroutineScope {
        // Bind DNS and TCP to this particular Wi-Fi Network; default route changes cannot
        // silently move a retry, a redirect or a stale connection to the cellular network.
        val bound = client.newBuilder()
            .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS))
            .dns(object : Dns {
                override fun lookup(hostname: String) = network.getAllByName(hostname).toList()
            })
            .socketFactory(network.socketFactory)
            .build()
        val call = bound.newCall(Request.Builder().url(release.url)
            .header("Accept-Encoding", "identity").build())
        val disconnect = launch(start = CoroutineStart.UNDISPATCHED) {
            wifi.first { it != network }
            call.cancel()
        }
        // Also interrupt a blocked read on skip, opt-out or a superseding check.
        val cancelCall = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        val parent = target.parentFile ?: throw IOException("No update folder")
        val partial = File(parent, "${target.name.removeSuffix(".apk")}.part.apk")
        try {
            withContext(Dispatchers.IO) {
                if (wifi.value != network) throw IOException("Wi-Fi changed")
                if (!parent.isDirectory && !parent.mkdirs()) {
                    throw IOException("Cannot create update folder")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty APK")
                    if (body.contentLength() >= 0 && body.contentLength() != release.sizeBytes) {
                        throw IOException("Unexpected APK length")
                    }
                    var bytes = 0L
                    var lastProgress = SystemClock.elapsedRealtime()
                    body.byteStream().use { input ->
                        partial.outputStream().buffered().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count == -1) break
                                bytes += count
                                if (bytes > release.sizeBytes) throw IOException("Oversize APK")
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastProgress >= 200) {
                                    onProgress((bytes * 100 / release.sizeBytes).toInt().coerceAtMost(99))
                                    lastProgress = now
                                }
                            }
                        }
                    }
                    if (bytes != release.sizeBytes || digest.digest().toByteString().hex() != release.sha256 ||
                        !matchesInstalledApp(context, partial, release.update.versionCode)
                    ) throw IOException("Unexpected APK content")
                    ensureActive()
                    if (!partial.renameTo(target)) throw IOException("Cannot finalize APK")
                }
            }
        } finally {
            disconnect.cancel()
            cancelCall.cancel()
            partial.delete()
        }
    }

    suspend fun verifyCached(context: Context, file: File, release: AutoUpdateInfo): Boolean =
        withContext(Dispatchers.IO) {
            if (!file.isFile || file.length() != release.sizeBytes) return@withContext false
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        digest.update(buffer, 0, count)
                    }
                }
                digest.digest().toByteString().hex() == release.sha256 &&
                    matchesInstalledApp(context, file, release.update.versionCode)
            } catch (_: IOException) {
                false
            }
        }

    @Suppress("DEPRECATION")
    private fun matchesInstalledApp(context: Context, file: File, versionCode: Long): Boolean = runCatching {
        val pm = context.packageManager
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val flags = if (modern) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        // Android 9's archive parser only collects certificates when GET_SIGNATURES is set.
        // Also request SigningInfo on newer APIs so we still compare the current signers.
        val actual = pm.getPackageArchiveInfo(file.absolutePath, flags or PackageManager.GET_SIGNATURES)
            ?: return false
        if (actual.packageName != context.packageName) return false
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun version(info: PackageInfo) = if (modern) info.longVersionCode else info.versionCode.toLong()
        fun signers(info: PackageInfo) = (if (modern) info.signingInfo?.apkContentsSigners else info.signatures)
            .orEmpty().toSet()
        val expected = signers(installed)
        version(actual) == versionCode && versionCode > version(installed) &&
            expected.isNotEmpty() && expected == signers(actual)
    }.getOrDefault(false)
}
