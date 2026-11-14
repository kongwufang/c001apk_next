package com.example.c001apk.view


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.method.Touch
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.TextView
import com.example.c001apk.R
import com.example.c001apk.util.SpannableStringBuilderUtil
import io.noties.markwon.ext.tables.TableRowSpan
import rikka.material.widget.FakeFontWeightMaterialTextView

//https://stackoverflow.com/questions/8558732
class LinkTextView : FakeFontWeightMaterialTextView {

    override fun getHighlightColor(): Int {
        return Color.TRANSPARENT
    }

    private var dontConsumeNonUrlClicks = true
    var linkHit = false

    /**
     * 正文里的「查看链接」是否画成胶囊按钮（`app:linkAsChip`）。
     * 私信气泡要它一眼能认出来是能点的链接，默认 false 保持其它页面不变。
     */
    private var linkAsChip = false

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initAttrs(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) {
        initAttrs(attrs)
    }

    private fun initAttrs(attrs: AttributeSet?) {
        if (attrs == null) return
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LinkTextView)
        linkAsChip = ta.getBoolean(R.styleable.LinkTextView_linkAsChip, false)
        ta.recycle()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        linkHit = false
        val res = super.onTouchEvent(event)
        // 正文/评论显式开启 textIsSelectable 时，必须把完整触摸序列交给 TextView，
        // 否则非链接 ACTION_DOWN 会返回 false，系统选择框永远不会出现。
        if (isTextSelectable) return res
        return if (dontConsumeNonUrlClicks) linkHit else res
    }

    class LocalLinkMovementMethod : LinkMovementMethod() {
        override fun onTouchEvent(
            widget: TextView,
            buffer: Spannable, event: MotionEvent
        ): Boolean {
            val action = event.action
            if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_DOWN
            ) {
                var x = event.x.toInt()
                var y = event.y.toInt()
                x -= widget.totalPaddingLeft
                y -= widget.totalPaddingTop
                x += widget.scrollX
                y += widget.scrollY
                val layout = widget.layout
                val isOutOfLineBounds: Boolean = if (y < 0 || y > layout.height) {
                    true
                } else {
                    val line = layout.getLineForVertical(y)
                    (x < layout.getLineLeft(line) || x > layout.getLineRight(line))
                }
                if (isOutOfLineBounds) {
                    Selection.removeSelection(buffer)
                    return Touch.onTouchEvent(widget, buffer, event)
                }
                val line = layout.getLineForVertical(y)
                val off = layout.getOffsetForHorizontal(line, x.toFloat())
                val link = buffer.getSpans(
                    off, off, ClickableSpan::class.java
                )
                if (link.isNotEmpty()) {
                    if (action == MotionEvent.ACTION_UP) {
                        link[0].onClick(widget)
                    }/* else if (action == MotionEvent.ACTION_DOWN) {
                        Selection.setSelection(
                            buffer,
                            buffer.getSpanStart(link[0]),
                            buffer.getSpanEnd(link[0])
                        )
                    }*/
                    val linkText =
                        buffer.substring(buffer.getSpanStart(link[0]), buffer.getSpanEnd(link[0]))
                    if (widget is LinkTextView) {
                        widget.linkHit = linkText != "查看更多"
                    }
                    return true
                } else {
                    Selection.removeSelection(buffer)
                    return Touch.onTouchEvent(widget, buffer, event)
                }
            }
            return super.onTouchEvent(widget, buffer, event)
        }

        companion object {
            private var sInstance: LocalLinkMovementMethod? = null
            val instance: LocalLinkMovementMethod?
                get() {
                    if (sInstance == null) sInstance = LocalLinkMovementMethod()
                    return sInstance
                }
        }
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        this.movementMethod = LocalLinkMovementMethod.instance
        val spText =
            SpannableStringBuilderUtil.setText(
                context,
                text.toString(),
                this.textSize,
                null,
                linkAsChip = linkAsChip
            )
        super.setText(spText, type)
        scheduleTableRows(spText)
    }

    /**
     * Markwon 的 markdown 表格行高要靠「绘制一次后再重新测量一次」才能收敛，
     * 原生由 `Markwon.setText` 内部的 TableRowsScheduler 负责；
     * 我们不走 Markwon.setText（正文统一由 SpannableStringBuilderUtil 渲染），
     * 所以要自己把 Invalidator 绑上，否则首轮行高偏小、单元格文字会溢出格子。
     */
    private fun scheduleTableRows(text: Spanned) {
        val rows = text.getSpans(0, text.length, TableRowSpan::class.java)
        if (rows.isEmpty() || isInEditMode) return
        val invalidator = object : TableRowSpan.Invalidator {
            private val runnable = Runnable {
                val current = this@LinkTextView.text
                if (current is Spanned &&
                    current.getSpans(0, current.length, TableRowSpan::class.java).isNotEmpty()
                ) setRawText(current)
            }

            override fun invalidate() {
                removeCallbacks(runnable)
                post(runnable)
            }
        }
        rows.forEach { it.invalidator(invalidator) }
    }

    /** 绕过本类的正文渲染，直接把已有 Spanned 交给父类重新排版（避免重复解析 markdown） */
    private fun setRawText(t: CharSequence) {
        super.setText(t, BufferType.SPANNABLE)
    }

}