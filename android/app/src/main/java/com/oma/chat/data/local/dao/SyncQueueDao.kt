package com.oma.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.oma.chat.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue WHERE ownerUserId = :ownerUserId ORDER BY createdAt ASC")
    fun getPendingQueue(ownerUserId: String): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE ownerUserId = :ownerUserId ORDER BY createdAt ASC")
    suspend fun getPendingQueueList(ownerUserId: String): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToQueue(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1 WHERE tempId = :tempId")
    suspend fun incrementRetryCount(tempId: String)

    @Query("DELETE FROM sync_queue WHERE tempId = :tempId")
    suspend fun removeFromQueue(tempId: String)

    @Query("DELETE FROM sync_queue WHERE ownerUserId = :ownerUserId")
    suspend fun clearQueueForUser(ownerUserId: String)
}
