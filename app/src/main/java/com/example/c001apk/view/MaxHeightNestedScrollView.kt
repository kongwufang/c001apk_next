package com.example.c001apk.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.widget.NestedScrollView

/**
 * 限高滚动容器（2026-11-15 新增，侧栏升级卡的更新日志用）。
 *
 * 为什么需要它：更新日志可能很长（现网 stable.json 就有 30 行），
 * 以前的写法是 `TextView` + `maxLines=6` + `ellipsize=end` ⇒ 后面的内容被截断，
 * 用户看不到完整内容；而把卡片整体拉高又会在侧栏里顶掉下面的按钮。
 *
 * 现在的行为：
 *   - 内容比 [maxContentHeight] 矮 ⇒ 高度跟随内容（短日志不会留一大块空白）；
 *   - 内容超过上限       ⇒ 高度固定在上限，内部纵向滚动，能一直滑到末行。
 *
 * 实现要点：只在父容器给的是 AT_MOST / UNSPECIFIED 时才把规格收紧成 AT_MOST(上限)，
 * 这样 `wrap_content` 的语义仍然是“内容多高就多高”，只是被上限截住；
 * 父容器若明确给了 EXACTLY 高度（例如以后有人写死 200dp），则尊重父容器。
 */
class MaxHeightNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : NestedScrollView(context, attrs, defStyleAttr) {

    /** 内容区最大高度（px）。默认 120dp ≈ 旧版 6 行 13sp 文本的高度。 */
    var maxContentHeight: Int = (DEFAULT_MAX_HEIGHT_DP * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val max = maxContentHeight
        val capped = if (max > 0 && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, capped)
    }

    /**
     * 侧栏这张卡在 RecyclerView 里：父列表会先把纵向拖动拦走，
     * 内部的 ScrollView 就永远滚不动（2026-11-15 实机验证：滑动前后画面完全一致）。
     *
     * 处理：手指落在日志区时先 requestDisallowIntercept，让本控件自己消费这次纵向拖动；
     * 抬手/取消再还给父列表，列表照常可以滚。
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(ev)
    }

    private companion object {
        const val DEFAULT_MAX_HEIGHT_DP = 120f
    }
}
