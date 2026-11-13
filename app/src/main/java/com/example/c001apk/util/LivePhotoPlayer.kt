package com.example.c001apk.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView

/**
 * 实况图片播放器（2026-11-13）
 *
 * 官方做法（逆向）：点实况图 ⇒ 播放与封面同 hash 前缀的 .mp4，
 * 默认静音循环，右上角可切「静图 / 视频」（live_photo_switch_view）。
 *
 * 这里用系统自带 [VideoView]（不引新依赖）：
 * · 全屏黑底 Dialog
 * · 循环播放 + 静音（`liveSoundEnable` 默认关）
 * · 右上角「关闭」；播放失败 ⇒ 关掉并提示（对应官方 render_live_picture_fail）
 */
object LivePhotoPlayer {

    fun show(context: Context, coverUrl: String?) {
        val video = LivePhoto.videoUrl(coverUrl)
        if (video.isNullOrEmpty()) return
        AppLog.i(LogModule.CARD, "LivePhoto", "PLAY " + video)

        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }

        val videoView = VideoView(context).apply {
            setVideoURI(Uri.parse(video))
            setOnPreparedListener { mp ->
                mp.isLooping = true
                mp.setVolume(0f, 0f)          // 静音：官方默认不带声音
                start()
            }
            setOnErrorListener { _, what, extra ->
                AppLog.w(LogModule.CARD, "LivePhoto", "PLAY 失败 what=" + what + " extra=" + extra)
                runCatching { dialog.dismiss() }
                Toast.makeText(context, "实况加载失败", Toast.LENGTH_SHORT).show()
                true
            }
        }
        root.addView(
            videoView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        // 封面（点「静图」时显示；对应官方 live_photo_switch_view 的静图态）
        val cover = android.widget.ImageView(context).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            visibility = android.view.View.GONE
            runCatching {
                com.example.c001apk.util.ImageUtil.showIMG(
                    this, com.example.c001apk.util.LivePhoto.coverUrl(coverUrl)
                )
            }
        }
        root.addView(
            cover,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        // 「静图 / 视频」切换（官方 live_photo_switch_view）
        val toggle = TextView(context).apply {
            text = "静图"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(40, 24, 40, 24)
            setOnClickListener {
                if (cover.visibility == android.view.View.VISIBLE) {
                    // 切回视频
                    cover.visibility = android.view.View.GONE
                    text = "静图"
                    runCatching { videoView.start() }
                } else {
                    // 切到静图
                    cover.visibility = android.view.View.VISIBLE
                    text = "视频"
                    runCatching { videoView.pause() }
                }
            }
        }
        root.addView(
            toggle,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply { topMargin = 60; leftMargin = 30 }
        )

        // 保存成对文件：封面 .jpg + 视频 .mp4（文件名沿用 hash 前缀，和官方一致）
        val save = TextView(context).apply {
            text = "保存"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(40, 24, 40, 24)
            setOnClickListener {
                val ok = runCatching { savePair(context, coverUrl, video) }.getOrDefault(false)
                Toast.makeText(
                    context,
                    if (ok) "已保存封面和实况视频" else "保存失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        root.addView(
            save,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = 60 }
        )

        val close = TextView(context).apply {
            text = "关闭"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(40, 24, 40, 24)
            setOnClickListener { runCatching { dialog.dismiss() } }
        }
        root.addView(
            close,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { topMargin = 60; rightMargin = 30 }
        )

        dialog.setContentView(root)
        dialog.setOnDismissListener {
            runCatching { videoView.stopPlayback() }
        }
        dialog.show()
    }

    /**
     * 成对保存（2026-11-13）
     *
     * 实况图 = 一张封面 jpg + 一段同 hash 前缀的 mp4，
     * 官方下载也是两个文件，这里用系统 [DownloadManager] 各排一个任务，
     * 文件名沿用 URL 里的 hash 前缀（例：44881806_cbfafce2_7304_3823_91-livepic）。
     */
    private fun savePair(context: Context, coverUrl: String?, videoUrl: String?): Boolean {
        if (coverUrl.isNullOrEmpty() || videoUrl.isNullOrEmpty()) return false
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
            ?: return false
        val cover = LivePhoto.coverUrl(coverUrl) ?: return false
        val base = cover.substringAfterLast('/').substringBefore('@').substringBeforeLast('.')
        var queued = 0
        listOf(cover to (base + ".jpg"), videoUrl to (base + ".mp4")).forEach { (url, name) ->
            runCatching {
                val req = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                    .setTitle(name)
                    .setDescription("c001apk 实况图片")
                    .setNotificationVisibility(
                        android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalFilesDir(
                        context, android.os.Environment.DIRECTORY_DOWNLOADS, name
                    )
                dm.enqueue(req)
                queued++
            }
        }
        AppLog.i(LogModule.CARD, "LivePhoto", "SAVE 成对下载 $queued 个文件 base=" + base)
        return queued > 0
    }
}
