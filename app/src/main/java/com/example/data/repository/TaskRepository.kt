package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.FloatTaskDatabase
import com.example.data.model.CategoryEntity
import com.example.data.model.CompletionHistoryEntity
import com.example.data.model.RecurrencePattern
import com.example.data.model.SubtaskEntity
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority
import com.example.data.model.TaskWithDetails
import com.example.domain.DateTimeUtils
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
  fun observeAllTasks(): Flow<List<TaskWithDetails>>
  fun observePendingCount(): Flow<Int>
  fun observeCategories(): Flow<List<CategoryEntity>>
  fun observeCompletionHistory(): Flow<List<CompletionHistoryEntity>>

  suspend fun getTaskWithDetails(taskId: Long): TaskWithDetails?
  suspend fun saveTask(task: TaskEntity, subtasks: List<SubtaskEntity>): Long
  suspend fun quickAddTask(
    title: String,
    priority: TaskPriority = TaskPriority.MEDIUM,
    dueDate: Long? = DateTimeUtils.startOfDayMillis(),
    categoryId: Long? = null,
  ): Long

  suspend fun toggleTaskCompletion(taskId: Long)
  suspend fun toggleSubtaskCompletion(subtaskId: Long, isCompleted: Boolean)
  suspend fun deleteTask(taskId: Long)

  suspend fun saveCategory(category: CategoryEntity): Long
  suspend fun deleteCategory(category: CategoryEntity)

  suspend fun clearCompletionHistory()
  suspend fun deleteHistoryEntry(id: Long)

  suspend fun exportBackupJson(): String
  suspend fun restoreBackupJson(json: String)
  suspend fun getPendingTasksWithDueDate(): List<TaskEntity>
}

