package com.example.c001apk.ui.follow

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.logic.model.FollowUserEntity
import com.example.c001apk.logic.repository.FollowRepo
import com.example.c001apk.ui.user.UserActivity
import com.example.c001apk.util.IntentUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 本机关注列表（2026-10-25 ✓）
 *
 * ## 什么时候用这个页
 * 设置里把「关注」关成**本地保存**时，侧边栏「我的关注」会进这里 ✓
 * （云端模式还是进原来的 [FFFListActivity] `type=follow` ✓）
 *
 * ## 为什么只显示 uid
 * 用户明确要求：「关注只存 uid 即可，到时间点进去再根据 uid 拉内容」
 * ⇒ 本地只记 `FollowUserEntity(uid)` ✓
 * ⇒ 列表页不预先拉所有用户的资料（那样会发 N 个请求 ✗）
 * ⇒ 每一行显示 uid（如果关注时顺手存了昵称就显示昵称 ✓）
 * ⇒ **点一下才进用户页** ✓ 那时才按 uid 拉内容 ✓
 *
 * ## 为什么不用 XML
 * 就一个列表 ✗ 建一套 layout + adapter 太重 ✗
 * ⇒ 代码里直接搭 ✓（`RecyclerView` + 内部小 adapter ✓）
 */
@AndroidEntryPoint
class LocalFollowActivity : AppCompatActivity() {

    @Inject
    lateinit var followRepo: FollowRepo

    private lateinit var adapter: Adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 顶部一个简单的返回栏（不引 MaterialToolbar ✗ 这个页要极简 ✓）
        val bar = TextView(this).apply {
            text = "  ←  本机关注"
            textSize = 18f
            setPadding(24, 40, 24, 24)
            setOnClickListener { finish() }
        }
        root.addView(bar)

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@LocalFollowActivity)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        adapter = Adapter()
        list.adapter = adapter
        root.addView(list)

        setContentView(root)

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { followRepo.loadAll() }
            adapter.submit(items)
        }
    }

    /** 极简 adapter：一行 = 一个 uid（有昵称就显示昵称 ✓） */
    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {

        private var items: List<FollowUserEntity> = emptyList()

        fun submit(list: List<FollowUserEntity>) {
            items = list
            notifyDataSetChanged()
        }

        inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(48, 36, 48, 36)
            }
            return VH(tv)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            // 有昵称显示昵称 ✓ 没有就显示 uid ✓（用户要求：只存 uid，点进去再拉 ✓）
            holder.tv.text = if (item.username.isNotEmpty()) {
                "${item.username}（${item.uid}）"
            } else {
                "uid: ${item.uid}"
            }
            holder.tv.setOnClickListener {
                IntentUtil.startActivity<UserActivity>(holder.itemView.context) {
                    putExtra("id", item.uid)
                }
            }
        }
    }
}
