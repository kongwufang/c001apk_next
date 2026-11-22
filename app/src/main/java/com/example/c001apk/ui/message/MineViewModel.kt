package com.example.c001apk.ui.message

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.example.c001apk.logic.repository.FollowRepo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.adapter.LoadingState
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.util.NoticeRepo
import com.example.c001apk.util.PrefManager
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
    private val networkRepo: NetworkRepo,
    followRepo: FollowRepo,
) : ViewModel() {

    val localFollowCount = followRepo.loadAllLive().map { it.size }

    var initLogin: Boolean = true
    var isInit: Boolean = true

    var countList = MutableLiveData<List<String>>()

    val notices = MutableLiveData<List<NoticeRepo.Card>>(emptyList())

    fun fetchNotices() {
        viewModelScope.launch(Dispatchers.IO) {
            notices.postValue(NoticeRepo.fetch())
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
