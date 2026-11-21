package com.example.domain

import com.example.data.model.TaskPriority
import com.example.data.model.TaskWithDetails
import com.example.data.remote.CloudTaskWithCategory

enum class TaskOrganizationTab(val label: String) {
  TODAY("Today"),
  UPCOMING("Upcoming"),
  ALL("All Tasks"),
  COMPLETED("Completed"),
  OVERDUE("Overdue"),
}

data class TaskDashboardStats(
  val todayTotalCount: Int = 0,
  val todayCompletedCount: Int = 0,
  val pendingCount: Int = 0,
  val completedCount: Int = 0,
  val overdueCount: Int = 0,
  val completionPercentage: Int = 0,
) {
  companion object {
    fun compute(
      tasks: List<TaskWithDetails>,
      nowMillis: Long = System.currentTimeMillis(),
    ): TaskDashboardStats {
      val todayTasks = tasks.filter { DateTimeUtils.isToday(it.task.dueDate, nowMillis) }
      val todayTotal = todayTasks.size
      val todayDone = todayTasks.count { it.task.isCompleted }
      val allPending = tasks.count { !it.task.isCompleted }
      val allDone = tasks.count { it.task.isCompleted }
      val overdue = tasks.count {
        DateTimeUtils.isOverdue(it.task.dueDate, it.task.dueTime, it.task.isCompleted, nowMillis)
      }
      val pct = if (todayTotal > 0) {
        ((todayDone.toFloat() / todayTotal.toFloat()) * 100f).toInt().coerceIn(0, 100)
      } else if (tasks.isNotEmpty()) {
        ((allDone.toFloat() / tasks.size.toFloat()) * 100f).toInt().coerceIn(0, 100)
      } else {
        0
      }
      return TaskDashboardStats(
        todayTotalCount = todayTotal,
        todayCompletedCount = todayDone,
        pendingCount = allPending,
        completedCount = allDone,
        overdueCount = overdue,
        completionPercentage = pct,
      )
    }

    fun computeCloud(
      tasks: List<CloudTaskWithCategory>,
      nowMillis: Long = System.currentTimeMillis(),
    ): TaskDashboardStats {
      val todayTasks = tasks.filter { DateTimeUtils.isToday(it.task.dueDate, nowMillis) }
      val todayTotal = todayTasks.size
      val todayDone = todayTasks.count { it.task.completed }
      val allPending = tasks.count { !it.task.completed }
      val allDone = tasks.count { it.task.completed }
      val overdue = tasks.count {
        DateTimeUtils.isOverdue(it.task.dueDate, it.task.dueTime, it.task.completed, nowMillis)
      }
      val pct = if (todayTotal > 0) {
        ((todayDone.toFloat() / todayTotal.toFloat()) * 100f).toInt().coerceIn(0, 100)
      } else if (tasks.isNotEmpty()) {
        ((allDone.toFloat() / tasks.size.toFloat()) * 100f).toInt().coerceIn(0, 100)
      } else {
        0
      }
      return TaskDashboardStats(
        todayTotalCount = todayTotal,
        todayCompletedCount = todayDone,
        pendingCount = allPending,
        completedCount = allDone,
        overdueCount = overdue,
        completionPercentage = pct,
      )
    }
  }
}

