package com.example.c001apk.logic.repository

import com.example.c001apk.util.MessageAccount
import com.example.c001apk.util.PrefManager
import androidx.lifecycle.LiveData
import com.example.c001apk.di.TopicBlackList
import com.example.c001apk.di.UserBlackList
import com.example.c001apk.logic.dao.StringEntityDao
import com.example.c001apk.logic.model.BlackListUser
import com.example.c001apk.logic.model.StringEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlackListRepo @Inject constructor(
    @UserBlackList
    private val userBlackListDao: StringEntityDao,
    @TopicBlackList
    private val topicBlackListDao: StringEntityDao,
    private val networkRepo: NetworkRepo,
) {

    fun loadAllUserListLive(): LiveData<List<StringEntity>> {
        return userBlackListDao.loadAllListLive()
    }

    suspend fun insertUid(uid: StringEntity) {
        userBlackListDao.insert(uid)
    }

    suspend fun insertUidList(list: List<StringEntity>) {
        userBlackListDao.insertList(list)
    }

    suspend fun checkUid(uid: String): Boolean {
        val account = MessageAccount.current()
        return if (account != null && PrefManager.saveBlackListToCloud) {
            uid in PrefManager.cloudBlocked(account)
        } else userBlackListDao.isExist(uid)
    }

    /**
     * 加入黑名单：先同步云端（POST /v6/user/addToBlackList?uid=x），再写本地库。
     *
     * 返回 false = 云端同步失败（本地仍会写入，保证离线也能屏蔽；但云端黑名单页看不到这一条）。
     */
    suspend fun saveUid(uid: String): Boolean {
        // 本地模式（设置里关掉「黑名单=云端」）⇒ 完全不请求云端 ✓
        //   返回 true = 本地已生效 ✓ 调用方不会报错 ✓
        if (!PrefManager.saveBlackListToCloud) {
            if (!userBlackListDao.isExist(uid)) {
                userBlackListDao.insert(StringEntity(uid))
            }
            return true
        }
        val owner = MessageAccount.current() ?: return false
        val synced = networkRepo.addToBlackList(uid).first().getOrNull()?.data != null
        synchronized(PrefManager) {
            PrefManager.saveCloudBlocked(owner, PrefManager.cloudBlocked(owner) + uid)
        }
        return synced && owner.isCurrent()
    }

    /** 移出黑名单：先同步云端（POST /v6/user/removeFromBlackList?uid=x），再删本地库 */
    suspend fun deleteUid(uid: String): Boolean {
        // 本地模式 ⇒ 只删本地 ✓ 不请求云端 ✓
        if (!PrefManager.saveBlackListToCloud) {
            userBlackListDao.delete(uid)
            return true
        }
        val owner = MessageAccount.current() ?: return false
        val synced = networkRepo.removeFromBlackList(uid).first().getOrNull()?.data != null
        synchronized(PrefManager) {
            PrefManager.saveCloudBlocked(owner, PrefManager.cloudBlocked(owner) - uid)
        }
        return synced && owner.isCurrent()
    }

    suspend fun deleteAllUser() {
        userBlackListDao.deleteAll()
    }

    /** 云端是否已拉黑该用户（POST /v6/user/getLimitAction，form uid）；失败返回 null 由调用方回落本地库 */
    suspend fun isBlockedOnCloud(uid: String): Boolean? {
        return runCatching {
            networkRepo.getLimitAction(uid).first().getOrNull()?.data?.isBlackList
        }.getOrNull()?.let { it == 1 }
    }

    /** Fetch the cloud list without overwriting the shared, explicitly local blacklist. */
    suspend fun fetchCloudUsers(): List<BlackListUser>? {
        val owner = MessageAccount.current() ?: return null
        val all = LinkedHashMap<String, BlackListUser>()
        var page = 1
        while (page <= CLOUD_MAX_PAGE) {
            if (!owner.isCurrent()) return null
            val data = networkRepo.getBlackList(page).first().getOrNull()?.data ?: return null
            val users = data.filter { it.entityType == "user" && !it.uid.isNullOrEmpty() }
            users.forEach { user -> user.uid?.let { all[it] = user } }
            if (users.isEmpty()) break
            page++
        }
        val list = all.values.toList()
        return if (PrefManager.saveCloudBlocked(owner, list.mapNotNull { it.uid }.toSet())) list else null
    }

    /** 启动时把云端黑名单同步到本地镜像（幂等，失败静默） */
    suspend fun syncFromCloud(): Boolean = fetchCloudUsers() != null

    fun loadAllTopicListLive(): LiveData<List<StringEntity>> {
        return topicBlackListDao.loadAllListLive()
    }

    suspend fun insertTopic(topic: StringEntity) {
        topicBlackListDao.insert(topic)
    }

    suspend fun insertTopicList(list: List<StringEntity>) {
        topicBlackListDao.insertList(list)
    }

    suspend fun checkTopic(topic: String): Boolean {
        return withContext(Dispatchers.IO) {
            topicBlackListDao.isContain(topic)
        }
    }

    suspend fun saveTopic(topic: String) {
        if (!topicBlackListDao.isExist(topic)) {
            topicBlackListDao.insert(StringEntity(topic))
        }
    }

    suspend fun deleteTopic(topic: String) {
        topicBlackListDao.delete(topic)
    }

    suspend fun deleteAllTopic() {
        topicBlackListDao.deleteAll()
    }

    companion object {
        /** 单页 20 条时 30 页 = 600 > 云端上限 300，足够翻完 */
        private const val CLOUD_MAX_PAGE = 30
    }

}
