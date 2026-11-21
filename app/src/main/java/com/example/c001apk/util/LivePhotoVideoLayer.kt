package com.example.c001apk.util

import android.graphics.Paint
import android.view.TextureView
import android.view.View
import androidx.media3.common.VideoSize
import kotlin.math.roundToInt

/** Keep the cover visible until the first decoded frame can be drawn at its fitted size. */
internal class LivePhotoVideoLayer(private val image: View) {
    val texture = TextureView(image.context).apply {
        isOpaque = false
        // Keep the TextureView drawable so Android creates its SurfaceTexture. Hiding the
        // view itself (or setting View.alpha = 0) can stop that on some devices.
        setLayerPaint(Paint().apply { alpha = 0 })
        layout(image.left, image.top, image.right, image.bottom)
    }

    private var hasFittedBounds = false
    private var hasFirstFrame = false
    var isRevealed = false
        private set

    fun onVideoSizeChanged(size: VideoSize) {
        if (size.width <= 0 || size.height <= 0 || image.width <= 0 || image.height <= 0) return
        val ratio = size.width * size.pixelWidthHeightRatio.toDouble() / size.height
        if (!ratio.isFinite() || ratio <= 0) return

        val fitHeight = image.width / ratio > image.height
        val width = if (fitHeight) (image.height * ratio).roundToInt().coerceAtLeast(1) else image.width
        val height = if (fitHeight) image.height else (image.width / ratio).roundToInt().coerceAtLeast(1)
        val left = image.left + (image.width - width) / 2
        val top = image.top + (image.height - height) / 2
        texture.layout(left, top, left + width, top + height)
        hasFittedBounds = true
        revealIfReady()
    }

    fun onRenderedFirstFrame() {
        hasFirstFrame = true
        revealIfReady()
    }

    private fun revealIfReady() {
        if (!isRevealed && hasFittedBounds && hasFirstFrame) {
            isRevealed = true
            texture.setLayerPaint(null)
        }
    }
}
