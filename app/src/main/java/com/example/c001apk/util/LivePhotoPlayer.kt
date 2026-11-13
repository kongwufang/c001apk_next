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
}
