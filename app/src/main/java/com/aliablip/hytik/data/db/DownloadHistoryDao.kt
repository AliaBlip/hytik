package com.aliablip.hytik.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {

    @Query("SELECT * FROM download_history ORDER BY downloadedAtMillis DESC")
    fun getAllHistoryFlow(): Flow<List<DownloadHistoryEntity>>

    @Query("SELECT * FROM download_history WHERE mediaType = :type ORDER BY downloadedAtMillis DESC")
    fun getHistoryByTypeFlow(type: String): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entity: DownloadHistoryEntity)

    @Query("DELETE FROM download_history WHERE id = :itemId")
    suspend fun deleteById(itemId: String)

    @Query("DELETE FROM download_history")
    suspend fun deleteAllHistory()
}
