package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.RecurrencePattern
import com.example.data.model.TaskPriority
import com.example.data.preferences.OverlayBubbleSize
import com.example.data.preferences.OverlayPreferences
import com.example.data.preferences.PreferencesRepository
import com.example.data.preferences.ThemeMode
import com.example.data.remote.CloudCategory
import com.example.data.remote.CloudHistoryEntry
import com.example.data.remote.CloudSubtask
import com.example.data.remote.CloudTask
import com.example.data.remote.CloudTaskWithCategory
import com.example.data.remote.FirestoreSyncRepository
import com.example.domain.DateTimeUtils
import com.example.domain.TaskDashboardStats
import com.example.domain.TaskFilterEngine
import com.example.domain.TaskOrganizationTab
import com.example.notifications.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CloudStreamResult<out T> {
  data object Loading : CloudStreamResult<Nothing>
  data class Success<T>(val data: T) : CloudStreamResult<T>
  data class Error(val message: String) : CloudStreamResult<Nothing>
}

data class TaskUiState(
  val allTasks: List<CloudTaskWithCategory> = emptyList(),
  val todayTasks: List<CloudTaskWithCategory> = emptyList(),
  val upcomingDeadlines: List<CloudTaskWithCategory> = emptyList(),
  val filteredTasks: List<CloudTaskWithCategory> = emptyList(),
  val categories: List<CloudCategory> = emptyList(),
  val history: List<CloudHistoryEntry> = emptyList(),
  val stats: TaskDashboardStats = TaskDashboardStats(),
  val selectedTab: TaskOrganizationTab = TaskOrganizationTab.TODAY,
  val searchQuery: String = "",
  val selectedCategoryId: String? = null,
  val selectedPriority: TaskPriority? = null,
  val preferences: OverlayPreferences = OverlayPreferences(),
  val isLoading: Boolean = true,
  val cloudError: String? = null,
)

