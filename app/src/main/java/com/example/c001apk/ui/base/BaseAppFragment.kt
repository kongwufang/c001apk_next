package com.example.c001apk.ui.base

import androidx.recyclerview.widget.ConcatAdapter
import com.example.c001apk.adapter.AppAdapter
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.adapter.HeaderAdapter
import com.example.c001apk.ui.dialog.ApkDownloadDialog
import com.example.c001apk.util.showPublishStatusDialog
import com.example.c001apk.ui.home.IOnTabClickContainer
import com.example.c001apk.ui.home.IOnTabClickListener

// SwipeRefreshLayout + RecyclerView
abstract class BaseAppFragment<VM : BaseAppViewModel> : BaseViewFragment<VM>(),
    IOnTabClickListener {

    private lateinit var appAdapter: AppAdapter
    private lateinit var footerAdapter: FooterAdapter

    override fun initObserve() {
        super.initObserve()

        viewModel.footerState.observe(viewLifecycleOwner) {
            footerAdapter.setLoadState(it)
            if (it !is FooterState.Loading) {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        viewModel.dataList.observe(viewLifecycleOwner) {
            viewModel.listSize = it.size
            appAdapter.submitList(it)
            if (binding.vfContainer.displayedChild != it.size)
                binding.vfContainer.displayedChild = it.size
        }

        viewModel.apkDownload.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let { info ->
                ApkDownloadDialog.newInstance(
                    url = info.url,
                    fileName = info.fileName,
                    title = info.title,
                    size = info.size,
                ).show(childFragmentManager, "apkDownload")
            }
        }

        viewModel.publishStatusEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let { id ->
                showPublishStatusDialog(requireContext(), viewModel.publishStatusOf(id)) {
                    viewModel.onPostPublishStatus(id, it)
                }
            }
        }

    }

    override fun initAdapter() {
        appAdapter = AppAdapter(viewModel.ItemClickListener()).apply {
            // 页面要求 feedCover 也按完整动态卡排时（个人主页「图文」tab），由 ViewModel 说明
            feedCoverAsFeed = viewModel.feedCoverAsFeed
        }
        footerAdapter = FooterAdapter(ReloadListener())
        mAdapter = ConcatAdapter(HeaderAdapter(), appAdapter, footerAdapter)
    }

    inner class ReloadListener : FooterAdapter.FooterListener {
        override fun onReLoad() {
            viewModel.isEnd = false
            loadMore()
        }
    }

    /**
     * 点标签回到顶部：不在顶部只滚动，已经在顶部才刷新。
     * `isRefresh == false` 是需要弹选择框的特殊标签（如首页「关注」），由子类自行处理。
     */
    override fun onReturnTop(isRefresh: Boolean?) {
        if (isRefresh == false) return
        returnTopOrRefresh()
    }

    override fun onResume() {
        super.onResume()
        (parentFragment as? IOnTabClickContainer)?.tabController = this
    }

    override fun onPause() {
        super.onPause()
        (parentFragment as? IOnTabClickContainer)?.tabController = null
    }

}