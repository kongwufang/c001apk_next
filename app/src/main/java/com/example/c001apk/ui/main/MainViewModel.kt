package com.example.c001apk.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.constant.Constants
import com.example.c001apk.logic.repository.AccountRepository
import com.example.c001apk.logic.repository.BlackListRepo
import com.example.c001apk.logic.repository.NetworkRepo
import com.example.c001apk.logic.repository.SpamConfigRepo
import com.example.c001apk.util.CookieUtil
import com.example.c001apk.util.PrefManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val networkRepo: NetworkRepo,
    private val blackListRepo: BlackListRepo,
    private val spamConfigRepo: SpamConfigRepo,
) : ViewModel() {

    private val accountRepository by lazy { AccountRepository() }
    val sessionInvalidated = MutableLiveData<Boolean>()

    var lastCheck = System.currentTimeMillis()
    var isInit: Boolean = true

    fun fetchAppInfo(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.getAppInfo(id)
                .collect { result ->
                    val appInfo = result.getOrNull()
                    if (appInfo?.data != null) {
                        try {
                            // 跟随官方最新版本号，保持请求头“现代”；空值不覆盖，避免生成无效 token
                            val versionName = appInfo.data.apkversionname
                            val versionCode = appInfo.data.apkversioncode
                            if (!versionName.isNullOrBlank()) PrefManager.VERSION_NAME = versionName
                            if (!versionCode.isNullOrBlank()) PrefManager.VERSION_CODE = versionCode
                            PrefManager.API_VERSION = Constants.API_VERSION
                            PrefManager.USER_AGENT =
                                "Dalvik/2.1.0 (Linux; U; Android ${PrefManager.ANDROID_VERSION}; ${PrefManager.MODEL} ${PrefManager.BUILDNUMBER}) (#Build; ${PrefManager.BRAND}; ${PrefManager.MODEL}; ${PrefManager.BUILDNUMBER}; ${PrefManager.ANDROID_VERSION}) +CoolMarket/${PrefManager.VERSION_NAME}-${PrefManager.VERSION_CODE}-${Constants.MODE}"
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            checkLoginInfo()
        }
    }

    private suspend fun checkLoginInfo() {
        val result = accountRepository.checkStartup()
        if (result == AccountRepository.Result.REJECTED) sessionInvalidated.postValue(true)
        if (result != AccountRepository.Result.STALE) syncBlackList()
    }

    /** 启动/登录后同步云端数据：黑名单镜像进本地库、其他屏蔽项（关键字/用户/节点）进内存缓存 */
    private fun syncBlackList() {
        if (!PrefManager.isLogin) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { blackListRepo.syncFromCloud() }
                .onFailure { it.printStackTrace() }
            runCatching { spamConfigRepo.refresh() }
                .onFailure { it.printStackTrace() }
        }
    }

    fun onCheckCount() {
        val account = com.example.c001apk.util.MessageAccount.current() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            networkRepo.checkCount().collect { result ->
                synchronized(PrefManager) {
                    if (!account.isCurrent()) return@synchronized
                    result.getOrNull()?.data?.let(CookieUtil::applyCheckCounts)
                }
            }
        }
    }

}