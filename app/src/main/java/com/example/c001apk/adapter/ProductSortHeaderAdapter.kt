package com.example.c001apk.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.databinding.ItemProductSortHeaderBinding
import com.google.android.material.button.MaterialButton

/**
 * 顶部横排排序控件（对齐官方 UI）
 *
 * 一开始只服务「产品页讨论 / 话题讨论」tab 的三段式「默认 / 最新 / 热度」，
 * 2026-11-13 扩成**可配置项**，给搜索「动态」tab 复用官方那 5 个 chip：
 * 综合 / 实时 / 热度 / 评论 / 精确
 *
 * [labels] 是「显示名 to 值」的列表：
 * · 话题/产品页不传 ⇒ 默认三段，值与显示名一致（"默认"/"最新"/"热度"）
 * · 搜索页传 5 项，值是接口参数（default/dateline/hot/reply/none）
 *
 * 按钮**代码动态生成**（原来 XML 里写死 3 个，不够用）；
 * [onSort] 只在用户点击时回调，恢复选中态不会触发。
 */
class ProductSortHeaderAdapter(
    private val currentSort: String,
    private val labels: List<Pair<String, String>> = DEFAULT_LABELS,
    private val onSort: (String) -> Unit
) : RecyclerView.Adapter<ProductSortHeaderAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemProductSortHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    private var suppressCallback = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemProductSortHeaderBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        (holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)
            ?.isFullSpan = true

        with(holder.binding) {
            sortGroup.clearOnButtonCheckedListeners()
            sortGroup.removeAllViews()

            suppressCallback = true
            var checkedId = View.NO_ID
            labels.forEach { (label, value) ->
                val btn = MaterialButton(
                    itemView.context,
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
                ).apply {
                    id = View.generateViewId()
                    text = label
                    textSize = 12f
                    isAllCaps = false
                    minWidth = 0
                    minHeight = 0
                    setPadding(28, 12, 28, 12)
                    tag = value
                }
                sortGroup.addView(btn)
                if (value == currentSort || (checkedId == View.NO_ID && value == labels.first().second)) {
                    checkedId = btn.id
                }
            }
            if (checkedId != View.NO_ID) sortGroup.check(checkedId)
            suppressCallback = false

            sortGroup.addOnButtonCheckedListener { _, id, isChecked ->
                if (!isChecked || suppressCallback) return@addOnButtonCheckedListener
                val v = sortGroup.findViewById<View>(id)?.tag as? String
                    ?: return@addOnButtonCheckedListener
                onSort(v)
            }
        }
    }

    companion object {
        /** 话题/产品页默认三段（值与显示名一致，调用方按名字判断） */
        private val DEFAULT_LABELS = listOf("默认" to "默认", "最新" to "最新", "热度" to "热度")

        /** 搜索「动态」tab：官方 5 个 chip，值全部由抓包实测确定 */
        val SEARCH_LABELS = listOf(
            "综合" to "default",
            "实时" to "dateline",
            "热度" to "hot",
            "评论" to "reply",
            "精确" to "none"
        )
    }
}
