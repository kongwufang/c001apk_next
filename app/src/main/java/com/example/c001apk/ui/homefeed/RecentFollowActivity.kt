package com.example.c001apk.ui.homefeed

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.ui.user.UserActivity
import com.example.c001apk.util.DateUtils
import com.example.c001apk.util.IntentUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「有更新的酷友」页（2026-11-07）
 *
 * ## 官方的真实接口（用户抓包 HAR 拿到 ✓）
 * ```
 * GET /v6/page/dataList
 *     ?url=/UserHelper/getFollowRows?type=user&getDataRowsByCard=1&sort=lastFeed&title=有更新的酷友
 *     &title=有更新的酷友&subTitle=&page=1
 * ```
 * 返回 `data[]`，每条是 `entityType = user`：
 * ```
 * uid / username / userAvatar / fans / follow / logintime（最后活跃） /
 * isFollow / level / experience / verify_title …
 * ```
 * ⇒ 页面每行：头像 + 昵称 + 「91关注 1522粉丝 22小时前活跃」+ 右侧按钮
 *
 * ## 和官方的一处**故意不同**
 * 官方右侧按钮是「已关注」✗ 用户要求换成「**查看详情**」✓
 * ⇒ 点了进 UserActivity 看这个人的更新内容 ✓
 *
 * ## 为什么不用 XML
 * 就一个列表 ✓ 照 [com.example.c001apk.ui.follow.LocalFollowActivity] 的做法在代码里搭 ✓
 */
@AndroidEntryPoint
class RecentFollowActivity : AppCompatActivity() {

    @Inject
    lateinit var networkRepo: NetworkRepo

    private val dp by lazy { resources.displayMetrics.density }
    private lateinit var rv: RecyclerView
    private lateinit var emptyView: TextView

    private val items = ArrayList<Item>()

    private data class Item(
        val uid: String,
        val name: String,
        val avatar: String,
        val follow: String,
        val fans: String,
        val activeAt: Long,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        root.addView(
            MaterialToolbar(this).apply {
                title = "有更新的酷友"
                setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                setNavigationOnClickListener { finish() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (56 * dp).toInt()
            )
        )

        rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@RecentFollowActivity)
            adapter = Adapter()
            setPadding(0, (4 * dp).toInt(), 0, (16 * dp).toInt())
            clipToPadding = false
        }
        root.addView(
            rv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        emptyView = TextView(this).apply {
            text = "加载中…"
            gravity = Gravity.CENTER
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, (80 * dp).toInt(), 0, 0)
            visibility = android.view.View.VISIBLE
        }
        root.addView(emptyView)

        setContentView(root)
        load()
    }

    private fun load() {
        lifecycleScope.launch(Dispatchers.IO) {
            val url = "/UserHelper/getFollowRows?type=user&getDataRowsByCard=1&sort=lastFeed&title=有更新的酷友"
            networkRepo.getDataList(url, "有更新的酷友", null, null, 1).collect { result ->
                val feed = result.getOrNull()
                val list = feed?.data.orEmpty()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    items.clear()
                    list.forEach { d ->
                        if (d.entityType != "user") return@forEach
                        val uid = d.uid.orEmpty()
                        if (uid.isEmpty()) return@forEach
                        items.add(
                            Item(
                                uid = uid,
                                name = d.username.orEmpty(),
                                avatar = d.userAvatar.orEmpty(),
                                follow = d.follow.orEmpty(),
                                fans = d.fans.orEmpty(),
                                activeAt = d.logintime ?: 0L,
                            )
                        )
                    }
                    emptyView.text = if (items.isEmpty()) "暂时没有更新的酷友" else ""
                    emptyView.visibility =
                        if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                    rv.adapter?.notifyDataSetChanged()
                }
            }
        }
    }

    /** 一行：头像 + 昵称 + 「x关注 y粉丝 z活跃」+ 右侧「查看详情」 */
    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {

        inner class VH(val row: LinearLayout) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
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
                setPadding((12 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            }
            val name = TextView(ctx).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
                tag = "name"
            }
            val meta = TextView(ctx).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
                setPadding(0, (4 * dp).toInt(), 0, 0)
                tag = "meta"
            }
            col.addView(name)
            col.addView(meta)
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val btn = MaterialButton(
                ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
            ).apply {
                text = "查看详情"
                textSize = 12f
                isAllCaps = false
                minWidth = 0
                setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
                tag = "btn"
            }
            row.addView(btn)
            return VH(row)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val it2 = items[position]
            val row = holder.row
            val ctx = row.context
            (row.findViewWithTag<ImageView>("avatar"))?.let { iv ->
                runCatching {
                    Glide.with(ctx).load(it2.avatar)
                        .apply(RequestOptions.circleCropTransform()).into(iv)
                }
            }
            row.findViewWithTag<TextView>("name")?.text = it2.name
            val parts = ArrayList<String>(3)
            if (it2.follow.isNotEmpty() && it2.follow != "0") parts.add("${it2.follow}关注")
            if (it2.fans.isNotEmpty() && it2.fans != "0") parts.add("${it2.fans}粉丝")
            if (it2.activeAt > 0) {
                runCatching { parts.add(DateUtils.fromToday(it2.activeAt) + "活跃") }
            }
            row.findViewWithTag<TextView>("meta")?.text = parts.joinToString("  ")

            val open: () -> Unit = {
                if (it2.uid.isNotEmpty()) {
                    IntentUtil.startActivity<UserActivity>(ctx) { putExtra("id", it2.uid) }
                }
            }
            row.setOnClickListener { open() }
            (row.findViewWithTag<MaterialButton>("btn"))?.setOnClickListener { open() }
        }

        override fun getItemCount() = items.size
    }
}
