package com.example.c001apk.ui.feed

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.example.c001apk.MyApplication
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.databinding.ItemFeedArticleTextBinding
import com.example.c001apk.logic.model.FeedArticleContentBean
import com.example.c001apk.view.SelectableLinkMovementMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArticleTextBindingTest {
    @Test fun boundArticleKeepsSelectionAndOpensTheCurrentInlineLinkOnTap() {
        MyApplication.context = RuntimeEnvironment.getApplication()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val binding = ItemFeedArticleTextBinding.inflate(activity.layoutInflater)
        val holder = FeedDataAdapter.TextViewHolder(binding, object : ItemListener {})
        val text = binding.textView
        activity.setContentView(binding.root)

        fun bindLink(id: String) {
            holder.bind(FeedArticleContentBean.Data(
                type = "text", message = "Before <a href=\"/feed/$id\">linked article</a> after",
                url = null, description = null, title = null, subTitle = null, logo = null,
            ))
            assertTrue(text.isTextSelectable)
            assertTrue(text.movementMethod is SelectableLinkMovementMethod)
            text.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
            text.layout(0, 0, 800, 200)
        }

        fun tapLink(heldFor: Long = 40) {
            val buffer = text.text as Spannable
            val span = buffer.getSpans(0, buffer.length, ClickableSpan::class.java).single()
            val start = buffer.getSpanStart(span)
            val end = buffer.getSpanEnd(span)
            val x = text.layout.getPrimaryHorizontal(start + 3)
            check(text.layout.getOffsetForHorizontal(0, x) in start until end)
            val y = text.totalPaddingTop + (text.layout.getLineTop(0) + text.layout.getLineBottom(0)) / 2f
            val down = SystemClock.uptimeMillis()
            fun event(action: Int, elapsed: Long) {
                MotionEvent.obtain(down, down + elapsed, action, x + text.totalPaddingLeft, y, 0).also {
                    text.onTouchEvent(it)
                    it.recycle()
                }
            }
            event(MotionEvent.ACTION_DOWN, 0)
            event(MotionEvent.ACTION_UP, heldFor)
        }

        val launched = shadowOf(activity)
        bindLink("123")
        Selection.removeSelection(text.text as Spannable)
        tapLink(ViewConfiguration.getLongPressTimeout() + 20L)
        assertNull(launched.nextStartedActivity)
        bindLink("456")
        Selection.removeSelection(text.text as Spannable)
        tapLink()
        val intent = launched.nextStartedActivity
        assertEquals(FeedActivity::class.java.name, intent.component?.className)
        assertEquals("456", intent.getStringExtra("id"))
        assertNull(launched.nextStartedActivity)
    }
}
