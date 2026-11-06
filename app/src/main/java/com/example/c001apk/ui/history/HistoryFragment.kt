package com.example.c001apk.ui.history

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.R
import com.example.c001apk.adapter.HeaderAdapter
import com.example.c001apk.databinding.FragmentHistoryBinding
import com.example.c001apk.logic.model.HitHistoryData
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
 * ★ 2026-11-07 从 [HistoryActivity] 抽成 Fragment（全量迁移第 3 批）。
 *
 * 这是「自包含页面」（自己一套布局 + RecyclerView，不是薄壳）的迁移样例：
 *   ① 布局只剥 MaterialToolbar 外壳（宿主的 toolbar 顶替 ✓）
 *   ② Activity 的 binding / observe(this) / makeToast(this) / NetWorkUtil.openLink(this)
 *      全部改成 Fragment 的 _binding / viewLifecycleOwner / requireContext() ✓
 *   ③ 返回键交给宿主（Fragment 返回栈 ✓ 不用自己 finish ✓）
 *
 * 老的 [HistoryActivity] 一行没动 ✓
 */
@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel by viewModels<HistoryViewModel>()
    private lateinit var mAdapter: HistoryAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var sLayoutManager: StaggeredGridLayoutManager
    private val isPortrait by lazy {
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title =
            getString(R.string.history)

        initView()

        viewModel.historyList.observe(viewLifecycleOwner) { list ->
            mAdapter.submitList(list)
        }
        viewModel.initialLoading.observe(viewLifecycleOwner) { show ->
            _binding?.let {
                it.indicator.parent.isIndeterminate = show
                it.indicator.parent.isVisible = show
                if (!show) it.swipeRefresh.isRefreshing = false
            }
        }
        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let { requireContext().makeToast(it) }
        }

        viewModel.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun initView() {
        val ctx = requireContext()
        mAdapter = HistoryAdapter { item -> onItemClick(item) }
        binding.swipeRefresh.apply {
            setColorSchemeColors(
                MaterialColors.getColor(ctx, androidx.appcompat.R.attr.colorPrimary, 0)
            )
            setOnRefreshListener { viewModel.refresh() }
        }
        binding.recyclerView.apply {
            adapter = ConcatAdapter(HeaderAdapter(), mAdapter)
            layoutManager = if (isPortrait) {
                mLayoutManager = LinearLayoutManager(ctx)
                mLayoutManager
            } else {
                sLayoutManager =
                    StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                sLayoutManager
            }
            if (itemDecorationCount == 0) {
                if (isPortrait) addItemDecoration(LinearItemDecoration(10.dp))
                else addItemDecoration(StaggerItemDecoration(10.dp))
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val last = if (isPortrait)
                            mLayoutManager.findLastVisibleItemPosition()
                        else
                            sLayoutManager.findLastVisibleItemPositions(null).max()
                        if (last + 1 == adapter?.itemCount) viewModel.loadMore()
                    }
                }
            })
        }
    }

    private fun onItemClick(item: HitHistoryData) {
        val url = item.url.orEmpty()
        if (url.isNotEmpty()) NetWorkUtil.openLink(requireContext(), url, item.title)
    }
}
