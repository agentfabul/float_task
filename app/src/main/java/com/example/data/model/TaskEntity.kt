package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "tasks",
  foreignKeys = [
    ForeignKey(
      entity = CategoryEntity::class,
      parentColumns = ["id"],
      childColumns = ["categoryId"],
      onDelete = ForeignKey.SET_NULL,
    )
  ],
  indices = [Index(value = ["categoryId"]), Index(value = ["dueDate"]), Index(value = ["isCompleted"])],
)
data class TaskEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val title: String,
  val description: String = "",
  val categoryId: Long? = null,
  val priority: TaskPriority = TaskPriority.MEDIUM,
  val dueDate: Long? = null,
  val dueTime: String? = null, // "HH:mm"
  val isCompleted: Boolean = false,
  val completedAt: Long? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val isRecurring: Boolean = false,
  val recurrencePattern: RecurrencePattern = RecurrencePattern.NONE,
)
