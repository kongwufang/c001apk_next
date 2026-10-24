package com.example.c001apk.ui.message

import com.example.c001apk.util.MessageCenterCacheStore
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.adapter.HeaderAdapter
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.databinding.ActivityMessageCenterBinding
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.ui.login.WebLoginActivity
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.dp
import com.example.c001apk.view.LinearItemDecoration
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

/**
 * 消息中心（独立二级页）：顶部是消息分类宫格，下面是通知列表。
 *
 * 原来这块混在「我的」里，跟个人资料、功能入口挤在一个 RecyclerView 上；
 * 独立成 Activity 后右滑返回能跟手预览（Fragment 返回栈拿不到预测性返回预览）。
 */
@AndroidEntryPoint
class MessageCenterActivity : BaseActivity<ActivityMessageCenterBinding>() {

    private val viewModel by viewModels<MessageCenterViewModel>()
    private val entryAdapter by lazy { MessageThirdAdapter() }
    private lateinit var mAdapter: MessageCenterAdapter
    private lateinit var footerAdapter: FooterAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private val isLogin by lazy { PrefManager.isLogin }
    private var lastVisibleItemPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initView()
        initLogin()
        initObserve()
        initRefresh()
        initScroll()

        if (isLogin) {
            showRefreshingIndicator()
            getData()
        }
    }

    private fun initView() {
        binding.toolBar.setNavigationOnClickListener { finish() }

        binding.entryList.apply {
            layoutManager = GridLayoutManager(this@MessageCenterActivity, 4)
            adapter = entryAdapter
            // 未读红点由 decoration 画在顶层 overdraw 层：宫格紧挨着，红点留在 item 里
            // 会被右边那格盖掉（详见 MessageBadgeDecoration）
            addItemDecoration(MessageBadgeDecoration(this@MessageCenterActivity) {
                entryAdapter.unreadCount(it)
            })
        }

        mAdapter = MessageCenterAdapter(ItemClickListener())
        // ★ 先把上次的缓存显示出来 ✓ 不用盯着空白等网络 ✓
        //   之后 messageData 回来时 submitList(新列表) ⇒ DiffUtil 增量插入 ✓
        runCatching { mAdapter.submitList(MessageCenterCacheStore.load()) }
        footerAdapter = FooterAdapter(ReloadListener())
        mLayoutManager = LinearLayoutManager(this)
        binding.recyclerView.apply {
            adapter = ConcatAdapter(HeaderAdapter(), mAdapter, footerAdapter)
            layoutManager = mLayoutManager
            addItemDecoration(LinearItemDecoration(10.dp))
        }
    }

    private fun initLogin() {
        binding.isLogin = isLogin
        if (isLogin) {
            viewModel.messCountList.value = true
        } else {
            binding.clickToLogin.setOnClickListener {
                IntentUtil.startActivity<WebLoginActivity>(this) {}
            }
        }
    }

    private fun initObserve() {
        viewModel.messCountList.observe(this) {
            entryAdapter.updateBadge()
        }

        viewModel.toastText.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.footerState.observe(this) {
            footerAdapter.setLoadState(it)
            if (it !is FooterState.Loading) {
                binding.swipeRefresh.isRefreshing = false
                // 加载结束（成功或失败）都收起转圈 ✓
                hideRefreshingIndicator()
            }
        }

        viewModel.messageData.observe(this) {
            // ListAdapter + DiffUtil ⇒ 只动变化的行 ✓ 不会重置整个列表 ✓
            //   ⇒ 有新消息时新的那几条会「挤」出来 ✓ 老的不动 ✓
            mAdapter.submitList(it)
            // 写缓存 ✓（空列表不覆盖旧缓存 ✓ 见 store 里的注释）
            runCatching { MessageCenterCacheStore.save(it) }
            hideRefreshingIndicator()
        }
    }

    /**
     * 顶部那个转圈（7 个宫格下面、消息列表上面）
     *
     * 用 visible / invisible 而不是 gone ⇒ 收起时高度不变 ⇒ 列表不会上下跳 ✓
     */
    private fun showRefreshingIndicator() {
        binding.refreshingIndicator.visibility = android.view.View.VISIBLE
    }

    private fun hideRefreshingIndicator() {
        binding.refreshingIndicator.visibility = android.view.View.INVISIBLE
    }

    private fun initRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeColors(
                MaterialColors.getColor(
                    this@MessageCenterActivity,
                    androidx.appcompat.R.attr.colorPrimary,
                    0
                )
            )
            setOnRefreshListener {
                if (isLogin) {
                    if (!viewModel.isLoadMore) {
                        binding.swipeRefresh.isRefreshing = true
                        showRefreshingIndicator()
                        getData()
                    }
                } else
                    binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun initScroll() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    lastVisibleItemPosition = mLayoutManager.findLastVisibleItemPosition()
                    if (lastVisibleItemPosition + 1 == binding.recyclerView.adapter?.itemCount
                        && !viewModel.isEnd && !viewModel.isRefreshing && !viewModel.isLoadMore
                        && !binding.swipeRefresh.isRefreshing && isLogin
                    ) {
                        loadMore()
                    }
                }
            }
        })
    }

    private fun loadMore() {
        viewModel.loadMore()
    }

    private fun getData() {
        viewModel.refresh()
    }

    override fun onStop() {
        super.onStop()
        // 离开本页才把「已展示」落进已读账本：页内宫格红点要保持显示（用户要求 —— 一进来
        // 就刷新掉，等于看不出哪一类有新消息），离页后红点和首页角标才按账本抵消
        if (isLogin) viewModel.commitSeen()
    }

    override fun onResume() {
        super.onResume()
        // 从分类页（@我 / 评论 / 赞 / 关注）回来时，那边已经把看过的条目记成已读，
        // 这里重算一遍：宫格红点按本机账本抵消，下面的未读列表同步
        if (isLogin) {
            viewModel.messCountList.value = true
            viewModel.refresh()
        }
    }

    inner class ReloadListener : FooterAdapter.FooterListener {
        override fun onReLoad() {
            viewModel.isEnd = false
            loadMore()
        }
    }

    inner class ItemClickListener : ItemListener {
        override fun onViewFeed(
            view: View,
            id: String?,
            uid: String?,
            username: String?,
            userAvatar: String?,
            deviceTitle: String?,
            message: String?,
            dateline: String?,
            rid: Any?,
            isViewReply: Any?,
            feedData: Any?
        ) {
            super.onViewFeed(
                view,
                id,
                uid,
                username,
                userAvatar,
                deviceTitle,
                message,
                dateline,
                rid,
                isViewReply,
                feedData
            )
            if (!uid.isNullOrEmpty() && PrefManager.isRecordHistory)
                viewModel.saveHistory(
                    id.toString(),
                    uid.toString(),
                    username.toString(),
                    userAvatar.toString(),
                    deviceTitle.toString(),
                    message.toString(),
                    dateline.toString()
                )
        }

        override fun onBlockUser(id: String, uid: String, position: Int) {
            viewModel.saveUid(uid)
            val currentList = viewModel.messageData.value?.toMutableList() ?: ArrayList()
            currentList.removeAt(position)
            viewModel.messageData.postValue(currentList)
        }

        override fun onMessLongClicked(uname: String, id: String, position: Int): Boolean {
            MaterialAlertDialogBuilder(this@MessageCenterActivity).apply {
                setTitle("删除来自 $uname 的通知？")
                setNegativeButton(android.R.string.cancel, null)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.onPostDelete(position, id)
                }
                show()
            }
            return true
        }
    }

}
