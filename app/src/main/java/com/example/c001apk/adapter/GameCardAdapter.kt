package com.example.c001apk.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.BR
import com.example.c001apk.databinding.ItemHomeGameCommentItemBinding
import com.example.c001apk.databinding.ItemHomeGameSortItemBinding
import com.example.c001apk.databinding.ItemHomeGameTabItemBinding
import com.example.c001apk.databinding.ItemHomeGameTopicGridItemBinding
import com.example.c001apk.databinding.ItemHomeGameTopicIconItemBinding
import com.example.c001apk.databinding.ItemHomeGameTopicImageItemBinding
import com.example.c001apk.databinding.ItemHomeGameTopicTimelineItemBinding
import com.example.c001apk.logic.model.HomeFeedResponse

/**
 * 游戏频道（/v6/page/dataList?url=V15_YOUXI）里卡片的条目适配器。
 *
 * 这些卡片的 entityTemplate 各不相同，但数据都是 entities[]，差别只在条目样式，
 * 所以用 Mode 区分，避免给每种模板各写一份 Adapter / 布局：
 * - TAB      顶部四个入口（iconMiniLinkGridCard）
 * - GRID     热门新游 / 游戏评分（iconLongTitleGridCard / iconGridCard）：图标 + 名字 + 一行灰字
 * - TIMELINE 发售日历（productTimelineListCard）：日期 / 封面 / 名字 / 热度
 * - IMAGE    游戏闲聊（imageScrollCard）：横滑大图
 * - ICON     热议手游（iconScrollCard）：横滑圆标
 * - COMMENT  最新点评（feedListCard）：点评正文 + 对应游戏
 * - SORT     世界频道（sortSelectCard）：排序选项按钮
 */
class GameCardAdapter(
    private val listener: ItemListener,
    private val mode: Mode
) : ListAdapter<HomeFeedResponse.Entities, RecyclerView.ViewHolder>(
    ImageTextScrollCardDiffCallback()
) {

    enum class Mode {
        TAB, GRID, TIMELINE, IMAGE, ICON, COMMENT, SORT;

        /** 横滑排布，条目之间需要额外间距 */
        val isHorizontal: Boolean
            get() = this == IMAGE || this == ICON || this == SORT
    }

    class TabViewHolder(
        val binding: ItemHomeGameTabItemBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Entities) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.executePendingBindings()
        }
    }

    class GridViewHolder(
        val binding: ItemHomeGameTopicGridItemBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Entities) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            // 游戏评分卡显示「N 条」，热门新游卡显示讨论热度
            val value = when {
                !data.ratingTotalNum.isNullOrEmpty() -> "${data.ratingTotalNum} 条"
                !data.starTotalCount.isNullOrEmpty() -> "${data.starTotalCount} 条"
                else -> data.hotNumTxt.orEmpty()
            }
            binding.value.isVisible = value.isNotEmpty()
            binding.value.text = value
            binding.executePendingBindings()
        }
    }

    class TimelineViewHolder(
        val binding: ItemHomeGameTopicTimelineItemBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Entities) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            // release_time 是「2026年11月19日」这种中文原文，也可能是「未公布」
            val raw = data.releaseTime.orEmpty()
            val date = RELEASE_TIME_REGEX.find(raw)
            if (date != null) {
                val (year, month, day) = date.destructured
                binding.day.text = "%02d.%02d".format(month.toInt(), day.toInt())
                binding.year.text = year
            } else {
                binding.day.text = raw
                binding.year.text = ""
            }
            binding.value.text = data.hotNumTxt.orEmpty()
            binding.executePendingBindings()
        }
    }

    class ImageViewHolder(
        val binding: ItemHomeGameTopicImageItemBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Entities) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.executePendingBindings()
        }
    }

    class IconViewHolder(
        val binding: ItemHomeGameTopicIconItemBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Entities) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.executePendingBindings()
        }
    }

    class CommentViewHolder(
        val binding: ItemHomeGameCommentItemBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Entities) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.root.setOnClickListener {
                listener.onViewFeed(
                    it, data.id, null, data.username, null, null, data.message, null,
                    null, null, null
                )
            }
            binding.executePendingBindings()
        }
    }

    class SortViewHolder(
        val binding: ItemHomeGameSortItemBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Entities) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.button.setOnClickListener { view ->
                if (data.url.startsWith("c001apk://search-sort/")) {
                    listener.onSearchSort(data.url.substringAfterLast('/'))
                } else {
                    listener.onOpenLink(view, data.url, data.title)
                }
            }
            binding.executePendingBindings()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (mode) {
            Mode.TAB ->
                TabViewHolder(ItemHomeGameTabItemBinding.inflate(inflater, parent, false), listener)

            Mode.GRID ->
                GridViewHolder(
                    ItemHomeGameTopicGridItemBinding.inflate(inflater, parent, false), listener
                )

            Mode.TIMELINE ->
                TimelineViewHolder(
                    ItemHomeGameTopicTimelineItemBinding.inflate(inflater, parent, false), listener
                )

            Mode.IMAGE ->
                ImageViewHolder(
                    ItemHomeGameTopicImageItemBinding.inflate(inflater, parent, false), listener
                )

            Mode.ICON ->
                IconViewHolder(
                    ItemHomeGameTopicIconItemBinding.inflate(inflater, parent, false), listener
                )

            Mode.COMMENT ->
                CommentViewHolder(
                    ItemHomeGameCommentItemBinding.inflate(inflater, parent, false), listener
                )

            Mode.SORT ->
                SortViewHolder(
                    ItemHomeGameSortItemBinding.inflate(inflater, parent, false), listener
                )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val data = currentList[position]
        when (holder) {
            is TabViewHolder -> holder.bind(data)
            is GridViewHolder -> holder.bind(data)
            is TimelineViewHolder -> holder.bind(data)
            is ImageViewHolder -> holder.bind(data)
            is IconViewHolder -> holder.bind(data)
            is CommentViewHolder -> holder.bind(data)
            is SortViewHolder -> holder.bind(data)
        }
    }

    companion object {
        private val RELEASE_TIME_REGEX = Regex("(\\d{4})年(\\d{1,2})月(\\d{1,2})日")
    }

}
