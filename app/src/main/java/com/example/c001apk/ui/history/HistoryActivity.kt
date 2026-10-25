package com.example.c001apk.ui.history

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.R
import com.example.c001apk.adapter.HeaderAdapter
import com.example.c001apk.databinding.ActivityHistoryBinding
import com.example.c001apk.logic.model.HitHistoryData
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.util.NetWorkUtil
import com.example.c001apk.util.dp
import com.example.c001apk.util.makeToast
import com.example.c001apk.view.LinearItemDecoration
import com.example.c001apk.view.StaggerItemDecoration
import com.google.android.material.color.MaterialColors
import dagger.hilt.android.AndroidEntryPoint

/**
 * 浏览历史（酷安云端）。
 *
 * 2026-09-19：数据源由本地 Room 换成 `GET /v6/user/hitHistoryList`，
 * 浏览记录随打开详情自动进入云端历史，与官方 App 互通。
 */
@AndroidEntryPoint
class HistoryActivity : BaseActivity<ActivityHistoryBinding>() {

    private val viewModel by viewModels<HistoryViewModel>()
    private lateinit var mAdapter: HistoryAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var sLayoutManager: StaggeredGridLayoutManager
    private val isPortrait by lazy {
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.toolBar.title = getString(R.string.history)

        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ⚠️ 只调 setDisplayHomeAsUpEnabled(true) 只是**画出**箭头 ✗
        //    点击要由 onOptionsItemSelected 处理 ✗
        //    而 BaseActivity 和本类都没处理 ⇒ 点了完全没反应 ✗
        //    （2026-10-25 用户反馈「历史记录里面那个左上角返回按钮无效」）
        // ⇒ 直接给 toolbar 挂导航点击，跟其它页面保持一致 ✓
        binding.toolBar.setNavigationOnClickListener { finish() }

        initView()

        viewModel.historyList.observe(this) { list ->
            mAdapter.submitList(list)
        }
        viewModel.initialLoading.observe(this) { show ->
            binding.indicator.parent.isIndeterminate = show
            binding.indicator.parent.isVisible = show
            if (!show) binding.swipeRefresh.isRefreshing = false
        }
        viewModel.toastText.observe(this) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let { makeToast(it) }
        }

        viewModel.refresh()
    }

    private fun initView() {
        mAdapter = HistoryAdapter { item -> onItemClick(item) }
        binding.swipeRefresh.apply {
            setColorSchemeColors(
                MaterialColors.getColor(
                    this@HistoryActivity,
                    androidx.appcompat.R.attr.colorPrimary,
                    0
                )
            )
            setOnRefreshListener { viewModel.refresh() }
        }
        binding.recyclerView.apply {
            adapter = ConcatAdapter(HeaderAdapter(), mAdapter)
            layoutManager =
                if (isPortrait) {
                    mLayoutManager = LinearLayoutManager(this@HistoryActivity)
                    mLayoutManager
                } else {
                    sLayoutManager =
                        StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                    sLayoutManager
                }
            if (itemDecorationCount == 0) {
                if (isPortrait)
                    addItemDecoration(LinearItemDecoration(10.dp))
                else
                    addItemDecoration(StaggerItemDecoration(10.dp))
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val last = if (isPortrait)
                            mLayoutManager.findLastVisibleItemPosition()
                        else
                            sLayoutManager.findLastVisibleItemPositions(null).max()
                        if (last + 1 == adapter?.itemCount)
                            viewModel.loadMore()
                    }
                }
            })
        }
    }

    private fun onItemClick(item: HitHistoryData) {
        val url = item.url.orEmpty()
        if (url.isNotEmpty())
            NetWorkUtil.openLink(this, url, item.title)
    }

}
