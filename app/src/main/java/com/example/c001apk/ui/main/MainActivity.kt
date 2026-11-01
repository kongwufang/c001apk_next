package com.example.c001apk.ui.main

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityMainBinding
import com.example.c001apk.ui.base.BaseActivity
import com.example.c001apk.ui.home.HomeFragment
import com.example.c001apk.ui.message.MineFragment
import com.example.c001apk.ui.settings.SettingsActivity
import com.example.c001apk.util.ActivityCollector
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.UpdateChecker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>(), IOnBottomClickContainer {

    private val viewModel by viewModels<MainViewModel>()
    override var controller: IOnBottomClickListener? = null
    private val isLogin by lazy { PrefManager.isLogin }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityCollector.addActivity(this)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // 启动时按开关自动检查本应用更新（每个进程只查一次，重建不重复弹）
        if (!UpdateChecker.checkedThisSession) {
            UpdateChecker.checkedThisSession = true
            checkSelfUpdate()
        }

        // 底部导航不再挂未读角标：那个位置的历史语义是「消息」，现在消息已独立成
        // MessageCenterActivity，未读提示统一走消息中心宫格（见 MessageThirdAdapter）。
        if (viewModel.isInit) {
            viewModel.isInit = false
            genData()
        }

        binding.viewPager.apply {
            offscreenPageLimit = 1
            adapter = object : FragmentStateAdapter(this@MainActivity) {
                // ★ 只留首页 ✓ 「我的」已挪进侧边栏抽屉（见 initMineDrawer）✓
                override fun getItemCount() = 1
                override fun createFragment(position: Int): Fragment {
                    return HomeFragment()
                }
            }
            isUserInputEnabled = false
            fixViewPager2Insets(this)
        }

        initMineDrawer()

        // 首次启动：告诉用户侧边栏在哪（否则☰太隐蔽，用户找不到 ✗）
        if (!PrefManager.sidebarHintShown) {
            PrefManager.sidebarHintShown = true
            // ★ 2026-11-01：「匿名举报联盟」改成首次进入主动问 ✓
            //   ⚠️ 必须等新手提示**关掉之后**再问 ✗
            //      两个 Dialog 同时弹会叠在一起 ✗ 用户一脸问号 ✗
            //   ⇒ 在 showSidebarHint() 的 dismiss 回调里接着问 ✓
            binding.root.post { showSidebarHint() }
        } else {
            // 已经看过新手提示 ⇒ 直接问（同样是首次进入才问 ✓ 问过就置位 ✓）
            binding.root.post { maybeAskReportAlliance() }
        }
    }

    /**
     * 首次启动主动问「要不要加入匿名举报联盟」✓
     *
     * 条件：没问过 ✓ 且 还没加入 ✓ 且 界面还在 ✓
     * 触发时机：新手提示 dismiss 之后 ✓（或本来就没有新手提示 ⇒ 启动即问 ✓）
     * 再等 400ms 让首页先画出来 ✓ 不然像连弹两个框 ✗
     */
    private fun maybeAskReportAlliance() {
        if (PrefManager.reportAllianceAsked || com.example.c001apk.util.ReportAlliance.joined) {
            return
        }
        PrefManager.reportAllianceAsked = true       // ★ 弹出前就置位 ✓ 中途被杀也不重复弹 ✓
        binding.root.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            runCatching {
                com.example.c001apk.util.ReportAllianceDialog.askJoin(this)
            }
        }, 400L)
    }

    /**
     * 「侧边栏在这里」新手提示（2026-10-25 ✓）
     *
     * 用一个**全屏 Dialog** 而不是往 DrawerLayout 里加 View ✗
     *   —— DrawerLayout 的子 View 只能是「内容」或「抽屉」✗ 塞第三个会报错 ✗
     *   ⇒ Dialog 是独立窗口 ✓ 零布局改动 ✓ 横竖屏都对 ✓
     *
     * 遮罩用 #B3000000（不是全黑）⇒ 底下的 ☰ 还看得见 ✓
     * 点遮罩任意处 / 点「知道了」都能关 ✓
     * 标记在**弹出前**就置位 ✓ ⇒ 中途被杀也不会下次又弹 ✓
     */
    private fun showSidebarHint() {
        if (isFinishing || isDestroyed) return
        runCatching {
            val view = layoutInflater.inflate(R.layout.dialog_sidebar_hint, null)
            val dialog = android.app.Dialog(this)
            dialog.setContentView(view)
            dialog.window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            view.setOnClickListener { dialog.dismiss() }
            view.findViewById<android.view.View>(R.id.hintConfirm)
                .setOnClickListener { dialog.dismiss() }
            // 关掉之后再问联盟 ⇒ 两个弹窗不会叠 ✓（见 maybeAskReportAlliance 注释）
            dialog.setOnDismissListener { maybeAskReportAlliance() }
            dialog.show()
        }
    }

    /**
     * 侧边栏（抽屉）初始化 —— 2026-10-24 UI 大改 ✓
     *
     * ## 设计
     * 直接把**原来的 MineFragment 整个塞进抽屉** ✓ 零改动：
     *   工具栏（右上角设置齿轮 ✓）→ 头像卡片（含三段式）→ 3×3 功能宫格
     * ⇒ 用户要求「个人信息卡片复用，右上角设置，下面三段式，再下面 3×3 宫格」
     *   这一条路径天然满足 ✓ 不用重写任何布局 ✓
     *
     * ## 宽度
     * 屏幕 85% ✓ —— 项目没有 percentlayout 依赖 ✗ 所以代码里算 ✓
     *
     * ## 返回键
     * 抽屉打开时把 [onBackPressedCallback] 打开 ⇒ 按返回先关抽屉 ✓
     * 关闭时再关掉 ✓ 不干扰首页自己的返回逻辑 ✓
     */
    private fun initMineDrawer() {
        // 宽度 = 屏幕 85%，但**封顶 420dp** ✓
        //   ⚠️ 横屏/平板上 85% 会宽得离谱 ✗（横屏宽度本来就是竖屏的 2 倍多 ✗）
        //   ⚠️ 竖屏 1080px 屏 ≈ 411dp ⇒ 85% ≈ 350dp < 420dp ⇒ 竖屏不受影响 ✓
        val cap = (420 * resources.displayMetrics.density).toInt()
        val w = minOf((resources.displayMetrics.widthPixels * 0.85f).toInt(), cap)
        binding.mineDrawer.layoutParams = binding.mineDrawer.layoutParams.apply { width = w }

        // 装 MineFragment（只在第一次）
        if (supportFragmentManager.findFragmentById(R.id.mineDrawer) == null) {
            supportFragmentManager.commit {
                replace(R.id.mineDrawer, MineFragment())
            }
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                onBackPressedCallback.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                onBackPressedCallback.isEnabled = false
            }
        })
    }

    /** 打开「我的」侧边栏（首页顶栏最左边那个 ☰ 调的 ✓）*/
    fun openMineDrawer() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
    }

    fun closeMineDrawer() {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    /**
     * 开关「左边缘右滑拉抽屉」（2026-10-25 ✓）
     *
     * 只有首页 tab 是「头条」时才开 ✓
     *   - 头条是纵向信息流 ⇒ 没有横向手势 ⇒ 抢边缘滑动不打架 ✓
     *   - 「关注 / 应用」等 tab 内部是 ViewPager2 ✗ 会被抢手势 ✗
     * 关掉手势时 ☰ 按钮依然能开抽屉 ✓（见 [com.example.c001apk.view.LockableDrawerLayout]）
     */
    fun setDrawerEdgeSwipeEnabled(enabled: Boolean) {
        binding.drawerLayout.edgeSwipeEnabled = enabled
    }

    /**
     * 启动时的自更新检查（只看正式版，Beta 通道已去掉）。
     *
     * ★★ 2026-11-01 用户要求：「APP 进入之后自动检查更新就不跳窗口了，
     *    只显示在侧栏，减少打扰」★★
     *   ⇒ 这里**只把接口响应预热进缓存** ✓ 不弹任何窗 ✗
     *   ⇒ 用户打开侧栏（「我的」）时，升级卡直接读缓存 ⇒ 秒出 ✓
     *   ⇒ 想看弹窗请去「关于」页点「立即检查更新」✓
     */
    private fun checkSelfUpdate() {
        if (!PrefManager.isCheckUpdateStable) return
        lifecycleScope.launch { UpdateChecker.prefetch() }
    }

    private fun genData() {
        viewModel.fetchAppInfo("com.coolapk.market")
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // 只有抽屉开着时才会 enabled（见 initMineDrawer 的 DrawerListener）✓
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
                this.isEnabled = false
            }
        }
    }

    /**
     * ⚠️ 这两个函数是**历史遗留的空实现** ✓ 千万不要删 ✗
     *
     * 原来用来让底栏随列表滑动收起/展开（HideBottomViewOnScrollBehavior）。
     * 2026-10-24 UI 大改把底栏整个删了 ✓ 但 **12 处调用点还在**：
     * AppListFragment / HomeFragment / HomeFeedFragment / HomeTopicContentFragment /
     * HomeTopicFragment / MineFragment / SettingsPreferenceFragment …
     * ⇒ 保留空函数 ⇒ 调用方一行都不用改 ✓ 也不会崩 ✓
     */
    fun showNavigationView() = Unit

    fun hideNavigationView() = Unit

    private fun fixViewPager2Insets(view: ViewPager2) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            /* Do nothing */
            windowInsets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ActivityCollector.removeActivity(this)
    }

    override fun onResume() {
        super.onResume()
        if (!viewModel.isInit && isLogin) {
            with(System.currentTimeMillis()) {
                if (this - viewModel.lastCheck >= 5 * 60 * 1000) {
                    viewModel.lastCheck = this
                    viewModel.onCheckCount()
                }
            }
        }
    }

}