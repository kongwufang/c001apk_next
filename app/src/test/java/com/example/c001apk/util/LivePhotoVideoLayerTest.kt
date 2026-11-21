package com.example.c001apk.util

import android.app.Activity
import android.graphics.Rect
import android.view.View
import androidx.media3.common.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LivePhotoVideoLayerTest {
    @Test fun firstFrameBeforeSizeDoesNotExposeStretchedVideo() {
        val layer = layer()
        assertFalse(layer.isRevealed)
        layer.onRenderedFirstFrame()
        layer.onVideoSizeChanged(VideoSize.UNKNOWN)
        assertFalse(layer.isRevealed)

        layer.onVideoSizeChanged(VideoSize(1600, 900))
        assertEquals(Rect(10, 317, 730, 722), layer.bounds())
        assertTrue(layer.isRevealed)
    }

    @Test fun fittedPortraitWaitsForFirstFrameAndUsesPixelAspectRatio() {
        val layer = layer()
        layer.onVideoSizeChanged(VideoSize(540, 1920, 0, 1.5f))
        assertEquals(Rect(159, 20, 581, 1020), layer.bounds())
        assertFalse(layer.isRevealed)

        layer.onRenderedFirstFrame()
        assertTrue(layer.isRevealed)
    }

    private fun layer(): LivePhotoVideoLayer {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        return LivePhotoVideoLayer(View(activity).apply { layout(10, 20, 730, 1020) })
    }

    private fun LivePhotoVideoLayer.bounds() = with(texture) { Rect(left, top, right, bottom) }
}
