package net.mikaelzero.mojito.view.sketch.core.drawable

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.gif.GifDrawable
import net.mikaelzero.mojito.view.sketch.core.decode.ImageAttrs
import net.mikaelzero.mojito.view.sketch.core.request.ImageFrom

/** Holds one Glide decoder and frame loader, owned exclusively by a single Sketch request. */
class GlideSketchGifDrawable(
    private var resource: Resource<GifDrawable>?,
    private val requestKey: String,
    private val requestUri: String,
    private val attrs: ImageAttrs,
    private val source: ImageFrom,
) : Drawable(), Drawable.Callback, SketchGifDrawable {
    private val gif = checkNotNull(resource).get()
    private val width = gif.intrinsicWidth
    private val height = gif.intrinsicHeight
    private val config = gif.firstFrame.config
    private var started = false
    private var pageVisible = true

    init {
        gif.callback = this
        gif.setLoopCount(GifDrawable.LOOP_INTRINSIC)
    }

    override fun draw(canvas: Canvas) {
        if (!isRecycled()) gif.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) { gif.bounds = bounds }
    override fun getIntrinsicWidth() = width
    override fun getIntrinsicHeight() = height
    private var drawingAlpha = 255
    override fun setAlpha(alpha: Int) {
        drawingAlpha = alpha
        gif.setAlpha(alpha)
    }
    override fun getAlpha() = drawingAlpha
    override fun setColorFilter(colorFilter: ColorFilter?) { gif.setColorFilter(colorFilter) }
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)

    override fun start() {
        if (isRecycled() || started) return
        started = true
        updateVisibility()
        gif.start()
    }

    override fun stop() {
        started = false
        if (!isRecycled()) gif.stop()
    }

    override fun isRunning() = !isRecycled() && gif.isRunning

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        updateVisibility()
        return changed
    }

    fun setPageVisible(visible: Boolean) {
        pageVisible = visible
        updateVisibility()
    }

    private fun updateVisibility() {
        if (!isRecycled()) gif.setVisible(isVisible && pageVisible && callback != null, false)
    }

    /** Independent, bounded placeholder; Glide may reclaim the current frame at any time. */
    fun snapshot(resources: Resources): Drawable? {
        if (isRecycled()) return null
        val scale = minOf(1f, 512f / maxOf(width, height))
        val bitmap = Bitmap.createBitmap(
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val previousBounds = Rect(gif.bounds)
        try {
            gif.setBounds(0, 0, bitmap.width, bitmap.height)
            gif.draw(Canvas(bitmap))
        } finally {
            gif.bounds = previousBounds
        }
        return BitmapDrawable(resources, bitmap)
    }

    override fun isRecycled() = resource == null

    override fun recycle() {
        val owned = resource ?: return
        stop()
        resource = null
        gif.callback = null
        owned.recycle()
    }

    override fun getKey() = requestKey
    override fun getUri() = requestUri
    override fun getOriginWidth() = attrs.width
    override fun getOriginHeight() = attrs.height
    override fun getMimeType(): String = attrs.mimeType
    override fun getExifOrientation() = attrs.exifOrientation
    override fun getByteCount() = if (isRecycled()) 0 else gif.size
    override fun getBitmapConfig(): Bitmap.Config? = config
    override fun getImageFrom() = source
    override fun getInfo() = "Glide GIF ${attrs.width}x${attrs.height} ($source)"
}
