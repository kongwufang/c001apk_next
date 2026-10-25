package com.example.c001apk.ui.collection

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.logic.model.LocalCollectionFolder
import com.example.c001apk.logic.repository.LocalCollectionRepo
import com.example.c001apk.ui.feed.FeedActivity
import com.example.c001apk.util.IntentUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 本机收藏页（2026-10-25 ✓）
 *
 * > 用户要求：「如果选择本地收藏的时候点开主页的收藏夹应该跳到本地收藏页面，而不是云端」
 *
 * ## 显示什么
 * 一行 = 一个「本地收藏夹 / 内容 id」
 * 因为本地只记 `LocalCollectionEntity(targetId, folderId)` ✓
 * 没有标题、没有封面（那些要拉接口 ✗ 列表页不该发 N 个请求 ✗）
 *
 * ⇒ 点某一行才进 [FeedActivity] ✓ 那时才按 id 拉内容 ✓
 *   （跟本地关注列表页同一个思路 ✓ 用户明确说过「点进去再根据 uid 拉内容」）
 *
 * ## 为什么纯代码构建
 * 就一个列表 ✗ 建 layout + adapter 太重 ✗
 * 跟 [com.example.c001apk.ui.follow.LocalFollowActivity] 保持一致 ✓
 */
@AndroidEntryPoint
class LocalCollectionActivity : AppCompatActivity() {

    @Inject
    lateinit var localCollectionRepo: LocalCollectionRepo

    private lateinit var adapter: Adapter

    /** 行 = 收藏夹名 + 内容 id */
    private data class Row(val folderTitle: String, val targetId: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val bar = TextView(this).apply {
            text = "  ←  本机收藏"
            textSize = 18f
            setPadding(24, 40, 24, 24)
            setOnClickListener { finish() }
        }
        root.addView(bar)

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@LocalCollectionActivity)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        adapter = Adapter()
        list.adapter = adapter
        root.addView(list)

        setContentView(root)

        lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) {
                runCatching {
                    val folders = localCollectionRepo.loadAllFolders()
                    val nameOf = folders.associateBy({ it.id }, { it.title })
                    localCollectionRepo.loadAll().map {
                        Row(
                            folderTitle = nameOf[it.folderId]
                                ?: if (it.folderId == LocalCollectionFolder.DEFAULT_ID) "默认收藏夹" else it.folderId,
                            targetId = it.targetId
                        )
                    }
                }.getOrDefault(emptyList())
            }
            adapter.submit(rows)
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {

        private var items: List<Row> = emptyList()

        fun submit(list: List<Row>) {
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
            holder.tv.text = "${item.folderTitle} · ${item.targetId}"
            holder.tv.setOnClickListener {
                IntentUtil.startActivity<FeedActivity>(holder.itemView.context) {
                    putExtra("id", item.targetId)
                }
            }
        }
    }
}
