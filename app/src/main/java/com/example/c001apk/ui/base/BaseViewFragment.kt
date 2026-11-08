package com.example.c001apk.ui.base

import com.example.c001apk.util.AppLog
import com.example.c001apk.util.LogModule
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.R
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.constant.Constants.LOADING_EMPTY
import com.example.c001apk.databinding.BaseRefreshRecyclerviewBinding
import com.example.c001apk.util.dp
import com.example.c001apk.view.LinearItemDecoration
import com.example.c001apk.view.StaggerItemDecoration
import com.google.android.material.color.MaterialColors

// SwipeRefreshLayout + RecyclerView
abstract class BaseViewFragment<VM : BaseViewModel> : Fragment() {

    private val TAG = "BaseViewFragment"

    var _binding: BaseRefreshRecyclerviewBinding? = null
    val binding get() = _binding!!
    abstract val viewModel: VM
    lateinit var mAdapter: ConcatAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var sLayoutManager: StaggeredGridLayoutManager
    val isPortrait by lazy { resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
    var lastVisibleItemPosition = 0

    /** 上一次「点击标签回到顶部」的时间戳，用于识别双击 */
    private var lastReturnTopTapTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = BaseRefreshRecyclerviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (!viewModel.isInit) {
            initView()
            initRefresh()
            initScroll()
            initObserve()
            initError()
        }
    }

    private fun initData() {
        viewModel.loadingState.value = LoadingState.Loading
    }

    open fun initView() {
        initAdapter()
        binding.recyclerView.apply {
            adapter = mAdapter
            layoutManager =
                if (isPortrait) {
                    mLayoutManager = LinearLayoutManager(requireContext())
                    mLayoutManager
                } else {
                    sLayoutManager =
                        StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
                    sLayoutManager
                }
            if (isPortrait)
                addItemDecoration(LinearItemDecoration(10.dp))
            else
                addItemDecoration(StaggerItemDecoration(10.dp))
        }
    }

    abstract fun initAdapter()

    open fun initRefresh() {
        binding.swipeRefresh.apply {
            isEnabled = false
            setColorSchemeColors(
                MaterialColors.getColor(
                    requireContext(),
                    androidx.appcompat.R.attr.colorPrimary,
                    0
                )
            )
            setOnRefreshListener {
                if (!viewModel.isLoadMore) {
                    binding.swipeRefresh.isRefreshing = true
                    refreshData()
                }
            }
        }
    }

    fun refreshData() {
        lastVisibleItemPosition = 0
        viewModel.lastItem = null
        viewModel.page = 1
        viewModel.isEnd = false
        viewModel.isRefreshing = true
        viewModel.isLoadMore = false
        fetchData()
    }

    /**
     * 点击标签回到顶部：
     * - 双击 → 无视当前位置，回到顶部并强制刷新（等价手动下拉）；
     * - 单击且列表不在顶部 → 只平滑滚回顶部，**不**重新请求数据；
     * - 单击且已经在顶部 → 走一次刷新，等价手动下拉。
     *
     * 加载中（`swipeRefresh` 尚未启用）时不响应，避免和进行中的请求打架。
     */
    fun returnTopOrRefresh() {
        val recyclerView = binding.recyclerView
        if (!binding.swipeRefresh.isEnabled) return
        val now = SystemClock.elapsedRealtime()
        val isDoubleTap = now - lastReturnTopTapTime <= ViewConfiguration.getDoubleTapTimeout()
        lastReturnTopTapTime = now
        recyclerView.stopScroll()
        val atTop = isAtTop()
        AppLog.d(LogModule.UI, "BaseViewFragment", "returnTopOrRefresh ${javaClass.simpleName} type=${arguments?.getString("type")}" +
                    " atTop=$atTop doubleTap=$isDoubleTap first=${firstVisibleItemPosition()}" +
                    " canUp=${recyclerView.canScrollVertically(-1)}" +
                    " offset=${recyclerView.computeVerticalScrollOffset()}"
        )
        if (isDoubleTap) {
            recyclerView.smoothScrollToPosition(0)
            startRefresh()
            return
        }
        if (!atTop) {
            recyclerView.smoothScrollToPosition(0)
            return
        }
        startRefresh()
    }

    /** 走一次刷新，等价手动下拉；`swipeRefresh` 的转圈由 LoadingState 统一收尾 */
    private fun startRefresh() {
        binding.swipeRefresh.isRefreshing = true
        refreshData()
    }

