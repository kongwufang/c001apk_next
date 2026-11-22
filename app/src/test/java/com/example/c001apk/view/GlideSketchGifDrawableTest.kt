package com.example.c001apk.view

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.widget.ImageView
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.resource.gif.ByteBufferGifDecoder
import java.nio.ByteBuffer
import net.mikaelzero.mojito.view.sketch.core.decode.ImageAttrs
import net.mikaelzero.mojito.view.sketch.core.drawable.GlideSketchGifDrawable
import net.mikaelzero.mojito.view.sketch.core.request.ImageFrom
import net.mikaelzero.mojito.view.sketch.core.request.LoadOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlideSketchGifDrawableTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun decode(): GlideSketchGifDrawable {
        // A 4×4 GIF with two 350 ms frames (red, blue) and infinite looping.
        val bytes = Base64.decode(
            "R0lGODlhBAAEAIEAAP8AAAAAAAAAAAAAACH5BAAjAAAAIf8LTkVUU0NBUEUyLjADAQAAACwAAAAABAAEAAAICQABCBxIsCCAgAAh+QQAIwAAACH/C05FVFNDQVBFMi4wAwEAAAAsAAAAAAQABACBAAD/AAAAAAAAAAAACAkAAQgcSLAggIAAOw==",
            Base64.DEFAULT,
        )
        val resource = checkNotNull(ByteBufferGifDecoder(context).decode(ByteBuffer.wrap(bytes), 4, 4, Options()))
        return GlideSketchGifDrawable(resource, "gif-key", "/cached-image", ImageAttrs("image/gif", 4, 4, 0), ImageFrom.LOCAL)
    }

    @Test fun drawsAndKeepsIndependentSnapshotAfterRecycle() {
        val drawable = decode()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 4, 4)
        drawable.draw(Canvas(bitmap))
        assertEquals(Color.RED, bitmap.getPixel(2, 2))
        val snapshot = drawable.snapshot(context.resources) as BitmapDrawable
        drawable.recycle()
        drawable.recycle()
        assertTrue(drawable.isRecycled())
        assertFalse(drawable.isRunning())
        assertEquals(Color.RED, snapshot.bitmap.getPixel(2, 2))
        assertEquals(4, drawable.intrinsicWidth)
        assertNull(drawable.snapshot(context.resources))
    }

    @Test fun playbackFollowsPageAndDrawableVisibilityWithoutSharingDecoder() {
        val first = decode()
        val second = decode()
        val firstView = ImageView(context)
        val secondView = ImageView(context)
        firstView.setImageDrawable(first)
        secondView.setImageDrawable(second)
        // Detached Robolectric views may set visibility false when the drawable is installed.
        first.setVisible(true, false)
        second.setVisible(true, false)
        try {
            first.setPageVisible(false)
            first.start()
            assertFalse(first.isRunning())
            first.setPageVisible(true)
            assertTrue(first.isRunning())
            first.setVisible(false, false)
            assertFalse(first.isRunning())
            first.setVisible(true, false)
            assertTrue(first.isRunning())
            second.start()
            assertTrue(second.isRunning())
            first.recycle()
            assertTrue(second.isRunning())
        } finally {
            firstView.setImageDrawable(null)
            secondView.setImageDrawable(null)
            first.recycle()
            second.recycle()
        }
    }

    @Test fun staticAndAnimatedRequestsUseDifferentCacheKeys() {
        val options = LoadOptions()
        val staticKey = options.makeKey()
        options.isDecodeGifImage = true
        assertNotEquals(staticKey, options.makeKey())
    }
}
