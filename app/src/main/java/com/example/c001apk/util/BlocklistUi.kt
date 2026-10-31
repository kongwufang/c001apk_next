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

    /**
     * ★★ 2026-10-31 第三层根因：**Glide 会覆盖 `tag`** ★★
     *
     * Glide 加载图片时会 `imageView.setTag(requestKey)` ✗
     * ⇒ 我们打的 `android:tag="bl_body"` 被**盖掉** ✗
     * ⇒ `forEachBody` 找不到图片区 ⇒ 它们永远不会被折叠 ✗
     * ⇒ 表现就是「正文/图片还在、红条也在」（真机截图证实 ✓）
     *
     * ⇒ 改用 **`id` 判断** ✓（`id` 不会被任何库覆盖 ✓）
     *    下面的 id 集合与布局里打 tag 的那批**一一对应** ✓
     *    （tag 仍然保留作兼容 ✓ 两者取或 ✓）
     */
    private val BODY_IDS: Set<Int> by lazy {
        setOf(
            R.id.messageTitle, R.id.message, R.id.messageMore,
            R.id.videoPart, R.id.multiImage, R.id.mediaBarrier,
            R.id.extraPart, R.id.hotReply,
            R.id.forwarded, R.id.forwardedMess, R.id.forwardedPic,
            R.id.dyhLayout, R.id.linearLayout, R.id.ip,
            R.id.extraUrlLayout, R.id.extraPic, R.id.extraTitle, R.id.extraUrl,
            // 时间行 + 操作栏（用户要求：「遮挡下面点赞和评论的那一行」）
            R.id.pubDate, R.id.like, R.id.reply,
            R.id.favorite, R.id.forward, R.id.report,
        )
    }

    private fun isBody(v: View): Boolean = BODY_TAG == v.tag || BODY_IDS.contains(v.id)
    private const val BLUR_RADIUS = 8f
    private const val DIM_ALPHA = 0.35f

    /** 正文 View 的**原始可见性**（折叠后要还原 ✓ gone/invisible 的不能一律变 visible ✗）*/
    private val originVisibility = WeakHashMap<View, Int>()

    /**
     * ★★ 2026-10-31 根治「红条在、正文回来」★★
     *
     * 证据：`app:pic` / `app:picArr` / `app:setHotReply` 这些 BindingAdapter
     *      **只改 `isVisible`** ✓ **不碰 `layoutParams`** ✓（看过源码 ✓）
     *      而且它们会在 `executePendingBindings()` + 图片异步加载后**再次**设成 VISIBLE ✗
     *      ⇒ 只藏 `visibility` 一定会被覆盖 ✗
     *
     * ⇒ 同时把**高度锁成 0** ✓（databinding 改不到 ✓）
     *   即使它把 visibility 设回 VISIBLE，高度 0 ⇒ 不占位 ⇒ 屏幕上就是折叠的 ✓
     */
    private val originHeight = WeakHashMap<View, Int>()

    /**
     * ★★ 方案 B（2026-10-31 用户选）：卡片**高度锁死** ★★
     *
     * 为什么换这个：正文 View 的 `visibility` 归 databinding 的 BindingAdapter 管 ✗
     *   （`app:pic` / `app:picArr` / `app:setHotReply` 有内容就设 VISIBLE ✗）
     *   无论怎么兜底都会被更晚的绑定覆盖 ✗（探针+坐标 dump 都证实了 ✓）
     *
     * ⇒ 改成锁**卡片自身的高度** ✓ databinding 碰不到 layoutParams ✓
     *   `clipChildren = true`（默认 ✓）⇒ 溢出的正文被裁掉 ✓
     */
    private val originLpHeight = WeakHashMap<View, Int>()

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

    /**
     * ★ 2026-10-31 持续强制折叠用（治「滑回来又展开」）
     *
     * 每次布局变化都再 `hideBody` 一遍 ✓
     * 为什么必须这样：`hideBody` 之后**还有更晚的 databinding**
     * （图片区 / 商品卡是异步绑定的 ✗）会把 visibility 设回来 ✗
     * `post` / `postDelayed(80ms)` 都兜不住 ✗（探针+截图证实过）
     */
    private val enforceListener = WeakHashMap<View, View.OnLayoutChangeListener>()

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
            // ★★ 2026-10-31 关键：**不能盲目信任 lastState** ✗
            //   `lastState` 记的是「我上次想让它怎样」✓
            //   但正文的 visibility 是 databinding 的 BindingAdapter 管的 ✗
            //   （`app:pic` / `app:picArr` / `app:setHotReply` 有内容就设 VISIBLE ✗）
            //   ⇒ 我藏完之后它又放出来 ⇒ 脱钩 ✗
            //   ⇒ 跳过前再强制藏一遍 ✓（一次遍历，很便宜 ✓）
            if (wantBlocked) {
                if (mode == Mode.FOLD) {
                    hideBody(blurView)
                    // 高度万一被别的什么重置了，补一下 ✓（已锁过就跳过 ✓）
                    val h = headerHeightOf(blurView)
                    if (h > 0) lockHeight(blurView, h)
                }
                // 顺便确保红条在 ✓（它也可能被别的东西弄没了 ✓ 少见但兜一下）
                bar.isVisible = true
                reveal?.isVisible = true
            }
            return
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

    /**
     * ★★ 只出红条（2026-10-31）★★
     *
     * 话题卡片 / 用户卡片用这个 ✓
     *
     * 为什么不能用 [bind]：`bind()` 会 `restore()` / `hideBody()` / 锁高度 ✗
     * 那些操作会碰到卡片里的 logo / 标题 ✗
     * ⇒ 真机表现：话题卡片的「头像 + 名称」不显示了 ✗（用户反馈）
     * ⇒ 这个入口**只碰红条那三个 View** ✓ 卡片内容一个字节都不动 ✓
     */
    fun bindBannerOnly(
        bar: View?,
        banner: View?,
        reveal: View?,
        kind: Blocklist.Kind,
        id: String?,
        labelRes: Int? = null,
    ) {
        val key = id?.trim().orEmpty()
        bar?.isVisible = false
        reveal?.isVisible = false
        if (key.isEmpty() || !Blocklist.enabled()) return
        if (revealed.contains(rtKey(kind, key))) return

        val apply = { blocked: Boolean ->
            bar?.isVisible = blocked
            reveal?.isVisible = blocked
            if (blocked) {
                (banner as? android.widget.TextView)?.text =
                    Blocklist.cachedMsg(kind, key)
                        ?: labelRes?.let { banner.context.getString(it) }
                        ?: banner.context.getString(R.string.blocked_multi_report)
                val pos = rtKey(kind, key)
                reveal?.setOnClickListener {
                    revealed.add(pos)
                    bar.isVisible = false
                    reveal.isVisible = false
                }
            }
        }

        val known = Blocklist.cached(kind, key)
        if (known != null) {
            apply(known)
            return
        }
        Blocklist.prefetch(kind, listOf(key)) {
            apply(Blocklist.isBlocked(kind, key))
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
                // ★ 挂持续强制（每次布局变化再藏一遍 ✓ 根治异步绑定把 visibility 设回来 ✗）
                if (!enforceListener.containsKey(root)) {
                    val l = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        if (lastState[v] == true) hideBody(v)
                    }
                    enforceListener[root] = l
                    root.addOnLayoutChangeListener(l)
                }
                // ★ 2026-10-31 修「正文还在」：`hideBody` 跑在
                //   `executePendingBindings()` **之前** ✗
                //   数据绑定里的 BindingAdapter 会把 visibility 设回来 ✗
                //   ⇒ 布局后再补两次（一次 post ✓ 一次延迟兜底 ✓）
                root.post {
                    hideBody(root)
                    // ★ 方案 B：锁死高度 ✓（不依赖子 View 的 visibility ✗）
                    val h = headerHeightOf(root)
                    if (h > 0) {
                        lockHeight(root, h)
                    } else {
                        // ⚠️ 第一张卡在 post 时可能**还没布局**（pub.bottom=0 ✗）
                        //   ⇒ 再等一帧 / 100ms 后重试一次 ✓（探针证实过 ✓）
                        root.postDelayed({
                            val h2 = headerHeightOf(root)
                            if (h2 > 0) lockHeight(root, h2)
                        }, 100L)
                    }
                }
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

    /**
     * 头部高度 = `pubDate` 底边 - 卡片顶边。
     *
     * ⚠️ `pubDate` 可能已经 GONE ✓ 但 `bottom` 仍然保留**上次布局的值** ✓
     *    （View 的 mTop/mBottom 在下一次 layout 之前不会变 ✓）
     */
    private fun headerHeightOf(root: View): Int {
        val pub = root.findViewById<View>(R.id.pubDate) ?: run {
            if (PROBE) android.util.Log.d(TAG, "headerHeight：找不到 pubDate")
            return 0
        }
        // ⚠️ pubDate 是 blurContainer 的**直接子 View** ✓
        //    ⇒ `pub.bottom` 本身就是「相对卡片顶部」的坐标 ✓
        //    ✗ 不要再减 `root.top` —— 那是相对外层容器的 ✗ 一减就变 0 / 负数 ✓
        val h = pub.bottom
        if (PROBE) {
            android.util.Log.d(
                TAG,
                "headerHeight pub.bottom=$h pub.top=${pub.top} pub.height=${pub.height} " +
                    "vis=${pub.visibility} rootH=${root.height}"
            )
        }
        return if (h > 0) h else 0
    }

    /** 锁死卡片高度（方案 B 的核心 ✓） */
    private fun lockHeight(root: View, h: Int) {
        if (h <= 0) return
        val lp = root.layoutParams ?: return
        if (!originLpHeight.containsKey(root)) {
            originLpHeight[root] = lp.height
        }
        // ⚠️ clipChildren / clipToPadding 是 **ViewGroup** 的属性 ✗ 不在 View 上
        (root as? android.view.ViewGroup)?.let {
            it.clipChildren = true
            it.clipToPadding = true
        }
        if (lp.height != h) {
            lp.height = h
            root.layoutParams = lp
        }
        if (PROBE) android.util.Log.d(TAG, "lockHeight h=$h（原=${originLpHeight[root]}）")
    }

    /** 解锁 */
    private fun unlockHeight(root: View) {
        val h = originLpHeight.remove(root) ?: return
        val lp = root.layoutParams ?: return
        if (lp.height != h) {
            lp.height = h
            root.layoutParams = lp
            if (PROBE) android.util.Log.d(TAG, "unlockHeight 还原=${h}")
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
            // ★ 高度锁 0（databinding 改不到 ✓）—— 这才是真正的保险 ✓
            v.layoutParams?.let { lp ->
                if (!originHeight.containsKey(v)) originHeight[v] = lp.height
                if (lp.height != 0) {
                    lp.height = 0
                    v.layoutParams = lp
                }
            }
        }
        if (PROBE) {
            var stillVisible = 0
            forEachBody(root) { v -> if (v.visibility != View.GONE) stillVisible++ }
            // ★ 把「仍然可见」的所有子孙的 **id 名** 打出来 ✓
            //   这样就能知道到底哪些 View 没被我们藏 ✓（漏了哪些 id ✓）
            val leftovers = ArrayList<String>()
            fun walk(v: View) {
                if (v is android.view.ViewGroup) {
                    for (i in 0 until v.childCount) walk(v.getChildAt(i))
                } else if (v.visibility == View.VISIBLE && v.height > 0 || v.visibility == View.VISIBLE) {
                    val name = runCatching {
                        v.resources.getResourceEntryName(v.id)
                    }.getOrNull() ?: ("id=${v.id}")
                    if (v.visibility == View.VISIBLE && v.width > 0) {
                        leftovers.add(name + "(" + v.width + "x" + v.height + ")")
                    }
                }
            }
            walk(root)
            android.util.Log.d(
                TAG,
                "hideBody 藏了 $hidden / 仍可见 $stillVisible（total=${bodyOf(root).size}）" +
                    " VISIBLE=${leftovers.take(14)}"
            )
        }
    }

    /** 还原 */
    private fun restore(root: View, mode: Mode) {
        if (PROBE) android.util.Log.d(TAG, "restore mode=$mode（把正文放出来）")
        when (mode) {
            Mode.BANNER_ONLY -> {
                enforceListener.remove(root)?.let { root.removeOnLayoutChangeListener(it) }
            }

            Mode.FOLD -> {
                // 取消持续强制 ✓ 否则放出来了也会被立刻藏回去 ✗
                enforceListener.remove(root)?.let { root.removeOnLayoutChangeListener(it) }
                // ★ 方案 B：高度还原 ✓
                unlockHeight(root)
                forEachBody(root) { v ->
                    // ★ 2026-10-31 修 bug（真机：卡片里冒出一堆空白框 ✗）
                    //   原来写的是 `originVisibility[v] ?: View.VISIBLE` ✗
                    //   `forwarded` / `hotReply` / `extraPart` / `linearLayout` 这些
                    //   正文 View **默认就是 gone** ✓（按内容隐藏）
                    //   ⇒ 没被折叠过（没记录原值）的会被强制设成 VISIBLE ✗
                    //   ⇒ 卡片里出现一堆空白圆角框 ✗
                    //   => 没记录过就**不动它** ✓ 保持 XML 里的默认可见性 ✓
                    originVisibility[v]?.let { v.visibility = it }
                    // 高度还原 ✓
                    originHeight[v]?.let { h ->
                        v.layoutParams?.let { lp ->
                            if (lp.height != h) {
                                lp.height = h
                                v.layoutParams = lp
                            }
                        }
                    }
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
                if (isBody(child)) {
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

    /** 该条是否已被用户点过「依然查看」（专用卡片要用 ✓） */
    fun isRevealed(kind: Blocklist.Kind, id: String?): Boolean {
        if (id.isNullOrBlank()) return false
        return revealed.contains(rtKey(kind, id))
    }

    /** 标记「依然查看」（专用卡片点按钮时调 ✓） */
    fun markRevealed(kind: Blocklist.Kind, id: String?) {
        if (id.isNullOrBlank()) return
        revealed.add(rtKey(kind, id))
    }

    /** 清空「依然查看」记录（调试用）*/
    fun clearRevealed() = revealed.clear()
}
