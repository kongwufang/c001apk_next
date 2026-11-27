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
        if (oldItem.entityId == "c001apk-search-sort" || newItem.entityId == "c001apk-search-sort")
            return oldItem == newItem
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
