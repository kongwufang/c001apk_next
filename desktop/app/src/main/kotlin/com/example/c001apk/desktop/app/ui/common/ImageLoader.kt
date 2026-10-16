package com.example.c001apk.desktop.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.example.c001apk.desktop.core.crypto.md5Hex
import com.example.c001apk.desktop.core.net.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * 图片加载 —— 复刻 Android 端 `util/SslVerify.imageClient()` 的关键行为。
 *
 * ## ★ 必须带酷安 UA，否则一律 567
 * Android 端 `MyApplication` 与 `SslVerify` 都有明确注释：
 * > 图片 CDN（`image.coolapk.com` 前面的腾讯云 EdgeOne）**按 UA 放行**，
 * > UA 不对直接回 567 +「请求已被站点的安全策略拦截」的 HTML 页 —— 表现就是点开大图黑屏。
 *
 * 实测确认（`_rev/win_ref/test_image_fullua.py`）：
 * ```
 * image.coolapk.com  无 UA      → HTTP 567（拦截页，7351 B）
 * image.coolapk.com  完整酷安UA → HTTP 200  image/jpg  145967 B   ✅
 * avatar.coolapk.com 无 UA      → HTTP 200  image/jpeg  3161 B    （头像域不校验 UA）
 * ```
 * 注意 UA 必须是 `Session.USER_AGENT` 那个**完整**值（两段拼接 + `+CoolMarket/x-y-z`），
 * 只取前半段同样会被 567。
 *
 * ## 缓存策略
 * * 内存：`ConcurrentHashMap<String, ImageBitmap>`，LRU 语义用简单容量上限
 * * 磁盘：用户目录下的 `c001apk-image-cache/`，避免每次启动重新下载头像
 *   （Android 端靠 Glide 的磁盘缓存；桌面端没有 Glide，这里自己做一个最简版）
 */
object ImageLoader {

    /** 内存缓存上限（张）。1080P 图约 0.5~2 MB/张，60 张约 30~120 MB —— 8G 内存机器要克制 */
    private const val MEMORY_LIMIT = 60

    private val memory = object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ImageBitmap>): Boolean =
            size > MEMORY_LIMIT
    }

    /** 进行中的请求，避免同一张图并发拉多次 */
    private val inflight = ConcurrentHashMap<String, Boolean>()

    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                // 与 Android 端 SslVerify.imageClient() 完全一致的做法：
                // 请求时才读 UA 并写入，保证图片 CDN 放行
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", Protocol.USER_AGENT)
                        .build()
                )
            }
            .build()
    }

    private val diskDir: java.io.File by lazy {
        java.io.File(System.getProperty("user.home"), "c001apk-image-cache").apply { mkdirs() }
    }

    /** 同步取缓存（不触发网络） */
    @Synchronized
    fun cached(url: String): ImageBitmap? = memory[url]

    /**
     * 取图：命中内存 → 命中磁盘 → 网络下载。
     *
     * 失败返回 null（调用方回退到占位块），**不抛异常** —— 一张图挂掉不能让整页崩。
     */
    suspend fun load(url: String): ImageBitmap? {
        cached(url)?.let { return it }
        if (inflight.putIfAbsent(url, true) != null) {
            // 已有人在下同一张：等一会儿再查缓存
            repeat(20) {
                kotlinx.coroutines.delay(100)
                cached(url)?.let { return it }
            }
            return null
        }
        return try {
            val bmp = withContext(Dispatchers.IO) { readDisk(url) ?: download(url) }
            if (bmp != null) {
                synchronized(memory) { memory[url] = bmp }
            }
            bmp
        } finally {
            inflight.remove(url)
        }
    }

    private fun diskFile(url: String): java.io.File =
        java.io.File(diskDir, url.md5Hex() + ".img")

    private fun readDisk(url: String): ImageBitmap? = runCatching {
        val f = diskFile(url)
        if (!f.isFile || f.length() == 0L) return null
        decode(f.readBytes())
    }.getOrNull()

    private fun download(url: String): ImageBitmap? = runCatching {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            if (bytes.isEmpty()) return null
            // 落盘（失败不影响本次显示）
            runCatching { diskFile(url).writeBytes(bytes) }
            decode(bytes)
        }
    }.getOrNull()

    private fun decode(bytes: ByteArray): ImageBitmap? =
        ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
}

/**
 * 异步图片 Composable：先给占位，加载完自动换成真图。
 *
 * 用法（把结果交给调用方的渲染 lambda，避免各页面各写一套）：
 * ```kotlin
 * AsyncImage(url) { bmp -> if (bmp != null) Image(bmp, ...) else Placeholder() }
 * ```
 */
@Composable
fun AsyncImage(
    url: String?,
    modifier: Modifier = Modifier,
    content: @Composable (ImageBitmap?) -> Unit,
) {
    var bmp by remember(url) { mutableStateOf(url?.let { ImageLoader.cached(it) }) }
    LaunchedEffect(url) {
        if (url != null && bmp == null) bmp = ImageLoader.load(url)
    }
    Box(modifier) { content(bmp) }
}

/**
 * 把 URL 变成适合列表展示的小图地址。
 *
 * Android 端 `UrlUtil.thumbImageUrl` 是给 URL 追加 `.s.jpg`（服务端的小图衍生），
 * 九宫格也这么用（见 `NineGridImageView`）。这里照做。
 */
fun String.toThumbUrl(): String = if (contains(".s.jpg")) this else "$this.s.jpg"
