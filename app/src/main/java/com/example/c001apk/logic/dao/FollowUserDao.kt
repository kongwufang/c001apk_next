package com.example.c001apk.logic.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.c001apk.logic.model.FollowUserEntity

@Dao
interface FollowUserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FollowUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: List<FollowUserEntity>)

    /** 关注列表：最近关注的排前面 ✓ */
    @Query("SELECT * FROM FollowUserEntity ORDER BY dateline DESC")
    suspend fun loadAll(): List<FollowUserEntity>

    @Query("SELECT * FROM FollowUserEntity ORDER BY dateline DESC")
    fun loadAllLive(): LiveData<List<FollowUserEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM FollowUserEntity WHERE uid = :uid)")
    suspend fun isExist(uid: String): Boolean

    @Query("DELETE FROM FollowUserEntity WHERE uid = :uid")
    suspend fun delete(uid: String)

    @Query("DELETE FROM FollowUserEntity")
    suspend fun deleteAll()
}
