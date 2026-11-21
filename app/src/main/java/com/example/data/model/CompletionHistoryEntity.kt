package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completion_history")
data class CompletionHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val taskId: Long,
  val taskTitle: String,
  val categoryName: String?,
  val categoryColor: Long?,
  val priority: TaskPriority,
  val completedAt: Long = System.currentTimeMillis(),
)
