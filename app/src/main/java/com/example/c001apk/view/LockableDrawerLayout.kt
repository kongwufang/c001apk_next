package com.example.c001apk.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.customview.widget.ViewDragHelper
import kotlin.math.abs

/**
 * 可以开关「全屏拖动拉抽屉」的 DrawerLayout（2026-10-25 ✓）
 *
 * ## 为什么要改 `ViewDragHelper` 的 `mEdgeSize`
 * 「我的」侧栏的拖动**必须跟手** ✓ —— 用户实测反馈：
 *
 * > 「我感觉他的更容易被触发，应该是反馈比较完整，因为他滑动的同时侧边栏会
 * >   一点点伸出，而我们的是一瞬间飞到手指的位置，看起来就很别扭」
 *
 * 一开始我是在 [onInterceptTouchEvent] 里识别一次手势然后 `openDrawer()` ✗
 * ⇒ 手指才移了 72dp，抽屉**已经全开** ✗ 而且手指还停在那儿 ⇒ 很别扭 ✗
 *
 * `DrawerLayout` 自己带的那套拖动**本来就是跟手的** ✓（`ViewDragHelper` +
 * `offsetLeftAndRight` ✓）——唯一的问题是它只在**左边缘 20dp** 内起手 ✗
 *
 * ⇒ 反射把 `mLeftDragger.mEdgeSize` 改成**整个屏幕宽** ✓
 *   ⇒ 从屏幕**任何地方**横向拖动，抽屉都跟手伸出 ✓✓
 *   ⇒ 手感跟 rikkahub 的 `ModalNavigationDrawer`（`anchoredDraggable`）一致 ✓
 *
 * ## 关掉时
 * `edgeSwipeEnabled = false` ⇒ `mEdgeSize` 设回 **0** ✓
 * ⇒ 完全不响应拖动 ✓ 但 `openDrawer()`（顶栏 ☰）照常 ✓
 *
 * ## 反射风险
 * `mLeftDragger` / `mEdgeSize` 从 API 21 到 35 都没改过名字 ✓
 * 但仍然 `runCatching` 兜底 ✓ —— 万一失败就退回「识别一次手势 + openDrawer」✓
 * （手感差一点 ✗ 但功能不会丢 ✓）
 *
 * ## 为什么只在「头条」tab 生效
 * 头条是**纵向**信息流 ✓ 横向手势不打架 ✓
 * 「关注 / 应用」等 tab 内部是 `ViewPager2` ✗ 会被抢 ✗
 */
class LockableDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DrawerLayout(context, attrs, defStyleAttr) {

    /**
     * 是否允许「横向拖动拉抽屉」
     *
     * `false` 只是**关掉拖动** ✓ 程序化的 [openDrawer] 依然有效 ✓
     */
    var edgeSwipeEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            applyEdgeMode()
        }

    /** 反射成功了没有（失败就退回手工识别 ✓） */
    private var helperPatched = false
    private var leftDragger: ViewDragHelper? = null
    private var edgeSizeField: java.lang.reflect.Field? = null
    private var baseEdgeSize = 0

    // ── 反射失败时的兜底：识别一次手势再开（跟不了手 ✗ 但能用 ✓）──
    private val fallbackThreshold = (48 * resources.displayMetrics.density).toInt()
    private var downX = 0f
    private var downY = 0f
    private var consumed = false

    init {
        helperPatched = runCatching {
            val df = DrawerLayout::class.java.getDeclaredField("mLeftDragger")
            df.isAccessible = true
            val helper = df.get(this) as ViewDragHelper
            val ef = ViewDragHelper::class.java.getDeclaredField("mEdgeSize")
            ef.isAccessible = true
            leftDragger = helper
            edgeSizeField = ef
            baseEdgeSize = ef.getInt(helper)
            true
        }.getOrDefault(false)
    }

    /**
     * 把 `mEdgeSize` 在「全屏」和「0」之间切
     *
     * `mEdgeSize` 是 `ViewDragHelper` 判断「这次触摸算不算从边缘开始」的距离 ✗
     * 设成屏幕宽 ⇒ 任何位置都算边缘 ✓
     */
    private fun applyEdgeMode() {
        val helper = leftDragger ?: return
        val field = edgeSizeField ?: return
        runCatching {
            field.setInt(helper, if (edgeSwipeEnabled) maxOf(width, 1) else 0)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // 尺寸变了要重设一次（width 之前可能是 0 ✓）
        applyEdgeMode()
        // 顺手把左边一条窄缝从「系统返回手势」里抢出来（API 29+ ✓）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val w = (24 * resources.displayMetrics.density).toInt()
                systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, w, height))
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 反射成功 ⇒ 完全交给 DrawerLayout 自己的 ViewDragHelper ✓ 跟手 ✓
        if (helperPatched) return super.onInterceptTouchEvent(ev)

        // ── 兜底路径（反射失败）──
        if (isDrawerOpen(GravityCompat.START) || !edgeSwipeEnabled) {
            consumed = false
            return super.onInterceptTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                consumed = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (consumed) return true
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (dx > fallbackThreshold && abs(dx) > abs(dy) * 1.5f) {
                    consumed = true
                    openDrawer(GravityCompat.START)
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> consumed = false
        }
        return super.onInterceptTouchEvent(ev)
    }
}
