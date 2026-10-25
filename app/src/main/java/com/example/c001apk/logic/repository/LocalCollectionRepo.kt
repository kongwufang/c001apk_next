package com.example.c001apk.logic.repository

import androidx.lifecycle.LiveData
import com.example.c001apk.logic.dao.LocalCollectionDao
import com.example.c001apk.logic.dao.LocalCollectionFolderDao
import com.example.c001apk.logic.model.LocalCollectionFolder
import com.example.c001apk.logic.model.LocalCollectionEntity
import javax.inject.Inject
import javax.inject.Singleton

/** 本机收藏（2026-10-25 ✓）只负责本机这一半 ✓ 云端那一半走 NetworkRepo ✓ */
@Singleton
class LocalCollectionRepo @Inject constructor(
    private val localCollectionDao: LocalCollectionDao,
    private val localCollectionFolderDao: LocalCollectionFolderDao,
) {

    // ── 本地收藏夹（2026-10-25 ✓）──

    suspend fun loadAllFolders(): List<LocalCollectionFolder> {
        ensureDefaultFolder()
        return localCollectionFolderDao.loadAll()
    }

    /** 默认夹必须存在 ✓ 否则第一次进来列表是空的 ✗ */
    private suspend fun ensureDefaultFolder() {
        if (!localCollectionFolderDao.isExist(LocalCollectionFolder.DEFAULT_ID)) {
            localCollectionFolderDao.insert(
                LocalCollectionFolder(LocalCollectionFolder.DEFAULT_ID, "默认收藏夹", 0L)
            )
        }
    }

    suspend fun createFolder(id: String, title: String) {
        localCollectionFolderDao.insert(LocalCollectionFolder(id, title))
    }

    suspend fun deleteFolder(id: String) {
        localCollectionDao.deleteByFolder(id)
        localCollectionFolderDao.delete(id)
    }

    suspend fun countInFolder(folderId: String): Int = localCollectionDao.countInFolder(folderId)

    /** 这一条是否在**某个**本地夹里 ✓（弹窗上打勾用 ✓） */
    suspend fun isCollectedIn(targetId: String, folderId: String): Boolean {
        return localCollectionDao.loadAll().any { it.targetId == targetId && it.folderId == folderId }
    }
    fun loadAllLive(): LiveData<List<LocalCollectionEntity>> = localCollectionDao.loadAllLive()

    suspend fun loadAll(): List<LocalCollectionEntity> = localCollectionDao.loadAll()

    suspend fun isCollected(id: String): Boolean = localCollectionDao.isExist(id)

    suspend fun collect(id: String, folderId: String = LocalCollectionFolder.DEFAULT_ID) {
        if (id.isEmpty()) return
        localCollectionDao.insert(LocalCollectionEntity(targetId = id, folderId = folderId))
    }

    suspend fun uncollect(id: String) {
        if (id.isEmpty()) return
        localCollectionDao.delete(id)
    }

    suspend fun clear() = localCollectionDao.deleteAll()
}