class MainViewModel(
  private val repository: FirestoreSyncRepository,
  private val preferencesRepository: PreferencesRepository,
  private val reminderScheduler: ReminderScheduler,
  private val currentUserId: String,
) : ViewModel() {

  private val selectedTab = MutableStateFlow(TaskOrganizationTab.TODAY)
  private val searchQuery = MutableStateFlow("")
  private val selectedCategoryId = MutableStateFlow<String?>(null)
  private val selectedPriority = MutableStateFlow<TaskPriority?>(null)

  private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
  val events: SharedFlow<String> = _events.asSharedFlow()

  private val filterState =
    combine(selectedTab, searchQuery, selectedCategoryId, selectedPriority) { tab, query, catId, prio ->
      FilterParams(tab, query, catId, prio)
    }

  private val tasksStream =
    repository.observeTasks(currentUserId)
      .map<List<CloudTask>, CloudStreamResult<List<CloudTask>>> { CloudStreamResult.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing tasks", rootError)
        emit(CloudStreamResult.Error(rootError.localizedMessage ?: "Failed to sync tasks"))
      }

  private val categoriesStream =
    repository.observeCategories(currentUserId)
      .map<List<CloudCategory>, CloudStreamResult<List<CloudCategory>>> { CloudStreamResult.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing categories", rootError)
        emit(CloudStreamResult.Error(rootError.localizedMessage ?: "Failed to sync categories"))
      }

  private val historyStream =
    repository.observeHistory(currentUserId)
      .map<List<CloudHistoryEntry>, CloudStreamResult<List<CloudHistoryEntry>>> { CloudStreamResult.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing history", rootError)
        emit(CloudStreamResult.Error(rootError.localizedMessage ?: "Failed to sync history"))
      }

  val uiState: StateFlow<TaskUiState> =
    combine(
      tasksStream,
      categoriesStream,
      historyStream,
      preferencesRepository.preferences,
      filterState,
    ) { tasksRes, catsRes, histRes, prefs, filter ->
      val errorMsg =
        listOf(tasksRes, catsRes, histRes)
          .filterIsInstance<CloudStreamResult.Error>()
          .firstOrNull()
          ?.message

      val isLoading =
        tasksRes is CloudStreamResult.Loading || catsRes is CloudStreamResult.Loading

      val rawTasks = (tasksRes as? CloudStreamResult.Success)?.data ?: emptyList()
      val categories = (catsRes as? CloudStreamResult.Success)?.data ?: emptyList()
      val history = (histRes as? CloudStreamResult.Success)?.data ?: emptyList()

      val categoryMap = categories.associateBy { it.id }
      val tasks = rawTasks.map { CloudTaskWithCategory(it, it.categoryId?.let { id -> categoryMap[id] }) }

      val now = System.currentTimeMillis()
      val stats = TaskDashboardStats.computeCloud(tasks, now)
      val todayList =
        TaskFilterEngine.filterCloud(
          tasks = tasks,
          tab = TaskOrganizationTab.TODAY,
          nowMillis = now,
        )
      val upcomingDeadlines =
        tasks
          .filter {
            !it.task.completed &&
              it.task.dueDate != null &&
              !DateTimeUtils.isOverdue(it.task.dueDate, it.task.dueTime, false, now)
          }
          .sortedBy { DateTimeUtils.combineDateAndTime(it.task.dueDate, it.task.dueTime) ?: Long.MAX_VALUE }
          .take(5)

      val filtered =
        TaskFilterEngine.filterCloud(
          tasks = tasks,
          tab = filter.tab,
          searchQuery = filter.query,
          selectedCategoryId = filter.categoryId,
          selectedPriority = filter.priority,
          nowMillis = now,
        )

      TaskUiState(
        allTasks = tasks,
        todayTasks = todayList,
        upcomingDeadlines = upcomingDeadlines,
        filteredTasks = filtered,
        categories = categories,
        history = history,
        stats = stats,
        selectedTab = filter.tab,
        searchQuery = filter.query,
        selectedCategoryId = filter.categoryId,
        selectedPriority = filter.priority,
        preferences = prefs,
        isLoading = isLoading && errorMsg == null,
        cloudError = errorMsg,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = TaskUiState(),
    )

  fun selectOrganizationTab(tab: TaskOrganizationTab) {
    selectedTab.value = tab
  }

  fun updateSearchQuery(query: String) {
    searchQuery.value = query
  }

  fun selectCategoryFilter(categoryId: String?) {
    selectedCategoryId.value = if (selectedCategoryId.value == categoryId) null else categoryId
  }

  fun selectPriorityFilter(priority: TaskPriority?) {
    selectedPriority.value = if (selectedPriority.value == priority) null else priority
  }

  fun quickAddTask(
    title: String,
    priority: TaskPriority = TaskPriority.MEDIUM,
    categoryId: String? = null,
  ) {
    if (title.isBlank()) return
    viewModelScope.launch {
      repository
        .quickAddTask(
          title = title,
          priority = priority,
          dueDate = DateTimeUtils.startOfDayMillis(),
          categoryId = categoryId,
        )
        .onSuccess { _events.tryEmit("Task synced to Today") }
        .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to add task") }
    }
  }

  fun saveTask(
    existingTask: CloudTask?,
    title: String,
    description: String,
    categoryId: String?,
    priority: TaskPriority,
    dueDate: Long?,
    dueTime: String?,
    isRecurring: Boolean,
    recurrencePattern: RecurrencePattern,
    subtasks: List<CloudSubtask>,
  ) {
    if (title.isBlank()) return
    viewModelScope.launch {
      val base = existingTask ?: CloudTask(userId = currentUserId)
      val updated =
        base.copy(
          title = title.trim(),
          description = description.trim(),
          categoryId = categoryId,
          priority = priority.name,
          dueDate = dueDate,
          dueTime = dueTime,
          recurring = isRecurring,
          recurrencePattern = if (isRecurring) recurrencePattern.name else RecurrencePattern.NONE.name,
          subtasks = subtasks,
        )
      repository
        .saveTask(updated)
        .onSuccess {
          _events.tryEmit(if (existingTask == null) "Task created & synced" else "Task updated")
        }
        .onFailure {
          _events.tryEmit(it.localizedMessage ?: "Failed to save task")
        }
    }
  }

  fun toggleTaskCompletion(taskId: String) {
    viewModelScope.launch {
      repository.toggleTaskCompletion(taskId).onFailure {
        _events.tryEmit(it.localizedMessage ?: "Failed to update task")
      }
    }
  }

  fun toggleSubtaskCompletion(taskId: String, subtaskId: String, isCompleted: Boolean) {
    viewModelScope.launch {
      repository.toggleSubtaskCompletion(taskId, subtaskId, isCompleted).onFailure {
        _events.tryEmit(it.localizedMessage ?: "Failed to update checklist item")
      }
    }
  }

  fun deleteTask(taskId: String) {
    viewModelScope.launch {
      repository
        .deleteTask(taskId)
        .onSuccess { _events.tryEmit("Task deleted") }
        .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to delete task") }
    }
  }

  fun saveCategory(existing: CloudCategory?, name: String, color: Long) {
    if (name.isBlank()) return
    viewModelScope.launch {
      val cat =
        existing?.copy(name = name.trim(), color = color)
          ?: CloudCategory(userId = currentUserId, name = name.trim(), color = color)
      repository
        .saveCategory(cat)
        .onSuccess { _events.tryEmit("Category saved") }
        .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to save category") }
    }
  }

  fun deleteCategory(category: CloudCategory) {
    viewModelScope.launch {
      if (selectedCategoryId.value == category.id) {
        selectedCategoryId.value = null
      }
      repository
        .deleteCategory(category.id)
        .onSuccess { _events.tryEmit("Category removed") }
        .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to delete category") }
    }
  }

  fun clearHistory() {
    viewModelScope.launch {
      repository
        .clearHistory()
        .onSuccess { _events.tryEmit("Completion history cleared") }
        .onFailure { _events.tryEmit(it.localizedMessage ?: "Failed to clear history") }
    }
  }

  fun setOverlayEnabled(enabled: Boolean) {
    viewModelScope.launch { preferencesRepository.setOverlayEnabled(enabled) }
  }

  fun setOverlayTemporarilyHidden(hidden: Boolean) {
    viewModelScope.launch { preferencesRepository.setOverlayTemporarilyHidden(hidden) }
  }

  fun setOverlayOpacity(opacity: Float) {
    viewModelScope.launch { preferencesRepository.setOverlayOpacity(opacity) }
  }

  fun setBubbleSize(size: OverlayBubbleSize) {
    viewModelScope.launch { preferencesRepository.setBubbleSize(size) }
  }

  fun setAccentColor(colorHex: Long) {
    viewModelScope.launch { preferencesRepository.setAccentColor(colorHex) }
  }

  fun setShowPendingBadge(show: Boolean) {
    viewModelScope.launch { preferencesRepository.setShowPendingBadge(show) }
  }

  fun resetOverlayPosition() {
    viewModelScope.launch {
      preferencesRepository.resetOverlayPosition()
      _events.tryEmit("Floating bubble position reset")
    }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
  }

  fun setDailyReminder(enabled: Boolean, hour: Int, minute: Int) {
    viewModelScope.launch {
      preferencesRepository.setDailyReminder(enabled, hour, minute)
      reminderScheduler.scheduleDailyReminder(enabled, hour, minute)
      _events.tryEmit(if (enabled) "Daily reminder scheduled" else "Daily reminder turned off")
    }
  }

  suspend fun exportBackupJson(): Result<String> = repository.exportBackupJson()

  fun restoreBackupJson(json: String) {
    viewModelScope.launch {
      repository
        .restoreBackupJson(json)
        .onSuccess { _events.tryEmit("Backup restored to cloud sync") }
        .onFailure { _events.tryEmit("Could not restore backup: invalid file format") }
    }
  }

  fun emitMessage(message: String) {
    _events.tryEmit(message)
  }

  private data class FilterParams(
    val tab: TaskOrganizationTab,
    val query: String,
    val categoryId: String?,
    val priority: TaskPriority?,
  )

  private companion object {
    const val TAG = "MainVM"
    const val STOP_TIMEOUT_MILLIS = 5_000L
  }

  class Factory(
    private val repository: FirestoreSyncRepository,
    private val preferencesRepository: PreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
    private val currentUserId: String,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return MainViewModel(repository, preferencesRepository, reminderScheduler, currentUserId) as T
    }
  }
}
