package com.example.c001apk.logic.repository

import androidx.lifecycle.LiveData
import com.example.c001apk.logic.dao.LocalCollectionDao
import com.example.c001apk.logic.model.LocalCollectionEntity
import javax.inject.Inject
import javax.inject.Singleton

/** 本机收藏（2026-10-25 ✓）只负责本机这一半 ✓ 云端那一半走 NetworkRepo ✓ */
@Singleton
class LocalCollectionRepo @Inject constructor(
    private val localCollectionDao: LocalCollectionDao,
) {
    fun loadAllLive(): LiveData<List<LocalCollectionEntity>> = localCollectionDao.loadAllLive()

    suspend fun loadAll(): List<LocalCollectionEntity> = localCollectionDao.loadAll()

    suspend fun isCollected(id: String): Boolean = localCollectionDao.isExist(id)

    suspend fun collect(id: String) {
        if (id.isEmpty()) return
        localCollectionDao.insert(LocalCollectionEntity(targetId = id))
    }

    suspend fun uncollect(id: String) {
        if (id.isEmpty()) return
        localCollectionDao.delete(id)
    }

    suspend fun clear() = localCollectionDao.deleteAll()
}
