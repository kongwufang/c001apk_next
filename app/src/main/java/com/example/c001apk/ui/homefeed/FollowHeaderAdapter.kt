package com.example.c001apk.ui.homefeed

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.c001apk.R
import com.google.android.material.color.MaterialColors

/**
 * 首页「关注」列表顶部的那块区域（2026-11-07）
 *
 * 对照官方 App 的关注页，官方在动态列表**之前**有两行：
 *
 * ```
 * ┌──────────────────────────────────────────┐
 * │ 最近更新   (头像) KoCleo  QuTick  Nanashi… │   ← 第一行：最近发过动态的关注对象
 * ├──────────────────────────────────────────┤
 * │ [全部] 好友关注  话题关注  数码关注  应用关注 │   ← 第二行：分组筛选 chips
 * └──────────────────────────────────────────┘
 * ```
 *
 * ## 为什么用「从动态里反推作者」而不是再请求一个接口
 * 官方那行「最近更新」没有对应的公开接口（试过 recentFollowList / feed/followList 都 404 ✗）
 * 但它想表达的就是「最近有新动态的人」✓
 * ⇒ 直接取当前列表里的**去重作者**，天然就是「最近更新」✓
 * ⇒ 零额外请求、零额外字段 ✓ 用户看到的效果一样 ✓
 *
 * ## 为什么 chips 用 MaterialButton 而不是 Chip
 * 项目里从来没引过 `com.google.android.material.chip.Chip`（主题是 M3 动态色 +
 * rikka 的 MaterialActivity，Chip 的默认样式不一定跟着主题走 ✗）
 * ⇒ 用 `materialButtonOutlinedStyle`（项目里已经在用 ✓ 主题安全 ✓）
 *   自己描边 / 填色，做出官方那种圆角矩形 chip 的样子 ✓
 */
