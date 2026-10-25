package com.example.c001apk.ui.blacklist

import com.example.c001apk.util.PrefManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.logic.model.BlackListUser
import com.example.c001apk.logic.model.StringEntity
import com.example.c001apk.logic.repository.BlackListRepo
import com.example.c001apk.util.Event
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = BlackListViewModel.Factory::class)
class BlackListViewModel @AssistedInject constructor(
    @Assisted val type: String,
    private val blackListRepo: BlackListRepo,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(type: String): BlackListViewModel
    }

    val toastText = MutableLiveData<Event<String>>()

    /** type = "user" 走云端接口，其余（topic）仍是本地库 */
    /**
     * 用户黑名单读云端还是读本机，由「设置 → 推荐流相关 → 黑名单（用户）」决定（2026-10-25 ✓）
     *
     * - 云端模式 ⇒ 拉服务端黑名单
     * - 本地模式 ⇒ 读本机 `user_blacklist.db`（跟以前 topic 类型走同一套本地逻辑 ✓）
     */
    val isCloud: Boolean get() = type == "user" && PrefManager.saveBlackListToCloud

    // topic：本地
    val blackListLiveData: LiveData<List<StringEntity>> = when (type) {
        "user" -> blackListRepo.loadAllUserListLive()
        "topic" -> blackListRepo.loadAllTopicListLive()
        else -> throw IllegalArgumentException("invalid type: $type")
    }

    // user：云端（列表实体，含昵称与头像）
    val cloudUsers = MutableLiveData<List<BlackListUser>>()
    val loading = MutableLiveData<Boolean>()

    /** 拉取云端黑名单（自动翻页），同时把 uid 镜像进本地库供各列表过滤 */
    fun loadCloudUsers() {
        viewModelScope.launch(Dispatchers.IO) {
            loading.postValue(true)
            val users = blackListRepo.fetchCloudUsers()
            if (users == null) toastText.postValue(Event("云端黑名单获取失败"))
            else publishCloudUsers(users)
            loading.postValue(false)
        }
    }

    private suspend fun reload() {
        blackListRepo.fetchCloudUsers()?.let { publishCloudUsers(it) }
    }

    /** cloudUsers 声明为非空，统一在这里下发（避免 NullSafeMutableLiveData lint 报错） */
    private fun publishCloudUsers(users: List<BlackListUser>) {
        cloudUsers.postValue(users)
    }

    /** 按 uid 加入云端黑名单 */
    fun addCloudUser(uid: String) {
        if (uid.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val synced = blackListRepo.saveUid(uid)
            toastText.postValue(
                Event(if (synced) "已加入黑名单" else "云端同步失败（已本地屏蔽）")
            )
            reload()
        }
    }

    /** 批量加入（导入备份用） */
    fun addCloudUserList(uids: List<String>) {
        if (uids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var failed = 0
            uids.forEach { if (!blackListRepo.saveUid(it)) failed++ }
            toastText.postValue(
                Event(if (failed == 0) "导入成功" else "有 $failed 条云端同步失败（已本地屏蔽）")
            )
            reload()
        }
    }

    /** 移出云端黑名单 */
    fun removeCloudUser(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val synced = blackListRepo.deleteUid(uid)
            if (synced) {
                cloudUsers.value?.let { list ->
                    publishCloudUsers(list.filterNot { it.uid == uid })
                }
                toastText.postValue(Event("已移出黑名单"))
            } else {
                toastText.postValue(Event("移出失败，请检查网络后重试"))
            }
        }
    }

    /** 清空云端黑名单（没有批量接口，逐条移除） */
    fun clearCloudUsers() {
        viewModelScope.launch(Dispatchers.IO) {
            val uids = cloudUsers.value?.mapNotNull { it.uid } ?: emptyList()
            if (uids.isEmpty()) return@launch
            var failed = 0
            uids.forEach { if (!blackListRepo.deleteUid(it)) failed++ }
            cloudUsers.postValue(emptyList())
            toastText.postValue(Event(if (failed == 0) "已清空黑名单" else "有 $failed 条移除失败"))
        }
    }

    fun insertList(data: String) {
        if (isCloud) {
            addCloudUser(data)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (blackListRepo.checkTopic(data))
                toast()
            else
                blackListRepo.insertTopic(StringEntity(data))
        }
    }

    fun insertList(dataList: List<StringEntity>) {
        if (isCloud) {
            addCloudUserList(dataList.map { it.data })
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            blackListRepo.insertTopicList(dataList)
        }
    }

    fun toast() {
        toastText.postValue(Event("已存在"))
    }

    fun deleteData(data: String) {
        if (isCloud) {
            removeCloudUser(data)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            blackListRepo.deleteTopic(data)
        }
    }

    fun deleteAll() {
        if (isCloud) {
            clearCloudUsers()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            blackListRepo.deleteAllTopic()
        }
    }

}
