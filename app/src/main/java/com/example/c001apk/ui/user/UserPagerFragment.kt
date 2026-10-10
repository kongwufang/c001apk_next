package com.example.c001apk.ui.user

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.c001apk.R
import com.example.c001apk.databinding.BaseViewUserBinding
import com.example.c001apk.ui.base.BasePagerFragment
import com.example.c001apk.ui.messagedetail.MessageDetailActivity
import com.example.c001apk.ui.others.WebViewActivity
import com.example.c001apk.ui.search.SearchActivity
import com.example.c001apk.util.DateUtils
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.ReplaceViewHelper
import com.example.c001apk.view.AppBarLayoutStateChangeListener
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UserPagerFragment : BasePagerFragment() {

    private val viewModel by viewModels<UserViewModel>(ownerProducer = { requireActivity() })
    private lateinit var userBinding: BaseViewUserBinding
    private var menuBlock: MenuItem? = null

    /** 签名当前是不是展开态（折叠时只留 2 行） */
    private var bioExpanded = false

    /** 是不是在看自己的主页（uid 和本地登录 uid 相同） */
    private val isSelf: Boolean
        get() = PrefManager.isLogin && PrefManager.uid.isNotEmpty() &&
                (viewModel.userData?.uid ?: viewModel.uid) == PrefManager.uid

    // 官方的「主页」tab 是 homeTabCardRows 卡片体系，这里先只做列表类的 tab
    private val tabType = listOf("feed", "rating", "article", "question", "coolpic")
    private val tabTitle = listOf("动态", "点评", "图文", "问答", "酷图")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUser()
        initObserve()
    }

    override fun initTabList() {
        tabList = tabTitle
    }

    override fun getFragment(position: Int): Fragment =
        UserTabFragment.newInstance(viewModel.uid, tabType[position])

    override fun onBackClick() {
        activity?.finish()
    }

    private fun initUser() {
        userBinding = BaseViewUserBinding.inflate(layoutInflater, null, false)
        ReplaceViewHelper(requireContext()).toReplaceView(binding.view, userBinding.root)
        (userBinding.root.layoutParams as? CollapsingToolbarLayout.LayoutParams)
            ?.collapseMode = CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_PARALLAX

        userBinding.userData = viewModel.userData
        userBinding.listener = viewModel.ItemClickListener()
        userBinding.isSelf = isSelf

        // 自己的主页：按钮变成「编辑信息」；别人的主页：关注 / 已关注；未登录不显示
        userBinding.followBtn.isVisible = isSelf || PrefManager.isLogin
        userBinding.followBtn.setOnClickListener {
            if (isSelf) {
                IntentUtil.startActivity<EditProfileActivity>(requireContext()) {}
            } else {
                viewModel.onPostFollowUnFollow(
                    if (viewModel.userData?.isFollow == 1) "/v6/user/unfollow" else "/v6/user/follow"
                )
            }
        }

        bindMailBtn()
        userBinding.mailBtn.setOnClickListener {
            // 只有 uid 也要能进：没聊过的话，发出第一条消息时由服务端隐式建会话
            IntentUtil.startActivity<MessageDetailActivity>(requireContext()) {
                putExtra("uid", viewModel.userData?.uid ?: viewModel.uid)
                putExtra("uname", viewModel.userData?.username.orEmpty())
                putExtra("avatar", viewModel.userData?.userAvatar.orEmpty())
            }
        }

        bindBio()
        userBinding.bioExpand.setOnClickListener {
            bioExpanded = !bioExpanded
            userBinding.bio.maxLines = if (bioExpanded) Int.MAX_VALUE else BIO_COLLAPSED_LINES
            userBinding.bioExpand.text = if (bioExpanded) "收起" else "展开"
        }

        userBinding.equipLayout.setOnClickListener {
            IntentUtil.startActivity<WebViewActivity>(requireContext()) {
                putExtra("url", "https://m.coolapk.com/myDevice/${viewModel.uid}")
            }
        }
    }

    private fun initObserve() {
        viewModel.blockState.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                menuBlock?.title = getMenuTitle(
                    if (it) "移除黑名单"
                    else "加入黑名单"
                )
            }
        }

        viewModel.followState.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                userBinding.userData = viewModel.userData
                userBinding.isSelf = isSelf
                userBinding.executePendingBindings()
            }
        }

        // 自己的资料被编辑过（头像 / 签名 / 等级）时重新绑定头部
        viewModel.profileState.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                binding.collapsingToolbar.title = viewModel.userData?.username
                userBinding.userData = viewModel.userData
                userBinding.isSelf = isSelf
                userBinding.executePendingBindings()
                // userData 是拉完资料才有的，isSelf / 关注态此时才最终确定
                userBinding.followBtn.isVisible = isSelf || PrefManager.isLogin
                bindMailBtn()
                // 签名可能刚被编辑过，重新折叠一次
                bindBio()
            }
        }

        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 私信圆钮：自己的主页不显示，未登录也没法发私信 */
    private fun bindMailBtn() {
        userBinding.mailBtn.isVisible = !isSelf && PrefManager.isLogin
    }

    /**
     * 签名折叠回 2 行，并判断要不要露出「展开」。
     *
     * 按钮现在排在签名文段之后（见 base_view_user.xml），不再挤占签名的可用宽度，
     * 所以显隐直接用 GONE、判定不会因为按钮自己的显隐而翻来翻去。
     */
    private fun bindBio() {
        bioExpanded = false
        val bio = userBinding.bio
        bio.maxLines = BIO_COLLAPSED_LINES
        userBinding.bioExpand.text = "展开"
        userBinding.bioExpand.visibility = View.GONE
        refreshBioExpand()
        // 签名文字可能是刚绑上去的，此刻 layout 还是旧的，等这次布局完再判一次
        bio.doOnNextLayout { refreshBioExpand() }
    }

    /** 折叠态下签名确实被截断了才露出「展开」 */
    private fun refreshBioExpand() {
        if (bioExpanded) return
        val layout = userBinding.bio.layout ?: return
        val lastLine = layout.lineCount - 1
        val truncated = layout.lineCount > BIO_COLLAPSED_LINES ||
                (lastLine >= 0 && layout.getEllipsisCount(lastLine) > 0)
        userBinding.bioExpand.visibility = if (truncated) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        // 自己的主页：编辑资料返回、或者切回来时重新拉一次资料
        if (isSelf) viewModel.fetchUser()
    }

    /** 顶栏图标当前是否为白色（null = 还没刷过，保证首次一定应用） */
    private var barIconWhite: Boolean? = null

    /**
     * 顶栏三个图标（返回 / 搜索 / 更多）统一上色。
     * `ic_search`、`ic_more` 自带 `android:tint="?attr/colorControlNormal"`，
     * 压在封面上会明显比返回键浅，所以这里显式覆盖。
     */
    private fun applyBarIconTint(white: Boolean) {
        if (barIconWhite == white) return
        barIconWhite = white
        val color = if (white) {
            Color.WHITE
        } else {
            MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorOnSurface,
                0
            )
        }
        binding.toolBar.navigationIcon?.setTintList(ColorStateList.valueOf(color))
        binding.toolBar.menu?.let { menu ->
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                val icon = item.icon ?: continue
                icon.mutate().setTint(color)
                item.icon = icon
            }
        }
        binding.toolBar.overflowIcon?.mutate()?.setTint(color)
    }

    private fun getMenuTitle(title: CharSequence?): SpannableString {
        val text = title ?: return SpannableString("")
        return SpannableString(text).also {
            it.setSpan(
                ForegroundColorSpan(
                    MaterialColors.getColor(
                        requireContext(),
                        androidx.appcompat.R.attr.colorControlNormal,
                        0
                    )
                ),
                0, text.length, 0
            )
        }
    }

    override fun initBar() {
        super.initBar()

        // 官方主页 tab 是左对齐、可横向滑动的
        binding.tabLayout.tabMode = TabLayout.MODE_SCROLLABLE

        // 收起后才显示昵称；展开时昵称在头部里
        binding.collapsingToolbar.title = viewModel.userData?.username
        binding.collapsingToolbar.setCollapsedTitleTextColor(
            MaterialColors.getColor(
                requireContext(),
                com.google.android.material.R.attr.colorOnSurface,
                0
            )
        )

        // percent: 1 = 完全展开，0 = 完全收起
        // 下滑时把整块头部淡掉、只留折叠后的工具栏标题，顺带决定图标用白还是用主题色
        // （头部基本还在时压在封面上用纯白，淡到一半以后底下已经是 colorSurface，再用白色就看不见了）
        binding.appBar.addOnOffsetChangedListener(object : AppBarLayoutStateChangeListener() {
            override fun onScroll(percent: Float) {
                // initBar() 比 initUser() 先跑（BasePagerFragment.onViewCreated 的顺序），
                // 这时 userBinding 还没有；偏移回调要等布局才来，实际不会提前触发，挡一下更稳
                val header = if (::userBinding.isInitialized) userBinding.infoLayout else null
                applyBarIconTint(applyHeaderFade(header, percent) > 0.5f)
            }
        })

        binding.toolBar.apply {
            inflateMenu(R.menu.user_menu)
            menuBlock = menu?.findItem(R.id.block)
            menuBlock?.title = getMenuTitle(menuBlock?.title)
            // 自己的主页：不能拉黑 / 举报自己
            menuBlock?.isVisible = !isSelf

            val menuShare = menu?.findItem(R.id.share)
            menuShare?.title = getMenuTitle(menuShare?.title)

            val menuReport = menu?.findItem(R.id.report)
            menuReport?.title = getMenuTitle(menuReport?.title)
            menuReport?.isVisible = PrefManager.isLogin && !isSelf

            menu?.findItem(R.id.check)?.title = getMenuTitle(menu?.findItem(R.id.check)?.title)

            // 顶栏右侧图标和左侧返回键同一色调（展开纯白 / 收起主题色）
            overflowIcon = ContextCompat.getDrawable(context, R.drawable.ic_more)

            viewModel.checkMenuState()

            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.check -> {
                        val data = viewModel.userData
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            setTitle(data?.username)
                            setMessage(
                                """
                                uid: ${data?.uid}
                                
                                等级: Lv.${data?.level}
                                
                                性别: ${if (data?.gender == 0) "女" else if (data?.gender == 1) "男" else "未知"}
                                
                                注册时长: ${((System.currentTimeMillis() / 1000 - (data?.regdate ?: 0)) / 24 / 3600)} 天
                                
                                注册时间: ${DateUtils.timeStamp2Date(data?.regdate ?: 0)}
                            """.trimIndent()
                            )
                            show()
                        }
                    }

                    R.id.search -> {
                        IntentUtil.startActivity<SearchActivity>(requireContext()) {
                            putExtra("pageType", "user")
                            putExtra("pageParam", viewModel.uid)
                            putExtra("title", viewModel.userData?.username)
                        }
                    }

                    R.id.block -> {
                        val isBlocked = menuBlock?.title.toString() == "移除黑名单"
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            setTitle("确定将 ${viewModel.userData?.username} ${menuBlock?.title}？")
                            setNegativeButton(android.R.string.cancel, null)
                            setPositiveButton(android.R.string.ok) { _, _ ->
                                viewModel.uid.let { uid ->
                                    menuBlock?.title = if (isBlocked) {
                                        viewModel.deleteUid(uid)
                                        getMenuTitle("加入黑名单")
                                    } else {
                                        viewModel.saveUid(uid)
                                        getMenuTitle("移除黑名单")
                                    }
                                }
                            }
                            show()
                        }
                    }

                    R.id.share -> {
                        IntentUtil.shareText(
                            requireContext(),
                            "https://www.coolapk1s.com/u/${viewModel.uid}"
                        )
                    }

                    R.id.report -> {
                        IntentUtil.startActivity<WebViewActivity>(requireContext()) {
                            putExtra(
                                "url",
                                "https://m.coolapk.com/mp/do?c=user&m=report&id=${viewModel.uid}"
                            )
                        }
                    }
                }
                return@setOnMenuItemClickListener true
            }
        }

        // 初始为完全展开
        applyBarIconTint(true)
    }

    companion object {
        /** 折叠时签名保留的行数，与 base_view_user.xml 里的 maxLines 保持一致 */
        private const val BIO_COLLAPSED_LINES = 2
    }
}
