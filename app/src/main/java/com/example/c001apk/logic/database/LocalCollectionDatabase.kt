package com.example.c001apk.logic.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.c001apk.logic.dao.LocalCollectionDao
import com.example.c001apk.logic.model.LocalCollectionEntity

/** 本机收藏库（2026-10-25 ✓）单独一颗 .db ⇒ 不动别人的库 ⇒ 不用 migration ✓ */
@Database(version = 1, entities = [LocalCollectionEntity::class])
abstract class LocalCollectionDatabase : RoomDatabase() {
    abstract fun localCollectionDao(): LocalCollectionDao
}
