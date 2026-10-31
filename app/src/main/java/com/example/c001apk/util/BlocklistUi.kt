package com.example.c001apk.util

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.example.c001apk.R
import java.util.WeakHashMap

/**
 * 公共黑名单的 UI 落地点（2026-10-31）。
 *
 * ## 两种展示模式
 *
 * | 模式 | 用于 | 效果 |
 * |---|---|---|
 * | **FOLD** | 动态卡片（首页 / 详情）| 保留头像·昵称·机型·操作栏 ✓ **正文折叠** ✓ 不模糊 ✓ |
 * | **BLUR** | 用户卡片 / 话题卡片 | 整卡模糊 ✓（这些卡片没有明显的"正文"可分 ✗）|
 *
 * 用户原话（第三轮）：
 * >「要不我们把这个页面改一下，展示作者的头像和信息在上面，就和正常的卡片一样，
 * >  只是正文被折叠，然后显示这个红色的标识」
 *
 * ## 布局约定
 * ```xml
 * <FrameLayout>
 *     <卡片根 android:id="@+id/blurContainer">
 *         …头部（avatar / uname / device …）…
 *         …正文（带 android:tag="bl_body" ✓ 会被折叠）…
 *         …操作栏（like / reply …）…
 *     </卡片根>
 *     <LinearLayout android:id="@+id/blockedBar" layout_gravity="top">
 *         <TextView android:id="@+id/blockedBanner" />   ← 文案
 *         <TextView android:id="@+id/blockedReveal" />   ← 「依然查看」
 *     </LinearLayout>
 * </FrameLayout>
 * ```
 *
 * ⚠️ 横幅必须是卡片的**兄弟**节点 ✓ 否则会被一起模糊掉 ✗（第一轮踩过）
 */
object BlocklistUi {

    /** 展示模式 */
    enum class Mode {
        /** 正文折叠（动态卡片 ✓ 保留作者信息）*/
        FOLD,

        /** 整卡模糊（用户 / 话题卡片 ✓）*/
        BLUR,
    }

    private const val BODY_TAG = "bl_body"
    private const val BLUR_RADIUS = 8f
    private const val DIM_ALPHA = 0.35f

    /** 正文 View 的**原始可见性**（折叠后要还原 ✓ gone/invisible 的不能一律变 visible ✗）*/
    private val originVisibility = WeakHashMap<View, Int>()

    /** 用户点过「依然查看」的项（本会话内不再折叠/模糊 ✓）*/
    private val revealed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * 绑定一个卡片。
     *
     * @param blurView 卡片根（`blurContainer` / 话题卡片的 `parent`）
     * @param bar      横幅条（`blockedBar`）
     * @param banner   文案（`blockedBanner`）
     * @param reveal   「依然查看」（`blockedReveal`，可空）
     * @param mode     FOLD（动态）/ BLUR（用户·话题）
     */
    fun bind(
        blurView: View,
        bar: View,
        banner: View,
        reveal: View?,
        kind: Blocklist.Kind,
        id: String?,
        labelRes: Int = R.string.blocked_multi_report,
        mode: Mode = Mode.BLUR,
    ) {
        val key = id?.trim().orEmpty()
        // 复位（RecyclerView 复用 ✗）
        bar.isVisible = false
        restore(blurView, mode)

        if (key.isEmpty()) return
        if (!Blocklist.enabled()) return
        if (revealed.contains(rtKey(kind, key))) return

        (banner as? android.widget.TextView)?.setText(labelRes)
        reveal?.setOnClickListener {
            revealed.add(rtKey(kind, key))
            bar.isVisible = false
            restore(blurView, mode)
        }

        val known = Blocklist.cached(kind, key)
        if (known != null) {
            if (known) apply(blurView, mode) else restore(blurView, mode)
            bar.isVisible = known
            return
        }
        Blocklist.prefetch(kind, listOf(key)) {
            if (revealed.contains(rtKey(kind, key))) return@prefetch
            val blocked = Blocklist.isBlocked(kind, key)
            if (blocked) apply(blurView, mode) else restore(blurView, mode)
            bar.isVisible = blocked
        }
    }

    /** 应用屏蔽效果 */
    private fun apply(root: View, mode: Mode) {
        when (mode) {
            Mode.FOLD -> {
                forEachBody(root) { v ->
                    if (!originVisibility.containsKey(v)) {
                        originVisibility[v] = v.visibility
                    }
                    v.visibility = View.GONE
                }
            }
            Mode.BLUR -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    root.setRenderEffect(
                        RenderEffect.createBlurEffect(
                            BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP
                        )
                    )
                } else {
                    root.alpha = DIM_ALPHA
                }
            }
        }
    }

    /** 还原 */
    private fun restore(root: View, mode: Mode) {
        when (mode) {
            Mode.FOLD -> {
                forEachBody(root) { v ->
                    v.visibility = originVisibility[v] ?: View.VISIBLE
                }
            }
            Mode.BLUR -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    root.setRenderEffect(null)
                } else {
                    root.alpha = 1f
                }
            }
        }
    }

    /** 递归找 `tag == "bl_body"` 的 View */
    private fun forEachBody(root: View, action: (View) -> Unit) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (BODY_TAG == child.tag) {
                    action(child)
                } else {
                    forEachBody(child, action)
                }
            }
        }
    }

    private fun rtKey(kind: Blocklist.Kind, id: String) = "${kind.name}|$id"

    /** 清空「依然查看」记录（调试用）*/
    fun clearRevealed() = revealed.clear()
}
