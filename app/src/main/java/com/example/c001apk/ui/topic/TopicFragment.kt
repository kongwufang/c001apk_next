package com.example.c001apk.ui.topic

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemTopicHeaderBinding
import com.example.c001apk.ui.base.BasePagerFragment
import com.example.c001apk.ui.feed.reply.ReplyActivity
import com.example.c001apk.ui.home.IOnTabClickListener
import com.example.c001apk.ui.others.WebViewFragment
import com.example.c001apk.ui.search.SearchActivity
import com.example.c001apk.util.ReportAllianceDialog
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.ReplaceViewHelper
import com.example.c001apk.view.AppBarLayoutStateChangeListener
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.GRAVITY_CENTER
import com.google.android.material.tabs.TabLayout.MODE_SCROLLABLE
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TopicFragment : BasePagerFragment() {

    private val viewModel by viewModels<TopicViewModel>(ownerProducer = { requireActivity() })
    override var tabController: IOnTabClickListener? = null
    private lateinit var subscribe: MenuItem
    private var menuBlock: MenuItem? = null
    private var headerBinding: ItemTopicHeaderBinding? = null

    /**
     * 话题详情页红带（2026-10-31 用户要求）
     *
     * ⚠️ 为什么不在 `item_topic_header.xml` 里加：
     *    那个布局的根是**横向** LinearLayout（logo | 信息 | 关注按钮）
     *    插一个子 View 进去 ⇒ 变成一列 ⇒ 整个头部塌掉 ✗（踩过，已回滚）
     *
     * ⚠️ 为什么不在代码里包一层竖向容器：
     *    `header.root.layoutParams` 必须是 `CollapsingToolbarLayout.LayoutParams` ✗
     *    包一层后父容器变了 ⇒ 直接崩 ✗
     *
     * ✅ 最终做法：叠在 `binding.view`（FrameLayout 占位）里、`layout_gravity=bottom`
     *    ⇒ 只是绘制在上面 ✓ 不参与 `header.root` 的测量 ✓ 绝对安全 ✓
     */
    private var topicBlockedBand: android.widget.TextView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSelected()
        if (PrefManager.isLogin)
            initFab()
        initObserve()
    }

    override fun initFab() {
        super.initFab()
        fab.setOnClickListener {
            // 机型页可以发动态，也可以发表点评（type=rating）
            if (viewModel.type == "product" && !viewModel.ratingItemInfo.isNullOrEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("发布")
                    .setItems(arrayOf("发布动态", "发表点评")) { _, which ->
                        startReply(if (which == 0) "createFeed" else "rating")
                    }
                    .show()
            } else
                startReply("createFeed")
        }
    }

    private fun startReply(type: String) {
        val intent = Intent(requireContext(), ReplyActivity::class.java)
        intent.putExtra("type", type)
        intent.putExtra(
            "targetType",
            if (viewModel.type == "topic") "tag" else "product_phone"
        )
        intent.putExtra("targetId", viewModel.id)
        if (viewModel.type == "topic")
            intent.putExtra("title", viewModel.title)
        if (type == "rating") {
            intent.putExtra("ratingTarget", viewModel.title)
            intent.putExtra("ratingItems", Gson().toJson(viewModel.ratingItemInfo))
        }
        val animationBundle = ActivityOptions.makeCustomAnimation(
            context,
            R.anim.anim_bottom_sheet_slide_up,
            R.anim.anim_bottom_sheet_slide_down
        ).toBundle()
        requireContext().startActivity(intent, animationBundle)
    }

    override fun onTabReselectedExtra() {
        if (fabBehavior.isScrolledDown)
            fabBehavior.slideUp(fab, true)
    }

    private fun initSelected() {
        viewModel.tabSelected?.let {
            binding.viewPager.setCurrentItem(it, false)
            viewModel.tabSelected = null
        }
    }

    private fun initObserve() {
        viewModel.blockState.observe(viewLifecycleOwner) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let {
                menuBlock?.title = if (it) "移除黑名单"
                else "加入黑名单"
            }
        }

        viewModel.followState.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                subscribe.title = if (it) "取消关注"
                else "关注"
                bindFollowBtn(it)
            }
        }

        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.headerState.observe(viewLifecycleOwner) { header ->
            header?.let { bindHeader(it) }
        }
    }

    /**
     * 头部卡片铺在折叠标题栏里（复用 base_tablayout_viewpager 的占位 View）：
     * 展开时可见，向上滚动时随标题栏收起、TabLayout 吸顶，
     * 收起过程中整块淡出（见 BasePagerFragment.applyHeaderFade），最后只剩折叠标题。
     */
    private fun initHeader() {
        val header = ItemTopicHeaderBinding.inflate(layoutInflater, null, false)
        ReplaceViewHelper(requireContext()).toReplaceView(binding.view, header.root)
        header.root.layoutParams = CollapsingToolbarLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PARALLAX
        }
        header.root.isVisible = false
        headerBinding = header

        // ★ 话题详情页红带：叠在占位 FrameLayout 的底部 ✓
        val host = binding.view as? android.widget.FrameLayout
        if (host != null) {
            val band = android.widget.TextView(requireContext()).apply {
                text = requireContext().getString(
                    com.example.c001apk.R.string.blocked_multi_report_topic
                )
                setTextColor(0xFFD32F2F.toInt())
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 20, 0, 20)
                setBackgroundColor(0xFFF2FFEBEE.toInt())
                visibility = View.GONE
            }
            host.addView(
                band,
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM
                )
            )
            topicBlockedBand = band
        }

        // 未登录不显示关注按钮（和工具栏菜单项保持一致）
        header.followBtn.isVisible = PrefManager.isLogin
        header.followBtn.setOnClickListener { onSubscribeClick() }
    }

    /**
     * 关注按钮的两态配色：未关注=主题色填充 / 已关注=弱化底色。
     * 用 MaterialColors 取主题属性，避免在 drawable 里写 ?attr（浅色深色都要能看）。
     */
    private fun bindFollowBtn(followed: Boolean) {
        val header = headerBinding ?: return
        val accent = MaterialColors.getColor(
            requireContext(), androidx.appcompat.R.attr.colorPrimary, 0
        )
        val onAccent = MaterialColors.getColor(
            requireContext(), com.google.android.material.R.attr.colorOnPrimary, 0
        )
        val muted = MaterialColors.getColor(
            requireContext(), com.google.android.material.R.attr.colorSurfaceVariant, 0
        )
        val onMuted = MaterialColors.getColor(
            requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0
        )
        header.followBtn.apply {
            text = if (followed) "已关注" else "关注"
            backgroundTintList =
                ColorStateList.valueOf(if (followed) muted else accent)
            setTextColor(if (followed) onMuted else onAccent)
        }
    }

    // 关注 / 取消关注的两种类型（话题 tag / 机型 product），菜单与头部按钮共用
    private fun onSubscribeClick() {
        when (viewModel.type) {
            "topic" -> {
                val followUrl =
                    if (viewModel.isFollow) "/v6/feed/unFollowTag"
                    else "/v6/feed/followTag"
                val tag = viewModel.url.replace("/t/", "")
                viewModel.onGetFollow(followUrl, tag, null)
            }

            "product" -> {
                if (viewModel.postFollowData.isNullOrEmpty())
                    viewModel.postFollowData = HashMap()
                viewModel.postFollowData?.let { map ->
                    map["id"] = viewModel.id
                    map["status"] = if (viewModel.isFollow) "0" else "1"
                }
                viewModel.onPostFollow()
            }

            else -> Toast.makeText(
                requireContext(),
                "type error: ${viewModel.type}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun bindHeader(header: TopicHeader) {
        val headerBinding = headerBinding ?: return
        headerBinding.root.isVisible = true
        // 头部数据是异步来的，等它到的时候用户可能已经滑下去了。
        // 补一次当前折叠进度，否则这里会把一个收起状态下本该不可见的头部点亮。
        applyHeaderFade(headerBinding.root, headerFadePercent)

        ImageUtil.showIMG(headerBinding.logo, header.logo)
        headerBinding.title.text = header.title.orEmpty()

        // 服务端只下发数字（如「1.2万」），单位在本地点上
        val stats = listOfNotNull(
            header.hotNum?.takeIf { it.isNotEmpty() },
            header.commentNum?.takeIf { it.isNotEmpty() }?.let { "${it}讨论" },
        )
        headerBinding.stats.text = stats.joinToString(" · ")
        headerBinding.stats.isVisible = stats.isNotEmpty()

        // ★ 2026-10-31 话题详情页红带（只控制这一个 View ✓ 不碰头部其它东西 ✓）
        val tid = viewModel.id
        val band = topicBlockedBand
        if (band != null && !tid.isNullOrBlank() &&
            com.example.c001apk.util.Blocklist.enabled()
        ) {
            val show = { blocked: Boolean ->
                band.visibility = if (blocked) View.VISIBLE else View.GONE
                if (blocked) {
                    band.text = com.example.c001apk.util.Blocklist.cachedMsg(
                        com.example.c001apk.util.Blocklist.Kind.TOPIC, tid
                    ) ?: requireContext().getString(
                        com.example.c001apk.R.string.blocked_multi_report_topic
                    )
                }
            }
            val known = com.example.c001apk.util.Blocklist.cached(
                com.example.c001apk.util.Blocklist.Kind.TOPIC, tid
            )
            if (known != null) {
                show(known)
            } else {
                com.example.c001apk.util.Blocklist.prefetch(
                    com.example.c001apk.util.Blocklist.Kind.TOPIC, listOf(tid)
                ) {
                    show(
                        com.example.c001apk.util.Blocklist.isBlocked(
                            com.example.c001apk.util.Blocklist.Kind.TOPIC, tid
                        )
                    )
                }
            }
        }

        // 简介放头部里（不放工具栏 subtitle：见 initBar 的注释）
        headerBinding.intro.text = header.intro.orEmpty()
        headerBinding.intro.isVisible = !header.intro.isNullOrEmpty()

        val avatars = header.avatars.take(3)
        listOf(headerBinding.avatar1, headerBinding.avatar2, headerBinding.avatar3)
            .forEachIndexed { index, imageView ->
                val url = avatars.getOrNull(index)
                imageView.isVisible = !url.isNullOrEmpty()
                url?.let { ImageUtil.showIMG(imageView, it) }
            }
        headerBinding.followText.text =
            header.followNum?.takeIf { it.isNotEmpty() }?.let { "${it}人关注" }.orEmpty()
        headerBinding.followText.isVisible = headerBinding.followText.text.isNotEmpty()
        headerBinding.followRow.isVisible = avatars.isNotEmpty() || headerBinding.followText.isVisible

        // 头部按钮初值（checkFollow 之后还会通过 followState 再刷一次）
        bindFollowBtn(viewModel.isFollow)
    }

    override fun onDestroyView() {
        headerBinding = null
        super.onDestroyView()
    }

    override fun getFragment(position: Int): Fragment {
        val bean = viewModel.topicList?.getOrNull(position)
        val url = bean?.url.orEmpty()
        // H5 类型的 tab（如「活动详情」tab 的 url = https://m.coolapk.com/activity/<name>）
        // 用 WebView 渲染，否则走 dataList 接口会返回空导致页面空白
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return WebViewFragment.newInstance(url)
        }
        // 「参数」tab 走原生 dataList（productConfigList / listCard），
        // 版本配置行来自 product/detail 的 configRows，随 Fragment 传入
        val isParamsTab = bean?.pageName == "main" || bean?.title == "参数"
        return TopicContentFragment.newInstance(
            url,
            bean?.title.orEmpty(),
            if (isParamsTab && viewModel.type == "product")
                ArrayList(viewModel.configRows.orEmpty())
            else null,
        )
    }

    override fun initTabList() {
        binding.tabLayout.apply {
            tabGravity = GRAVITY_CENTER
            tabMode = MODE_SCROLLABLE
        }
        tabList = viewModel.topicList?.map { it.title } ?: emptyList()
    }

    override fun onBackClick() {
        activity?.finish()
    }

    override fun initBar() {
        super.initBar()
        initHeader()

        // 标题交给 CollapsingToolbarLayout 画：展开时 expandedTitleTextColor 是透明的、
        // 根本不显示，收起后才出现；配合下滑时把头部整块淡掉，最后只剩这一个标题
        // —— 和用户主页一套做法。
        // 原先走的是 isTitleEnabled = false + toolBar.title，标题常驻，
        // 收起过程中会和头部里的机型名 / 话题名叠在一起。
        binding.collapsingToolbar.title = if (viewModel.type == "topic")
            viewModel.url.replace("/t/", "")
        else
            viewModel.title
        binding.collapsingToolbar.setCollapsedTitleTextColor(
            MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorOnSurface,
                0
            )
        )
        // 简介不放工具栏 subtitle：CollapsingToolbarLayout 会把工具栏的 subtitle 收进
        // 自己的折叠文字，并按「展开态」画在头部下沿（Material 1.14 的
        // updateTitleFromToolbarIfNeeded），于是那行字直接压在头部卡片上。
        // 现在简介画在头部卡片里（item_topic_header 的 intro），跟着头部一起淡出。

        // percent: 1 = 完全展开，0 = 完全收起
        binding.appBar.addOnOffsetChangedListener(object : AppBarLayoutStateChangeListener() {
            override fun onScroll(percent: Float) {
                // 顶栏图标不用换色：这块头部本身是浅色的，收起后底色也是 colorSurface
                applyHeaderFade(headerBinding?.root, percent)
            }
        })

        binding.toolBar.apply {
            inflateMenu(R.menu.topic_product_menu)

            menuBlock = menu.findItem(R.id.block)
            subscribe = menu.findItem(R.id.subscribe)
            subscribe.isVisible = PrefManager.isLogin

            viewModel.checkMenuState()

            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.search -> {
                        if (viewModel.type == "topic") {
                            IntentUtil.startActivity<SearchActivity>(requireContext()) {
                                putExtra("type", "topic")
                                putExtra("pageType", "tag")
                                putExtra("pageParam", viewModel.url.replace("/t/", ""))
                                putExtra("title", viewModel.url.replace("/t/", ""))
                            }
                        } else {
                            IntentUtil.startActivity<SearchActivity>(requireContext()) {
                                putExtra("type", "topic")
                                putExtra("pageType", "product_phone")
                                putExtra("pageParam", viewModel.id)
                                putExtra("title", viewModel.title)
                            }
                        }
                    }

                    // 匿名举报话题（2026-10-31 用户需求：放在右上角的更多菜单里 ✓）
                    // 「如果填写就上报话题ID」=> id 和 topic_id 都传话题 ID ✓
                    R.id.reportTopic -> {
                        val tid = viewModel.id
                        if (tid.isBlank()) {
                            return@setOnMenuItemClickListener true
                        }
                        ReportAllianceDialog.start(requireActivity()) { reasons ->
                            ReportAllianceDialog.submit(
                                requireActivity(), "topic", tid, tid, reasons
                            )
                        }
                    }

                    R.id.block -> {
                        val isBlocked = menuBlock?.title.toString() == "移除黑名单"
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            val title =
                                if (viewModel.type == "topic") viewModel.url
                                    .replace("/t/", "")
                                else viewModel.title
                            setTitle("确定将 $title ${menuBlock?.title}？")
                            setNegativeButton(android.R.string.cancel, null)
                            setPositiveButton(android.R.string.ok) { _, _ ->
                                viewModel.title.let { title ->
                                    menuBlock?.title = if (isBlocked) {
                                        viewModel.deleteTopic(title)
                                        "加入黑名单"
                                    } else {
                                        viewModel.saveTopic(title)
                                        "移除黑名单"
                                    }
                                }
                            }
                            show()
                        }
                    }

                    R.id.subscribe -> onSubscribeClick()

                }
                return@setOnMenuItemClickListener true
            }
        }
    }
}