    private fun firstVisibleItemPosition(): Int =
        when (val layoutManager = binding.recyclerView.layoutManager) {
            is LinearLayoutManager -> layoutManager.findFirstVisibleItemPosition()
            is StaggeredGridLayoutManager -> layoutManager
                .findFirstVisibleItemPositions(null)
                .filter { it != RecyclerView.NO_POSITION }
                .minOrNull() ?: RecyclerView.NO_POSITION

            else -> RecyclerView.NO_POSITION
        }

    /**
     * 列表是否已经贴着顶部。
     *
     * 不用 `canScrollVertically(-1)`：它依赖 `computeVerticalScroll*` 的估算值，
     * 实测在本布局里会误报为“已在顶部”，从而把“滚回顶部”错走成“刷新”。
     * 这里直接用 LayoutManager 的首个可见位置 + 该 child 的实际偏移判断：
     * - 首个可见位置 > 0 → 顶部肯定不可见；
     * - 首个可见位置 == 0 且其 top 被滚出了 padding → 顶部只露出了一部分，仍算“不在顶部”。
     */
    private fun isAtTop(): Boolean {
        val recyclerView = binding.recyclerView
        val first = firstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return true
        if (first > 0) return false
        val firstChild =
            recyclerView.layoutManager?.findViewByPosition(first) ?: return true
        return firstChild.top >= recyclerView.paddingTop
    }

    open fun fetchData() {
        viewModel.fetchData()
    }

    open fun initObserve() {
        viewModel.loadingState.observe(viewLifecycleOwner) {
            when (it) {
                LoadingState.Loading -> {
                    if (!viewModel.isLoadMore)
                        refreshData()
                }

                LoadingState.LoadingDone -> {}

                is LoadingState.LoadingError -> {
                    binding.errorMessage.errMsg.text = it.errMsg
                }

                is LoadingState.LoadingFailed -> {
                    binding.errorLayout.apply {
                        msg.text = it.msg
                        retry.text = if (it.msg == LOADING_EMPTY) getString(R.string.refresh)
                        else getString(R.string.retry)
                    }
                }
            }
            binding.indicator.parent.isIndeterminate = it is LoadingState.Loading
            binding.indicator.parent.isVisible = it is LoadingState.Loading
            binding.swipeRefresh.isEnabled = it is LoadingState.LoadingDone
            binding.errorMessage.errMsg.isVisible = it is LoadingState.LoadingError
            binding.errorLayout.parent.isVisible = it is LoadingState.LoadingFailed
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun initError() {
        binding.errorLayout.retry.setOnClickListener {
            viewModel.loadingState.value = LoadingState.Loading
        }
    }

    fun loadMore() {
        viewModel.isLoadMore = true
        viewModel.fetchData()
    }

    private fun initScroll() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    lastVisibleItemPosition = if (isPortrait)
                        mLayoutManager.findLastVisibleItemPosition()
                    else
                        sLayoutManager.findLastVisibleItemPositions(null).max()

                    if (lastVisibleItemPosition + 1 == binding.recyclerView.adapter?.itemCount
                        && !viewModel.isEnd && !viewModel.isRefreshing && !viewModel.isLoadMore
                        && !binding.swipeRefresh.isRefreshing
                    ) {
                        loadMore()
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                onScrolled(dy)
            }
        })
    }

    open fun onScrolled(dy: Int) {}

    override fun onResume() {
        super.onResume()
        if (viewModel.isInit) {
            viewModel.isInit = false
            initView()
            initData()
            initRefresh()
            initScroll()
            initObserve()
            initError()
        }
        initLift()
    }

    override fun onStart() {
        super.onStart()
        initLift()
    }

    override fun onPause() {
        super.onPause()
        detachLift()
    }

    override fun onStop() {
        super.onStop()
        detachLift()
    }

    private fun detachLift() {
        binding.recyclerView.borderViewDelegate.borderVisibilityChangedListener = null
    }

    private fun initLift() {
        val parent = parentFragment as? BasePagerFragment
        parent?.let {
            it.binding.appBar.setLifted(
                !binding.recyclerView.borderViewDelegate.isShowingTopBorder
            )
            binding.recyclerView.borderViewDelegate
                .setBorderVisibilityChangedListener { top, _, _, _ ->
                    it.binding.appBar.setLifted(!top)
                }
        }
    }

}
