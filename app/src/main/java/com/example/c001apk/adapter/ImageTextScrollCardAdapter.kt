package com.example.c001apk.adapter

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.BR
import com.example.c001apk.databinding.ItemHomeImageTextScrollCardItemBinding
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.util.DensityTool
import com.example.c001apk.util.dp

class ImageTextScrollCardAdapter(
    private val listener: ItemListener
) :
    ListAdapter<HomeFeedResponse.Entities, ImageTextScrollCardAdapter.ViewHolder>(
        ImageTextScrollCardDiffCallback()
    ) {
    class ViewHolder(
        val binding: ItemHomeImageTextScrollCardItemBinding,
        val listener: ItemListener
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(data: HomeFeedResponse.Entities) {
            binding.setVariable(BR.data, data)
            binding.setVariable(BR.listener, listener)
            binding.executePendingBindings()

            // ★ 2026-10-31 优质图文组内的每张小卡也查公共黑名单（用户要求）
            //   命中 ⇒ 藏封面 + 标题、显示红条 ✓
            //   ⚠️ 这两个 View 的 visibility **没有** databinding 绑定 ✓
            //      所以不会被 BindingAdapter 设回来 ✓（不会重现「藏了又显示」✗）
            val id = data.id
            val apply = { blocked: Boolean ->
                binding.imageTextScrollCard.isVisible = !blocked
                binding.title.isVisible = !blocked
                binding.blockedBar.isVisible = blocked
                if (blocked) {
                    binding.blockedBar.text =
                        com.example.c001apk.util.Blocklist.cachedMsg(
                            com.example.c001apk.util.Blocklist.Kind.FEED, id
                        ) ?: binding.root.context.getString(
                            com.example.c001apk.R.string.blocked_multi_report
                        )
                }
            }
            if (id.isNullOrBlank() || !com.example.c001apk.util.Blocklist.enabled()) {
                apply(false)
            } else {
                val known = com.example.c001apk.util.Blocklist.cached(
                    com.example.c001apk.util.Blocklist.Kind.FEED, id
                )
                if (known != null) {
                    apply(known)
                } else {
                    apply(false)
                    com.example.c001apk.util.Blocklist.prefetch(
                        com.example.c001apk.util.Blocklist.Kind.FEED, listOf(id)
                    ) {
                        apply(
                            com.example.c001apk.util.Blocklist.isBlocked(
                                com.example.c001apk.util.Blocklist.Kind.FEED, id
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemHomeImageTextScrollCardItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        val padding = 50.dp
        val imageWidth =
            if (parent.context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                DensityTool.getScreenWidth(parent.context) - padding
            else
                DensityTool.getScreenWidth(parent.context) / 2 - padding
        binding.root.layoutParams.width = imageWidth - imageWidth / 3
        return ViewHolder(binding, listener)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(currentList[position])
    }

}

class ImageTextScrollCardDiffCallback : DiffUtil.ItemCallback<HomeFeedResponse.Entities>() {
    override fun areItemsTheSame(
        oldItem: HomeFeedResponse.Entities,
        newItem: HomeFeedResponse.Entities
    ): Boolean {
        return oldItem.url == newItem.url
    }

    override fun areContentsTheSame(
        oldItem: HomeFeedResponse.Entities,
        newItem: HomeFeedResponse.Entities
    ): Boolean {
        return oldItem.url == newItem.url
    }
}
