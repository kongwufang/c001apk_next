package com.example.c001apk.view

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.GravityCompat
import androidx.customview.widget.ViewDragHelper
import androidx.drawerlayout.widget.DrawerLayout
import kotlin.math.abs

/**
 * 可以开关「跟手拖动拉抽屉」的 DrawerLayout（2026-10-25 ✓ 真机验证 ✓）
 *
 * ## 核心：反射放开 `ViewDragHelper.mEdgeSize`
 * `DrawerLayout` 自带的拖动**本来就是跟手的** ✓（`mLeftDragger` + `offsetLeftAndRight`）
 * 唯一限制是「只在左边缘 20dp 内起手」✗
 * ⇒ 把 `mEdgeSize` 改成**屏幕宽** ⇒ 任何位置都能起手，且**跟手** ✓
 *
 * 为什么不用「识别手势 + openDrawer()」✗ —— 那样手指才移 72dp 抽屉就全开了 ✗
 * 用户原话：「我们的是一瞬间飞到手指的位置，看起来就很别扭」✗
 *
 * ## 双向手势分流（关键 ✗ 否则「切 tab」会失效）
 * | 手势 | 期望行为 |
 * |---|---|
 * | **向右**拖 | 拉出侧边栏 ✓ |
 * | **向左**拖 | 切到右边的 tab（`ViewPager2` 的活 ✓）|
 *
 * ⚠️ 但 `ViewDragHelper` 在 **ACTION_DOWN 那一刻**就记下「这次算不算边缘触摸」✗
 *    那时还不知道用户要往哪边划 ✗
 *
 * ⇒ 所以分两步：
 * 1. **DOWN 时把 `mEdgeSize` 设成 0** ⇒ `DrawerLayout` 不会抢 ✓
 *    `ViewPager2` 正常拿到 DOWN ✓ 向左滑照常切 tab ✓
 * 2. 在 MOVE 里判断：**向右且明显横于竖向** ⇒ 这才是要拉抽屉 ✓
 *    此时把 `mEdgeSize` 放回屏宽 ✓ **合成一个 ACTION_DOWN 吞给 super** ✓
 *    ⇒ `ViewDragHelper` 从这个合成 DOWN 开始跟手 ✓
 *    ⇒ 子 View 会被 `ViewGroup` 自动 CANCEL ✓ 不会两边都在滚 ✓
 *
 * ## 展开动画期间
 * `isDrawerVisible && !isDrawerOpen`（正在展开 ✓）⇒ 完全不拦截 ✓
 * ⇒ 按钮当场可点 ✓ 不用等动画走完 ✓
 * （`DrawerLayout` 在 settling 期间会吞子 View 的触摸 ✗）
 *
 * ## 关掉时
 * `edgeSwipeEnabled = false` ⇒ `mEdgeSize` 一直 0 ✓ 拖动无效 ✓
 * 但 `openDrawer()`（顶栏 ☰）照常 ✓
 *
 * ## 反射风险
 * `mLeftDragger` / `mEdgeSize` 从 API 21 到 35 名字没变过 ✓
 * 仍然 `runCatching` 兜底 ✓ —— 失败就退回「识别手势 + openDrawer」（手感差但能用 ✓）
 */
class LockableDrawerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : DrawerLayout(context, attrs, defStyleAttr) {

