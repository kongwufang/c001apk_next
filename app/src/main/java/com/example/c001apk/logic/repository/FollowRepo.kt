package com.example.c001apk.logic.repository

import androidx.lifecycle.LiveData
import com.example.c001apk.logic.dao.FollowUserDao
import com.example.c001apk.logic.model.FollowUserEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本机关注关系（2026-10-25 ✓）
 *
 * 只负责**本机**这一半 ✓
 * 云端那一半仍然走 `NetworkRepo.postFollowUnFollow` ✓
 * 两者怎么配合由调用方按 [com.example.c001apk.util.PrefManager.saveFollowToCloud] 决定 ✓
 */
@Singleton
class FollowRepo @Inject constructor(
    private val followUserDao: FollowUserDao,
) {

    fun loadAllLive(): LiveData<List<FollowUserEntity>> = followUserDao.loadAllLive()

    suspend fun loadAll(): List<FollowUserEntity> = followUserDao.loadAll()

    suspend fun isFollowed(uid: String): Boolean = followUserDao.isExist(uid)

    /** 记一条关注 ✓ 重复调用不会出现两条（uid 是主键 ✓ REPLACE ✓） */
    suspend fun follow(uid: String, username: String = "", avatar: String = "") {
        if (uid.isEmpty()) return
        followUserDao.insert(FollowUserEntity(uid = uid, username = username, avatar = avatar))
    }

    suspend fun unfollow(uid: String) {
        if (uid.isEmpty()) return
        followUserDao.delete(uid)
    }

    suspend fun clear() = followUserDao.deleteAll()
}
