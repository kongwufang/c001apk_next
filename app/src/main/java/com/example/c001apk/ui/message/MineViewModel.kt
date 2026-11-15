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
            val real = if (PrefManager.isCheckUpdateStable) UpdateChecker.fetchUpdate() else null
            // TEMP(2026-11-15)：仅本机模拟器（设备标识白名单）注入长日志假卡，验收完整段回退
            sideCards.postValue(Pair(notices, debugFakeUpdate() ?: real))
        }
    }

    // TEMP(2026-11-15)：仅本轮设备验收使用，回退时连同调用一并删除。
    // 只在指定设备上生效，其他任何设备都返回 null ⇒ 走真实更新检查。
    private fun debugFakeUpdate(): UpdateChecker.UpdateInfo? {
        if (PrefManager.SZLMID != "DUKEHdVaYc-l0g9RH-y--wl8tgEnSncMV5g7") return null
        val body = buildString {
            appendLine("V9.9.9 · 侧栏日志滚动验收（临时）")
            for (i in 1..40) {
                appendLine("- 第 $i 条：验证卡片高度受限、内容可滚动、下方两个按钮始终可见。")
            }
            append("【末行标记】看到这行说明已经滑到日志末尾。")
        }
        return UpdateChecker.UpdateInfo(
            versionName = "V9.9.9",
            versionCode = com.example.c001apk.BuildConfig.VERSION_CODE + 1L,
            changelog = body,
            lines = listOf(UpdateChecker.DownloadLine("调试线路", "https://example.invalid/")),
        )
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
