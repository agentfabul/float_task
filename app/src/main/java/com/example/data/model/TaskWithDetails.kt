package com.example.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithDetails(
  @Embedded val task: TaskEntity,
  @Relation(parentColumn = "categoryId", entityColumn = "id")
  val category: CategoryEntity?,
  @Relation(parentColumn = "id", entityColumn = "taskId")
  val subtasks: List<SubtaskEntity> = emptyList(),
) {
  val sortedSubtasks: List<SubtaskEntity>
    get() = subtasks.sortedBy { it.sortOrder }
}
