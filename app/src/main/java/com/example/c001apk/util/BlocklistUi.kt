package com.example.c001apk.util

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.core.view.isVisible
import com.example.c001apk.R

/**
 * 公共黑名单的 UI 落地点（2026-10-31）。
 *
 * 用户真机反馈的三个问题（已修）：
 * 1. ❌「提示仿佛被卡在了页面下面」 → 横幅改成 `layout_gravity="top"` 顶部覆盖 ✓
 * 2. ❌「模糊有时候不生效」       → 首页卡片走的是 `AppAdapter` 不是 `FeedDataAdapter` ✗
 *                                    现在由本工具统一供所有 adapter 调用 ✓
 * 3. ❌「就算生效了提示会被卡在模糊下面，根本看不清」
 *    → `setRenderEffect` 作用在**容器**上 ⇒ 子 View 全糊 ✗
 *      现在布局改成 FrameLayout 包一层：`blurContainer` 被糊 ✓ 横幅是**它的兄弟** ⇒ 清晰 ✓
 *
 * ## 布局约定（两种卡片布局都按这个改）
 * ```xml
 * <FrameLayout>
 *     <原根 android:id="@+id/blurContainer"> … </原根>   ← 被模糊
 *     <TextView android:id="@+id/blockedBanner" layout_gravity="top" />  ← 清晰
 * </FrameLayout>
 * ```
 */
object BlocklistUi {

    /** 模糊半径（dp≈px，8f 够糊又不至于看不清轮廓） */
    private const val BLUR_RADIUS = 8f

    /** 低版本没有 RenderEffect ⇒ 用压暗代替 ✓ */
    private const val DIM_ALPHA = 0.35f

    /**
     * 绑定一个卡片。
     *
     * @param blurView 被模糊的容器（`blurContainer`）
     * @param banner   横幅（`blockedBanner`，必须是 blurView 的**兄弟** ✓）
     * @param kind     FEED / USER / TOPIC
     * @param id       对象 ID（空则不处理）
     * @param labelRes 横幅文案（默认「该内容已被多人举报」）
     */
    fun bind(
        blurView: View,
        banner: View,
        kind: Blocklist.Kind,
        id: String?,
        labelRes: Int = R.string.blocked_multi_report,
    ) {
        val key = id?.trim().orEmpty()
        // 先复位（RecyclerView 复用 ✓ 上一条的屏蔽状态不能带过来 ✗）
        applyState(blurView, banner, false, labelRes)

        if (key.isEmpty()) return
        // 开关没开 => 不查也不改 ✓
        if (!Blocklist.enabled()) return

        // 缓存命中就立即生效 ✓（不闪）
        val known = Blocklist.cached(kind, key)
        if (known != null) {
            applyState(blurView, banner, known, labelRes)
            return
        }

        // 后台查 ✓ 结果回来再更新这一项 ✓
        Blocklist.prefetch(kind, listOf(key)) {
            val blocked = Blocklist.isBlocked(kind, key)
            applyState(blurView, banner, blocked, labelRes)
        }
    }

    private fun applyState(blurView: View, banner: View, blocked: Boolean, labelRes: Int) {
        banner.isVisible = blocked
        if (blocked) {
            (banner as? android.widget.TextView)?.setText(labelRes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurView.setRenderEffect(
                if (blocked) {
                    RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            )
        } else {
            blurView.alpha = if (blocked) DIM_ALPHA else 1f
        }
    }
}
