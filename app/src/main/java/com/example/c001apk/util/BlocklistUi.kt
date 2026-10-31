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

    /**
     * ★ 探针 TAG（2026-10-31）
     * `adb logcat -s BLPROBE` 就能看到每一次 bind / apply / restore 的完整决策链 ✓
     * 排查「滑回来又展开」用 ✓ 稳定后可以删掉这一段 ✗
     */
    private const val TAG = "BLPROBE"
    private const val PROBE = true

    /** 展示模式 */
    enum class Mode {
        /** 正文折叠（动态卡片 ✓ 保留作者信息）*/
        FOLD,

        /** 整卡模糊（话题卡片 ✓）*/
        BLUR,

        /**
         * **只显示红条**（个人主页头部 ✓ 2026-10-31 用户要求）
         * >「个人主页屏蔽时不需要模糊，只需要添加那个红条即可」
         */
        BANNER_ONLY,
    }

    private const val BODY_TAG = "bl_body"
    private const val BLUR_RADIUS = 8f
    private const val DIM_ALPHA = 0.35f

    /** 正文 View 的**原始可见性**（折叠后要还原 ✓ gone/invisible 的不能一律变 visible ✗）*/
    private val originVisibility = WeakHashMap<View, Int>()

    /**
     * ★ 性能缓存（2026-10-31 修「滑动卡顿」）
     *
     * 原来每次 `bind()` 都要**递归走三遍** View 树（restore + hideBody + post 里的 hideBody ✗）
     * ⇒ RecyclerView 滑动时每帧都在干这个 ⇒ 掉帧 / 划不回去 ✗
     *
     * 现在：
     *   root -> 打标了 `bl_body` 的 View 列表（**只算一次** ✓）
     *   root -> 上次应用的状态（**没变就直接 return** ✓ 这是最大的省 ✓）
     */
    private val lastState = WeakHashMap<View, Boolean>()

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
        if (PROBE) {
            android.util.Log.d(
                TAG,
                "bind kind=$kind id=$key mode=$mode " +
                    "last=${lastState[blurView]} cached=${Blocklist.cached(kind, key)} " +
                    "enabled=${Blocklist.enabled()} revealed=${revealed.contains(rtKey(kind, key))} " +
                    "barVisible=${bar.isVisible} bodies=${bodyOf(blurView).size}"
            )
        }

        // ★ 状态没变 => 整个跳过 ✓（RecyclerView 滑回来时会反复 bind 同一项 ✗）
        //   这是修「滑动卡顿」最关键的一条 ✓
        // ★★ 2026-10-31 探针找到的真根因 ★★
        //   日志：
        //     bind id=74741472 last=true cached=null
        //     restore mode=FOLD（把正文放出来）      ← 先展开了 ✗
        //     （242ms 后）
        //     apply mode=FOLD                        ← 异步结果回来才折叠
        //   ⇒ 「展开」不是折叠状态丢失 ✗ 是**缓存未知时先 restore 了** ✗
        //     中间那 200+ms 就是用户看到的样子 ✓
        //
        //   修：未知（cached == null，批量查询还在路上 ✓）时
        //       **沿用上一张卡片的状态**（乐观 ✓ 相邻卡片状态基本一致 ✓）
        //       => want 与 last 相同 => 直接跳过 => 零闪烁 ✓
        val wantBlocked = when {
            key.isEmpty() || !Blocklist.enabled() -> false
            else -> Blocklist.cached(kind, key) ?: (lastState[blurView] == true)
        }
        if (lastState[blurView] == wantBlocked && !revealed.contains(rtKey(kind, key))) {
            if (PROBE) android.util.Log.d(TAG, "  => 状态没变，跳过（want=$wantBlocked）")
            return          // 上一轮就是这个状态 ✓ 什么都不用做 ✓
        }

        // 复位（RecyclerView 复用 ✗）
        bar.isVisible = false
        reveal?.isVisible = false
        restore(blurView, mode)

        if (key.isEmpty() || !Blocklist.enabled()) {
            lastState[blurView] = false
            return
        }
        if (revealed.contains(rtKey(kind, key))) {
            lastState[blurView] = false
            return
        }

        // ★ 2026-10-31：横幅文本由**服务器**决定 ✓（服务器没给就回落到本地文案 ✓）
        val localLabel = banner.context.getString(labelRes)
        val serverMsg = Blocklist.cachedMsg(kind, key)
        (banner as? android.widget.TextView)?.text = serverMsg ?: localLabel
        reveal?.setOnClickListener {
            revealed.add(rtKey(kind, key))
            bar.isVisible = false
            reveal.isVisible = false
            restore(blurView, mode)
            lastState[blurView] = false
        }

        val known = Blocklist.cached(kind, key)
        if (known != null) {
            if (known) {
                apply(blurView, mode)
                (banner as? android.widget.TextView)?.text =
                    Blocklist.cachedMsg(kind, key) ?: localLabel
            } else {
                restore(blurView, mode)
            }
            bar.isVisible = known
            reveal?.isVisible = known
            lastState[blurView] = known
            if (PROBE) android.util.Log.d(TAG, "  => 同步命中 known=$known")
            return
        }
        Blocklist.prefetch(kind, listOf(key)) {
            if (revealed.contains(rtKey(kind, key))) return@prefetch
            val blocked = Blocklist.isBlocked(kind, key)
            if (blocked) {
                apply(blurView, mode)
                (banner as? android.widget.TextView)?.text =
                    Blocklist.cachedMsg(kind, key) ?: localLabel
            } else {
                restore(blurView, mode)
            }
            bar.isVisible = blocked
            reveal?.isVisible = blocked
            lastState[blurView] = blocked
            if (PROBE) android.util.Log.d(TAG, "  => 异步返回 blocked=$blocked")
        }
    }

    /** 应用屏蔽效果 */
    private fun apply(root: View, mode: Mode) {
        if (PROBE) android.util.Log.d(TAG, "apply mode=$mode")
        when (mode) {
            Mode.BANNER_ONLY -> Unit      // 只出红条 ✓ 不动内容 ✓

            Mode.FOLD -> {
                // 现收正文 ✓ 隐藏 ✓
                hideBody(root)
                // ★ 2026-10-31 修「正文还在」：`hideBody` 跑在
                //   `executePendingBindings()` **之前** ✗
                //   数据绑定里的 BindingAdapter 会把 visibility 设回来 ✗
                //   ⇒ 布局后再补两次（一次 post ✓ 一次延迟兜底 ✓）
                root.post { hideBody(root) }
                root.postDelayed({ hideBody(root) }, 80L)
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

    /** 隐藏所有 `tag == "bl_body"` 的 View（记住原可见性 ✓） */
    private fun hideBody(root: View) {
        var hidden = 0
        forEachBody(root) { v ->
            if (!originVisibility.containsKey(v)) {
                originVisibility[v] = v.visibility
            }
            if (v.visibility != View.GONE) hidden++
            v.visibility = View.GONE
        }
        if (PROBE) {
            // 打完再查一遍：如果还剩下可见的，说明**有东西在我们之后把它设回来了** ✗
            var stillVisible = 0
            forEachBody(root) { v -> if (v.visibility != View.GONE) stillVisible++ }
            android.util.Log.d(
                TAG,
                "hideBody 隐藏了 $hidden 个 / 仍可见 $stillVisible 个（total=${bodyOf(root).size}）"
            )
        }
    }

    /** 还原 */
    private fun restore(root: View, mode: Mode) {
        if (PROBE) android.util.Log.d(TAG, "restore mode=$mode（把正文放出来）")
        when (mode) {
            Mode.BANNER_ONLY -> Unit      // 什么都没动 ✓ 无需还原 ✓

            Mode.FOLD -> {
                forEachBody(root) { v ->
                    // ★ 2026-10-31 修 bug（真机：卡片里冒出一堆空白框 ✗）
                    //   原来写的是 `originVisibility[v] ?: View.VISIBLE` ✗
                    //   `forwarded` / `hotReply` / `extraPart` / `linearLayout` 这些
                    //   正文 View **默认就是 gone** ✓（按内容隐藏）
                    //   ⇒ 没被折叠过（没记录原值）的会被强制设成 VISIBLE ✗
                    //   ⇒ 卡片里出现一堆空白圆角框 ✗
                    //   => 没记录过就**不动它** ✓ 保持 XML 里的默认可见性 ✓
                    originVisibility[v]?.let { v.visibility = it }
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

    /** 递归找 `tag == "bl_body"` 的 View（内部用 ✓ 只在**建缓存**时走一次）*/
    private fun collectBody(root: View, out: MutableList<View>) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (BODY_TAG == child.tag) {
                    out.add(child)
                } else {
                    collectBody(child, out)
                }
            }
        }
    }

    /**
     * 取正文 View 列表。
     *
     * ⚠️ 2026-10-31 **不能缓存** ✗（踩过）：
     *   第一次 bind 时图片区 / 转发区还没 inflate ✗
     *   ⇒ 缓存里少了它们 ⇒ 之后一直用旧缓存 ⇒ 那些 View 永远不隐藏 ✗
     *   ⇒ 表现：「红条在、正文也在」（用户截图）✗
     * ⇒ 每次现收 ✓ 代价可接受（一次树的遍历 ✓ 不是递归三遍 ✓）
     */
    private fun bodyOf(root: View): Array<View> {
        val out = ArrayList<View>(24)
        collectBody(root, out)
        return out.toTypedArray()
    }

    private fun forEachBody(root: View, action: (View) -> Unit) {
        for (v in bodyOf(root)) action(v)
    }

    private fun rtKey(kind: Blocklist.Kind, id: String) = "${kind.name}|$id"

    /** 清空「依然查看」记录（调试用）*/
    fun clearRevealed() = revealed.clear()
}
