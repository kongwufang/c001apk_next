package com.example.c001apk.ui.search

import android.annotation.SuppressLint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.c001apk.R
import com.example.c001apk.logic.model.SearchSuggestResponse
import com.example.c001apk.util.NetWorkUtil
import com.google.android.material.color.MaterialColors

/**
 * 搜索联想列表。
 *
 * 实测（2026-11-06）服务端会在联想里夹带**卡片**（`entityType=card`）：
 *
 *   · `iconListCard`   —— 卡内 1~3 条 topic（logo + 标题 + 「13.2万热度 11.0万讨论」+ 箭头）
 *     官方布局：卡片 = item_title_list，行 = item_topic_view
 *   · `imageScaleCard` —— 一条横幅（抖音广告位）
 *     官方布局：item_image_list_card
 *
 * 所以在 3 种 viewType 之间分派：
 *   [TYPE_WORD]   —— 原来的普通联想词（首项是「搜索用户：xxx」）
 *   [TYPE_LIST]   —— iconListCard（卡内多行）
 *   [TYPE_BANNER] —— imageScaleCard（单图）
 */
class SearchSuggestAdapter(
    /** 点击整行：直接搜索该词；isUser 表示这是「搜索用户」条目 */
    private val onItemClick: (String, Boolean) -> Unit,
    /** 点击右侧箭头：只把词填回输入框，不发起搜索 */
    private val onFillClick: (String) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_WORD = 0
        private const val TYPE_LIST = 1
        private const val TYPE_BANNER = 2
        private const val USER_PREFIX = "搜索用户："
    }

    private val dataList = mutableListOf<SearchSuggestResponse.Data>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<SearchSuggestResponse.Data>?) {
        dataList.clear()
        list?.let { dataList.addAll(it) }
        notifyDataSetChanged()
    }

    private fun typeOf(item: SearchSuggestResponse.Data): Int = when {
        item.isCard && item.template == "iconListCard" && !item.entities.isNullOrEmpty() -> TYPE_LIST
        item.isCard && item.template == "imageScaleCard" && item.firstEntity?.pic != null -> TYPE_BANNER
        else -> TYPE_WORD
    }

    override fun getItemViewType(position: Int) = typeOf(dataList[position])

    override fun getItemCount() = dataList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_LIST -> ListVH(inflater.inflate(R.layout.item_search_suggest_card, parent, false))
            TYPE_BANNER -> BannerVH(
                inflater.inflate(R.layout.item_search_suggest_banner, parent, false)
            )

            else -> WordVH(inflater.inflate(R.layout.item_search_suggest, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = dataList[position]
        when (holder) {
            is WordVH -> bindWord(holder, item)
            is ListVH -> bindList(holder, item)
            is BannerVH -> bindBanner(holder, item)
        }
    }

    // ---------------- 普通联想词（原逻辑保留） ----------------

    private fun bindWord(holder: WordVH, item: SearchSuggestResponse.Data) {
        val text = item.title.orEmpty()
        val isUserItem = text.startsWith(USER_PREFIX)
        val word = if (isUserItem) text.removePrefix(USER_PREFIX) else text

        val accent = MaterialColors.getColor(
            holder.title,
            androidx.appcompat.R.attr.colorPrimary
        )
        holder.title.text = if (isUserItem) {
            val s = SpannableString(text)
            s.setSpan(ForegroundColorSpan(accent), USER_PREFIX.length, text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s
        } else text
        holder.icon.setImageResource(
            if (isUserItem) R.drawable.ic_account else R.drawable.ic_search
        )
        holder.fill.isVisible = !isUserItem
        holder.itemView.setOnClickListener { onItemClick(word, isUserItem) }
        holder.fill.setOnClickListener { onFillClick(word) }
    }

    // ---------------- iconListCard：卡内 N 行 ----------------

    private inner class ListVH(view: View) : RecyclerView.ViewHolder(view) {
        val rows: LinearLayout = view.findViewById(R.id.rows)
    }

    private fun bindList(holder: ListVH, item: SearchSuggestResponse.Data) {
        holder.rows.removeAllViews()
        val entities = item.entities.orEmpty()
        val inflater = LayoutInflater.from(holder.rows.context)
        entities.forEachIndexed { index, e ->
            val row = inflater.inflate(R.layout.item_search_suggest_row, holder.rows, false)
            val logo = row.findViewById<ImageView>(R.id.row_logo)
            val title = row.findViewById<TextView>(R.id.row_title)
            val sub = row.findViewById<TextView>(R.id.row_sub)
            val arrow = row.findViewById<ImageView>(R.id.row_arrow)

            title.text = e.title.orEmpty()
            // 副标题：官方这个位置放的就是「N关注 M讨论」这类统计
            val subText = listOfNotNull(
                e.hotNumTxt?.takeIf { it.isNotBlank() }?.let { "$it 热度" },
                e.commentNumTxt?.takeIf { it.isNotBlank() }?.let { "$it 讨论" },
            ).joinToString("  ")
                .ifBlank { e.description.orEmpty() }
            sub.text = subText
            sub.isVisible = subText.isNotBlank()

            if (!e.logo.isNullOrBlank()) {
                Glide.with(logo).load(e.logo).into(logo)
            } else {
                logo.setImageResource(R.drawable.ic_account)
            }
            arrow.isVisible = !e.url.isNullOrBlank()

            row.setOnClickListener {
                val url = e.url.orEmpty()
                if (url.isNotBlank()) {
                    NetWorkUtil.openLink(row.context, url, e.title)
                }
            }
            holder.rows.addView(row)
            if (index < entities.size - 1) {
                val divider = View(holder.rows.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (row.resources.displayMetrics.density).toInt()
                    )
                    setBackgroundColor(
                        MaterialColors.getColor(
                            row,
                            com.google.android.material.R.attr.colorSurfaceVariant
                        )
                    )
                }
                holder.rows.addView(divider)
            }
        }
    }

    // ---------------- imageScaleCard：横幅 ----------------

    private inner class BannerVH(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.banner_image)
    }

    private fun bindBanner(holder: BannerVH, item: SearchSuggestResponse.Data) {
        val e = item.firstEntity ?: return
        Glide.with(holder.image).load(e.pic).into(holder.image)
        holder.itemView.setOnClickListener {
            // 这类 url 形如 /clickUrl/csjNewInterAction?posId=…（广告位跳转，需要绝对地址）
            val url = e.url.orEmpty()
            if (url.isNotBlank()) {
                val abs = if (url.startsWith("http")) url else "https://www.coolapk.com$url"
                NetWorkUtil.openLink(holder.itemView.context, abs, e.title)
            }
        }
    }

    // ---------------- ViewHolder ----------------

    private inner class WordVH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val title: TextView = view.findViewById(R.id.title)
        val fill: ImageView = view.findViewById(R.id.fill)
    }
}
