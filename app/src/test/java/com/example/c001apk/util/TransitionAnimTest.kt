package com.example.c001apk.util

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import com.example.c001apk.MyApplication
import com.example.c001apk.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TransitionAnimTest {
    @Before fun reset() {
        MyApplication.context = RuntimeEnvironment.getApplication()
        // Robolectric can reuse the PrefManager singleton across fresh Application contexts.
        val field = PrefManager::class.java.getDeclaredField("pref").apply { isAccessible = true }
        val settings = field.get(PrefManager) as android.content.SharedPreferences
        settings.edit().clear().commit()
    }

    @Test fun defaultsToSystemAndDoesNotSendAnimationOverrides() {
        val context = LaunchRecorder(MyApplication.context)
        val activity = Robolectric.buildActivity(ReturnRecorder::class.java).setup().get()
        assertEquals(TransitionAnim.MODE_SYSTEM, PrefManager.animMode)
        TransitionAnim.startActivity(context, Intent())
        assertEquals(1, context.withoutOptions)
        assertEquals(0, context.withOptions)
        assertNull(TransitionAnim.enterOptionsCompat(context))
        assertNull(TransitionAnim.photoPickerOptions(context))
        TransitionAnim.applyReturn(activity)
        assertTrue(activity.overrides.isEmpty())
    }

    @Test fun floatingComposerSlidesWithSystemPageTransitions() {
        val context = LaunchRecorder(MyApplication.context)
        val activity = Robolectric.buildActivity(ReturnRecorder::class.java).setup().get()
        TransitionAnim.startActivity(context, Intent(), sheet = true)
        assertEquals(1, context.withOptions)
        val options = TransitionAnim.enterOptionsCompat(context, sheet = true)
        assertNotNull(options)
        assertEquals(R.anim.anim_bottom_sheet_slide_up, options?.toBundle()?.getInt("android:activity.animEnterRes"))
        assertEquals(0, options?.toBundle()?.getInt("android:activity.animExitRes"))
        TransitionAnim.applyReturn(activity, sheet = true)
        assertEquals(listOf(0 to R.anim.anim_bottom_sheet_slide_down), activity.overrides)
    }

    @Test fun legacyCustomChoicesAreRetainedButNotAppliedUntilOptIn() {
        PrefManager.animCurve = "m2"
        PrefManager.animType = "fade"
        PrefManager.animDuration = 200
        assertEquals(TransitionAnim.MODE_SYSTEM, PrefManager.animMode)
        val activity = Robolectric.buildActivity(ReturnRecorder::class.java).setup().get()
        val context = LaunchRecorder(MyApplication.context)
        PrefManager.animMode = TransitionAnim.MODE_CUSTOM
        TransitionAnim.startActivity(context, Intent())
        assertEquals(1, context.withOptions)
        assertNotNull(TransitionAnim.enterOptionsCompat(context))
        TransitionAnim.applyReturn(activity)
        assertEquals(listOf(R.anim.exp_fade_m2_200_fin to R.anim.exp_fade_m2_200_fout), activity.overrides)
        PrefManager.animMode = TransitionAnim.MODE_SYSTEM
        assertEquals(TransitionAnim.CURVE_ANDROID, PrefManager.animCurve)
        assertEquals("fade", PrefManager.animType)
        assertEquals(200, PrefManager.animDuration)
        assertNull(TransitionAnim.enterOptionsCompat(context))
    }

    @Test fun explicitNoAnimationIsNotTheSameAsSystemDefault() {
        PrefManager.animMode = TransitionAnim.MODE_CUSTOM
        PrefManager.animType = TransitionAnim.TYPE_NONE
        val activity = Robolectric.buildActivity(ReturnRecorder::class.java).setup().get()
        assertNotNull(TransitionAnim.enterOptionsCompat(activity))
        TransitionAnim.applyReturn(activity, sheet = true)
        assertEquals(listOf(0 to 0), activity.overrides)
    }

    @Test fun androidCurveReferencesPlatformResourceForBothLayers() {
        val resources = MyApplication.context.resources
        for (anim in listOf(R.anim.exp_slide_m2_300_rin, R.anim.exp_slide_m2_300_lout,
            R.anim.exp_slide_m2_300_lin, R.anim.exp_slide_m2_300_rout)) {
            resources.getAnimation(anim).use { parser ->
                while (parser.eventType != XmlPullParser.START_TAG && parser.eventType != XmlPullParser.END_DOCUMENT) {
                    parser.next()
                }
                assertEquals("translate", parser.name)
                assertEquals(android.R.interpolator.fast_out_slow_in, parser.getAttributeResourceValue(
                    "http://schemas.android.com/apk/res/android", "interpolator", 0,
                ))
            }
        }
    }

    class ReturnRecorder : Activity() {
        val overrides = mutableListOf<Pair<Int, Int>>()
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun overridePendingTransition(enterAnim: Int, exitAnim: Int) {
            overrides += enterAnim to exitAnim
        }
    }

    private class LaunchRecorder(context: Context) : ContextWrapper(context) {
        var withoutOptions = 0
        var withOptions = 0
        override fun startActivity(intent: Intent) { withoutOptions++ }
        override fun startActivity(intent: Intent, options: Bundle?) { withOptions++ }
    }
}
