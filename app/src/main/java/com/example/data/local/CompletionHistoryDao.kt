package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CompletionHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionHistoryDao {
  @Query("SELECT * FROM completion_history ORDER BY completedAt DESC")
  fun observeHistory(): Flow<List<CompletionHistoryEntity>>

  @Query("SELECT * FROM completion_history ORDER BY completedAt DESC")
  suspend fun getAllHistorySync(): List<CompletionHistoryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHistory(entry: CompletionHistoryEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertHistoryList(entries: List<CompletionHistoryEntity>)

  @Query("DELETE FROM completion_history WHERE id = :id")
  suspend fun deleteHistoryById(id: Long)

  @Query("DELETE FROM completion_history")
  suspend fun clearHistory()
}
