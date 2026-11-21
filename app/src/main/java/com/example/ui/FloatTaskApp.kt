package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.FloatTaskApplication
import com.example.R
import com.example.auth.GoogleAuthManager
import com.example.auth.authStateFlow
import com.example.data.preferences.OverlayPreferences
import com.example.data.remote.CloudTaskWithCategory
import com.example.notifications.NotificationHelper
import com.example.overlay.OverlayActions
import com.example.overlay.OverlayPermissionHelper
import com.example.ui.components.CategoryManagerDialog
import com.example.ui.components.NotificationPermissionRationaleDialog
import com.example.ui.components.OverlayPermissionRationaleDialog
import com.example.ui.components.TaskEditorSheet
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.OverlayControlScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.theme.FloatTaskTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class AppDestination(
  val route: String,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Rounded.Home, Icons.Outlined.Home),
  TASKS("tasks", R.string.nav_tasks, Icons.Rounded.CheckCircle, Icons.Outlined.CheckCircle),
  OVERLAY("overlay", R.string.nav_overlay, Icons.Rounded.Layers, Icons.Outlined.Layers),
  HISTORY("history", R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History),
}

@Composable
fun FloatTaskRootContent() {
  val context = LocalContext.current
  val app = context.applicationContext as FloatTaskApplication
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val scope = rememberCoroutineScope()

  val prefs by app.preferencesRepository.preferences.collectAsStateWithLifecycle(
    initialValue = OverlayPreferences()
  )
  val currentUser by remember { Firebase.auth.authStateFlow() }.collectAsStateWithLifecycle(
    initialValue = Firebase.auth.currentUser
  )

  LaunchedEffect(Unit) {
    if (Firebase.auth.currentUser == null) {
      GoogleAuthManager.attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  FloatTaskTheme(themeMode = prefs.themeMode) {
    val signedInUser = currentUser
    if (signedInUser == null) {
      AuthScreen(onAuthSuccess = {})
    } else {
      val uid = signedInUser.uid
      LaunchedEffect(uid) {
        app.firestoreRepository.ensureUserInitialized(signedInUser)
      }
      key(uid) {
        val viewModel: MainViewModel =
          viewModel(
            key = "main_vm_$uid",
            factory =
              MainViewModel.Factory(
                repository = app.firestoreRepository,
                preferencesRepository = app.preferencesRepository,
                reminderScheduler = app.reminderScheduler,
                currentUserId = uid,
              ),
          )
        FloatTaskApp(
          viewModel = viewModel,
          user = signedInUser,
          onSignOut = {
            GoogleAuthManager.signOut(
              credentialManager = credentialManager,
              scope = scope,
            )
          },
        )
      }
    }
  }
}

@Composable
fun FloatTaskApp(
  viewModel: MainViewModel,
  user: FirebaseUser,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var hasOverlayPermission by remember {
    mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context))
  }
  var awaitingOverlaySettingsReturn by remember { mutableStateOf(false) }
  var showOverlayRationale by remember { mutableStateOf(false) }
  var showNotificationRationale by remember { mutableStateOf(false) }

  var showTaskEditor by remember { mutableStateOf(false) }
  var editingTask by remember { mutableStateOf<CloudTaskWithCategory?>(null) }
  var showCategoryManager by remember { mutableStateOf(false) }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        viewModel.emitMessage("Notifications enabled")
      }
    }

  val exportBackupLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
      if (uri != null) {
        scope.launch {
          viewModel
            .exportBackupJson()
            .onSuccess { json ->
              try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                  stream.write(json.toByteArray(Charsets.UTF_8))
                }
                viewModel.emitMessage("Backup exported successfully")
              } catch (_: Exception) {
                viewModel.emitMessage("Failed to write backup file")
              }
            }
            .onFailure {
              viewModel.emitMessage("Failed to export backup")
            }
        }
      }
    }

  val importBackupLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
              stream.bufferedReader().readText()
            }
            if (!json.isNullOrBlank()) {
              viewModel.restoreBackupJson(json)
            }
          } catch (_: Exception) {
            viewModel.emitMessage("Could not read backup file")
          }
        }
      }
    }

  // Re-check overlay permission whenever returning to the app from system settings
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    val permitted = OverlayPermissionHelper.canDrawOverlays(context)
    hasOverlayPermission = permitted
    if (awaitingOverlaySettingsReturn) {
      awaitingOverlaySettingsReturn = false
      if (permitted) {
        viewModel.setOverlayEnabled(true)
        viewModel.setOverlayTemporarilyHidden(false)
        OverlayPermissionHelper.startOverlayService(context)
        viewModel.emitMessage("Floating widget enabled!")
      } else {
        viewModel.emitMessage("Overlay permission not granted")
      }
    } else if (permitted && uiState.preferences.isOverlayEnabled) {
      OverlayPermissionHelper.startOverlayService(
        context,
        if (uiState.preferences.isTemporarilyHidden) OverlayActions.ACTION_HIDE_TEMPORARILY
        else OverlayActions.ACTION_SHOW_OVERLAY,
      )
    }
  }

  LaunchedEffect(Unit) {
    viewModel.events.collectLatest { message ->
      snackbarHostState.showSnackbar(message)
    }
  }

  fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      !NotificationHelper.hasNotificationPermission(context)
    ) {
      showNotificationRationale = true
    }
  }

  fun enableOverlayFlow() {
    if (!OverlayPermissionHelper.canDrawOverlays(context)) {
      showOverlayRationale = true
      return
    }
    requestNotificationPermissionIfNeeded()
    viewModel.setOverlayEnabled(true)
    viewModel.setOverlayTemporarilyHidden(false)
    OverlayPermissionHelper.startOverlayService(context, OverlayActions.ACTION_START_OR_SHOW)
  }

  fun disableOverlayFlow() {
    viewModel.setOverlayEnabled(false)
    OverlayPermissionHelper.stopOverlayService(context)
  }

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar {
            AppDestination.entries.forEach { dest ->
              val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
              val label = stringResource(dest.labelRes)
              NavigationBarItem(
                selected = selected,
                onClick = {
                  navController.navigate(dest.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                      saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label) },
                modifier = Modifier.testTag("nav_${dest.route}"),
              )
            }
          }
        }
      },
      floatingActionButton = {
        val route = currentDestination?.route
        if (route == AppDestination.DASHBOARD.route || route == AppDestination.TASKS.route) {
          FloatingActionButton(
            onClick = {
              editingTask = null
              showTaskEditor = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.testTag("fab_add_task"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Create new task")
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useNavRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            AppDestination.entries.forEach { dest ->
              val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
              val label = stringResource(dest.labelRes)
              NavigationRailItem(
                selected = selected,
                onClick = {
                  navController.navigate(dest.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                      saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
                icon = {
                  Icon(
                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                    contentDescription = label,
                  )
                },
                label = { Text(label) },
                modifier = Modifier.testTag("rail_${dest.route}"),
              )
            }
          }
        }

        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          NavHost(
            navController = navController,
            startDestination = AppDestination.DASHBOARD.route,
            modifier = Modifier
              .fillMaxSize()
              .widthIn(max = 840.dp),
          ) {
            composable(AppDestination.DASHBOARD.route) {
              DashboardScreen(
                uiState = uiState,
                hasOverlayPermission = hasOverlayPermission,
                onQuickAddTask = { title -> viewModel.quickAddTask(title) },
                onToggleTask = { viewModel.toggleTaskCompletion(it) },
                onToggleSubtask = { taskId, subId, done ->
                  viewModel.toggleSubtaskCompletion(taskId, subId, done)
                },
                onEditTask = {
                  editingTask = it
                  showTaskEditor = true
                },
                onDeleteTask = { viewModel.deleteTask(it) },
                onToggleOverlay = { enableOverlayFlow() },
                onToggleHideOverlay = {
                  val hide = !uiState.preferences.isTemporarilyHidden
                  viewModel.setOverlayTemporarilyHidden(hide)
                  OverlayPermissionHelper.startOverlayService(
                    context,
                    if (hide) OverlayActions.ACTION_HIDE_TEMPORARILY else OverlayActions.ACTION_SHOW_OVERLAY,
                  )
                },
                onOpenOverlayTab = {
                  navController.navigate(AppDestination.OVERLAY.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                  }
                },
              )
            }

            composable(AppDestination.TASKS.route) {
              TasksScreen(
                uiState = uiState,
                onSelectTab = { viewModel.selectOrganizationTab(it) },
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onSelectCategory = { viewModel.selectCategoryFilter(it) },
                onSelectPriority = { viewModel.selectPriorityFilter(it) },
                onManageCategories = { showCategoryManager = true },
                onToggleTask = { viewModel.toggleTaskCompletion(it) },
                onToggleSubtask = { taskId, subId, done ->
                  viewModel.toggleSubtaskCompletion(taskId, subId, done)
                },
                onEditTask = {
                  editingTask = it
                  showTaskEditor = true
                },
                onDeleteTask = { viewModel.deleteTask(it) },
              )
            }

            composable(AppDestination.OVERLAY.route) {
              OverlayControlScreen(
                uiState = uiState,
                hasOverlayPermission = hasOverlayPermission,
                userName = user.displayName,
                userEmail = user.email,
                onSignOut = onSignOut,
                onToggleOverlayEnabled = { enabled ->
                  if (enabled) enableOverlayFlow() else disableOverlayFlow()
                },
                onToggleTemporarilyHidden = { hidden ->
                  viewModel.setOverlayTemporarilyHidden(hidden)
                  OverlayPermissionHelper.startOverlayService(
                    context,
                    if (hidden) OverlayActions.ACTION_HIDE_TEMPORARILY else OverlayActions.ACTION_SHOW_OVERLAY,
                  )
                },
                onRequestOverlayPermission = { showOverlayRationale = true },
                onOpacityChange = { viewModel.setOverlayOpacity(it) },
                onBubbleSizeChange = { viewModel.setBubbleSize(it) },
                onAccentColorChange = { viewModel.setAccentColor(it) },
                onShowBadgeChange = { viewModel.setShowPendingBadge(it) },
                onResetPosition = { viewModel.resetOverlayPosition() },
                onThemeModeChange = { viewModel.setThemeMode(it) },
                onDailyReminderChange = { enabled, hour, minute ->
                  if (enabled) requestNotificationPermissionIfNeeded()
                  viewModel.setDailyReminder(enabled, hour, minute)
                },
                onExportBackup = {
                  exportBackupLauncher.launch("floattask_backup.json")
                },
                onImportBackup = {
                  importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                },
              )
            }

            composable(AppDestination.HISTORY.route) {
              HistoryScreen(
                uiState = uiState,
                onClearHistory = { viewModel.clearHistory() },
              )
            }
          }
        }
      }
    }
  }

  if (showTaskEditor) {
    TaskEditorSheet(
      existingTask = editingTask,
      categories = uiState.categories,
      onManageCategories = { showCategoryManager = true },
      onSave = { existing, title, desc, catId, prio, dueDate, dueTime, recurring, pattern, subs ->
        if (dueDate != null) {
          requestNotificationPermissionIfNeeded()
        }
        viewModel.saveTask(
          existingTask = existing,
          title = title,
          description = desc,
          categoryId = catId,
          priority = prio,
          dueDate = dueDate,
          dueTime = dueTime,
          isRecurring = recurring,
          recurrencePattern = pattern,
          subtasks = subs,
        )
        showTaskEditor = false
        editingTask = null
      },
      onDismiss = {
        showTaskEditor = false
        editingTask = null
      },
    )
  }

  if (showCategoryManager) {
    CategoryManagerDialog(
      categories = uiState.categories,
      onSaveCategory = { existing, name, color -> viewModel.saveCategory(existing, name, color) },
      onDeleteCategory = { viewModel.deleteCategory(it) },
      onDismiss = { showCategoryManager = false },
    )
  }

  if (showOverlayRationale) {
    OverlayPermissionRationaleDialog(
      onConfirmOpenSettings = {
        showOverlayRationale = false
        awaitingOverlaySettingsReturn = true
        context.startActivity(OverlayPermissionHelper.createOverlaySettingsIntent(context))
      },
      onDismiss = { showOverlayRationale = false },
    )
  }

  if (showNotificationRationale) {
    NotificationPermissionRationaleDialog(
      onConfirmRequest = {
        showNotificationRationale = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
      },
      onDismiss = { showNotificationRationale = false },
    )
  }
}
