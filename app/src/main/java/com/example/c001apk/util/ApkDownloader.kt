package com.example.c001apk.util

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** 一次 APK 下载的实时进度：已下载字节、总大小（未知为 -1）、瞬时速度（B/s） */
data class ApkDownloadProgress(val downloaded: Long, val total: Long, val speed: Long)

/**
 * APK 下载：不再用系统 DownloadManager（拿不到进度，也无法在取消时精确删包），
 * 改为自己流式下载，这样才有百分比 / 速度 / 剩余时间，取消时能立刻清掉半成品。
 *
 * 文件落在应用私有目录 `Android/data/<包名>/files/apk/`：卸载即清理，不需要任何存储权限；
 * 交给系统安装器或其它应用时走 FileProvider 授权。
 */
object ApkDownloader {

    const val DIR_NAME = "apk"

    private const val BUFFER_SIZE = 64 * 1024

    /** 进度回调节流：64KB 一次太密，会把主线程刷爆 */
    private const val PROGRESS_INTERVAL_MS = 200L

    /**
     * 独立客户端：下载走酷安 CDN，不挂 Cookie / 签名 / 日志拦截器。
     * SSL 处理与 [UpdateChecker] 一致：调试模式放开，其余交给 network_security_config
     * （CDN 证书链不保证锚定在内置 CA 库上，套严格校验容易在握手阶段挂掉）。
     */
    private val client by lazy {
        val builder = NetworkHealth.clientBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        if (PrefManager.isSslDebug) SslVerify.applyDebug(builder).build() else builder.build()
    }

    /**
     * 流式下载并写入私有目录，返回落地文件。
     * 失败或协程取消都会删掉半成品，调用方不需要自己清理。
     */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: (ApkDownloadProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, DIR_NAME)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建下载目录")
        val file = File(dir, fileName)
        if (file.exists() && !file.delete()) throw IOException("同名文件被占用")

        val call = client.newCall(Request.Builder().url(url).build())
        // 用户点取消时立刻断开连接，不必等当前这次 read 返回
        coroutineContext.job.invokeOnCompletion {
            if (it is CancellationException) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("响应为空")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = 0L
                        var lastBytes = 0L
                        var speed = 0L
                        var lastTime = SystemClock.elapsedRealtime()
                        while (true) {
                            // 阻塞 IO 不响应协程取消，这里手动检查一次（最多多读 64KB）
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val now = SystemClock.elapsedRealtime()
                            val elapsed = now - lastTime
                            // 总大小未知（分块传输）时同样按时间节流回调，只是没有百分比
                            if (elapsed >= PROGRESS_INTERVAL_MS ||
                                (total > 0 && downloaded >= total)
                            ) {
                                if (elapsed > 0) speed = (downloaded - lastBytes) * 1000 / elapsed
                                lastBytes = downloaded
                                lastTime = now
                                onProgress(ApkDownloadProgress(downloaded, total, speed))
                            }
                        }
                        output.flush()
                    }
                }
            }
        } catch (e: Throwable) {
            file.delete()
            // 取消引发的 IO 中断不该当成「下载失败」上报
            coroutineContext.ensureActive()
            throw e
        }
        file
    }
}