class LocalTaskRepository(
  private val database: FloatTaskDatabase,
  private val onTaskChangedForReminders: (TaskEntity, Boolean) -> Unit = { _, _ -> },
) : TaskRepository {

  private val taskDao = database.taskDao()
  private val categoryDao = database.categoryDao()
  private val subtaskDao = database.subtaskDao()
  private val historyDao = database.completionHistoryDao()

  override fun observeAllTasks(): Flow<List<TaskWithDetails>> =
    taskDao.observeAllTasksWithDetails()

  override fun observePendingCount(): Flow<Int> = taskDao.observePendingCount()

  override fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeCategories()

  override fun observeCompletionHistory(): Flow<List<CompletionHistoryEntity>> =
    historyDao.observeHistory()

  override suspend fun getTaskWithDetails(taskId: Long): TaskWithDetails? =
    taskDao.getTaskWithDetailsById(taskId)

  override suspend fun saveTask(task: TaskEntity, subtasks: List<SubtaskEntity>): Long {
    val now = System.currentTimeMillis()
    val normalizedTask =
      task.copy(
        title = task.title.trim(),
        description = task.description.trim(),
        recurrencePattern =
          if (task.isRecurring && task.recurrencePattern == RecurrencePattern.NONE) {
            RecurrencePattern.DAILY
          } else if (!task.isRecurring) {
            RecurrencePattern.NONE
          } else {
            task.recurrencePattern
          },
        updatedAt = now,
        createdAt = if (task.id == 0L) now else task.createdAt,
      )

    val savedId =
      database.withTransaction {
        val id = taskDao.insertTask(normalizedTask)
        subtaskDao.deleteSubtasksForTask(id)
        if (subtasks.isNotEmpty()) {
          val normalizedSubtasks =
            subtasks
              .filter { it.title.isNotBlank() }
              .mapIndexed { index, sub ->
                sub.copy(id = 0L, taskId = id, title = sub.title.trim(), sortOrder = index)
              }
          if (normalizedSubtasks.isNotEmpty()) {
            subtaskDao.insertSubtasks(normalizedSubtasks)
          }
        }
        id
      }
    val persistedTask = normalizedTask.copy(id = savedId)
    onTaskChangedForReminders(persistedTask, !persistedTask.isCompleted)
    return savedId
  }

  override suspend fun quickAddTask(
    title: String,
    priority: TaskPriority,
    dueDate: Long?,
    categoryId: Long?,
  ): Long {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return 0L
    val now = System.currentTimeMillis()
    val entity =
      TaskEntity(
        title = trimmed,
        priority = priority,
        dueDate = dueDate,
        categoryId = categoryId,
        createdAt = now,
        updatedAt = now,
      )
    val id = taskDao.insertTask(entity)
    onTaskChangedForReminders(entity.copy(id = id), true)
    return id
  }

  override suspend fun toggleTaskCompletion(taskId: Long) {
    var reminderTask: TaskEntity? = null
    var shouldSchedule = false

    database.withTransaction {
      val existing = taskDao.getTaskByIdSync(taskId) ?: return@withTransaction
      val now = System.currentTimeMillis()
      val newCompleted = !existing.isCompleted

      if (newCompleted) {
        val category = existing.categoryId?.let { categoryDao.getCategoryByIdSync(it) }
        historyDao.insertHistory(
          CompletionHistoryEntity(
            taskId = existing.id,
            taskTitle = existing.title,
            categoryName = category?.name,
            categoryColor = category?.color,
            priority = existing.priority,
            completedAt = now,
          )
        )

        if (existing.isRecurring && existing.recurrencePattern != RecurrencePattern.NONE) {
          // Mark current instance completed AND schedule next occurrence automatically
          taskDao.updateTask(
            existing.copy(isCompleted = true, completedAt = now, updatedAt = now)
          )
          val nextDueDate =
            DateTimeUtils.nextRecurrenceDueDate(
              existing.dueDate,
              existing.recurrencePattern,
              now,
            )
          val nextTask =
            existing.copy(
              id = 0L,
              dueDate = nextDueDate,
              isCompleted = false,
              completedAt = null,
              createdAt = now,
              updatedAt = now,
            )
          val newId = taskDao.insertTask(nextTask)
          val existingSubtasks = subtaskDao.getSubtasksForTaskSync(existing.id)
          if (existingSubtasks.isNotEmpty()) {
            subtaskDao.insertSubtasks(
              existingSubtasks.map { sub ->
                sub.copy(id = 0L, taskId = newId, isCompleted = false)
              }
            )
          }
          reminderTask = nextTask.copy(id = newId)
          shouldSchedule = true
        } else {
          val updated = existing.copy(isCompleted = true, completedAt = now, updatedAt = now)
          taskDao.updateTask(updated)
          reminderTask = updated
          shouldSchedule = false
        }
      } else {
        val updated = existing.copy(isCompleted = false, completedAt = null, updatedAt = now)
        taskDao.updateTask(updated)
        reminderTask = updated
        shouldSchedule = true
      }
    }

    reminderTask?.let { onTaskChangedForReminders(it, shouldSchedule) }
  }

  override suspend fun toggleSubtaskCompletion(subtaskId: Long, isCompleted: Boolean) {
    subtaskDao.setSubtaskCompleted(subtaskId, isCompleted)
  }

  override suspend fun deleteTask(taskId: Long) {
    val existing = taskDao.getTaskByIdSync(taskId)
    taskDao.deleteTaskById(taskId)
    if (existing != null) {
      onTaskChangedForReminders(existing, false)
    }
  }

  override suspend fun saveCategory(category: CategoryEntity): Long {
    return if (category.id == 0L) {
      categoryDao.insertCategory(category.copy(name = category.name.trim()))
    } else {
      categoryDao.updateCategory(category.copy(name = category.name.trim()))
      category.id
    }
  }

  override suspend fun deleteCategory(category: CategoryEntity) {
    categoryDao.deleteCategory(category)
  }

  override suspend fun clearCompletionHistory() {
    historyDao.clearHistory()
  }

  override suspend fun deleteHistoryEntry(id: Long) {
    historyDao.deleteHistoryById(id)
  }

  override suspend fun exportBackupJson(): String {
    val payload =
      BackupPayload(
        categories = categoryDao.getAllCategoriesSync(),
        tasks = taskDao.getAllTasksSync(),
        subtasks = subtaskDao.getAllSubtasksSync(),
        history = historyDao.getAllHistorySync(),
      )
    return BackupManager.serializeToJson(payload)
  }

  override suspend fun restoreBackupJson(json: String) {
    val payload = BackupManager.parseFromJson(json)
    database.withTransaction {
      subtaskDao.deleteAllSubtasks()
      taskDao.deleteAllTasks()
      categoryDao.deleteAllCategories()
      historyDao.clearHistory()

      if (payload.categories.isNotEmpty()) {
        categoryDao.insertCategories(payload.categories)
      }
      if (payload.tasks.isNotEmpty()) {
        payload.tasks.forEach { taskDao.insertTask(it) }
      }
      if (payload.subtasks.isNotEmpty()) {
        subtaskDao.insertSubtasks(payload.subtasks)
      }
      if (payload.history.isNotEmpty()) {
        historyDao.insertHistoryList(payload.history)
      }
    }
    payload.tasks.filter { !it.isCompleted }.forEach { task ->
      onTaskChangedForReminders(task, true)
    }
  }

  override suspend fun getPendingTasksWithDueDate(): List<TaskEntity> =
    taskDao.getPendingTasksWithDueDateSync()
}
