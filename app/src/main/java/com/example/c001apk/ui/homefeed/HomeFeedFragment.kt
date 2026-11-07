package com.example.c001apk.ui.homefeed

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import com.example.c001apk.R
import com.example.c001apk.adapter.FooterState
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.adapter.PlaceHolderAdapter
import com.example.c001apk.databinding.BaseRefreshRecyclerviewBinding
import com.example.c001apk.ui.article.ArticlePublishActivity
import com.example.c001apk.ui.base.BaseAppFragment
import com.example.c001apk.ui.feed.reply.ReplyActivity
import com.example.c001apk.ui.home.IOnTabClickContainer
import com.example.c001apk.ui.home.IOnTabClickListener
import com.example.c001apk.ui.main.IOnBottomClickContainer
import com.example.c001apk.ui.main.IOnBottomClickListener
import com.example.c001apk.ui.main.MainActivity
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.TokenDeviceUtils.getLastingInstallTime
import com.example.c001apk.util.dp
import com.example.c001apk.util.setSpaceFooterView
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFeedFragment : BaseAppFragment<HomeFeedViewModel>(), IOnTabClickListener,
    IOnBottomClickListener {

    @Inject
    lateinit var viewModelAssistedFactory: HomeFeedViewModel.Factory
    override val viewModel by viewModels<HomeFeedViewModel> {
        HomeFeedViewModel.provideFactory(
            viewModelAssistedFactory,
            getLastingInstallTime(requireContext())
        )
    }
    private lateinit var fab: FloatingActionButton
    private val fabViewBehavior by lazy { HideBottomViewOnScrollBehavior<FloatingActionButton>() }
    private val placeHolderAdapter by lazy { PlaceHolderAdapter() }

    /** 「关注」tab 顶部的「最近更新 + 分组 chips」（其它 tab 为 null ✓） */
    private var followHeader: FollowHeaderAdapter? = null

    companion object {
        @JvmStatic
        fun newInstance(type: String) =
            HomeFeedFragment().apply {
                arguments = Bundle().apply {
                    putString("type", type)
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BaseRefreshRecyclerviewBinding.inflate(inflater, container, false)
        if (viewModel.type == "feed" && PrefManager.isLogin) {
            fab = FloatingActionButton(requireContext())
            initPublish()
            _binding?.root?.addView(fab)
        }
        return binding.root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            viewModel.type = it.getString("type")
            when (viewModel.type) {
                "rank" -> {
                    viewModel.dataListUrl = "/page?url=V9_HOME_TAB_RANKING"
                    viewModel.dataListTitle = "热榜"
                }

                "follow" -> {
                    if (viewModel.dataListUrl.isNullOrEmpty()) {
                        when (PrefManager.FOLLOWTYPE) {
                            "all" -> {
                                viewModel.dataListUrl = "/page?url=V9_HOME_TAB_FOLLOW"
                                viewModel.dataListTitle = "全部关注"
                            }

                            "circle" -> {
                                viewModel.dataListUrl = "/page?url=V9_HOME_TAB_FOLLOW&type=circle"
                                viewModel.dataListTitle = "好友关注"
                            }

                            "topic" -> {
                                viewModel.dataListUrl = "/page?url=V9_HOME_TAB_FOLLOW&type=topic"
                                viewModel.dataListTitle = "话题关注"
                            }

                            else -> {
                                viewModel.dataListUrl = "/page?url=V9_HOME_TAB_FOLLOW&type=product"
                                viewModel.dataListTitle = "数码关注"
                            }
                        }


                    }
                }

                "coolPic" -> {
                    viewModel.dataListUrl = "/page?url=V11_FIND_COOLPIC"
                    viewModel.dataListTitle = "酷图"
                }
            }
        }

    }

    /**
     * 「关注」tab 的头部（2026-11-07 ✓ 对照官方 App 补的）
     *
     * 官方关注页在动态列表前有两行：最近更新头像 + 分组 chips ✓
     * 这里用 [FollowHeaderAdapter] 插到 ConcatAdapter 最前面 ✓
     */
    override fun topAdapter(): androidx.recyclerview.widget.RecyclerView.Adapter<*>? {
        if (viewModel.type != "follow") return null
        return FollowHeaderAdapter(
            onPickGroup = { key -> switchFollowGroup(key) },
            onOpenRecent = { openRecentFollow() },
            onToggleUser = { uid -> setAuthorFilter(uid) }
        ).also { followHeader = it }
    }

    override fun initView() {
        super.initView()

        binding.vfContainer.setOnDisplayedChildChangedListener {
            binding.recyclerView.setSpaceFooterView(placeHolderAdapter)
        }
    }

    override fun initObserve() {
        super.initObserve()

        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        // ★ 关注 tab 的头部：从动态里反推「最近更新」的人 + 同步当前分组高亮
        //   （2026-11-07 ✓ 顺序 = 列表顺序 ⇒ 天然按时间新→旧 ✓）
        if (viewModel.type == "follow") {
            // 头像行用**独立接口**的数据（不再从已加载动态里反推 ✗）
            viewModel.recentUsers.observe(viewLifecycleOwner) { list ->
                val header = followHeader ?: return@observe
                header.setAuthors(
                    list.map {
                        FollowHeaderAdapter.Author(
                            uid = it.uid.orEmpty(),
                            name = it.username.orEmpty(),
                            avatar = it.userAvatar.orEmpty(),
                        )
                    }
                )
                header.setSelected(PrefManager.FOLLOWTYPE)
            }
            viewModel.fetchRecentUsers()
        }
    }

    @SuppressLint("InflateParams")
    private fun initPublish() {
        fab.apply {
            setImageResource(R.drawable.ic_add1)
            layoutParams = CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                CoordinatorLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                behavior = fabViewBehavior
            }
            if (SDK_INT >= 26)
                tooltipText = getString(R.string.publishFeed)
            setOnClickListener {
                // 首页发动态按钮：二选一菜单，给「图文」留入口
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.publish)
                    .setItems(arrayOf(
                        getString(R.string.publishFeed),
                        getString(R.string.publishArticle)
                    )) { _, which ->
                        startReply(if (which == 0) "createFeed" else "createArticle")
                    }
                    .show()
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(fab) { _, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                rightMargin = 25.dp
                bottomMargin = navigationBars.bottom + if (isPortrait) 105.dp else 25.dp
            }
            insets
        }
    }

    private fun startReply(type: String) {
        val options = ActivityOptionsCompat.makeCustomAnimation(
            requireContext(),
            R.anim.anim_bottom_sheet_slide_up,
            R.anim.anim_bottom_sheet_slide_down
        )
        // 「图文」走独立混排发布页，其余（动态/点评）仍走 ReplyActivity
        val intent = if (type == "createArticle") {
            Intent(requireContext(), ArticlePublishActivity::class.java)
        } else {
            Intent(requireContext(), ReplyActivity::class.java).putExtra("type", type)
        }
        requireContext().startActivity(intent, options.toBundle())
    }

    override fun onScrolled(dy: Int) {
        if (dy > 0) {
            (activity as? MainActivity)?.hideNavigationView()
        } else if (dy < 0) {
            (activity as? MainActivity)?.showNavigationView()
        }
    }

    override fun onPause() {
        super.onPause()
        (parentFragment as? IOnTabClickContainer)?.tabController = null
        (activity as? IOnBottomClickContainer)?.controller = null
    }

    override fun onResume() {
        super.onResume()
        (parentFragment as? IOnTabClickContainer)?.tabController = this
        (activity as? IOnBottomClickContainer)?.controller = this
    }

    override fun onReturnTop(isRefresh: Boolean?) {
        if (!binding.swipeRefresh.isEnabled) return
        if (isRefresh == false) {
            // 「关注」标签重新点击 → 选关注分组，不滚动也不刷新
            if (viewModel.type == "follow") showFollowGroupPicker()
            return
        }
        if (viewModel.type == "feed" && fabViewBehavior.isScrolledDown)
            fabViewBehavior.slideUp(fab, true)
        returnTopOrRefresh()
    }

    override fun onReturnTop() {
        onReturnTop(null)
    }

    /** 「关注」标签重新点击时的分组选择框 */
    private fun showFollowGroupPicker() {
        MaterialAlertDialogBuilder(requireContext()).apply {
            setTitle("关注分组")
            val items = arrayOf("全部关注", "好友关注", "话题关注", "数码关注", "应用关注")
            viewModel.position = when (PrefManager.FOLLOWTYPE) {
                "all" -> 0
                "circle" -> 1
                "topic" -> 2
                "product" -> 3
                "apk" -> 4
                else -> 0
            }
            setSingleChoiceItems(
                items,
                viewModel.position ?: 0
            ) { dialog: DialogInterface, position: Int ->
                when (position) {
                    0 -> {
                        viewModel.dataListUrl = "/page?url=V9_HOME_TAB_FOLLOW"
                        viewModel.dataListTitle = "全部关注"
                        PrefManager.FOLLOWTYPE = "all"
                    }

                    1 -> {
                        viewModel.dataListUrl = "/page?url=V9_HOME_TAB_FOLLOW&type=circle"
                        viewModel.dataListTitle = "好友关注"
                        PrefManager.FOLLOWTYPE = "circle"
                    }

                    2 -> {
                        viewModel.dataListUrl = "/page?url=V9_HOME_TAB_FOLLOW&type=topic"
                        viewModel.dataListTitle = "话题关注"
                        PrefManager.FOLLOWTYPE = "topic"
                    }

                    3 -> {
                        viewModel.dataListUrl = "/page?url=V9_HOME_TAB_FOLLOW&type=product"
                        viewModel.dataListTitle = "数码关注"
                        PrefManager.FOLLOWTYPE = "product"
                    }

                    4 -> {
                        viewModel.dataListUrl = "/page?url=V9_HOME_TAB_FOLLOW&type=apk"
                        viewModel.dataListTitle = "应用关注"
                        PrefManager.FOLLOWTYPE = "apk"
                    }
                }
                viewModel.dataList.value = emptyList()
                viewModel.footerState.value = FooterState.LoadingDone
                viewModel.loadingState.value = LoadingState.Loading
                dialog.dismiss()
            }
            show()
        }
    }

    /** 当前筛选的作者 uid（null = 显示全部 ✓） */
    private var filterUid: String? = null

    /**
     * 点头像 = 只看这个人的动态；再点一次 = 取消（2026-11-07 用户要求）
     *
     * ⚠️ 不改 viewModel.dataList 本身 ✗
     *   只是把它重新 assign 一个新 list 实例 ⇒ 触发 observer ⇒ [mapList] 过滤 ✓
     *   （这样上拉加载 / 分页 lastItem 完全不受影响 ✓）
     */
    private fun setAuthorFilter(uid: String) {
        filterUid = uid.ifEmpty { null }
        followHeader?.setSelectedUid(filterUid)
        val cur = viewModel.dataList.value ?: return
        viewModel.dataList.value = ArrayList(cur)
    }

    override fun mapList(
        list: List<com.example.c001apk.logic.model.HomeFeedResponse.Data>
    ): List<com.example.c001apk.logic.model.HomeFeedResponse.Data> {
        val uid = filterUid ?: return list
        if (viewModel.type != "follow") return list
        return list.filter { it.entityType == "feed" && it.uid == uid }
    }

    /** 进「有更新的酷友」页（官方就是这么点进去的 ✓ 2026-11-07） */
    private fun openRecentFollow() {
        startActivity(android.content.Intent(requireContext(), RecentFollowActivity::class.java))
    }

    /** 切换关注分组（chips 点击 ⇒ 换 url 重新拉 ✓ 逻辑与原来的弹窗一致） */
    private fun switchFollowGroup(key: String) {
        val url = when (key) {
            "all" -> "/page?url=V9_HOME_TAB_FOLLOW"
            "circle" -> "/page?url=V9_HOME_TAB_FOLLOW&type=circle"
            "topic" -> "/page?url=V9_HOME_TAB_FOLLOW&type=topic"
            "product" -> "/page?url=V9_HOME_TAB_FOLLOW&type=product"
            else -> "/page?url=V9_HOME_TAB_FOLLOW&type=apk"
        }
        val title = when (key) {
            "all" -> "全部关注"
            "circle" -> "好友关注"
            "topic" -> "话题关注"
            "product" -> "数码关注"
            else -> "应用关注"
        }
        if (viewModel.dataListUrl == url) return
        // 切换分组 ⇒ 清掉作者筛选（否则换了分组却还只看某个人 ✗ 会误以为没数据）
        if (filterUid != null) {
            filterUid = null
            followHeader?.setSelectedUid(null)
        }
        PrefManager.FOLLOWTYPE = key
        followHeader?.setSelected(key)
        // ★ 不清空列表、不切 loading ⇒ 头部两行不会闪一下消失 ✓
        viewModel.switchGroup(url, title)
    }

    override fun fetchData() {
        viewModel.fetchData()
        viewModel.changeFirstItem = true
    }

}