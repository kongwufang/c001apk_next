package com.example.c001apk.adapter

import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.example.c001apk.logic.model.HomeFeedResponse

abstract class BaseAdapter<T : ViewDataBinding> :
    ListAdapter<HomeFeedResponse.Data, BaseViewHolder<T>>(HomeFeedDiffCallback()) {

    override fun onBindViewHolder(holder: BaseViewHolder<T>, position: Int) {
        holder.bind(currentList[position])
        holder.dataBinding.executePendingBindings()
    }

    /**
     * ★ 2026-10-31 分页级批量查询（用户要求）
     *
     * 用户原话：
     * >「应该是用户刷新首页内容时，拿到了首页刷新的那十几个帖子，
     * >  一次性把那十几个帖子全部询问，然后一次性渲染卡片，
     * >  而不是等划到才渲染」
     *
     * `BaseAdapter` 是 `ListAdapter` ⇒ 所有列表（首页 / 关注 / 搜索 / 话题 / 个人主页）
     * 都走 `submitList()` ✓ ⇒ **只覆写这一处**就把所有列表都覆盖了 ✓
     *
     * ⇒ 拉到一个分页 ⇒ 立刻把这一页的 feed id / 用户 uid 一次性发过去问 ✓
     *    （每类最多 100 个 ✓ 收到结果后 bind 时直接读缓存 ⇒ 不闪烁 ✓）
     */
    override fun submitList(list: List<HomeFeedResponse.Data>?) {
        super.submitList(list)
        if (list.isNullOrEmpty()) return
        runCatching {
            com.example.c001apk.util.Blocklist.prefetchPage(
                feeds = list.mapNotNull { it.id },
                users = list.mapNotNull { it.userInfo?.uid ?: it.uid },
                // ★★ 2026-10-31 补上 **话题** ★★
                //   之前只发 feeds + users ✗ ⇒ 话题 id 从来没问过服务器 ✗
                //   ⇒ `cached(TOPIC, id)` 永远 null ⇒ 话题卡片永远不出红条 ✗（用户反馈）
                //   话题实体：`entityType == "topic"` ✓ 也有混在别处的话题卡片
                //   ⇒ 只要模板里出现话题就一起问 ✓（多问几个 id 很便宜 ✓）
                topics = list.filter {
                    it.entityType == "topic" || it.ttitle?.isNotBlank() == true
                }.mapNotNull { it.id },
            ) {
                // ★★ 2026-10-31 关键 ★★
                //   `getItemViewType` 是**同步**在布局时调用的 ✗
                //   那一刻批量查询还没回来 ⇒ 缓存空 ⇒ 只能返回普通卡片 ✗
                //   结果回来后如果**不通知** ⇒ 界面永远停在普通卡片 ✗（踩过：首页完全没红条）
                //   ⇒ 刷新一遍 ✓（`getItemViewType` 会重新分流到 TYPE_BLOCKED ✓）
                if (itemCount > 0) {
                    notifyItemRangeChanged(0, itemCount)
                }
            }
        }
    }

}

class HomeFeedDiffCallback : DiffUtil.ItemCallback<HomeFeedResponse.Data>() {
    override fun areItemsTheSame(
        oldItem: HomeFeedResponse.Data,
        newItem: HomeFeedResponse.Data
    ): Boolean {
        // 版本历史（/v6/apk/downloadVersionList）的条目不带 entityId，
        // 不退回 versionId 的话 DiffUtil 会把整列视为同一条，列表只会剩一行
        return (oldItem.entityId ?: oldItem.versionId) == (newItem.entityId ?: newItem.versionId)
    }

    override fun areContentsTheSame(
        oldItem: HomeFeedResponse.Data,
        newItem: HomeFeedResponse.Data
    ): Boolean {
        return if ((oldItem.entityTemplate == "iconMiniScrollCard" && newItem.entityTemplate == "iconMiniScrollCard")
            || oldItem.entityTemplate == "iconMiniGridCard" && newItem.entityTemplate == "iconMiniGridCard"
        )
            oldItem == newItem
        else oldItem.lastupdate == newItem.lastupdate && oldItem.likenum == newItem.likenum
                && oldItem.isFollow == newItem.isFollow && oldItem.isStickTop == newItem.isStickTop
    }

    override fun getChangePayload(
        oldItem: HomeFeedResponse.Data,
        newItem: HomeFeedResponse.Data
    ): Any? {
        return if (oldItem.likenum != newItem.likenum || oldItem.isFollow != newItem.isFollow) true else null
    }

}
