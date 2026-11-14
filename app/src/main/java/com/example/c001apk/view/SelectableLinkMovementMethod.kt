package com.example.c001apk.view

import android.text.Spannable
import android.text.method.ArrowKeyMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView

/**
 * 系统 ArrowKeyMovementMethod 支持任意区间选择（LinkMovementMethod 不支持）。
 * 仅补充没有长按、拖动、既有选区时的链接短按，其余手势交给 TextView/Editor。
 * 每个 TextView 独立实例，不能共享一次手势的状态。
 */
class SelectableLinkMovementMethod : ArrowKeyMovementMethod() {
    private var downX = 0f
    private var downY = 0f
    private var downLink: ClickableSpan? = null
    private var startedWithSelection = false

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                startedWithSelection = widget.hasSelection()
                downLink = linkAt(widget, buffer, event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val slop = ViewConfiguration.get(widget.context).scaledTouchSlop
                if (kotlin.math.abs(event.x - downX) > slop ||
                    kotlin.math.abs(event.y - downY) > slop) downLink = null
            }
            MotionEvent.ACTION_UP -> {
                val link = downLink
                downLink = null
                if (link != null && !startedWithSelection && !widget.hasSelection() &&
                    event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout() &&
                    link === linkAt(widget, buffer, event.x, event.y)) {
                    super.onTouchEvent(widget, buffer, event)
                    link.onClick(widget)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> downLink = null
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    private fun linkAt(widget: TextView, text: Spannable, x: Float, y: Float): ClickableSpan? {
        val layout = widget.layout ?: return null
        val localX = x - widget.totalPaddingLeft + widget.scrollX
        val localY = y - widget.totalPaddingTop + widget.scrollY
        if (localY < 0 || localY >= layout.height) return null
        val line = layout.getLineForVertical(localY.toInt())
        if (localX < layout.getLineLeft(line) || localX > layout.getLineRight(line)) return null
        val offset = layout.getOffsetForHorizontal(line, localX)
        return text.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
    }

    companion object {
        fun applyTo(view: TextView) {
            if (!view.isTextSelectable) return
            // 强制保留系统选择能力，避免 DataBinding/自定义 setText 覆盖属性。
            view.setTextIsSelectable(true)
            view.isLongClickable = true
            view.isFocusableInTouchMode = true
            if (view.movementMethod !is SelectableLinkMovementMethod) {
                view.movementMethod = SelectableLinkMovementMethod()
            }
        }
    }
}
