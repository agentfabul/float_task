package com.example.data.repository

import com.example.data.model.CategoryEntity
import com.example.data.model.CompletionHistoryEntity
import com.example.data.model.RecurrencePattern
import com.example.data.model.SubtaskEntity
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
  val categories: List<CategoryEntity>,
  val tasks: List<TaskEntity>,
  val subtasks: List<SubtaskEntity>,
  val history: List<CompletionHistoryEntity>,
)

object BackupManager {
  private const val SCHEMA_VERSION = 1

  fun serializeToJson(payload: BackupPayload): String {
    val root = JSONObject()
    root.put("schemaVersion", SCHEMA_VERSION)
    root.put("exportedAt", System.currentTimeMillis())

    val categoriesArray = JSONArray()
    payload.categories.forEach { cat ->
      categoriesArray.put(
        JSONObject().apply {
          put("id", cat.id)
          put("name", cat.name)
          put("color", cat.color)
        }
      )
    }
    root.put("categories", categoriesArray)

    val tasksArray = JSONArray()
    payload.tasks.forEach { task ->
      tasksArray.put(
        JSONObject().apply {
          put("id", task.id)
          put("title", task.title)
          put("description", task.description)
          if (task.categoryId != null) put("categoryId", task.categoryId) else put("categoryId", JSONObject.NULL)
          put("priority", task.priority.name)
          if (task.dueDate != null) put("dueDate", task.dueDate) else put("dueDate", JSONObject.NULL)
          if (task.dueTime != null) put("dueTime", task.dueTime) else put("dueTime", JSONObject.NULL)
          put("isCompleted", task.isCompleted)
          if (task.completedAt != null) put("completedAt", task.completedAt) else put("completedAt", JSONObject.NULL)
          put("createdAt", task.createdAt)
          put("updatedAt", task.updatedAt)
          put("isRecurring", task.isRecurring)
          put("recurrencePattern", task.recurrencePattern.name)
        }
      )
    }
    root.put("tasks", tasksArray)

    val subtasksArray = JSONArray()
    payload.subtasks.forEach { sub ->
      subtasksArray.put(
        JSONObject().apply {
          put("id", sub.id)
          put("taskId", sub.taskId)
          put("title", sub.title)
          put("isCompleted", sub.isCompleted)
          put("sortOrder", sub.sortOrder)
        }
      )
    }
    root.put("subtasks", subtasksArray)

    val historyArray = JSONArray()
    payload.history.forEach { entry ->
      historyArray.put(
        JSONObject().apply {
          put("id", entry.id)
          put("taskId", entry.taskId)
          put("taskTitle", entry.taskTitle)
          if (entry.categoryName != null) put("categoryName", entry.categoryName) else put("categoryName", JSONObject.NULL)
          if (entry.categoryColor != null) put("categoryColor", entry.categoryColor) else put("categoryColor", JSONObject.NULL)
          put("priority", entry.priority.name)
          put("completedAt", entry.completedAt)
        }
      )
    }
    root.put("history", historyArray)

    return root.toString(2)
  }

  fun parseFromJson(jsonText: String): BackupPayload {
    val root = JSONObject(jsonText)
    val categories = mutableListOf<CategoryEntity>()
    val catArr = root.optJSONArray("categories") ?: JSONArray()
    for (i in 0 until catArr.length()) {
      val obj = catArr.getJSONObject(i)
      categories.add(
        CategoryEntity(
          id = obj.getLong("id"),
          name = obj.getString("name"),
          color = obj.getLong("color"),
        )
      )
    }

    val tasks = mutableListOf<TaskEntity>()
    val taskArr = root.optJSONArray("tasks") ?: JSONArray()
    for (i in 0 until taskArr.length()) {
      val obj = taskArr.getJSONObject(i)
      tasks.add(
        TaskEntity(
          id = obj.getLong("id"),
          title = obj.getString("title"),
          description = obj.optString("description", ""),
          categoryId = if (obj.isNull("categoryId")) null else obj.getLong("categoryId"),
          priority = TaskPriority.fromName(obj.optString("priority", "MEDIUM")),
          dueDate = if (obj.isNull("dueDate")) null else obj.getLong("dueDate"),
          dueTime = if (obj.isNull("dueTime")) null else obj.getString("dueTime"),
          isCompleted = obj.optBoolean("isCompleted", false),
          completedAt = if (obj.isNull("completedAt")) null else obj.getLong("completedAt"),
          createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
          updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
          isRecurring = obj.optBoolean("isRecurring", false),
          recurrencePattern = RecurrencePattern.fromName(obj.optString("recurrencePattern", "NONE")),
        )
      )
    }

    val subtasks = mutableListOf<SubtaskEntity>()
    val subArr = root.optJSONArray("subtasks") ?: JSONArray()
    for (i in 0 until subArr.length()) {
      val obj = subArr.getJSONObject(i)
      subtasks.add(
        SubtaskEntity(
          id = obj.getLong("id"),
          taskId = obj.getLong("taskId"),
          title = obj.getString("title"),
          isCompleted = obj.optBoolean("isCompleted", false),
          sortOrder = obj.optInt("sortOrder", i),
        )
      )
    }

    val history = mutableListOf<CompletionHistoryEntity>()
    val histArr = root.optJSONArray("history") ?: JSONArray()
    for (i in 0 until histArr.length()) {
      val obj = histArr.getJSONObject(i)
      history.add(
        CompletionHistoryEntity(
          id = obj.getLong("id"),
          taskId = obj.getLong("taskId"),
          taskTitle = obj.getString("taskTitle"),
          categoryName = if (obj.isNull("categoryName")) null else obj.getString("categoryName"),
          categoryColor = if (obj.isNull("categoryColor")) null else obj.getLong("categoryColor"),
          priority = TaskPriority.fromName(obj.optString("priority", "MEDIUM")),
          completedAt = obj.optLong("completedAt", System.currentTimeMillis()),
        )
      )
    }

    return BackupPayload(
      categories = categories,
      tasks = tasks,
      subtasks = subtasks,
      history = history,
    )
  }
}
