package com.example.c001apk.ui.search

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.logic.model.SearchHotResponse

/**
 * 热搜榜的横向滚动容器：每个条目是一整列榜单。
 * 列宽取屏宽的 62%，滑动时能同时露出左右两列，与官方观感一致。
 */
class HotRankColumnAdapter(
    private val onItemClick: (SearchHotResponse.Item) -> Unit,
) : RecyclerView.Adapter<HotRankColumnAdapter.ViewHolder>() {

    private val dataList = mutableListOf<SearchHotResponse.Item>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<SearchHotResponse.Item>?) {
        dataList.clear()
        list?.let { dataList.addAll(it) }
        notifyDataSetChanged()
    }

    /** 供 TabLayout 反查榜单标题 */
    fun titleAt(position: Int): String? = dataList.getOrNull(position)?.title

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rankRecyclerView: RecyclerView = view.findViewById(R.id.rankRecyclerView)
        var itemAdapter: HotRankItemAdapter? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val holder = ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_hot_column, parent, false)
        )
        holder.itemView.layoutParams = RecyclerView.LayoutParams(
            (parent.resources.displayMetrics.widthPixels * 0.62f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        holder.itemAdapter = HotRankItemAdapter(onItemClick).also {
            holder.rankRecyclerView.layoutManager = LinearLayoutManager(parent.context)
            holder.rankRecyclerView.adapter = it
        }
        return holder
    }

    override fun getItemCount() = dataList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemAdapter?.submitList(dataList[position].entities)
    }
}
