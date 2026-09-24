package com.example.c001apk.ui.search

import android.annotation.SuppressLint
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.logic.model.SearchSuggestResponse
import com.google.android.material.color.MaterialColors

/**
 * 搜索联想列表。
 * 首项是「搜索用户：关键词」（带人形图标、没有回填箭头），其余是普通联想词。
 */
class SearchSuggestAdapter(
    /** 点击整行：直接搜索该词；isUser 表示这是「搜索用户」条目 */
    private val onItemClick: (String, Boolean) -> Unit,
    /** 点击右侧箭头：只把词填回输入框，不发起搜索 */
    private val onFillClick: (String) -> Unit,
) : RecyclerView.Adapter<SearchSuggestAdapter.ViewHolder>() {

    private val dataList = mutableListOf<SearchSuggestResponse.Data>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(list: List<SearchSuggestResponse.Data>?) {
        dataList.clear()
        list?.let { dataList.addAll(it) }
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val title: TextView = view.findViewById(R.id.title)
        val fill: ImageView = view.findViewById(R.id.fill)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_suggest, parent, false)
        )

    override fun getItemCount() = dataList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataList[position]
        val text = item.title.orEmpty()
        // 「搜索用户：xxx」这种条目走人形图标，且不提供回填
        val isUserItem = text.startsWith(USER_PREFIX)
        val word = if (isUserItem) text.removePrefix(USER_PREFIX) else text

        val accent = MaterialColors.getColor(
            holder.title,
            com.google.android.material.R.attr.colorPrimary,
            0
        )
        holder.icon.setImageResource(if (isUserItem) R.drawable.ic_account else R.drawable.ic_search)
        holder.title.text = highlight(text, word, accent)
        holder.fill.isVisible = !isUserItem

        holder.itemView.setOnClickListener { onItemClick(word, isUserItem) }
        holder.fill.setOnClickListener { onFillClick(word) }
    }

    /**
     * 把 [text] 里与 [keyword] 最长的公共片段染成主题色，模仿官方的关键词高亮。
     * 公共片段不足 2 个字符时不做高亮。
     */
    private fun highlight(text: String, keyword: String, color: Int): CharSequence {
        if (keyword.isEmpty() || text.isEmpty()) return text
        val (start, length) = longestCommonSubstring(text, keyword)
        if (length < 2) return text
        return SpannableString(text).apply {
            setSpan(
                ForegroundColorSpan(color),
                start,
                start + length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /** 返回 [text] 与 [keyword] 最长公共子串的 (起始下标, 长度) */
    private fun longestCommonSubstring(text: String, keyword: String): Pair<Int, Int> {
        var bestStart = 0
        var bestLength = 0
        val dp = IntArray(text.length + 1)
        for (i in 1..keyword.length) {
            var diagonal = 0
            for (j in 1..text.length) {
                val temp = dp[j]
                dp[j] = if (keyword[i - 1].lowercaseChar() == text[j - 1].lowercaseChar())
                    diagonal + 1
                else
                    0
                if (dp[j] > bestLength) {
                    bestLength = dp[j]
                    bestStart = j - dp[j]
                }
                diagonal = temp
            }
        }
        return bestStart to bestLength
    }

    companion object {
        private const val USER_PREFIX = "搜索用户："
    }
}
