package com.example.c001apk.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

/**
 * 可以关掉「左边缘右滑」的 DrawerLayout（2026-10-25 ✓）
 *
 * ## 为什么要这个类
 * 用户要求：「新增右滑拉出侧栏手势，但是这个必须要首页 tab 在左边第一个时才生效，
 *            避免和 tab 的横向滑动手势冲突」
 *
 * `DrawerLayout` 自带边缘滑动 ✓ 但没有「只关手势、不关程序化打开」的开关 ✗：
 * - [DrawerLayout.LOCK_MODE_LOCKED_CLOSED] 会连 `openDrawer()` 一起禁掉 ✗
 *   ⇒ 顶栏的 ☰ 也会失效 ✗（除非用「临时解锁 → 打开 → 再锁上」那种脏手法 ✗）
 * - 所以这里只拦 [onInterceptTouchEvent] ✓
 *   ⇒ 边缘滑动失效 ✓ 但 `openDrawer()` 照常 ✓
 *
 * ## 用法
 * ```kotlin
 * (binding.drawerLayout as LockableDrawerLayout).edgeSwipeEnabled = (tab == "头条")
 * ```
 *
 * ## 为什么只在「头条」tab 生效
 * 头条是**纵向**信息流 ✓ 没有横向手势 ⇒ 抢边缘滑动不会打架 ✓
 * 而「关注 / 应用」等 tab 内部是 `ViewPager2` ✗ 横向翻页和抽屉手势会抢 ✗
 * ⇒ 在那些 tab 上必须关掉手势 ✓ 只留 ☰ 按钮 ✓
 */
class LockableDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DrawerLayout(context, attrs, defStyleAttr) {

    /**
     * 是否允许从左边缘右滑拉出抽屉
     *
     * `false` 只是**关掉手势** ✓ 程序化的 [openDrawer] 依然有效 ✓
     */
    var edgeSwipeEnabled: Boolean = true

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // ⚠️ 抽屉**已经开着**时必须放行 ✓
        //    否则用户想拖回去关掉都拖不动 ✗（而且这是唯一的关闭手势 ✗）
        if (!edgeSwipeEnabled && !isDrawerOpen(GravityCompat.START)) return false
        return super.onInterceptTouchEvent(ev)
    }
}
