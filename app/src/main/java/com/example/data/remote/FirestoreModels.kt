package com.example.data.remote

import androidx.annotation.Keep
import com.example.data.model.RecurrencePattern
import com.example.data.model.TaskPriority
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CloudSubtask(
  val id: String = "",
  val title: String = "",
  val completed: Boolean = false,
  val sortOrder: Long = 0L,
) {
  fun toMap(): Map<String, Any> =
    mapOf(
      "id" to id,
      "title" to title.take(120),
      "completed" to completed,
      "sortOrder" to sortOrder,
    )
}

@Keep
@IgnoreExtraProperties
data class CloudCategory(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "",
  val color: Long = 0xFF6366F1L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toCreateMap(uid: String): Map<String, Any> =
    mapOf(
      "userId" to uid,
      "name" to name.trim().take(40),
      "color" to color,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )

  fun toUpdateMap(): Map<String, Any> =
    mapOf(
      "name" to name.trim().take(40),
      "color" to color,
      "updatedAt" to FieldValue.serverTimestamp(),
    )
}

@Keep
@IgnoreExtraProperties
data class CloudTask(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val description: String = "",
  val categoryId: String? = null,
  val priority: String = TaskPriority.MEDIUM.name,
  val dueDate: Long? = null,
  val dueTime: String? = null,
  val completed: Boolean = false,
  val completedAt: Long? = null,
  val recurring: Boolean = false,
  val recurrencePattern: String = RecurrencePattern.NONE.name,
  val subtasks: List<CloudSubtask> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val priorityEnum: TaskPriority
    get() = TaskPriority.fromName(priority)

  val recurrenceEnum: RecurrencePattern
    get() = RecurrencePattern.fromName(recurrencePattern)

  val sortedSubtasks: List<CloudSubtask>
    get() = subtasks.sortedBy { it.sortOrder }

  fun toCreateMap(uid: String): Map<String, Any> {
    val cleanSubtasks =
      subtasks
        .filter { it.title.isNotBlank() }
        .take(30)
        .mapIndexed { idx, sub -> sub.copy(sortOrder = idx.toLong()).toMap() }
    val raw =
      mutableMapOf<String, Any?>(
        "userId" to uid,
        "title" to title.trim().take(200),
        "description" to description.trim().take(2000),
        "categoryId" to categoryId?.takeIf { it.isNotBlank() },
        "priority" to priorityEnum.name,
        "dueDate" to dueDate,
        "dueTime" to dueTime?.takeIf { it.isNotBlank() }?.take(10),
        "completed" to completed,
        "completedAt" to completedAt,
        "recurring" to recurring,
        "recurrencePattern" to
          if (recurring && recurrenceEnum == RecurrencePattern.NONE) RecurrencePattern.DAILY.name
          else if (!recurring) RecurrencePattern.NONE.name
          else recurrenceEnum.name,
        "subtasks" to cleanSubtasks,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }

  fun toUpdateMap(): Map<String, Any> {
    val cleanSubtasks =
      subtasks
        .filter { it.title.isNotBlank() }
        .take(30)
        .mapIndexed { idx, sub -> sub.copy(sortOrder = idx.toLong()).toMap() }
    val raw =
      mutableMapOf<String, Any?>(
        "title" to title.trim().take(200),
        "description" to description.trim().take(2000),
        "categoryId" to categoryId?.takeIf { it.isNotBlank() },
        "priority" to priorityEnum.name,
        "dueDate" to dueDate,
        "dueTime" to dueTime?.takeIf { it.isNotBlank() }?.take(10),
        "completed" to completed,
        "completedAt" to completedAt,
        "recurring" to recurring,
        "recurrencePattern" to
          if (recurring && recurrenceEnum == RecurrencePattern.NONE) RecurrencePattern.DAILY.name
          else if (!recurring) RecurrencePattern.NONE.name
          else recurrenceEnum.name,
        "subtasks" to cleanSubtasks,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    // Explicitly remove cleared optional fields using FieldValue.delete() if needed
    if (categoryId.isNullOrBlank()) raw["categoryId"] = FieldValue.delete()
    if (dueDate == null) raw["dueDate"] = FieldValue.delete()
    if (dueTime.isNullOrBlank()) raw["dueTime"] = FieldValue.delete()
    if (completedAt == null) raw["completedAt"] = FieldValue.delete()
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }
}

@Keep
@IgnoreExtraProperties
data class CloudTaskWithCategory(
  val task: CloudTask,
  val category: CloudCategory?,
)

@Keep
@IgnoreExtraProperties
data class CloudHistoryEntry(
  @DocumentId val id: String = "",
  val userId: String = "",
  val taskId: String = "",
  val taskTitle: String = "",
  val categoryName: String? = null,
  val categoryColor: Long? = null,
  val priority: String = TaskPriority.MEDIUM.name,
  val completedAtMillis: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
) {
  val priorityEnum: TaskPriority
    get() = TaskPriority.fromName(priority)

  fun toCreateMap(uid: String): Map<String, Any> {
    val raw =
      mapOf<String, Any?>(
        "userId" to uid,
        "taskId" to taskId.take(128),
        "taskTitle" to taskTitle.trim().take(200),
        "categoryName" to categoryName?.trim()?.take(40)?.takeIf { it.isNotEmpty() },
        "categoryColor" to categoryColor,
        "priority" to priorityEnum.name,
        "completedAtMillis" to completedAtMillis,
        "createdAt" to FieldValue.serverTimestamp(),
      )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }
}
