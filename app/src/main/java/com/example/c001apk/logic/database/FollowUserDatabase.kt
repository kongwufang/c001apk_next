package com.example.c001apk.logic.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.c001apk.logic.dao.FollowUserDao
import com.example.c001apk.logic.model.FollowUserEntity

/**
 * 本机「关注」关系库（2026-10-25 ✓）
 *
 * 单独一个 .db ✓ 跟 UserBlackListDatabase 一样各管一摊 ✓
 * 不动别人的库 ⇒ 不用写 migration ✓ 风险最低 ✓
 */
@Database(version = 1, entities = [FollowUserEntity::class])
abstract class FollowUserDatabase : RoomDatabase() {
    abstract fun followUserDao(): FollowUserDao
}
