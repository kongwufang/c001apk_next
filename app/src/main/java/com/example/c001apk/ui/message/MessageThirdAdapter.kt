package com.example.c001apk.ui.message

import com.example.c001apk.ui.base.PageHostActivity
import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemMessageMessBinding
import com.example.c001apk.ui.messagedetail.MessageActivity
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.UnreadCounter


/**
 * 消息中心的入口宫格：@我的动态 / @我的评论 / 我收到的赞 / 好友关注 / 我的回复 /
 * 私信 / 酷安小秘书。
 *
 * 小秘书单独开一个入口是因为它**不是私信对象**：登录提醒和站内信都走通知接口
 * （`/v6/notification/list` 里 type=notify_xms），`/v6/message/list` 里根本没有它。
 *
 * 「我的回复」（别人回复我的评论）是新加的：这类通知原先只在下方汇总列表里，
 * 老 `/v6/notification/list` 更是一条都没有（实测一页 20 条里 15 条是小秘书）。
 * 它走 V18 通知流，未读数用 checkCount 的 commentme —— 跟下方汇总列表的 commentMe
 * 分类共用同一份账本，所以从哪个口进去看的，另一个口的红点都会跟着消。
 */
class MessageThirdAdapter : RecyclerView.Adapter<MessageThirdAdapter.ThirdViewHolder>() {

    private val messTitle = listOf(
        "@我的动态", "@我的评论", "我收到的赞", "好友关注", "我的回复", "私信", "酷安小秘书"
    )
    private val logoColorList =
        listOf("#2196f3", "#00bcd4", "#4caf50", "#f44336", "#e91e63", "#ff9800", "#9c27b0")
    private val logoList = listOf(
        R.drawable.ic_at, R.drawable.ic_comment, R.drawable.ic_thumb,
        R.drawable.ic_add, R.drawable.ic_reply_white, R.drawable.ic_message1,
        R.drawable.ic_notification_bell
    )

    /** 这一轮里用户已经点进去看过的格子：红点先撤掉，免得要等回到本页才消失 */
    private val tapped = mutableSetOf<Int>()

    @SuppressLint("NotifyDataSetChanged")
    fun updateBadge() {
        tapped.clear()
        notifyDataSetChanged()
    }

    /**
     * 某一格的未读数，0 表示不画红点。
     *
     * 数字统一由 [UnreadCounter] 算（服务端未读 − 本机已读账本），首页工具栏的消息
     * 入口角标读的是同一份，改口径不会两处跑偏。
     *
     * 红点由 [MessageBadgeDecoration] 在 RecyclerView 顶层绘制，这里只算数字、不碰 View：
     * 数字画在 Canvas 上才不会随 item 一起被裁或被相邻格子盖住。
     */
    fun unreadCount(position: Int): Int {
        if (position in tapped) return 0
        return UnreadCounter.of(categoryOf(position))
    }

    /**
     * 宫格位置 → 未读分类。
     * 位置顺序必须跟 [messTitle] 和 [UnreadCounter.gridCategories] 保持一致。
     */
    private fun categoryOf(position: Int) = when (position) {
        0 -> UnreadCounter.AT_ME
        1 -> UnreadCounter.AT_COMMENT_ME
        2 -> UnreadCounter.FEED_LIKE
        3 -> UnreadCounter.CONTACTS_FOLLOW
        4 -> UnreadCounter.COMMENT_ME
        5 -> UnreadCounter.MESSAGE
        else -> UnreadCounter.SECRETARY
    }

    inner class ThirdViewHolder(val binding: ItemMessageMessBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            if (PrefManager.isLogin) {
                itemView.setOnClickListener {
                    // 红点由 decoration 画，先把它记成「点过」，跳转前就撤掉
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        tapped.add(position)
                        notifyItemChanged(position)
                    }
                    // 这里不再把 CookieUtil 的未读数本地清零：红点统一由
                    // 「服务端未读数 − 本机已读账本」算出来，点进分类页看过的那些
                    // 会在分类页里记账（MessageViewModel.markSeen），返回后红点自然消掉
                    // ★ 全量迁移：先算出 type，再进宿主（原来是 when 里直接 putExtra ✗）
                    val msgType = when (binding.title.text) {
                        "@我的动态" -> "atMe"
                        "@我的评论" -> "atCommentMe"
                        "我收到的赞" -> "feedLike"
                        "好友关注" -> "contactsFollow"
                        "我的回复" -> "commentMe"
                        "私信" -> "list"
                        "酷安小秘书" -> "secretary"
                        else -> "atMe"
                    }
                    itemView.context.startActivity(
                        PageHostActivity.intentByPage(
                            itemView.context, "message",
                            android.os.Bundle().apply { putString("type", msgType) }, null,
                        )
                    )
                }
            }
        }
    }

    override fun getItemViewType(position: Int) = position

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ThirdViewHolder {
        val binding =
            ItemMessageMessBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.title.text = messTitle[position]
        binding.logoCover.setBackgroundColor(Color.parseColor(logoColorList[position]))
        binding.logo.setBackgroundDrawable(parent.context.getDrawable(logoList[position]))
        return ThirdViewHolder(binding)
    }

    override fun getItemCount() = messTitle.size

    override fun onBindViewHolder(holder: ThirdViewHolder, position: Int) {
        // 红点改由 MessageBadgeDecoration 在顶层画，item 里没有随数据变化的东西
    }
}