    /** 是否允许「向右拖动拉抽屉」。`false` 只关拖动 ✓ [openDrawer] 照常 ✓ */
    var edgeSwipeEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (!value) setEdgeSize(0)
        }

    private var helperPatched = false
    private var leftDragger: ViewDragHelper? = null
    private var edgeSizeField: java.lang.reflect.Field? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f

    /** 本次手势已经判定为「向右拉抽屉」✓ 开始跟手了 */
    private var dragging = false

    /** 兜底路径（反射失败）用的阈值 */
    private val fallbackThreshold = (48 * resources.displayMetrics.density).toInt()

    init {
        helperPatched = runCatching {
            val df = DrawerLayout::class.java.getDeclaredField("mLeftDragger")
            df.isAccessible = true
            val helper = df.get(this) as ViewDragHelper
            val ef = ViewDragHelper::class.java.getDeclaredField("mEdgeSize")
            ef.isAccessible = true
            leftDragger = helper
            edgeSizeField = ef
            true
        }.getOrDefault(false)

        // ★ 动画结束/开始时的 mEdgeSize 切换 ✓
        //   - 完全打开 ⇒ 放回屏宽 ✓ 「在抽屉上向左拖」才能关掉它 ✓
        //   - 完全关闭 ⇒ 设成 0 ✓ 等下一次 DOWN 再按方向决定要不要放开 ✓
        //   （不开这条的话：动画期间把它关成 0 之后就一直关着 ✗ 拖不动了 ✗）
        addDrawerListener(object : SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: android.view.View) {
                setEdgeSize(maxOf(width, 1))
            }

            override fun onDrawerClosed(drawerView: android.view.View) {
                setEdgeSize(0)
            }
        })
    }

    private fun setEdgeSize(px: Int) {
        val helper = leftDragger ?: return
        val field = edgeSizeField ?: return
        runCatching { field.setInt(helper, px) }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        // 左边一条窄缝从「系统返回手势」里抢出来（API 29+ ✓）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val w = (24 * resources.displayMetrics.density).toInt()
                systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, w, height))
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // ① 展开动画期间：完全不拦 ✓ 按钮当场可点 ✓
        //
        // ⚠️ 还必须把 mEdgeSize 关掉 ✗ —— 用户实测：「动画还在播放时点按钮，
        //    有概率被识别成改变动画而不是按下按钮」
        //    原因：拉抽屉时我们把 mEdgeSize 设成了「屏幕宽」✗
        //    ⇒ 动画期间 ViewDragHelper 一看到 DOWN 就当「边缘触摸」capture 掉 ✗
        //    ⇒ 点击被当成拖动 ✗
        if (isDrawerVisible(GravityCompat.START) && !isDrawerOpen(GravityCompat.START)) {
            dragging = false
            setEdgeSize(0)
            return false
        }

        // ② 抽屉**已经开着**：右侧那一窄条（没被抽屉盖住的部分）里的横向滑动
        //    ⇒ 直接收起抽屉 ✓ 不要漏给下面的 ViewPager2 ✓
        //
        //    用户实测：「侧栏弹出时只遮挡了屏幕的一大半，有一小半没被遮挡，
        //    此时在这一小半里从右到左滑，就会滑动下方的 tab 栏，非常容易误触」
        //
        //    原因：`DrawerLayout` 只在 scrim 区处理「点击关闭」✗
        //    横向滑动它不管 ⇒ 事件落到下面的 ViewPager2 ⇒ tab 被翻掉 ✗
        if (isDrawerOpen(GravityCompat.START)) {
            if (handleScrimSwipe(ev)) return true
            dragging = false
            return super.onInterceptTouchEvent(ev)
        }

        // ③ 关掉了拖动：交给 super（☰ 的 openDrawer 照常 ✓）
        if (!edgeSwipeEnabled) {
            dragging = false
            return super.onInterceptTouchEvent(ev)
        }

        // ③ 反射失败：退回「识别手势 + openDrawer」
        if (!helperPatched) return fallbackIntercept(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                dragging = false
                // ★ 先不让 DrawerLayout 抢 ⇒ ViewPager2 才能收到「向左滑切 tab」✓
                setEdgeSize(0)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    // 向右 + 过 slop + 明显横于竖向 ⇒ 这是要拉抽屉 ✓
                    if (dx > touchSlop && abs(dx) > abs(dy) * 1.5f) {
                        dragging = true
                        setEdgeSize(maxOf(width, 1))
                        // 合成一个 DOWN 喂给 DrawerLayout ⇒ 它从这里开始跟手 ✓
                        runCatching {
                            val synth = MotionEvent.obtain(ev)
                            synth.action = MotionEvent.ACTION_DOWN
                            super.onInterceptTouchEvent(synth)
                            synth.recycle()
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                // 手势结束（没拉抽屉）⇒ 恢复成「不可抢」，下次 DOWN 再关 ✓
                if (!isDrawerVisible(GravityCompat.START)) setEdgeSize(0)
            }
        }

        // 判定为拉抽屉之后 super 会返回 true（接管）✓ 否则 false（子 View 继续）✓
        return super.onInterceptTouchEvent(ev)
    }

    /**
     * ⚠️ 关键：`DrawerLayout` 在 scrim 上**按下就会 intercept** ✓
     *   （为了实现「点空白处关闭抽屉」✓）
     *   ⇒ 一旦它 intercept 了，后续的 MOVE **不会**再走 [onInterceptTouchEvent] ✗
     *   ⇒ 只在 intercept 里判断滑动是**永远等不到**的 ✗
     *   ⇒ 所以这里也要判一次 ✓（DOWN 的坐标已经在 intercept 里记过了 ✓）
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (isDrawerOpen(GravityCompat.START) && handleScrimSwipe(ev)) return true
        return super.onTouchEvent(ev)
    }

    /**
     * 抽屉开着时，处理「没被抽屉盖住的那一窄条」里的横向滑动（2026-10-25 ✓）
     *
     * 命中条件：
     * - 起手位置 x **大于抽屉的右边缘**（= 落在 scrim 上 ✓ 不是点在抽屉里 ✓）
     * - 横向位移超过 touchSlop 且明显大于竖向
     *
     * 命中就 [closeDrawer] 并把这次手势消费掉 ✓
     *   ⇒ 不会再传给下面的 `ViewPager2` ⇒ tab 不会被误翻 ✓
     *
     * 点在抽屉**里面**时不算 ✓（那里要留给抽屉自己的列表滚动 ✓）
     */
    private fun handleScrimSwipe(ev: MotionEvent): Boolean {
        val drawerRight = drawerRightEdge()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 只有起手在 scrim 上才进入「准备识别」
                scrimArmed = drawerRight > 0 && ev.rawX >= drawerRight
                downX = ev.rawX
                downY = ev.rawY
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scrimArmed) return false
                val dx = abs(ev.rawX - downX)
                val dy = abs(ev.rawY - downY)
                if (dx > touchSlop && dx > dy * 1.2f) {
                    scrimArmed = false
                    closeDrawer(GravityCompat.START)
                    return true
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> scrimArmed = false
        }
        return false
    }

    /** 抽屉的右边缘（像素）。找不到（抽屉没露出）返回 0 */
    private fun drawerRightEdge(): Int {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility != VISIBLE) continue
            val lp = c.layoutParams as? LayoutParams ?: continue
            val absGravity = GravityCompat.getAbsoluteGravity(
                lp.gravity, androidx.core.view.ViewCompat.getLayoutDirection(this)
            )
            if (absGravity and GravityCompat.START == GravityCompat.START &&
                c.left <= 1 && c.right in 1 until width
            ) {
                return c.right
            }
        }
        return 0
    }

    /** 本次手势是否从 scrim（没被抽屉盖住的部分）起手 */
    private var scrimArmed = false

    // ── 反射失败时的兜底：跟不了手 ✗ 但能用 ✓ ──
    private var consumed = false

    private fun fallbackIntercept(ev: MotionEvent): Boolean {
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
