package com.example.c001apk.logic.repository

import android.util.Log
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.util.SharePostHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把一篇动态分享成「第三方链接」。
 *
 * ## 数据流
 *
 * ```
 * 详情页点分享
 *   → 弹「是否通过第三方链接分享」
 *     → 确认：SharePostHtml 生成 HTML（图片位置是 {{IMG0}} 占位）
 *       → 加上 HMAC 签名，multipart 传到服务器
 *         → 服务器写 123（冷）+ 预热 OSS（热）
 *           → 返回 { sid, url, cdn }
 *             → app 弹「链接已生成 [分享] [复制]」
 * ```
 *
 * ## 为什么服务器不自己爬
 *
 * 实测机房 IP 爬 `/v6/feed/detail` 一律「当前访问需要验证码」✗（连登录 Cookie 都过不了 ✗）
 * ⇒ 只能客户端爬好、把成品传上去 ✓（服务器只做存储与分发）
 *
 * ## 签名
 *
 * `X-Share-Ts` + `X-Share-Sig`（HMAC-SHA256）
 * 算法跟 `_rev/server_img/share-create.php` 一致：
 * ```
 * digest = sha256(html);  对每张图: digest = sha256(digest + sha256(img))
 * sig    = hex(hmac_sha256(secret, ts + ":" + digest))
 * ```
 * ⚠️ secret 内置在 app 里 ⇒ **只能挡随手乱刷**，不是安全边界（真有心想逆也能扒出来）✓
 * 真正的防线是服务端的限流 + 体积上限 + 不可枚举的 sid ✓
 */
@Singleton
class ShareLinkRepo @Inject constructor() {

    /**
     * 自己建客户端，不复用注入的那个。
     *
     * 两个原因：
     * ① 注入的 `OkHttpClient` 带 `@Api1Service` / `@Api1ServiceNoRedirect` 限定符 ✗
     *    无 Qualifier 拿不到 ✓（Dagger 会直接编译失败）
     * ② 那个 client 挂了 `AddCookiesInterceptor` ⇒ 会把**酷安 Cookie 发给自家服务器** ✗✗
     *    （完全没必要，而且泄漏登录态）
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "ShareLinkRepo"

        /** 服务端入口（跟 _rev/server_img/share-create.php 对应） */
        private const val ENDPOINT =
            "https://service.houlangs.cn/c001apk/share/create.php"

        /**
         * 跟服务器 `share/secret.php` 里的 `share_secret` **必须一致** ✓
         * 改的话两边一起改（服务器上改完 app 也要发版）
         */
        private const val SECRET = "ebde74914ab93f63d5efa8134a672ecc6aa084baccf9cd69"

        /** 一次最多传几张图（服务器也限 12 ✓） */
        private const val MAX_IMAGES = 12

        /** 单张图上限，超了就跳过（服务器 8MB ✓ 这里保守点 6MB） */
        private const val MAX_IMAGE_BYTES = 6 * 1024 * 1024
    }

    /** 分享结果 */
    data class Result(
        val sid: String,
        val url: String,
        val cdn: String?,
        val images: Int
    )

    private fun hex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append(String.format("%02x", x))
        return sb.toString()
    }

    private fun sha256(b: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(b))

    private fun hmacSha256(key: String, msg: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return hex(mac.doFinal(msg.toByteArray(Charsets.UTF_8)))
    }

    /**
     * 上传分享。
     *
     * @param localImages 已经下载到本地缓存目录的图片（离线缓存那边现成的 ✓）
     *                    顺序必须跟 `picArr` 一致 ✓
     */
    suspend fun createShare(
        data: HomeFeedResponse.Data,
        localImages: List<File> = emptyList()
    ): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        try {
            val fid = data.id?.takeIf { it.isNotBlank() }
                ?: return@withContext kotlin.Result.failure(IllegalArgumentException("没有帖子 id"))

            // ① 挑图：优先用本地已缓存的文件 ✓ 没有就跳过（不强求）
            val imgs = localImages
                .filter { it.isFile && it.length() in 1..MAX_IMAGE_BYTES.toLong() }
                .take(MAX_IMAGES)

            // ② 生成 HTML（图片数 = 实际要传的张数 ✓）
            val html = SharePostHtml.build(data, imgs.size)

            // ③ 签名
            val ts = (System.currentTimeMillis() / 1000).toString()
            var digest = sha256(html.toByteArray(Charsets.UTF_8))
            for (f in imgs) {
                digest = sha256((digest + sha256(f.readBytes())).toByteArray(Charsets.UTF_8))
            }
            val sig = hmacSha256(SECRET, "$ts:$digest")

            // ④ multipart
            val textType = "text/plain; charset=utf-8".toMediaTypeOrNull()
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("fid", fid)
                .addFormDataPart("title", SharePostHtml.titleOf(data))
                .addFormDataPart("author", data.username.orEmpty())
                .addFormDataPart("html", html.toRequestBody(textType))
            imgs.forEachIndexed { i, f ->
                builder.addFormDataPart(
                    "img$i", f.name,
                    f.readBytes().toRequestBody("application/octet-stream".toMediaTypeOrNull())
                )
            }

            val req = Request.Builder()
                .url(ENDPOINT)
                .post(builder.build())
                .header("X-Share-Ts", ts)
                .header("X-Share-Sig", sig)
                .header("User-Agent", "c001apk_next")
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "HTTP ${resp.code}: ${body.take(200)}")
                    return@withContext kotlin.Result.failure(
                        RuntimeException("服务器返回 ${resp.code}")
                    )
                }
                val j = JSONObject(body)
                if (j.optInt("status", 0) != 1) {
                    return@withContext kotlin.Result.failure(
                        RuntimeException(j.optString("message", "分享失败"))
                    )
                }
                kotlin.Result.success(
                    Result(
                        sid = j.optString("sid"),
                        url = j.optString("url"),
                        cdn = j.optString("cdn").takeIf { it.isNotBlank() },
                        images = j.optInt("images", imgs.size)
                    )
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "createShare failed", e)
            kotlin.Result.failure(e)
        }
    }
}
