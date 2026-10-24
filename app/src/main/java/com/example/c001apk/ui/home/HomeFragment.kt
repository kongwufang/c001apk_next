package com.example.c001apk.ui.home

import com.example.c001apk.util.PrefManager
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.c001apk.databinding.FragmentHomeBinding
import com.example.c001apk.ui.applist.AppListFragment
import com.example.c001apk.ui.base.BaseFragment
import com.example.c001apk.ui.homefeed.HomeFeedFragment
import com.example.c001apk.ui.hometopic.HomeTopicFragment
import com.example.c001apk.ui.main.MainActivity
import com.example.c001apk.ui.message.MessageCenterActivity
import com.example.c001apk.ui.search.SearchActivity
import com.example.c001apk.util.IntentUtil
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>(), IOnTabClickContainer {

    private val viewModel by viewModels<HomeViewModel>()
    override var tabController: IOnTabClickListener? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initButton()

        // 消息入口的未读角标：数字与消息中心宫格同源（UnreadCounter），0 时不显示
        viewModel.unreadCount.observe(viewLifecycleOwner) { count ->
            binding.messageBadge.isVisible = count > 0
            binding.messageBadge.text = if (count > 99) "99+" else count.toString()
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: Tab) {
                viewModel.position = tab.position
                // 左边缘右滑拉抽屉只在「头条」这个 tab 生效（2026-10-25 ✓）
                //   ⚠️ 别的 tab 内部是 ViewPager2，横向手势会打架 ✗
                //   头条是纵向信息流，没有横向手势 ⇒ 可以安全地抢 ✓
                (activity as? MainActivity)?.setDrawerEdgeSwipeEnabled(
                    tab.text?.toString() == "头条"
                )
            }

            override fun onTabUnselected(tab: Tab?) {}

            override fun onTabReselected(tab: Tab?) {
                if (tab?.text == "关注")
                    tabController?.onReturnTop(false)
                else {
                    tabController?.onReturnTop(true)
                    (activity as? MainActivity)?.showNavigationView()
                }
            }
        })

        viewModel.tabListLiveData.observe(viewLifecycleOwner) { tabList ->
            if (tabList.isEmpty()) {
                viewModel.initTab()
            } else {
                // ── 一次性迁移：把「头条」挪到最左（2026-10-25 ✓）──
                //   改 defaultList 只影响新装 ✗ 老用户 Room 里存的是旧顺序 ✗
                var list = tabList
                if (!PrefManager.tabOrderHeadlineFirst) {
                    PrefManager.tabOrderHeadlineFirst = true
                    val migrated = list
                        .sortedBy { if (it.title == "头条") -1 else it.position }
                        .mapIndexed { index, menu -> menu.copy(position = index) }
                    if (migrated != list) {
                        viewModel.saveTabOrder(migrated)
                        list = migrated
                    }
                }

                val enableList = list.filter {
                    it.isEnable
                }.map {
                    it.title
                }
                if (enableList.isEmpty()) {
                    viewModel.updateTab(viewModel.defaultList)
                } else {
                    initView(enableList)
                }
            }

        }

    }

    override fun onResume() {
        super.onResume()
        // 回到首页刷新一次未读数：消息中心宫格的红点和这里的消息入口角标都读这份缓存。
        // 从消息中心返回时也会走到这里，刚看过的通知会同步从角标上掉下来。
        viewModel.refreshUnreadCount()
    }

    private fun initButton() {
        // 「我的」侧边栏（2026-10-24 UI 大改 ✓）
        binding.mine.setOnClickListener {
            (activity as? MainActivity)?.openMineDrawer()
        }

        binding.search.setOnClickListener {
            IntentUtil.startActivity<SearchActivity>(requireContext()) {
            }
        }

        // 原来这里是「编辑Tab」，已挪到 设置 → 外观，位置让给消息入口
        binding.message.setOnClickListener {
            IntentUtil.startActivity<MessageCenterActivity>(requireContext()) {
            }
        }
    }

    private fun initView(enableList: List<String>) {
        binding.viewPager.offscreenPageLimit = enableList.size
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int): Fragment {
                return when (enableList[position]) {
                    "关注" -> HomeFeedFragment.newInstance("follow")
                    "应用" -> AppListFragment()
                    "头条" -> HomeFeedFragment.newInstance("feed")
                    "热榜" -> HomeFeedFragment.newInstance("rank")
                    "话题" -> HomeTopicFragment.newInstance("topic")
                    "数码" -> HomeTopicFragment.newInstance("product")
                    "酷图" -> HomeFeedFragment.newInstance("coolPic")
                    else -> throw IllegalArgumentException()
                }
            }

            override fun getItemCount() = enableList.size

        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = enableList[position]
        }.attach()
        if (viewModel.isInit) {
            viewModel.isInit = false
            if (enableList.contains("头条"))
                binding.viewPager.setCurrentItem(enableList.indexOf("头条"), false)
        }
    }

}
