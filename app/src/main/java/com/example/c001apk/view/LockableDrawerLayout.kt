package com.example.c001apk.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.math.abs

/**
 * 可以关掉「右滑拉抽屉」的 DrawerLayout（2026-10-25 ✓）
 *
 * ## 为什么不用 `DrawerLayout` 自带的边缘滑动
 * 用户实测：「不能是左边缘右滑，得像 tab 切换那样在屏幕中间滑也可以，
 *            不然这样滑直接触发系统返回手势退出了（安卓 10 以上全面屏手势）」
 *
 * Android 10+ 的**手势导航**把屏幕左右两条边**留给自己**（返回手势）✗
 * ⇒ 从最左边起的滑动被系统吃掉 ✗ 抽屉根本收不到 ✗
 *
 * ⇒ 这里自己做手势识别：**屏幕任意位置**向右滑超过阈值就拉抽屉 ✓
 *   （不用改 `ViewDragHelper` 的私有 `EDGE_SIZE`，也不依赖手势导航设置 ✓）
 *
 * ## 为什么不直接用 `LOCK_MODE_LOCKED_CLOSED`
 * 它会连 `openDrawer()` 一起禁掉 ✗ ⇒ 顶栏的 ☰ 也失效 ✗
 * ⇒ 这里只拦触摸 ✓ `openDrawer()` 照常 ✓
 *
 * ## 为什么只在「头条」tab 生效
 * 头条是**纵向**信息流 ✓ 抢横向手势不会打架 ✓
 * 「关注 / 应用」等 tab 内部是 `ViewPager2` ✗ 横向翻页会被抢 ✗
 */
class LockableDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DrawerLayout(context, attrs, defStyleAttr) {

    /**
     * 是否允许「右滑拉开抽屉」
     *
     * `false` 只是**关掉手势** ✓ 程序化的 [openDrawer] 依然有效 ✓
     * 抽屉**已经开着**时也不受影响 ✓（左滑关回去照常）
     */
    var edgeSwipeEnabled: Boolean = true

    /** 触发阈值（dp → px，构造时算一次） */
    private val threshold: Int = (72 * resources.displayMetrics.density).toInt()

    private var downX = 0f
    private var downY = 0f

    /** 本次手势已经触发过了 ⇒ 不再重复开 */
    private var consumed = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 抽屉开着：完全不拦 ✓ 交给 super 处理「拖动关闭」✓
        if (isDrawerOpen(GravityCompat.START)) {
            consumed = false
            return super.onInterceptTouchEvent(ev)
        }

        // 关掉了手势：不拦 ✓（☰ 依然能开 ✓）
        if (!edgeSwipeEnabled) {
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
                // 向右、超过阈值、且明显比竖向更横（免得把上下滑动误判 ✗）
                if (dx > threshold && abs(dx) > abs(dy) * 1.5f) {
                    consumed = true
                    openDrawer(GravityCompat.START)
                    // 返回 true ⇒ 本次手势被消费 ✓ 不会再传给列表 ✓
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> consumed = false
        }
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * 把左边一条窄缝从「系统返回手势」里抢出来（API 29+ ✓ / 不支持的版本自动跳过 ✓）
     *
     * 这样从最左边起手也能拉抽屉 ✓ 不用非得从屏幕中间起手 ✓
     * ⚠️ 只排除 **24dp** 的窄条 ✓ 别排太多，否则系统返回手势会变难触发 ✗
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val w = (24 * resources.displayMetrics.density).toInt()
                systemGestureExclusionRects = listOf(
                    android.graphics.Rect(0, 0, w, height)
                )
            }
        }
    }
}
