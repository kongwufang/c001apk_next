package com.example.c001apk.util

import android.content.Context
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

/** 实况图片成对保存，由统一看图页长按菜单调用。 */
object LivePhotoPlayer {
    private val downloads = Executors.newSingleThreadExecutor()

    /** 只有两个流均写入成功才提示成功；签名每次保存时重新取，避免过期。 */
    fun savePair(context: Context, original: String) {
        val app = context.applicationContext
        Toast.makeText(context, "正在保存封面和实况视频", Toast.LENGTH_SHORT).show()
        downloads.execute {
            val result = runCatching {
                val signed = LivePhotoResolver.resolve(original) ?: error("签名地址获取失败")
                val dir = app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: app.filesDir
                check(dir.exists() || dir.mkdirs())
                val base = original.substringAfterLast('/').substringBefore('@').substringBeforeLast('.')
                val client = SslVerify.apply(okhttp3.OkHttpClient.Builder()).build()
                listOf(original to "$base.jpg", signed to "$base.mp4").forEach { (url, name) ->
                    val file = File(dir, name)
                    val request = okhttp3.Request.Builder().url(url)
                        .header("User-Agent", PrefManager.USER_AGENT)
                        .header("Referer", "https://www.coolapk.com/").build()
                    client.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val body = response.body ?: error("空响应")
                        body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                        check(file.length() > 0) { "空文件" }
                        AppLog.i(LogModule.CARD, "LivePhoto", "SAVE complete $name bytes=${file.length()}")
                    }
                }
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(app, if (result.isSuccess) "封面和实况视频已保存至应用下载目录"
                    else "保存未完成：${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
