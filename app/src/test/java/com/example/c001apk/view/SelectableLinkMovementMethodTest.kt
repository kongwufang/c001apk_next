package com.example.c001apk.view

import android.app.Activity
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SelectableLinkMovementMethodTest {
    private class Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val view = TextView(activity)
        var clicks = 0
        val link = object : ClickableSpan() {
            override fun onClick(widget: View) { clicks++ }
        }
        val start = 7
        val end = 14
        val text: Spannable get() = view.text as Spannable

        init {
            view.setTextIsSelectable(true)
            view.textSize = 20f
            view.text = SpannableString("before picture after").apply {
                setSpan(link, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            SelectableLinkMovementMethod.applyTo(view)
            activity.setContentView(view)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 800, 200)
            Selection.removeSelection(text)
        }

        val x: Float get() = view.totalPaddingLeft +
            view.layout.getPrimaryHorizontal(start + 2)
        val y: Float get() = view.totalPaddingTop +
            (view.layout.getLineTop(0) + view.layout.getLineBottom(0)) / 2f

        fun event(action: Int, x: Float = this.x, elapsed: Long = 0,
                  down: Long = SystemClock.uptimeMillis()) {
            MotionEvent.obtain(down, down + elapsed, action, x, y, 0).also {
                view.onTouchEvent(it)
                it.recycle()
            }
        }

        fun tap(x: Float = this.x) {
            val down = SystemClock.uptimeMillis()
            event(MotionEvent.ACTION_DOWN, x, down = down)
            event(MotionEvent.ACTION_UP, x, elapsed = 40, down = down)
        }
    }

    @Test fun installsPerViewHandlerAndKeepsNativeSelection() {
        val a = Fixture()
        val b = Fixture()
        assertTrue(a.view.isTextSelectable)
        assertTrue(a.view.isLongClickable)
        assertTrue(a.view.movementMethod is SelectableLinkMovementMethod)
        assertNotSame(a.view.movementMethod, b.view.movementMethod)
        val method = a.view.movementMethod
        SelectableLinkMovementMethod.applyTo(a.view)
        assertSame(method, a.view.movementMethod)
    }

    @Test fun shortTapDispatchesPictureLinkExactlyOnce() {
        val f = Fixture()
        f.tap()
        assertEquals(1, f.clicks)
        assertFalse(f.view.hasSelection())
    }

    @Test fun plainTextAndTrailingWhitespaceNeverOpenPicture() {
        val f = Fixture()
        f.tap(f.view.layout.getPrimaryHorizontal(2))
        f.tap(790f)
        assertEquals(0, f.clicks)
    }

    @Test fun existingSelectionKeepsCopyInteractionInsteadOfOpeningLink() {
        val f = Fixture()
        Selection.setSelection(f.text, 0, 4)
        f.tap()
        assertEquals(0, f.clicks)
    }

    @Test fun longPressDoesNotOpenPicture() {
        val f = Fixture()
        val down = SystemClock.uptimeMillis()
        f.event(MotionEvent.ACTION_DOWN, down = down)
        f.event(MotionEvent.ACTION_UP, elapsed = ViewConfiguration.getLongPressTimeout() + 20L,
            down = down)
        assertEquals(0, f.clicks)
    }

    @Test fun dragOffAndBackDoesNotBecomeLinkTap() {
        val f = Fixture()
        val down = SystemClock.uptimeMillis()
        f.event(MotionEvent.ACTION_DOWN, down = down)
        f.event(MotionEvent.ACTION_MOVE, x = f.x + 100, elapsed = 20, down = down)
        f.event(MotionEvent.ACTION_UP, elapsed = 40, down = down)
        assertEquals(0, f.clicks)
    }

    @Test fun cancellationClearsPendingLink() {
        val f = Fixture()
        val down = SystemClock.uptimeMillis()
        f.event(MotionEvent.ACTION_DOWN, down = down)
        f.event(MotionEvent.ACTION_CANCEL, elapsed = 20, down = down)
        f.event(MotionEvent.ACTION_UP, elapsed = 40, down = down)
        assertEquals(0, f.clicks)
    }
}
