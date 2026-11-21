package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SubtaskEntity

@Dao
interface SubtaskDao {
  @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder ASC")
  suspend fun getSubtasksForTaskSync(taskId: Long): List<SubtaskEntity>

  @Query("SELECT * FROM subtasks ORDER BY taskId ASC, sortOrder ASC")
  suspend fun getAllSubtasksSync(): List<SubtaskEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSubtasks(subtasks: List<SubtaskEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSubtask(subtask: SubtaskEntity): Long

  @Update
  suspend fun updateSubtask(subtask: SubtaskEntity)

  @Query("UPDATE subtasks SET isCompleted = :completed WHERE id = :subtaskId")
  suspend fun setSubtaskCompleted(subtaskId: Long, completed: Boolean)

  @Query("DELETE FROM subtasks WHERE taskId = :taskId")
  suspend fun deleteSubtasksForTask(taskId: Long)

  @Query("DELETE FROM subtasks")
  suspend fun deleteAllSubtasks()
}
