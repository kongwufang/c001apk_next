package com.example.c001apk.util

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 实况图片「签名视频地址」解析（2026-11-14）
 *
 * ## 官方调用链（真机抓包证实）
 * ```
 * GET https://api.coolapk.com/v6/livePhoto/showVideo?picUrl=<URL 编码的封面地址>
 *  → 302 Found
 *  → Location: http://image.coolapk.com/…-livepic@1920x1080.jpg.mp4?t=…&sign=<md5>
 * ```
 * 客户端**不计算签名**，只是把 302 的 Location 拿去播放/下载。
 *
 * ## 关键约束（实测）
 * · 必须用 `api.coolapk.com` 域名（换成 image 域名会 404）
 * · **必须带 `X-App-Token`**（去掉它接口返回 200 但**不给 Location**）
 * · `id` 参数可选
 *
 * ⇒ 因此这里复用 App 自己的 OkHttp 客户端：
 *   `AddCookiesInterceptor` 会加上 X-App-*（含现场计算的 X-App-Token），
 *   和 `_rev/v3_token_fixed.py` 是同一套算法，不用另写一份。
 */
object LivePhotoResolver {

    private const val HOST = "https://api.coolapk.com"

    private val client by lazy {
        SslVerify.apply(
            okhttp3.OkHttpClient.Builder()
                .addInterceptor(AddCookiesInterceptor)
                .followRedirects(false)      // 我们要的就是 Location 本身
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        )
    }

    /**
     * 拿带 sign 的 mp4 地址
     * @return null = 拿不到（网络失败 / 服务端没签发 / 不是实况图）
     */
    fun resolve(coverUrl: String?): String? {
        if (!LivePhoto.isLive(coverUrl)) return null
        return runCatching {
            val pic = URLEncoder.encode(coverUrl, "UTF-8")
            val req = okhttp3.Request.Builder()
                .url(HOST + "/v6/livePhoto/showVideo?picUrl=" + pic)
                .build()
            client.newCall(req).execute().use { resp ->
                val loc = resp.header("Location")
                AppLog.d(
                    LogModule.CARD, "LivePhoto",
                    "RESOLVE code=" + resp.code + " loc=" + (loc ?: "null")
                )
                loc
            }
        }.getOrNull()
    }
}
