package com.example.c001apk.ui.message

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.databinding.ItemMessageFffBinding
import com.example.c001apk.ui.follow.FFFListActivity
import com.example.c001apk.ui.follow.LocalFollowActivity
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager

class MessageFirstAdapter : RecyclerView.Adapter<MessageFirstAdapter.FirstViewHolder>() {
    private var cloudCounts: List<String> = emptyList()
    private var localCount: Int? = null
    private var signedIn = false
    private var cloudFollow = false

    fun setMode(isLogin: Boolean, saveFollowToCloud: Boolean) {
        if (signedIn == isLogin && cloudFollow == saveFollowToCloud) return
        signedIn = isLogin
        cloudFollow = saveFollowToCloud
        notifyItemChanged(0)
    }

    fun setFFFList(counts: List<String>) {
        cloudCounts = counts
        notifyItemChanged(0)
    }

    fun setLocalFollowCount(count: Int) {
        localCount = count
        if (!cloudFollow) notifyItemChanged(0)
    }

    class FirstViewHolder(val binding: ItemMessageFffBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.feedLayout.setOnClickListener { openCloud("feed") }
            binding.fansLayout.setOnClickListener { openCloud("fans") }
            binding.followLayout.setOnClickListener {
                if (PrefManager.saveFollowToCloud) openCloud("follow")
                else IntentUtil.startActivity<LocalFollowActivity>(itemView.context) {}
            }
        }

        private fun openCloud(type: String) {
            if (!PrefManager.isLogin) return
            IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                putExtra("uid", PrefManager.uid)
                putExtra("type", type)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FirstViewHolder {
        val binding = ItemMessageFffBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        (binding.root.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan = true
        return FirstViewHolder(binding)
    }

    override fun getItemCount() = 1

    override fun onBindViewHolder(holder: FirstViewHolder, position: Int) = with(holder.binding) {
        feedLayout.isVisible = signedIn
        fansLayout.isVisible = signedIn
        line1.isVisible = signedIn
        line2.isVisible = signedIn
        feedTitle.text = "动态"
        feedCount.text = cloudCounts.getOrNull(0) ?: "—"
        followTitle.text = if (cloudFollow) "关注" else "本机关注"
        followCount.text = if (cloudFollow) cloudCounts.getOrNull(1) ?: "—" else localCount?.toString() ?: "—"
        fansTitle.text = "粉丝"
        fansCount.text = cloudCounts.getOrNull(2) ?: "—"
        executePendingBindings()
    }
}
