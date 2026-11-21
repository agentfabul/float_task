package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FloatTaskDatabase
import com.example.data.model.CategoryEntity
import com.example.data.model.RecurrencePattern
import com.example.data.model.SubtaskEntity
import com.example.data.model.TaskEntity
import com.example.data.model.TaskPriority
import com.example.data.preferences.OverlayPreferences
import com.example.data.repository.LocalTaskRepository
import com.example.domain.DateTimeUtils
import com.example.domain.TaskDashboardStats
import com.example.domain.TaskFilterEngine
import com.example.domain.TaskOrganizationTab
import com.example.overlay.FloatingOverlayRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FloatTaskRepositoryAndUiTest {

  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var database: FloatTaskDatabase
  private lateinit var repository: LocalTaskRepository

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database =
      Room.inMemoryDatabaseBuilder(context, FloatTaskDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    repository = LocalTaskRepository(database)
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `save task with subtasks, complete recurring task, and verify history and next occurrence`() =
    runBlocking {
      val catId = repository.saveCategory(CategoryEntity(name = "Work", color = 0xFF6366F1))
      val today = DateTimeUtils.startOfDayMillis()

      val taskId =
        repository.saveTask(
          task =
            TaskEntity(
              title = "Standup Sync",
              description = "Review sprint blockers",
              categoryId = catId,
              priority = TaskPriority.HIGH,
              dueDate = today,
              dueTime = "09:30",
              isRecurring = true,
              recurrencePattern = RecurrencePattern.DAILY,
            ),
          subtasks =
            listOf(
              SubtaskEntity(taskId = 0L, title = "Check PRs", sortOrder = 0),
              SubtaskEntity(taskId = 0L, title = "Update Jira", sortOrder = 1),
            ),
        )

      val saved = repository.getTaskWithDetails(taskId)!!
      assertEquals("Standup Sync", saved.task.title)
      assertEquals(2, saved.subtasks.size)

      repository.toggleTaskCompletion(taskId)

      val allTasks = repository.observeAllTasks().first()
      assertEquals(2, allTasks.size) // completed original + newly generated next occurrence
      val nextTask = allTasks.first { !it.task.isCompleted }
      assertEquals("Standup Sync", nextTask.task.title)
      assertTrue(nextTask.task.dueDate!! > today)
      assertEquals(2, nextTask.subtasks.size)
      assertFalse(nextTask.subtasks.any { it.isCompleted })

      val history = repository.observeCompletionHistory().first()
      assertEquals(1, history.size)
      assertEquals("Standup Sync", history.first().taskTitle)
      assertEquals("Work", history.first().categoryName)
    }

  @Test
  fun `backup export and restore preserves tasks categories subtasks and history`() =
    runBlocking {
      val catId = repository.saveCategory(CategoryEntity(name = "Personal", color = 0xFF10B981))
      val taskId =
        repository.saveTask(
          task =
            TaskEntity(
              title = "Prepare Tax Docs",
              priority = TaskPriority.URGENT,
              categoryId = catId,
              dueDate = DateTimeUtils.startOfDayMillis(),
            ),
          subtasks = listOf(SubtaskEntity(taskId = 0L, title = "Download W2")),
        )
      repository.toggleTaskCompletion(taskId)

      val json = repository.exportBackupJson()

      // Clear everything and restore
      database.clearAllTables()
      assertTrue(repository.observeAllTasks().first().isEmpty())

      repository.restoreBackupJson(json)
      val restoredTasks = repository.observeAllTasks().first()
      val restoredCategories = repository.observeCategories().first()
      val restoredHistory = repository.observeCompletionHistory().first()

      assertEquals(1, restoredCategories.size)
      assertEquals("Personal", restoredCategories.first().name)
      assertEquals(1, restoredTasks.size)
      assertEquals("Prepare Tax Docs", restoredTasks.first().task.title)
      assertEquals(1, restoredTasks.first().subtasks.size)
      assertEquals(1, restoredHistory.size)
    }

  @Test
  fun `dashboard stats and filter engine compute pending completed and overdue accurately`() =
    runBlocking {
      val now = System.currentTimeMillis()
      val today = DateTimeUtils.startOfDayMillis(now)
      val yesterday = today - 24L * 60 * 60 * 1000
      val tomorrow = today + 24L * 60 * 60 * 1000

      repository.saveTask(TaskEntity(title = "Today Pending", dueDate = today, priority = TaskPriority.HIGH), emptyList())
      val doneId = repository.saveTask(TaskEntity(title = "Today Done", dueDate = today), emptyList())
      repository.toggleTaskCompletion(doneId)
      repository.saveTask(TaskEntity(title = "Overdue Task", dueDate = yesterday, priority = TaskPriority.URGENT), emptyList())
      repository.saveTask(TaskEntity(title = "Upcoming Task", dueDate = tomorrow), emptyList())

      val tasks = repository.observeAllTasks().first()
      val stats = TaskDashboardStats.compute(tasks, now)

      assertEquals(2, stats.todayTotalCount)
      assertEquals(1, stats.todayCompletedCount)
      assertEquals(50, stats.completionPercentage)
      assertEquals(3, stats.pendingCount)
      assertEquals(1, stats.completedCount)
      assertEquals(1, stats.overdueCount)

      val overdueList = TaskFilterEngine.filter(tasks, TaskOrganizationTab.OVERDUE, nowMillis = now)
      assertEquals(1, overdueList.size)
      assertEquals("Overdue Task", overdueList.first().task.title)

      val upcomingList = TaskFilterEngine.filter(tasks, TaskOrganizationTab.UPCOMING, nowMillis = now)
      assertEquals(1, upcomingList.size)
      assertEquals("Upcoming Task", upcomingList.first().task.title)
    }

  @Test
  fun `floating overlay root switches between bubble and expanded mini panel and supports quick add`() {
    var addedTitle = ""

    composeTestRule.setContent {
      var expanded by remember { mutableStateOf(false) }
      FloatingOverlayRoot(
        isExpanded = expanded,
        isAuthenticated = true,
        tasks = emptyList(),
        pendingCount = 2,
        preferences = OverlayPreferences(isOverlayEnabled = true),
        onBubbleTap = { expanded = true },
        onBubbleDragStart = {},
        onBubbleDrag = { _, _ -> },
        onBubbleDragEnd = {},
        onMinimize = { expanded = false },
        onHideTemporarily = {},
        onCloseOverlay = {},
        onOpenFullApp = {},
        onToggleTask = {},
        onQuickAddTask = { title, _ -> addedTitle = title },
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("floating_bubble").assertIsDisplayed()
    composeTestRule.onNodeWithText("2").assertIsDisplayed()

    composeTestRule.onNodeWithTag("floating_bubble").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("floating_mini_panel").assertIsDisplayed()
    composeTestRule.onNodeWithTag("overlay_quick_add_input").performTextInput("Call designer")
    composeTestRule.onNodeWithTag("overlay_quick_add_submit").performClick()
    composeTestRule.waitForIdle()

    assertEquals("Call designer", addedTitle)
  }

  @Test
  fun `cloud task serialization omits nulls and id and cloud filter computes stats`() {
    val now = System.currentTimeMillis()
    val today = DateTimeUtils.startOfDayMillis(now)
    val yesterday = today - 24L * 60 * 60 * 1000

    val cloudTask =
      com.example.data.remote.CloudTask(
        id = "doc_123",
        userId = "user_abc",
        title = "Sync Design Doc",
        priority = TaskPriority.URGENT.name,
        dueDate = today,
        completed = false,
        recurring = true,
        recurrencePattern = RecurrencePattern.WEEKLY.name,
        subtasks = listOf(
          com.example.data.remote.CloudSubtask(id = "s1", title = "Step 1", completed = false, sortOrder = 0L)
        ),
      )

    val createMap = cloudTask.toCreateMap("user_abc")
    assertFalse(createMap.containsKey("id"))
    assertFalse(createMap.containsKey("categoryId"))
    assertFalse(createMap.containsKey("dueTime"))
    assertFalse(createMap.containsKey("completedAt"))
    assertEquals("user_abc", createMap["userId"])
    assertEquals(false, createMap["completed"])
    assertEquals(true, createMap["recurring"])

    val items = listOf(
      com.example.data.remote.CloudTaskWithCategory(cloudTask, null),
      com.example.data.remote.CloudTaskWithCategory(
        com.example.data.remote.CloudTask(id = "t2", title = "Overdue", dueDate = yesterday, completed = false),
        null,
      ),
      com.example.data.remote.CloudTaskWithCategory(
        com.example.data.remote.CloudTask(id = "t3", title = "Done Today", dueDate = today, completed = true),
        null,
      ),
    )

    val stats = TaskDashboardStats.computeCloud(items, now)
    assertEquals(2, stats.todayTotalCount)
    assertEquals(1, stats.todayCompletedCount)
    assertEquals(50, stats.completionPercentage)
    assertEquals(2, stats.pendingCount)
    assertEquals(1, stats.completedCount)
    assertEquals(1, stats.overdueCount)

    val overdue = TaskFilterEngine.filterCloud(items, TaskOrganizationTab.OVERDUE, nowMillis = now)
    assertEquals(1, overdue.size)
    assertEquals("Overdue", overdue.first().task.title)
  }
}
