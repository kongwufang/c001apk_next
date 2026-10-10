package com.example.c001apk.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.c001apk.R
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.FeedVideo
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.dp
import com.google.android.material.color.MaterialColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 动态卡里的视频槽位（对应官方卡片模板的 `video_player_space_view`）。
 *
 * 单独开一个控件的原因：视频动态的 `pic` / `picArr` 实测是空的，封面只在 `media_info.cover` 里，
 * 九图槽位对它完全不出图 —— 没有这个槽，整块媒体区是空白（详见 [FeedVideo] 的口径说明）。
 *
 * 为什么是 ExoPlayer 而不是内置 `VideoView`（实测逼出来的，不是选型洁癖）：
 *  - B 站给的是 **DASH**，`urlList` 只有画面、`audioList` 才是声音，`VideoView` 没有合流能力；
 *  - B 站还要带 `Referer`，`VideoView` / `MediaPlayer` 设不了请求头。
 *
 * 行为：
 *  - 有地址（酷安自托管直链，或已解析过）→ 点一下内嵌播放；播放中再点暂停
 *  - 没地址但有解析参数（微博 / B 站）→ 点一下先请求 `/v6/player/getUrl` 再播，期间出转圈
 *  - 解析不出来 / 播放失败 → 收回播放器恢复封面，并把点击让回卡片（进详情页），
 *    不留一个点不动的播放按钮
 *  - 播放中滑出屏幕（detach）或重新绑定 → 一定停，不残留声音
 *
 * 视频槽与九图槽在数据上互斥，所以两者共用同一个位置，靠可见性决定谁显示。
 */
class FeedVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cover = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(
            MaterialColors.getColor(
                this@FeedVideoView,
                com.google.android.material.R.attr.colorSurfaceVariant,
                0
            )
        )
    }
    private val playIcon = ImageView(context).apply {
        setImageResource(R.drawable.ic_feed_play)
    }
    private val loading = ProgressBar(context).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(
            MaterialColors.getColor(
                this@FeedVideoView,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                Color.WHITE
            )
        )
    }
    private val durationText = TextView(context).apply {
        setTextColor(0xFFFFFFFF.toInt())
        textSize = 12f
        setBackgroundResource(R.drawable.bg_video_duration)
        setPadding(6.dp, 2.dp, 6.dp, 2.dp)
    }
    private val progressBar = ProgressBar(
        context, null, android.R.attr.progressBarStyleHorizontal
    ).apply {
        max = PROGRESS_MAX
        progressDrawable = context.getDrawable(R.drawable.bg_video_progress)
    }
    private val playerView = PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        setShutterBackgroundColor(Color.TRANSPARENT)
    }

    private var player: ExoPlayer? = null
    private var video: FeedVideo? = null

    /** 本条数据已经播失败过：不再给播放按钮，点击也让回卡片 */
    private var playFailed = false

    /** 解析失败过：同上，避免反复点反复打接口 */
    private var resolveFailed = false
    private var resolving = false

    private var aspect = FeedVideo.DEFAULT_ASPECT
    private var scope: CoroutineScope? = null
    private var resolveJob: Job? = null
    private var progressJob: Job? = null

    /** 解析接口要走工程现有的 Hilt 依赖图（`NetworkRepo`），不能在控件里新起一套网络栈 */
    private val repo: NetworkRepo by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext, VideoResolveEntryPoint::class.java
        ).networkRepo()
    }

    init {
        addView(cover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(playIcon, LayoutParams(48.dp, 48.dp, Gravity.CENTER))
        addView(loading, LayoutParams(36.dp, 36.dp, Gravity.CENTER))
        addView(
            progressBar,
            LayoutParams(LayoutParams.MATCH_PARENT, 2.dp, Gravity.BOTTOM)
        )
        addView(
            durationText,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.BOTTOM
            ).apply { setMargins(0, 0, 10.dp, 10.dp) }
        )
        playerView.visibility = View.GONE
        progressBar.visibility = View.GONE
        loading.visibility = View.GONE
        visibility = View.GONE
    }

    fun bind(data: HomeFeedResponse.Data?) = bind(data?.let { FeedVideo.from(it) })

    fun bind(video: FeedVideo?) {
        resolveJob?.cancel()
        resolveJob = null
        resolving = false
        resolveFailed = false
        playFailed = false
        releasePlayer()

        this.video = video
        if (video == null) {
            visibility = View.GONE
            return
        }
        visibility = View.VISIBLE
        aspect = video.aspectRatio
        durationText.text = video.duration
        if (video.poster.isEmpty()) cover.setImageDrawable(null)
        else ImageUtil.showIMG(cover, video.poster)
        applyState()
        requestLayout()
    }

    /**
     * 可交互时才消费点击，否则点击要透传给卡片（进详情页）。
     * 复用的 ViewHolder 上 `setOnClickListener(null)` 不会把 clickable 复位，必须显式赋值。
     */
    private fun applyState() {
        val video = video
        val playing = player?.isPlaying == true
        val canInline = video != null && !playFailed && !resolving &&
                (video.playable || (video.resolvable && !resolveFailed))

        if (canInline) setOnClickListener { onSlotClick() } else setOnClickListener(null)
        isClickable = canInline
        playIcon.isVisible = canInline && !playing
        loading.isVisible = resolving
        progressBar.isVisible = playing
        durationText.isVisible = !playing && video?.duration?.isNotEmpty() == true
    }

    /** 封面比例（服务端给了宽高就按真实比例，否则 16:9），播放时由 PlayerView 自己按视频裁 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec((width / aspect).toInt(), MeasureSpec.EXACTLY)
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (scope == null) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    override fun onDetachedFromWindow() {
        releasePlayer()
        resolveJob?.cancel()
        resolveJob = null
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    private fun onSlotClick() {
        val video = video ?: return
        val existing = player
        if (existing != null) {
            if (existing.isPlaying) existing.pause() else existing.play()
            applyState()
            return
        }
        when {
            video.playable -> preparePlayer(video)
            video.resolvable && !resolveFailed -> resolveAndPlay(video)
        }
    }

    /** 请求 `/v6/player/getUrl` 换可播地址；失败就退回封面并把点击让给卡片 */
    private fun resolveAndPlay(video: FeedVideo) {
        val active = scope ?: return
        resolveJob?.cancel()
        resolving = true
        applyState()
        resolveJob = active.launch {
            val resolved = repo.getVideoUrl(video.requestParams).firstOrNull()
                ?.getOrNull()
                ?.data
                ?.let { video.withResolved(it) }
            resolving = false
            if (resolved == null || !resolved.playable) {
                resolveFailed = true
                applyState()
                return@launch
            }
            this@FeedVideoView.video = resolved
            preparePlayer(resolved)
        }
    }

    private fun preparePlayer(video: FeedVideo) {
        val http = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(video.headers)
            .setAllowCrossProtocolRedirects(true)
        val exo = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            setMediaSource(buildSource(video, http))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playFailed = true
                    releasePlayer()
                    applyState()
                }
            })
            prepare()
        }
        player = exo
        playerView.player = exo
        playerView.visibility = View.VISIBLE
        cover.visibility = View.INVISIBLE
        startProgressLoop()
        applyState()
    }

    /**
     * B 站是 DASH：画面和声音分两条，必须 [MergingMediaSource] 合流，否则只有画没有声。
     * `.m4s` 是 fragmented MP4，ExoPlayer 推断不出容器类型，得手工把 MIME 告诉它。
     */
    private fun buildSource(
        video: FeedVideo,
        http: DefaultHttpDataSource.Factory
    ): MediaSource {
        val factory = ProgressiveMediaSource.Factory(http)
        val videoSource = factory.createMediaSource(
            mediaItem(video.url, MimeTypes.VIDEO_MP4)
        )
        if (video.audioUrl.isEmpty()) return videoSource
        val audioSource = factory.createMediaSource(
            mediaItem(video.audioUrl, MimeTypes.AUDIO_MP4)
        )
        return MergingMediaSource(videoSource, audioSource)
    }

    private fun mediaItem(url: String, dashMime: String): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .setMimeType(if (url.lowercase().contains(".m4s")) dashMime else null)
            .build()

    private fun startProgressLoop() {
        val active = scope ?: return
        progressJob?.cancel()
        progressJob = active.launch {
            while (isActive) {
                val exo = player ?: break
                val total = exo.duration
                if (total > 0) {
                    progressBar.progress =
                        (exo.currentPosition * PROGRESS_MAX / total).toInt()
                }
                delay(PROGRESS_INTERVAL)
            }
        }
    }

    /** 停播并收回播放器，恢复封面。任何状态异常都不许把控件留在半播状态 */
    private fun releasePlayer() {
        progressJob?.cancel()
        progressJob = null
        val exo = player
        if (exo != null) {
            player = null
            runCatching {
                exo.stop()
                exo.release()
            }
        }
        playerView.player = null
        playerView.visibility = View.GONE
        cover.visibility = View.VISIBLE
        if (video != null) applyState()
    }

    private companion object {
        const val PROGRESS_MAX = 1000
        const val PROGRESS_INTERVAL = 500L
    }
}

/**
 * 只为让 [FeedVideoView] 拿到 [NetworkRepo]。控件不是 Hilt 注入点（是 `new` 出来的），
 * 走 EntryPoint 比在控件里自己建 Retrofit 干净。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface VideoResolveEntryPoint {
    fun networkRepo(): NetworkRepo
}
