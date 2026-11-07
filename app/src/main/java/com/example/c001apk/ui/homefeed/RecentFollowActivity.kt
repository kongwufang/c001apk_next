package com.example.c001apk.ui.homefeed

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.c001apk.ui.user.UserActivity
import com.example.c001apk.util.DateUtils
import com.example.c001apk.util.IntentUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors

/**
 * 「最近更新」页（2026-11-07）
 *
 * ## 为什么要单独一个 Activity
 * 用户反馈：官方 App 的「关注」页顶部有个「最近更新」入口，
 * 点进去是**一个列表**（哪些人更新了），点某个人再看他的更新内容 ✓
 * 我们原来把头像直接摊在列表顶部 ✗ 空间浪费、也点不出「谁更新了什么」
 * ⇒ 改成官方那种两级结构 ✓
 *
 * ## 数据从哪来（不在这个页面请求）
 * 官方那个入口没有公开接口（`/v6/user/recentFollowList` 之类全 404 ✗）
 * ⇒ 由「关注」列表**已经加载好的动态**反推：
 *    作者去重 + 每人取最新一条的时间与摘要 ✓
 * ⇒ 打开本页之前，[RecentFollowCache] 已经填好了 ✓ 本页零请求 ✓
 *
 * ## 为什么不用 XML
 * 就一个列表 ✓ 建 layout + ViewBinding 太重 ✓
 * 照 [com.example.c001apk.ui.follow.LocalFollowActivity] 的做法：代码里直接搭 ✓
 */
class RecentFollowActivity : AppCompatActivity() {

    private val dp by lazy { resources.displayMetrics.density }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val toolbar = MaterialToolbar(this).apply {
            title = "最近更新"
            setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
            setNavigationOnClickListener { finish() }
        }
        root.addView(
            toolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (56 * dp).toInt()
            )
        )

        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@RecentFollowActivity)
            adapter = Adapter(RecentFollowCache.items)
            setPadding(0, (4 * dp).toInt(), 0, (16 * dp).toInt())
            clipToPadding = false
        }
        root.addView(
            rv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        if (RecentFollowCache.items.isEmpty()) {
            val empty = TextView(this).apply {
                text = "最近还没有人更新\n下拉刷新一下「关注」再来看看"
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                gravity = Gravity.CENTER
                setPadding(0, (80 * dp).toInt(), 0, 0)
            }
            root.addView(empty)
        }

        setContentView(root)
    }

    /** 一行：头像 + 昵称 + 「x 分钟前更新」+ 正文摘要 */
    private inner class Adapter(
        private val list: List<RecentFollowCache.Item>,
    ) : RecyclerView.Adapter<Adapter.VH>() {

        inner class VH(val box: LinearLayout) : RecyclerView.ViewHolder(box)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                isClickable = true
            }
            val avatar = ImageView(ctx).apply {
                val s = (44 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(s, s)
                scaleType = ImageView.ScaleType.CENTER_CROP
                tag = "avatar"
            }
            row.addView(avatar)

            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((12 * dp).toInt(), 0, 0, 0)
            }
            val name = TextView(ctx).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                tag = "name"
            }
            val time = TextView(ctx).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, (2 * dp).toInt(), 0, (4 * dp).toInt())
                tag = "time"
            }
            val snippet = TextView(ctx).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                tag = "snippet"
            }
            col.addView(name)
            col.addView(time)
            col.addView(snippet)
            row.addView(
                col,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            return VH(row)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            val row = holder.box
            val ctx = row.context
            (row.findViewWithTag<ImageView>("avatar"))?.let { iv ->
                runCatching {
                    Glide.with(ctx).load(item.avatar)
                        .apply(RequestOptions.circleCropTransform()).into(iv)
                }
            }
            row.findViewWithTag<TextView>("name")?.text = item.name
            row.findViewWithTag<TextView>("time")?.text = runCatching {
                DateUtils.fromToday(item.time) + "更新"
            }.getOrDefault("")
            row.findViewWithTag<TextView>("snippet")?.text = item.snippet
            row.setOnClickListener {
                if (item.uid.isNotEmpty()) {
                    IntentUtil.startActivity<UserActivity>(row.context) {
                        putExtra("id", item.uid)
                    }
                }
            }
        }

        override fun getItemCount() = list.size
    }
}

/**
 * 「最近更新」的数据中转（内存单例 ✓ 2026-11-07）
 *
 * 为什么不用 Intent 传：清单可能有几十条 ✗ Parcelable 序列化没意义 ✗
 * ⇒ 「关注」页点进本页之前先把结果塞这里 ✓ 本页直接读 ✓
 * ⚠️ 只活在内存里 ✓ App 被杀就没了 ✓ 但下次打开关注页会重新算 ✓
 */
object RecentFollowCache {

    data class Item(
        val uid: String,
        val name: String,
        val avatar: String,
        val time: Long,
        val snippet: String,
    )

    var items: List<Item> = emptyList()
        private set

    fun put(list: List<Item>) {
        items = list
    }
}
