package com.example.c001apk.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

/** 全屏始终先展示封面；播放、重播复用详情页的单次 ExoPlayer 会话。 */
object LivePhotoPlayer {
    private val downloads = Executors.newSingleThreadExecutor()

    fun show(context: Context, coverUrl: String?) {
        val original = LivePhoto.coverUrl(coverUrl) ?: return
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val cover = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        root.addView(cover, FrameLayout.LayoutParams(-1, -1))
        ImageUtil.showIMG(cover, original)
        LivePhotoInline.bind(cover, original)

        fun button(label: String, gravity: Int, action: (TextView) -> Unit) {
            val text = TextView(context).apply {
                this.text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(16.dp, 12.dp, 16.dp, 12.dp)
                setOnClickListener { action(this) }
            }
            root.addView(text, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or gravity).apply { topMargin = 24.dp })
        }
        button("返回", Gravity.START) { dialog.dismiss() }
        button("保存", Gravity.CENTER_HORIZONTAL) { savePair(context, original) }
        button("实况 ⋮", Gravity.END) { anchor ->
            PopupMenu(context, anchor).apply {
                menu.add(0, 1, 0, if (PrefManager.showLivePhoto) "关闭实况" else "开启实况")
                menu.add(0, 2, 1, "重新播放").isEnabled = PrefManager.showLivePhoto
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> {
                            PrefManager.showLivePhoto = !PrefManager.showLivePhoto
                            LivePhotoInline.stopAll()
                            Toast.makeText(context,
                                if (PrefManager.showLivePhoto) "已开启实况" else "已全局关闭实况，可在设置中重新开启",
                                Toast.LENGTH_SHORT).show()
                        }
                        2 -> cover.performLongClick()
                    }
                    true
                }
            }.show()
        }
        dialog.setContentView(root)
        dialog.show()
    }

    /** 只有两个流均写入成功才提示成功；签名每次保存时重新取，避免过期。 */
    private fun savePair(context: Context, original: String) {
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
