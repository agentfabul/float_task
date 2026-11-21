package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.TaskEntity
import com.example.data.model.TaskWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
  @Transaction
  @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueDate ASC, createdAt DESC")
  fun observeAllTasksWithDetails(): Flow<List<TaskWithDetails>>

  @Transaction
  @Query("SELECT * FROM tasks WHERE id = :taskId")
  suspend fun getTaskWithDetailsById(taskId: Long): TaskWithDetails?

  @Query("SELECT * FROM tasks ORDER BY id ASC")
  suspend fun getAllTasksSync(): List<TaskEntity>

  @Query("SELECT * FROM tasks WHERE id = :taskId")
  suspend fun getTaskByIdSync(taskId: Long): TaskEntity?

  @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDate IS NOT NULL")
  suspend fun getPendingTasksWithDueDateSync(): List<TaskEntity>

  @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0")
  fun observePendingCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTask(task: TaskEntity): Long

  @Update
  suspend fun updateTask(task: TaskEntity)

  @Delete
  suspend fun deleteTask(task: TaskEntity)

  @Query("DELETE FROM tasks WHERE id = :taskId")
  suspend fun deleteTaskById(taskId: Long)

  @Query("DELETE FROM tasks")
  suspend fun deleteAllTasks()
}