object TaskFilterEngine {
  fun filter(
    tasks: List<TaskWithDetails>,
    tab: TaskOrganizationTab,
    searchQuery: String = "",
    selectedCategoryId: Long? = null,
    selectedPriority: TaskPriority? = null,
    nowMillis: Long = System.currentTimeMillis(),
  ): List<TaskWithDetails> {
    val trimmedQuery = searchQuery.trim().lowercase()
    return tasks
      .filter { item ->
        val task = item.task
        val matchesTab =
          when (tab) {
            TaskOrganizationTab.TODAY ->
              DateTimeUtils.isToday(task.dueDate, nowMillis) ||
                (!task.isCompleted && task.dueDate == null)
            TaskOrganizationTab.UPCOMING ->
              !task.isCompleted && DateTimeUtils.isUpcoming(task.dueDate, nowMillis)
            TaskOrganizationTab.ALL -> true
            TaskOrganizationTab.COMPLETED -> task.isCompleted
            TaskOrganizationTab.OVERDUE ->
              DateTimeUtils.isOverdue(task.dueDate, task.dueTime, task.isCompleted, nowMillis)
          }
        val matchesCategory =
          selectedCategoryId == null || task.categoryId == selectedCategoryId
        val matchesPriority =
          selectedPriority == null || task.priority == selectedPriority
        val matchesQuery =
          trimmedQuery.isEmpty() ||
            task.title.lowercase().contains(trimmedQuery) ||
            task.description.lowercase().contains(trimmedQuery) ||
            (item.category?.name?.lowercase()?.contains(trimmedQuery) == true)
        matchesTab && matchesCategory && matchesPriority && matchesQuery
      }
      .sortedWith(
        compareBy<TaskWithDetails> { it.task.isCompleted }
          .thenByDescending { it.task.priority.sortWeight }
          .thenBy { it.task.dueDate ?: Long.MAX_VALUE }
          .thenByDescending { it.task.createdAt }
      )
  }

  fun filterCloud(
    tasks: List<CloudTaskWithCategory>,
    tab: TaskOrganizationTab,
    searchQuery: String = "",
    selectedCategoryId: String? = null,
    selectedPriority: TaskPriority? = null,
    nowMillis: Long = System.currentTimeMillis(),
  ): List<CloudTaskWithCategory> {
    val trimmedQuery = searchQuery.trim().lowercase()
    return tasks
      .filter { item ->
        val task = item.task
        val matchesTab =
          when (tab) {
            TaskOrganizationTab.TODAY ->
              DateTimeUtils.isToday(task.dueDate, nowMillis) ||
                (!task.completed && task.dueDate == null)
            TaskOrganizationTab.UPCOMING ->
              !task.completed && DateTimeUtils.isUpcoming(task.dueDate, nowMillis)
            TaskOrganizationTab.ALL -> true
            TaskOrganizationTab.COMPLETED -> task.completed
            TaskOrganizationTab.OVERDUE ->
              DateTimeUtils.isOverdue(task.dueDate, task.dueTime, task.completed, nowMillis)
          }
        val matchesCategory =
          selectedCategoryId == null || task.categoryId == selectedCategoryId
        val matchesPriority =
          selectedPriority == null || task.priorityEnum == selectedPriority
        val matchesQuery =
          trimmedQuery.isEmpty() ||
            task.title.lowercase().contains(trimmedQuery) ||
            task.description.lowercase().contains(trimmedQuery) ||
            (item.category?.name?.lowercase()?.contains(trimmedQuery) == true)
        matchesTab && matchesCategory && matchesPriority && matchesQuery
      }
      .sortedWith(
        compareBy<CloudTaskWithCategory> { it.task.completed }
          .thenByDescending { it.task.priorityEnum.sortWeight }
          .thenBy { it.task.dueDate ?: Long.MAX_VALUE }
          .thenByDescending { it.task.createdAt?.toDate()?.time ?: 0L }
      )
  }

  fun todayAndUrgentCloudForOverlay(
    tasks: List<CloudTaskWithCategory>,
    includeAllPending: Boolean = false,
    nowMillis: Long = System.currentTimeMillis(),
  ): List<CloudTaskWithCategory> {
    return tasks
      .filter { item ->
        val task = item.task
        if (task.completed) return@filter false
        if (includeAllPending) return@filter true
        DateTimeUtils.isToday(task.dueDate, nowMillis) ||
          DateTimeUtils.isOverdue(task.dueDate, task.dueTime, false, nowMillis) ||
          task.dueDate == null
      }
      .sortedWith(
        compareByDescending<CloudTaskWithCategory> { it.task.priorityEnum.sortWeight }
          .thenBy { it.task.dueDate ?: Long.MAX_VALUE }
          .thenByDescending { it.task.createdAt?.toDate()?.time ?: 0L }
      )
  }
}
