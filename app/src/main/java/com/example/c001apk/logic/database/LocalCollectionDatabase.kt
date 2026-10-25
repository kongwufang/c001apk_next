package com.example.c001apk.logic.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.c001apk.logic.dao.LocalCollectionDao
import com.example.c001apk.logic.dao.LocalCollectionFolderDao
import com.example.c001apk.logic.model.LocalCollectionFolder
import com.example.c001apk.logic.model.LocalCollectionEntity

/**
 * 本机收藏库（2026-10-25 ✓）单独一颗 .db ⇒ 不动别人的库 ✓
 *
 * ⚠️ version 1 -> 2：加了 [LocalCollectionFolder] 和 `folderId` 列
 *   这颗库**还没进任何正式版** ✓ 所以直接允许破坏性迁移 ✓
 *   （只是本机缓存 ✗ 丢了重新收藏就行 ✓ 不值得写 migration ✗）
 */
@Database(version = 2, entities = [LocalCollectionEntity::class, LocalCollectionFolder::class])
abstract class LocalCollectionDatabase : RoomDatabase() {
    abstract fun localCollectionDao(): LocalCollectionDao
    abstract fun localCollectionFolderDao(): LocalCollectionFolderDao
}
