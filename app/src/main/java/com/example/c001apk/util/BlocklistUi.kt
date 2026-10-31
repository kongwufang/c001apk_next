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
 * ## 真机反馈 → 修法（三轮）
 *
 * | 反馈 | 根因 | 修法 |
 * |---|---|---|
 * | 提示卡在页面下面 | 横幅上下双约束 ⇒ 被垂直居中 | `FrameLayout` + `layout_gravity="top"` |
 * | 首页/搜索没模糊 | 首页走 `AppAdapter` 不是 `FeedDataAdapter` | 四个 ViewHolder 全接 |
 * | 模糊把提示也糊了 | `setRenderEffect` 打在 `root` 上 ⇒ 子 View 一起糊 | 横幅移到模糊目标的**兄弟**节点 |
 * | 想临时看内容 | — | 新增「依然查看」按钮 ⇒ 点一下取消模糊（本会话记住） |
 * | 话题卡片没接 | 布局没包 | `item_search_topic.xml` 也包 |
 *
 * ## 布局约定
 * ```xml
 * <FrameLayout>
 *     <原根 android:id="@+id/blurContainer"> … </原根>   ← 被模糊（话题卡片用 @id/parent）
 *     <LinearLayout android:id="@+id/blockedBar" layout_gravity="top">
 *         <TextView android:id="@+id/blockedBanner" />   ← 文案
 *         <TextView android:id="@+id/blockedReveal" />   ← 「依然查看」
 *     </LinearLayout>
 * </FrameLayout>
 * ```
 */
object BlocklistUi {

    private const val BLUR_RADIUS = 8f
    private const val DIM_ALPHA = 0.35f

    /**
     * 用户点过「依然查看」的项（本会话内不再模糊）。
     * key = `KIND|id`  ✓ 进程重启就清空（下次进来还是模糊 ✓ 合理）
     */
    private val revealed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * 绑定一个卡片。
     *
     * @param blurView 被模糊的容器
     * @param bar      横幅条（`blockedBar`，必须是 blurView 的**兄弟** ✓）
     * @param banner   文案（`blockedBanner`）
     * @param reveal   「依然查看」按钮（`blockedReveal`，可为 null）
     */
    fun bind(
        blurView: View,
        bar: View,
        banner: View,
        reveal: View?,
        kind: Blocklist.Kind,
        id: String?,
        labelRes: Int = R.string.blocked_multi_report,
    ) {
        val key = id?.trim().orEmpty()
        // 复位（RecyclerView 复用 ✗ 上一条的状态不能带过来）
        bar.isVisible = false
        clearBlur(blurView)

        if (key.isEmpty()) return
        if (!Blocklist.enabled()) return

        // 用户点过「依然查看」⇒ 本条不模糊 ✓ 但保留一条细提示 ✗（用户要的是取消模糊 ✓）
        if (revealed.contains(rtKey(kind, key))) return

        (banner as? android.widget.TextView)?.setText(labelRes)
        reveal?.setOnClickListener {
            revealed.add(rtKey(kind, key))
            bar.isVisible = false
            clearBlur(blurView)
        }

        val known = Blocklist.cached(kind, key)
        if (known != null) {
            applyState(blurView, bar, known)
            return
        }
        Blocklist.prefetch(kind, listOf(key)) {
            if (revealed.contains(rtKey(kind, key))) return@prefetch
            applyState(blurView, bar, Blocklist.isBlocked(kind, key))
        }
    }

    private fun applyState(blurView: View, bar: View, blocked: Boolean) {
        bar.isVisible = blocked
        if (blocked) setBlur(blurView) else clearBlur(blurView)
    }

    private fun setBlur(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setRenderEffect(
                RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP)
            )
        } else {
            v.alpha = DIM_ALPHA
        }
    }

    private fun clearBlur(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            v.setRenderEffect(null)
        } else {
            v.alpha = 1f
        }
    }

    private fun rtKey(kind: Blocklist.Kind, id: String) = "${kind.name}|$id"

    /** 清空「依然查看」记录（调试用） */
    fun clearRevealed() = revealed.clear()
}
