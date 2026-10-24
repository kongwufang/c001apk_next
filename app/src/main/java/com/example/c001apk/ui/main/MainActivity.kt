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
     * 启动时的自更新检查（只看正式版，Beta 通道已去掉）。
     *
     * 自建接口一次响应里还有关于页按钮那段，跟这里共用 60 秒缓存，不会重复请求。
     */
    private fun checkSelfUpdate() {
        if (!PrefManager.isCheckUpdateStable) return
        lifecycleScope.launch {
            val info = UpdateChecker.fetchUpdate() ?: return@launch
            if (info.isNewer && !isFinishing && !isDestroyed)
                UpdateChecker.showUpdateDialog(this@MainActivity, info)
        }
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

    override fun onPause() {
        super.onPause()
        // 从抽屉里点进任何一个页面（收藏 / 设置 / 个人主页…）之后，
        // 退回来时抽屉不该还开着 ✗ —— 而且这时 onPause 一定先触发 ✓
        // 用 false = 不做关闭动画 ✓（用户已经在别的页面了，看不到 ✓）
        binding.drawerLayout.closeDrawer(GravityCompat.START, false)
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