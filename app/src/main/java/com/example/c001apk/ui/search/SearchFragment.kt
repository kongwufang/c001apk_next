package com.example.c001apk.ui.search

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.c001apk.R
import com.example.c001apk.databinding.FragmentSearchBinding
import com.example.c001apk.logic.model.SearchHotResponse
import com.example.c001apk.ui.base.BaseFragment
import com.example.c001apk.ui.blacklist.IOnItemClickListener
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class SearchFragment : BaseFragment<FragmentSearchBinding>(), IOnItemClickListener {

    @Inject
    lateinit var viewModelAssistedFactory: SearchFragmentViewModel.Factory
    private val viewModel by viewModels<SearchFragmentViewModel> {
        SearchFragmentViewModel.provideFactory(
            viewModelAssistedFactory,
            arguments?.getString("pageType").orEmpty(),
            arguments?.getString("pageParam").orEmpty(),
            arguments?.getString("title").orEmpty(),
        )
    }

    private var mAdapter: HistoryAdapter? = null
    private var mLayoutManager: FlexboxLayoutManager? = null

    private lateinit var hotAdapter: HotSearchAdapter
    private lateinit var hotRankColumnAdapter: HotRankColumnAdapter
    private lateinit var suggestAdapter: SearchSuggestAdapter

    /** 由 TabLayout 主动切换榜单时置位，避免与横向滑动的回调用互相打架 */
    private var isTabSelectedByUser = false

    companion object {
        @JvmStatic
        fun newInstance(pageType: String?, pageParam: String?, title: String?) =
            SearchFragment().apply {
                arguments = Bundle().apply {
                    putString("pageType", pageType)
                    putString("pageParam", pageParam)
                    putString("title", title)
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        initHotRank()
        initSuggest()
        initEditText()
        initButton()
        initClearHistory()

        viewModel.blackListLiveData.observe(viewLifecycleOwner) { list ->
            mAdapter?.submitList(list)
            // 没有历史时整块隐藏，别留一条空白标题
            binding.historyHeader.isVisible = list.isNotEmpty()
        }

        viewModel.hotSearch.observe(viewLifecycleOwner) { bindHotSearch(it) }
        viewModel.suggest.observe(viewLifecycleOwner) { suggestAdapter.submitList(it) }

        // 旋转屏幕后 ViewModel 里已有数据，不必再请求一次
        if (viewModel.hotSearch.value == null)
            viewModel.fetchHotSearch()
    }

    // ---------------- 搜索历史 ----------------

    private fun initView() {
        mLayoutManager = FlexboxLayoutManager(requireContext(), FlexDirection.ROW, FlexWrap.WRAP)
        mAdapter = HistoryAdapter()
        mAdapter?.setOnItemClickListener(this)
        binding.historyRecyclerView.apply {
            adapter = mAdapter
            layoutManager = mLayoutManager
        }

        hotAdapter = HotSearchAdapter { onSearchWord(it.title) }
        binding.hotRecyclerView.apply {
            adapter = hotAdapter
            layoutManager = FlexboxLayoutManager(requireContext(), FlexDirection.ROW, FlexWrap.WRAP)
        }
    }

    private fun initClearHistory() {
        binding.clearAll.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(R.string.clearAllTitle)
                setNegativeButton(android.R.string.cancel, null)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.deleteAll()
                }
                show()
            }
        }
    }

    // ---------------- 热搜榜（tab + 横向榜单）----------------

    private fun initHotRank() {
        hotRankColumnAdapter = HotRankColumnAdapter { onSearchWord(it.title) }
        binding.hotRankRecyclerView.apply {
            adapter = hotRankColumnAdapter
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
            addOnScrollListener(hotRankScrollListener)
        }
        binding.hotTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateHotTabColors(tab.position)
                if (!isTabSelectedByUser)
                    binding.hotRankRecyclerView.smoothScrollToPosition(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.refreshHot.setOnClickListener {
            viewModel.fetchHotSearch(1)
        }
    }

    /** 横向滑动时反查当前落在哪一列，同步选中对应的 tab */
    private val hotRankScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dx == 0) return
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val first = layoutManager.findFirstVisibleItemPosition()
            if (first == RecyclerView.NO_POSITION) return
            val child = layoutManager.findViewByPosition(first) ?: return
            val width = child.width
            if (width <= 0) return
            // 左半屏被划走超过 50% 时，认为下一列才是当前列
            val hiddenRatio = -child.left.toFloat() / width
            selectHotTab(if (hiddenRatio > 0.5f) first + 1 else first)
        }
    }

    private fun selectHotTab(position: Int) {
        val tab = binding.hotTabLayout.getTabAt(position) ?: return
        if (tab.isSelected) return
        isTabSelectedByUser = true
        tab.select()
        isTabSelectedByUser = false
    }

    /**
     * 返回值为「热门搜索 + 热搜榜」两张卡片：
     *  - hotSearch          → 扁平的热门搜索词条
     *  - searchHotListCard  → 榜单 tab，每种 tab 里再嵌 10 条榜单项
     */
    private fun bindHotSearch(response: SearchHotResponse?) {
        val cards = response?.data.orEmpty()

        val hotItems = cards.firstOrNull { it.entityTemplate == "hotSearch" }?.entities
        hotAdapter.submitList(hotItems)
        binding.hotSearchGroup.isVisible = !hotItems.isNullOrEmpty()

        val rankList = cards.firstOrNull { it.entityTemplate == "searchHotListCard" }?.entities
        hotRankColumnAdapter.submitList(rankList)
        binding.hotRankGroup.isVisible = !rankList.isNullOrEmpty()
        buildHotTabs(rankList.orEmpty())
    }

    private fun buildHotTabs(list: List<SearchHotResponse.Item>) {
        binding.hotTabLayout.removeAllTabs()
        list.forEachIndexed { index, item ->
            val tab = binding.hotTabLayout.newTab()
            val titleView = layoutInflater
                .inflate(R.layout.item_search_hot_tab, binding.hotTabLayout, false) as TextView
            titleView.text = item.title
            tab.customView = titleView
            binding.hotTabLayout.addTab(tab, index == 0)
        }
        updateHotTabColors(0)
    }

    /** 自定义 tab 视图不吃 TabLayout 的 tabTextColors，只能自己上色 */
    private fun updateHotTabColors(selected: Int) {
        val selectedColor = MaterialColors.getColor(
            binding.hotTabLayout,
            com.google.android.material.R.attr.colorPrimary,
            0
        )
        val normalColor = MaterialColors.getColor(
            binding.hotTabLayout,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            0
        )
        for (i in 0 until binding.hotTabLayout.tabCount) {
            val titleView = binding.hotTabLayout.getTabAt(i)?.customView as? TextView ?: continue
            titleView.setTextColor(if (i == selected) selectedColor else normalColor)
        }
    }

    // ---------------- 搜索联想 ----------------

    private fun initSuggest() {
        suggestAdapter = SearchSuggestAdapter(
            onItemClick = { word, _ ->
                binding.editText.setText(word)
                binding.editText.setSelection(word.length)
                startSearch(word)
            },
            onFillClick = { word ->
                binding.editText.setText(word)
                binding.editText.setSelection(word.length)
            }
        )
        binding.suggestRecyclerView.apply {
            adapter = suggestAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    /** 有输入 → 展示联想；无输入 → 回到「历史 + 热门 + 热搜榜」 */
    private fun updatePanelState() {
        val typing = binding.editText.text?.isNotBlank() == true
        binding.searchHome.isVisible = !typing
        binding.suggestRecyclerView.isVisible = typing
    }

    // ---------------- 输入框与搜索动作 ----------------

    private fun initEditText() {
        binding.editText.apply {
            highlightColor = ColorUtils.setAlphaComponent(
                MaterialColors.getColor(
                    requireContext(),
                    com.google.android.material.R.attr.colorPrimaryDark,
                    0
                ), 128
            )
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(binding.editText, 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = EditorInfo.TYPE_CLASS_TEXT
            hint = if (viewModel.pageType.isNotEmpty()) "在 ${viewModel.title} 中搜索"
            else "搜索"
            setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, keyEvent ->
                if ((actionId == EditorInfo.IME_ACTION_UNSPECIFIED || actionId == EditorInfo.IME_ACTION_SEARCH) && keyEvent != null) {
                    startSearch(text.toString())
                    return@OnEditorActionListener false
                }
                false
            })
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) updatePanelState()
            }

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, i: Int, i2: Int, i3: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    binding.clear.isVisible = s.isNotBlank()
                    updatePanelState()
                    if (s.isBlank()) viewModel.clearSuggest()
                    else viewModel.fetchSuggest(s.toString())
                }

                override fun afterTextChanged(s: Editable) {}
            })

        }
    }

    private fun initButton() {
        binding.toolBar.apply {
            setNavigationOnClickListener {
                activity?.finish()
            }
        }
        binding.search.setOnClickListener {
            startSearch(binding.editText.text.toString())
        }
        binding.clear.setOnClickListener {
            binding.editText.text = null
        }
    }

    private fun startSearch(word: String) {
        val keyWord = word.trim()
        if (keyWord.isEmpty()) {
            Toast.makeText(requireContext(), "请输入关键词", Toast.LENGTH_SHORT).show()
            return
        }
        requireActivity().supportFragmentManager
            .beginTransaction()
            .setCustomAnimations(
                R.anim.right_in,
                R.anim.left_out_fragment,
                R.anim.left_in,
                R.anim.right_out
            )
            .replace(
                R.id.fragmentContainer,
                SearchResultFragment.newInstance(
                    keyWord,
                    viewModel.pageType,
                    viewModel.pageParam,
                    viewModel.title
                )
            )
            .addToBackStack(null)
            .commit()
        updateHistory(keyWord)
        hideKeyBoard()
        // 结果页返回时直接看到默认态，而不是停留在联想列表
        binding.searchHome.isVisible = true
        binding.suggestRecyclerView.isVisible = false
    }

    private fun onSearchWord(word: String?) {
        if (word.isNullOrBlank()) return
        binding.editText.setText(word)
        binding.editText.setSelection(word.length)
        startSearch(word)
    }

    private fun hideKeyBoard() {
        binding.editText.clearFocus()
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.editText.windowToken, 0)
    }

    override fun onItemClick(data: String) {
        binding.editText.setText(data)
        binding.editText.setSelection(data.length)
        startSearch(data)
    }

    private fun updateHistory(data: String) {
        viewModel.insertData(data)
    }

    override fun onItemDeleteClick(data: String) {
        viewModel.deleteData(data)
    }

    override fun onDestroyView() {
        binding.hotRankRecyclerView.removeOnScrollListener(hotRankScrollListener)
        binding.hotTabLayout.clearOnTabSelectedListeners()
        mLayoutManager = null
        mAdapter = null
        super.onDestroyView()
    }

}
