package com.example.c001apk.util

import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.c001apk.R

/** 图片自身保留为封面，仅播放期间在其上覆盖纹理；结束/失败/离屏均释放播放器。 */
object LivePhotoInline {
    private val sessions = java.util.Collections.newSetFromMap(
        java.util.WeakHashMap<Session, Boolean>())

    /** 在全屏菜单关闭实况时，同时停止详情页和全屏的所有播放会话。 */
    fun stopAll() { sessions.toList().forEach { it.stop() } }

    fun stop(image: View) {
        (image.getTag(R.id.live_photo_session) as? Session)?.stop()
    }

    fun replay(image: View) {
        (image.getTag(R.id.live_photo_session) as? Session)?.let { it.stop(); it.play() }
    }

    fun bind(image: View, url: String, handleLongPress: Boolean = true) {
        if (!LivePhoto.isLive(url)) return
        (image.getTag(R.id.live_photo_session) as? Session)?.let {
            if (it.url == url) { replay(image); return }
            it.stop()
            image.removeOnAttachStateChangeListener(it)
            sessions.remove(it)
        }
        val session = Session(image, url)
        sessions.add(session)
        image.setTag(R.id.live_photo_session, session)
        image.addOnAttachStateChangeListener(session)
        if (handleLongPress) {
            image.setOnLongClickListener {
                if (!PrefManager.showLivePhoto) false else {
                    replay(image)
                    true
                }
            }
        }
        image.post { session.play() }
    }

    private class Session(val image: View, val url: String) : View.OnAttachStateChangeListener {
        private var player: ExoPlayer? = null
        private var texture: TextureView? = null
        private var host: ViewGroup? = null
        private var generation = 0
        private var active = false
        private var autoStarted = false

        fun play() {
            if (!PrefManager.showLivePhoto || !image.isAttachedToWindow || active) return
            autoStarted = true
            active = true
            val request = ++generation
            LivePhotoResolver.resolveAsync(url) { signed ->
                if (request != generation) return@resolveAsync
                if (signed == null || !PrefManager.showLivePhoto || !image.isAttachedToWindow) {
                    active = false
                    return@resolveAsync
                }
                val parent = image.parent as? ViewGroup
                if (parent == null || image.width == 0 || image.height == 0) {
                    active = false
                    return@resolveAsync
                }
                host = parent
                val view = TextureView(image.context).apply {
                    isOpaque = false
                    layout(image.left, image.top, image.right, image.bottom)
                }
                texture = view
                parent.overlay.add(view)
                val exo = ExoPlayer.Builder(image.context).build()
                player = exo
                exo.volume = 0f
                exo.repeatMode = Player.REPEAT_MODE_OFF
                exo.setVideoTextureView(view)
                exo.addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                        if (size.width <= 0 || size.height <= 0) return
                        val ratio = size.width * size.pixelWidthHeightRatio / size.height
                        val width = minOf(image.width.toFloat(), image.height * ratio).toInt()
                        val height = (width / ratio).toInt()
                        val left = image.left + (image.width - width) / 2
                        val top = image.top + (image.height - height) / 2
                        view.layout(left, top, left + width, top + height)
                    }
                    override fun onRenderedFirstFrame() {
                        AppLog.i(LogModule.CARD, "LivePhoto", "INLINE first frame rendered")
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            AppLog.i(LogModule.CARD, "LivePhoto", "INLINE completed; restore cover")
                            stop()
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        AppLog.w(LogModule.CARD, "LivePhoto", "INLINE error=${error.errorCodeName}")
                        stop()
                    }
                })
                // 仅图床请求头；绝不把 API Cookie/Token 发送给视频下载地址。
                val factory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setUserAgent(PrefManager.USER_AGENT)
                    .setDefaultRequestProperties(mapOf("Referer" to "https://www.coolapk.com/"))
                exo.setMediaSource(ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri(signed)))
                exo.prepare()
                exo.playWhenReady = true
            }
        }

        fun stop() {
            generation++
            active = false
            player?.release()
            player = null
            texture?.let { host?.overlay?.remove(it) }
            texture = null
            host = null
        }

        override fun onViewDetachedFromWindow(v: View) { stop() }
        override fun onViewAttachedToWindow(v: View) {
            if (!autoStarted) image.post { if (!autoStarted) play() }
        }
    }
}
