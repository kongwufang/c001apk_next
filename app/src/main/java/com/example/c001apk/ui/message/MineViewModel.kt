package com.example.c001apk.ui.message

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.NoticeRepo
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import javax.inject.Inject

/**
 * 「我的」页：只负责个人资料（用户名 / 头像 / 等级 / 经验 / 动态·关注·粉丝数）。
 * 消息相关（通知列表、未读数、分页）已经拆到 MessageCenterViewModel。
 */
@HiltViewModel
class MineViewModel @Inject constructor(
    private val networkRepo: NetworkRepo
) : ViewModel() {

    var initLogin: Boolean = true
    var isInit: Boolean = true

    var countList = MutableLiveData<List<String>>()

    /**
     * 侧栏卡片数据（2026-11-01 新增）
     * `first` = 公告列表 ✓ `second` = 升级信息（null = 没更新 / 检查关着 / 请求失败 ✓）
     *
     * ⚠️ 只在**打开侧栏 / 进「我的」**时拉一次 ✓ 不轮询 ✗
     *    升级接口有 60 秒缓存 ✓ 和关于页共用 ✓ 不会重复请求 ✓
     */
    val sideCards =
        MutableLiveData<Pair<List<NoticeRepo.Card>, UpdateChecker.UpdateInfo?>>(
            Pair(emptyList(), null)
        )

    fun fetchSideCards() {
        viewModelScope.launch(Dispatchers.IO) {
            val notices = NoticeRepo.fetch()
            // 设置里关掉「检查更新」⇒ 侧栏也不显示升级卡 ✓（用户说的「设置那边才能关闭」✓）
            val update = if (PrefManager.isCheckUpdateStable) UpdateChecker.fetchUpdate() else null
            sideCards.postValue(Pair(notices, update))
        }
    }
    val loadingState = MutableLiveData<LoadingState>()

    fun fetchProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.getProfile(PrefManager.uid)
                .collect { result ->
                    val data = result.getOrNull()
                    if (data?.data != null) {
                        countList.postValue(
                            listOf(
                                data.data.feed,
                                data.data.follow,
                                data.data.fans
                            )
                        )
                        PrefManager.username =
                            withContext(Dispatchers.IO) {
                                URLEncoder.encode(data.data.username, "UTF-8")
                            }
                        PrefManager.userAvatar = data.data.userAvatar
                        PrefManager.level = data.data.level
                        PrefManager.experience = data.data.experience.toString()
                        PrefManager.nextLevelExperience = data.data.nextLevelExperience.toString()
                        loadingState.postValue(LoadingState.LoadingDone)
                    } else {
                        loadingState.postValue(LoadingState.LoadingFailed(""))
                        result.exceptionOrNull()?.printStackTrace()
                    }
                }
        }
    }

}
