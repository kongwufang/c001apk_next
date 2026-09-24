package com.example.c001apk.ui.search

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.c001apk.R
import com.example.c001apk.logic.model.SearchHotResponse
import com.example.c001apk.util.http2https

/** 搜索页默认态的「热门搜索」词条胶囊 */
class HotSearchAdapter(
    private val onItemClick: (SearchHotResponse.Item) -> Unit,
) : RecyclerView.Adapter<HotSearchAdapter.ViewHolder>() {

    private val dataList = mutableListOf<SearchHotResponse.Item>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<SearchHotResponse.Item>?) {
        dataList.clear()
        list?.let { dataList.addAll(it) }
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.logo)
        val title: TextView = view.findViewById(R.id.title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_hot_chip, parent, false)
        )

    override fun getItemCount() = dataList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataList[position]
        holder.title.text = item.title
        // logo 只有是图片地址时才加载，本地图标名（如 ic_xxx）直接忽略
        val logoUrl = item.logo
        if (!logoUrl.isNullOrEmpty() && logoUrl.startsWith("http")) {
            holder.logo.isVisible = true
            Glide.with(holder.logo).load(logoUrl.http2https).into(holder.logo)
        } else {
            Glide.with(holder.logo).clear(holder.logo)
            holder.logo.isVisible = false
        }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }
}
