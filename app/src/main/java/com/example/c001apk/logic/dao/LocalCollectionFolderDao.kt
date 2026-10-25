package com.example.c001apk.logic.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.c001apk.logic.model.LocalCollectionFolder

@Dao
interface LocalCollectionFolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: LocalCollectionFolder)

    @Query("SELECT * FROM LocalCollectionFolder ORDER BY dateline ASC")
    suspend fun loadAll(): List<LocalCollectionFolder>

    @Query("SELECT * FROM LocalCollectionFolder ORDER BY dateline ASC")
    fun loadAllLive(): LiveData<List<LocalCollectionFolder>>

    @Query("SELECT EXISTS(SELECT 1 FROM LocalCollectionFolder WHERE id = :id)")
    suspend fun isExist(id: String): Boolean

    @Query("DELETE FROM LocalCollectionFolder WHERE id = :id")
    suspend fun delete(id: String)
}
