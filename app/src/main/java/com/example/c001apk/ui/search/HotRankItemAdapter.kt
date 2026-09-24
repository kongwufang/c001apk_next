package com.example.c001apk.ui.search

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.logic.model.SearchHotResponse
import com.google.android.material.color.MaterialColors

/** 热搜榜里的一个条目行：名次 + 标题 + 火焰 + 热度 */
class HotRankItemAdapter(
    private val onItemClick: (SearchHotResponse.Item) -> Unit,
) : RecyclerView.Adapter<HotRankItemAdapter.ViewHolder>() {

    private val dataList = mutableListOf<SearchHotResponse.Item>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<SearchHotResponse.Item>?) {
        dataList.clear()
        list?.let { dataList.addAll(it) }
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rank: TextView = view.findViewById(R.id.rank)
        val title: TextView = view.findViewById(R.id.title)
        val hotNum: TextView = view.findViewById(R.id.hotNum)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_hot_item, parent, false)
        )

    override fun getItemCount() = dataList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataList[position]
        val rankNo = position + 1
        holder.rank.text = rankNo.toString()
        holder.title.text = item.title
        // 服务端已经给好「528万」这类文案，兜底才用原始数值
        holder.hotNum.text = item.hotNumTxt ?: item.hotNum.orEmpty()

        // 官方前三名是彩色圆角方块 + 白字，第 4 名开始只是普通灰色数字
        val bgColor = when (rankNo) {
            1 -> R.color.search_rank_1
            2 -> R.color.search_rank_2
            3 -> R.color.search_rank_3
            else -> 0
        }
        if (bgColor != 0) {
            holder.rank.background = GradientDrawable().apply {
                cornerRadius = holder.itemView.resources.displayMetrics.density * 4f
                setColor(ContextCompat.getColor(holder.itemView.context, bgColor))
            }
            holder.rank.setTextColor(Color.WHITE)
        } else {
            holder.rank.background = null
            holder.rank.setTextColor(
                MaterialColors.getColor(
                    holder.rank,
                    com.google.android.material.R.attr.colorOnSurfaceVariant,
                    0
                )
            )
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }
}
