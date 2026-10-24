package com.example.c001apk.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.HomeMenu
import com.example.c001apk.logic.repository.HomeMenuRepo
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.CookieUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.UnreadCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeMenuRepo: HomeMenuRepo,
    private val networkRepo: NetworkRepo,
) : ViewModel() {

    var isInit = true
    var position: Int = 0

    val tabListLiveData: LiveData<List<HomeMenu>> = homeMenuRepo.loadAllListLive()
    val restart = MutableLiveData<Boolean>()

    /** 首页工具栏消息入口的未读角标数字，0 表示不显示 */
    val unreadCount = MutableLiveData<Int>()

    /**
     * 默认 tab 顺序（2026-10-25 调整 ✓）
     *
     * 用户要求：「把关注和应用移动到头条右边，这样头条这个 tab 就在最左边」
     *
     * ⚠️ 为什么头条必须在最左
     *   侧边栏抽屉的「左边缘右滑」只在头条 tab 生效（见 HomeFragment）——
     *   头条是**纵向**信息流，没有横向手势 ✓ 可以安全地抢
     *   而「关注 / 应用」等 tab 内部是 ViewPager2，横向滑动会打架 ✗
     *
     * 存储用的是 [HomeMenu.position]（Dao 里 `ORDER BY position ASC` ✓）
     */
    val defaultList by lazy {
        listOf(
            HomeMenu(0, "头条", true),
            HomeMenu(1, "关注", true),
            HomeMenu(2, "应用", true),
            HomeMenu(3, "热榜", true),
            HomeMenu(4, "话题", true),
            HomeMenu(5, "数码", true),
            HomeMenu(6, "酷图", true)
        )
    }

    fun initTab() {
        viewModelScope.launch(Dispatchers.IO) {
            homeMenuRepo.insertList(defaultList)
        }
    }

    /**
     * 只写库、**不**触发重启（2026-10-25 ✓）
     *
     * 给「头条挪到最左」的一次性迁移用：
     * [updateTab] 会 postValue(restart = true) ⇒ 整个首页重启 ⇒ 白闪一下 ✗
     * 迁移只改 position ⇒ 当前 tabList 已经在内存里了 ⇒ 直接写库就行 ✓
     */
    fun saveTabOrder(menuList: List<HomeMenu>) {
        viewModelScope.launch(Dispatchers.IO) {
            homeMenuRepo.updateList(menuList)
        }
    }

    fun updateTab(menuList: List<HomeMenu>) {
        viewModelScope.launch(Dispatchers.IO) {
            homeMenuRepo.updateList(menuList)
            restart.postValue(true)
        }
    }

    /**
     * 刷新未读数缓存（CookieUtil）并喂给首页角标。
     *
     * 消息中心的宫格红点读同一份缓存，两边都由 [UnreadCounter] 算，
     * 所以「在消息中心里看过的」会同时从宫格和首页角标上消失。
     * 未登录直接清零，不发请求。
     */
    fun refreshUnreadCount() {
        if (!PrefManager.isLogin) {
            unreadCount.postValue(0)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.checkCount()
                .collect { result ->
                    result.getOrNull()?.data?.let {
                        CookieUtil.atme = it.atme
                        CookieUtil.atcommentme = it.atcommentme
                        CookieUtil.feedlike = it.feedlike
                        CookieUtil.contacts_follow = it.contactsFollow
                        CookieUtil.badge = it.unreadBadge
                        CookieUtil.notification = it.unreadNotification
                        CookieUtil.message = it.message
                        CookieUtil.commentme = it.commentme
                        unreadCount.postValue(UnreadCounter.total)
                    }
                }
        }
    }

}
