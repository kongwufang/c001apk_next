package com.example.c001apk.util

import android.os.Handler
import android.os.Looper
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 官方签发接口返回 302；认证头仅发送到 API，不跟随重定向到图床。 */
object LivePhotoResolver {
    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private data class Cached(val url: String, val expiresAt: Long)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Cached>()
    private val client by lazy {
        SslVerify.apply(okhttp3.OkHttpClient.Builder()
            .addInterceptor(AddCookiesInterceptor)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)).build()
    }

    /** 回调始终在主线程；网络、token 计算始终在工作线程。 */
    fun resolveAsync(coverUrl: String?, callback: (String?) -> Unit) {
        executor.execute {
            val result = resolve(coverUrl)
            main.post { callback(result) }
        }
    }

    /** 仅可从工作线程调用。缓存按原图地址索引，不接受缩略图后缀。 */
    fun resolve(coverUrl: String?): String? {
        val cover = LivePhoto.coverUrl(coverUrl) ?: return null
        if (!LivePhoto.isLive(cover)) return null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            AppLog.w(LogModule.CARD, "LivePhoto", "RESOLVE rejected main-thread call")
            return null
        }
        val now = System.currentTimeMillis()
        cache[cover]?.takeIf { it.expiresAt > now }?.let { return it.url }
        return runCatching {
            val req = okhttp3.Request.Builder()
                .url("https://api.coolapk.com/v6/livePhoto/showVideo?picUrl=" +
                    URLEncoder.encode(cover, "UTF-8"))
                .build()
            client.newCall(req).execute().use { response ->
                val location = response.header("Location")
                val uri = location?.let { URI(it) }
                val valid = response.code in 300..399 &&
                    uri?.scheme in listOf("http", "https") &&
                    uri?.host?.lowercase() == "image.coolapk.com"
                AppLog.i(LogModule.CARD, "LivePhoto",
                    "RESOLVE code=${response.code} signedUrl=$valid")
                if (valid && location != null) {
                    val expiry = uri?.rawQuery?.split('&')
                        ?.firstOrNull { it.startsWith("t=") }
                        ?.substringAfter('=')?.toLongOrNull()?.times(1000)
                    // 不把即将失效的地址缓存太久，且限制缓存大小。
                    if (cache.size >= 32) cache.clear()
                    cache[cover] = Cached(location, minOf(now + 120_000, (expiry ?: (now + 120_000)) - 10_000))
                    location
                } else null
            }
        }.onFailure {
            AppLog.w(LogModule.CARD, "LivePhoto", "RESOLVE failed: ${it.javaClass.simpleName}")
        }.getOrNull()
    }
}