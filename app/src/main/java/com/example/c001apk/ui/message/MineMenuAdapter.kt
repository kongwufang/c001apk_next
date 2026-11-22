package com.example.c001apk.ui.message

import com.example.c001apk.ui.blacklist.BlackListActivity
import com.example.c001apk.ui.collection.LocalCollectionActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemMineMenuBinding
import com.example.c001apk.ui.collection.CollectionActivity
import com.example.c001apk.ui.follow.FFFListActivity
import com.example.c001apk.ui.history.HistoryActivity
import com.example.c001apk.ui.others.WebViewActivity
import com.example.c001apk.ui.offline.OfflineCacheActivity
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager

/**
 * 侧栏功能九宫格。酷回收在应用内打开，不受应用登录状态限制。
 */
class MineMenuAdapter : RecyclerView.Adapter<MineMenuAdapter.MenuViewHolder>() {

    class MenuViewHolder(val binding: ItemMineMenuBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            binding.offlineLayout.setOnClickListener(this)
            binding.coolRecycleLayout.setOnClickListener(this)
            binding.equipmentLayout.setOnClickListener(this)
            binding.historyLayout.setOnClickListener(this)
            binding.comingSoonLayout.setOnClickListener(this)
            if (PrefManager.isLogin) {
                binding.favLayout.setOnClickListener(this)
                binding.likeLayout.setOnClickListener(this)
                binding.replyLayout.setOnClickListener(this)
                binding.freqLayout.setOnClickListener(this)
            }
        }

        override fun onClick(view: View?) {
            when (view?.id) {
                R.id.offlineLayout ->
                    IntentUtil.startActivity<OfflineCacheActivity>(itemView.context) {
                    }

                R.id.coolRecycleLayout ->
                    IntentUtil.startActivity<WebViewActivity>(itemView.context) {
                        putExtra("url", "https://www.coolapk.com/link/coolhuishou?drawNav=1")
                    }

                R.id.favLayout ->
                    // 2026-10-25：「收藏」存本地还是云端由设置决定 ✓
                    //   本地 ⇒ 本机收藏页（列本地收藏夹里的内容，点进去再拉）
                    //   云端 ⇒ 原来的 CollectionActivity
                    if (!PrefManager.saveCollectionToCloud) {
                        IntentUtil.startActivity<LocalCollectionActivity>(itemView.context) {}
                    } else if (PrefManager.isLogin) {
                        IntentUtil.startActivity<CollectionActivity>(itemView.context) {
                        }
                    }

                R.id.likeLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                            putExtra("type", "like")
                            putExtra("uid", PrefManager.uid)
                        }

                R.id.replyLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                            putExtra("type", "reply")
                            putExtra("uid", PrefManager.uid)
                        }

                R.id.freqLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                            putExtra("type", "recentHistory")
                            putExtra("uid", PrefManager.uid)
                        }

                R.id.historyLayout ->
                    IntentUtil.startActivity<HistoryActivity>(itemView.context) {
                        putExtra("type", "browse")
                    }

                // 第 9 格（2026-10-25 用户要求：从「敬请期待」换成**黑名单**）
                //   跳 BlackListActivity(type=user) ⇒ 它内部按
                //   PrefManager.saveBlackListToCloud 决定读云端还是读本机库 ✓
                R.id.comingSoonLayout ->
                    IntentUtil.startActivity<BlackListActivity>(itemView.context) {
                        putExtra("type", "user")
                    }

                // 我的装备：直接进入装备页；「修改/编辑装备」按钮放在该页右上角
                R.id.equipmentLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<WebViewActivity>(itemView.context) {
                            putExtra("url", "https://m.coolapk.com/myDevice/${PrefManager.uid}")
                            putExtra(
                                "editUrl",
                                "https://m.coolapk.com/mp/do?c=product&m=editProductOwner&from=home"
                            )
                        }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val binding = ItemMineMenuBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        val lp = binding.root.layoutParams
        if (lp is StaggeredGridLayoutManager.LayoutParams) {
            lp.isFullSpan = true
        }
        return MenuViewHolder(binding)
    }

    override fun getItemCount() = 1

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.binding.executePendingBindings()
    }

}
