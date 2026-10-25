package com.example.c001apk.logic.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.c001apk.logic.model.OfflineFeedEntity
import com.example.c001apk.logic.model.OfflineFeedSummary

@Dao
interface OfflineCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: OfflineFeedEntity)

    /** 备份还原用：批量灌回去 ✓（按 fid REPLACE ✓ 重复导入不会炸 ✓）*/
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: List<OfflineFeedEntity>)

    /**
     * 列表页直接订这个：存 / 删都会自己推一次，不用手动刷新。
     *
     * 只挑展示要用的列，`feedJson` / `replyJson` 留在库里（见 [OfflineFeedSummary]）。
     */
    @Query(
        """
        SELECT fid, uid, uname, avatar, title, message, cover, device, dateline,
               savedReplyCount, savedAt
        FROM OfflineFeedEntity
        ORDER BY savedAt DESC
        """
    )
    fun loadAllLive(): LiveData<List<OfflineFeedSummary>>

    @Query("SELECT * FROM OfflineFeedEntity ORDER BY savedAt DESC")
    suspend fun loadAll(): List<OfflineFeedEntity>

    @Query("SELECT * FROM OfflineFeedEntity WHERE fid = :fid LIMIT 1")
    suspend fun get(fid: String): OfflineFeedEntity?

    @Query("DELETE FROM OfflineFeedEntity WHERE fid = :fid")
    suspend fun delete(fid: String)

    @Query("DELETE FROM OfflineFeedEntity")
    suspend fun deleteAll()
}
