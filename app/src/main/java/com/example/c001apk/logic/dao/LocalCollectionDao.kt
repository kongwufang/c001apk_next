package com.example.c001apk.logic.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.c001apk.logic.model.LocalCollectionEntity

@Dao
interface LocalCollectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocalCollectionEntity)

    @Query("SELECT * FROM LocalCollectionEntity ORDER BY dateline DESC")
    suspend fun loadAll(): List<LocalCollectionEntity>

    @Query("SELECT * FROM LocalCollectionEntity ORDER BY dateline DESC")
    fun loadAllLive(): LiveData<List<LocalCollectionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM LocalCollectionEntity WHERE targetId = :id)")
    suspend fun isExist(id: String): Boolean

    @Query("DELETE FROM LocalCollectionEntity WHERE targetId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM LocalCollectionEntity")
    suspend fun deleteAll()
}
