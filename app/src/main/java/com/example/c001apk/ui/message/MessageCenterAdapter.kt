package com.example.c001apk.ui.message

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.setPadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.BR
import com.example.c001apk.R
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.adapter.PopClickListener
import com.example.c001apk.databinding.ItemMessageContentBinding
import com.example.c001apk.databinding.ItemMessageItemBinding
import com.example.c001apk.databinding.ItemMessageUserBinding
import com.example.c001apk.logic.model.MessageResponse
import com.example.c001apk.ui.feed.FeedActivity
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.dp

/**
 * 消息中心外层列表：把各类未读（@我 / @我的评论 / 评论回复 / 赞 / 关注）混排在一起。
 *
 * 卡片样式直接复用分类页那三套布局：
 * - feed 卡片：@我 / @我的评论 / 我收到的赞（`ItemMessageContentBinding`，靠 `type` 换文案）
 * - 用户条目：好友关注（`ItemMessageUserBinding`，显示「关注了你」）
 * - 通知条目：评论回复（`ItemMessageItemBinding`，note 富文本）
 *
 * 所以同一条通知在「外层未读汇总」和「点进去的分类页」里长得一模一样。
 */
class MessageCenterAdapter(
    private val listener: ItemListener
) : ListAdapter<MessageCenterAdapter.Item, RecyclerView.ViewHolder>(DiffCallback()) {

    /** 列表条目 = 数据 + 它属于哪个分类（分类决定卡片样式和点击行为） */
    data class Item(val category: String, val data: MessageResponse.Data)

    /**
     * feed 类卡片（@我 / @我的评论 / 我收到的赞）。
     * `type` 必须逐次绑定传入：RecyclerView 的 ViewHolder 会跨分类复用，
     * 在创建时定死分类会把上一类的文案带到下一类。
     */
    private class FeedViewHolder(
        val binding: ItemMessageContentBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(category: String, data: MessageResponse.Data) {
            binding.setVariable(BR.type, category)
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            // 同分类页：布局里的分类判定读这个 Boolean（布局里写 `type == `feedLike``
            // 是引用比较，恒 false）
            binding.setVariable(BR.isFeedLike, category == "feedLike")
            binding.executePendingBindings()

            // 分类页里 @我 / @我的评论 点进动态详情；「我收到的赞」的 id 是点赞记录主键
            // （feed-<动态id>-<点赞人uid>），拿去开动态会开错，真正要开的是被赞的动态
            // —— 归一化时它在 fid 上（取自条目自带的 url /feed/<动态id>）。
            // 「赞了你的评论」还带一个评论 id（rid），有值时顺带滚到评论区。
            val target = if (category == "feedLike") data.fid.orEmpty() else data.id
            val rid = if (category == "feedLike")
                data.rid?.takeIf { it > 0 }?.toString().orEmpty() else ""
            itemView.setOnClickListener(
                if (target.isBlank()) null
                else { view ->
                    IntentUtil.startActivity<FeedActivity>(view.context) {
                        putExtra("id", target)
                        if (rid.isNotBlank()) {
                            putExtra("rid", rid)
                            putExtra("viewReply", true)
                        }
                    }
                }
            )
        }
    }

    /** 好友关注（`ItemMessageUserBinding`，点击开对方主页由布局里的 databinding 处理） */
    private class UserViewHolder(
        val binding: ItemMessageUserBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: String, data: MessageResponse.Data) {
            binding.setVariable(BR.type, category)
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.executePendingBindings()
        }
    }

    /** 评论回复（跟分类页的小秘书条目共用同一套渲染和长按菜单） */
    private class NotifyViewHolder(
        val binding: ItemMessageItemBinding,
        val listener: ItemListener
    ) : RecyclerView.ViewHolder(binding.root) {

        private var id = ""
        private var uid = ""
        private var fromusername = ""

        init {
            itemView.setOnLongClickListener {
                listener.onMessLongClicked(fromusername, id, bindingAdapterPosition)
                true
            }

            binding.expand.setOnClickListener {
                PopupMenu(it.context, it).apply {
                    menuInflater.inflate(R.menu.feed_reply_menu, menu).apply {
                        menu.findItem(R.id.show)?.isVisible = false
                        menu.findItem(R.id.copy)?.isVisible = false
                        menu.findItem(R.id.delete)?.isVisible = false
                    }
                    setOnMenuItemClickListener(
                        PopClickListener(
                            listener, it.context, "user", id, uid, bindingAdapterPosition
                        )
                    )
                    show()
                }
            }
        }

        fun bind(data: MessageResponse.Data) {
            id = data.id
            uid = data.fromuid
            fromusername = data.fromusername

            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.executePendingBindings()
        }
    }

    override fun getItemViewType(position: Int): Int =
        viewTypeOf(currentList[position].category)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(
                ItemMessageUserBinding.inflate(inflater, parent, false), listener
            )

            TYPE_NOTIFY -> {
                val binding = ItemMessageItemBinding.inflate(inflater, parent, false)
                // 跟分类页的小秘书条目同一套卡片样式，两处看起来是一回事
                binding.root.setPadding(10.dp)
                binding.root.background = parent.context.getDrawable(R.drawable.round_corners_12)
                binding.root.foreground =
                    parent.context.getDrawable(R.drawable.selector_bg_12_trans)
                NotifyViewHolder(binding, listener)
            }

            else -> FeedViewHolder(
                ItemMessageContentBinding.inflate(inflater, parent, false), listener
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = currentList[position]
        when (holder) {
            is FeedViewHolder -> holder.bind(item.category, item.data)
            is UserViewHolder -> holder.bind(item.category, item.data)
            is NotifyViewHolder -> holder.bind(item.data)
        }
    }

    companion object {
        private const val TYPE_FEED = 0
        private const val TYPE_USER = 1
        private const val TYPE_NOTIFY = 2

        /** 分类 → 卡片样式 */
        fun viewTypeOf(category: String): Int = when (category) {
            "atMe", "atCommentMe", "feedLike" -> TYPE_FEED
            "contactsFollow" -> TYPE_USER
            else -> TYPE_NOTIFY
        }
    }

    internal class DiffCallback : DiffUtil.ItemCallback<Item>() {
        override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
            oldItem.category == newItem.category && oldItem.data.id == newItem.data.id

        override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
            oldItem == newItem
    }
}
