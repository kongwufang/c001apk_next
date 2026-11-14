package com.example.c001apk.ui.search

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.adapter.ProductSortHeaderAdapter
import com.example.c001apk.ui.base.BaseAppFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SearchContentFragment : BaseAppFragment<SearchContentViewModel>(),
    IOnSearchMenuClickListener {

    @Inject
    lateinit var viewModelAssistedFactory: SearchContentViewModel.Factory
    override val viewModel by viewModels<SearchContentViewModel> {
        SearchContentViewModel.provideFactory(
            viewModelAssistedFactory,
            arguments?.getString("keyWord").orEmpty(),
            arguments?.getString("type").orEmpty(),
            arguments?.getString("pageType").orEmpty(),
            arguments?.getString("pageParam").orEmpty(),
        )
    }

    companion object {
        @JvmStatic
        fun newInstance(keyWord: String, type: String, pageType: String?, pageParam: String?) =
            SearchContentFragment().apply {
                arguments = Bundle().apply {
                    putString("keyWord", keyWord)
                    putString("type", type)
                    putString("pageType", pageType)
                    putString("pageParam", pageParam)
                }
            }
    }

    /** 「动态」tab 顶部：官方那 5 个排序 chip（综合/实时/热度/评论/精确） */
    private var sortHeader: ProductSortHeaderAdapter? = null

    override fun topAdapter(): RecyclerView.Adapter<*>? {
        if (viewModel.type != "feed") return null
        val a = ProductSortHeaderAdapter(
            viewModel.sort,
            ProductSortHeaderAdapter.SEARCH_LABELS
        ) { value ->
            // 点 chip 等于换排序：先改 sort/isStrict，再走和菜单完全同一条流程
            // （直接改 LiveData 不会触发重新请求，必须调 onSearch ✓）
            viewModel.isStrict = if (value == "none") 1 else 0
            onSearch("sort", value, null)
        }
        sortHeader = a
        return a
    }

    override fun onSearch(type: String, value: String, id: String?) {
        when (type) {
            "sort" -> {
                viewModel.sort = value
                // 「精确」= sort:none + isStrict:1（HAR 实证）
                viewModel.isStrict = if (value == "none") 1 else 0
            }
            "feedType" -> viewModel.feedType = value
        }
        viewModel.dataList.value = emptyList()
        viewModel.footerState.value = FooterState.LoadingDone
        viewModel.loadingState.value = LoadingState.Loading
        // 菜单里也能换排序 ⇒ 让顶部 chip 跟着变（不触发回调）
        sortHeader?.select(viewModel.sort)
    }

    override fun onResume() {
        super.onResume()
        (parentFragment as? IOnSearchMenuClickContainer)?.controller = this
    }

    override fun onPause() {
        super.onPause()
        (parentFragment as? IOnSearchMenuClickContainer)?.controller = null
    }

    override fun initObserve() {
        super.initObserve()

        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

}