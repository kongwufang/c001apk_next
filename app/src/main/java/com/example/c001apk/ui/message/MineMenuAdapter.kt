package com.example.c001apk.ui.message

import android.widget.Toast
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
 * 「我的」页功能宫格（8 格）：离线缓存 / 我的关注 / 我的收藏 / 我的赞 /
 * 我的回复 / 我的常去 / 浏览历史 / 我的装备。
 *
 * 跳转参数沿用原来的 item_message_mine 卡片（FFFListActivity 的 type 取值等都没动）。
 * 第一格原来是「我的动态」，用户的动态列表在个人页头部点头像/动态就能进，作用重复，
 * 位置腾给了离线缓存（不需要登录，未登录也该能看自己存过的东西）。
 */
class MineMenuAdapter : RecyclerView.Adapter<MineMenuAdapter.MenuViewHolder>() {

    class MenuViewHolder(val binding: ItemMineMenuBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {

        init {
            // 离线缓存 / 装备 / 浏览历史不需要登录；其余入口登录后才可点
            binding.offlineLayout.setOnClickListener(this)
            binding.equipmentLayout.setOnClickListener(this)
            binding.historyLayout.setOnClickListener(this)
            // 第 9 格占位：不需要登录也点得动（只是弹个 Toast ✓）
            binding.comingSoonLayout.setOnClickListener(this)
            if (PrefManager.isLogin) {
                binding.followLayout.setOnClickListener(this)
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

                R.id.followLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                            putExtra("type", "follow")
                            putExtra("uid", PrefManager.uid)
                        }

                R.id.favLayout ->
                    if (PrefManager.isLogin)
                        IntentUtil.startActivity<CollectionActivity>(itemView.context) {
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

                // 第 9 格占位（2026-10-24 从 4x2 改成 3x3，多出来的一格）
                // 用户要求「先挂敬请期待」⇒ 点了给个友好提示，不做别的 ✓
                R.id.comingSoonLayout ->
                    Toast.makeText(
                        itemView.context,
                        itemView.context.getString(R.string.coming_soon),
                        Toast.LENGTH_SHORT
                    ).show()

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