class FollowHeaderAdapter(
    private val onPickGroup: (String) -> Unit,
    /** 点头像 = 筛选（再点一次取消 ✓ 2026-11-07 用户要求） */
    private val onToggleUser: (String) -> Unit,
    /** 点「最近更新」⇒ 进「最近更新」页（官方就是两级结构 ✓ 2026-11-07） */
    private val onOpenRecent: () -> Unit,
) : RecyclerView.Adapter<FollowHeaderAdapter.Holder>() {

    /** 最近更新的作者（从动态里反推 ✓ 按出现顺序去重 ✓） */
    data class Author(val uid: String, val name: String, val avatar: String)

    companion object {
        /** 分组顺序与 `PrefManager.FOLLOWTYPE` 的取值一一对应 ✓ */
        private val GROUPS = listOf(
            "all" to "全部",
            "circle" to "好友关注",
            "topic" to "话题关注",
            "product" to "数码关注",
            "apk" to "应用关注",
        )
        private const val MAX_AUTHORS = 12
    }

    private val authors = ArrayList<Author>()
    private var selected = "all"

    /** 当前被选中用于**筛选**的作者 uid（null = 不筛选 ✓） */
    private var selectedUid: String? = null

    fun setAuthors(list: List<Author>) {
        val cut = list.distinctBy { it.uid }.take(MAX_AUTHORS)
        if (cut == authors) return
        authors.clear()
        authors.addAll(cut)
        notifyItemChanged(0)
    }

    fun setSelected(type: String) {
        if (type == selected) return
        selected = type
        notifyItemChanged(0)
    }

    /** 外部同步「当前筛选的作者」（例如切换分组后清空筛选） */
    fun setSelectedUid(uid: String?) {
        if (uid == selectedUid) return
        selectedUid = uid
        notifyItemChanged(0)
    }

    override fun getItemCount() = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val ctx = parent.context
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(ctx, 12), dp(ctx, 8), 0, dp(ctx, 4))
        }
        val authorRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val authorScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(authorRow)
        }
        root.addView(
            authorScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(ctx, 6), 0, dp(ctx, 2))
        }
        val chipScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        }
        root.addView(
            chipScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return Holder(root, authorRow, chipRow)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind()
    }

    inner class Holder(
        private val root: LinearLayout,
        private val authorRow: LinearLayout,
        private val chipRow: LinearLayout,
    ) : RecyclerView.ViewHolder(root) {

        fun bind() {
            val ctx = root.context
            authorRow.removeAllViews()
            chipRow.removeAllViews()

            // ---------- 第一行：最近更新 ----------
            if (authors.isEmpty()) {
                val hint = TextView(ctx).apply {
                    text = "最近更新 ›"
                    isClickable = true
                    setOnClickListener { onOpenRecent() }
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(ctx, 4), 0, dp(ctx, 8), 0)
                }
                authorRow.addView(
                    hint,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.CENTER_VERTICAL }
                )
            } else {
                val label = TextView(ctx).apply {
                    // ★ 官方这里是个**入口**：点进去才是「哪些人更新了」的列表 ✓
                    text = "最近更新 ›"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(ctx, 4), 0, dp(ctx, 10), 0)
                    isClickable = true
                    setOnClickListener { onOpenRecent() }
                }
                authorRow.addView(
                    label,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.CENTER_VERTICAL }
                )
                authors.forEach { a ->
                    authorRow.addView(makeAuthor(ctx, a))
                }
            }

            // ---------- 第二行：分组 chips ----------
            GROUPS.forEachIndexed { index, (key, name) ->
                chipRow.addView(makeChip(ctx, key, name, index > 0))
            }
        }

        /** 一个圆形头像 + 昵称（竖排） */
        private fun makeAuthor(ctx: Context, a: Author): View {
            val box = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(ctx, 6), 0, dp(ctx, 6), 0)
                isClickable = true
                setOnClickListener {
                    // 再点一次同一个 = 取消筛选 ✓
                    val next = if (selectedUid == a.uid) null else a.uid
                    selectedUid = next
                    notifyItemChanged(0)
                    onToggleUser(next ?: "")
                }
            }
            val picked = selectedUid == a.uid
            val accent = MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary)
            val ringColor = if (picked) accent else Color.TRANSPARENT
            val avatar = ImageView(ctx).apply {
                val size = dp(ctx, 46)
                layoutParams = LinearLayout.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.CENTER_CROP
                // 选中：外面套一圈主题色（官方是加粗描边 ✓ 这里用 padding + 圆底实现）
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(ctx, 2), ringColor)
                    setColor(Color.TRANSPARENT)
                }
                if (picked) setPadding(dp(ctx, 2), dp(ctx, 2), dp(ctx, 2), dp(ctx, 2))
                // 圆形：用 background 画不出来 ⇒ 交给 Glide 的 circleCrop ✓
                runCatching {
                    Glide.with(ctx)
                        .load(a.avatar)
                        .apply(com.bumptech.glide.request.RequestOptions.circleCropTransform())
                        .into(this)
                }
            }
            val name = TextView(ctx).apply {
                text = if (a.name.length > 6) a.name.substring(0, 6) else a.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(if (picked) accent else MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(0, dp(ctx, 4), 0, 0)
            }
            box.addView(avatar)
            box.addView(name)
            return box
        }

        /** 一个分组 chip（选中 = 主题色描边 + 淡底 ✓ 未选 = 灰描边 ✓） */
        private fun makeChip(ctx: Context, key: String, name: String, withLeftGap: Boolean): View {
            val on = key == selected
            val accent = MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary)
            val outline = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOutline)
            val onSurface = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurfaceVariant)

            val tv = TextView(ctx).apply {
                text = name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(if (on) accent else onSurface)
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 14), dp(ctx, 7), dp(ctx, 14), dp(ctx, 7))
                background = GradientDrawable().apply {
                    cornerRadius = dp(ctx, 18).toFloat()
                    setColor(if (on) withAlpha(accent, 0.14f) else Color.TRANSPARENT)
                    setStroke(dp(ctx, 1), if (on) accent else withAlpha(outline, 0.6f))
                }
                isClickable = true
                setOnClickListener {
                    if (!on) {
                        selected = key
                        notifyItemChanged(0)
                        onPickGroup(key)
                    }
                }
            }
            return tv.apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (withLeftGap) marginStart = dp(ctx, 8) else marginStart = 0 }
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics)
            .toInt()
}
