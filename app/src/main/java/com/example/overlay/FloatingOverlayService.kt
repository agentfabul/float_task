package com.example.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ServiceCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.FloatTaskApplication
import com.example.auth.authStateFlow
import com.example.data.preferences.OverlayPreferences
import com.example.data.remote.CloudTaskWithCategory
import com.example.domain.DateTimeUtils
import com.example.notifications.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingOverlayService : Service() {

  private lateinit var windowManager: WindowManager
  private lateinit var app: FloatTaskApplication
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var overlayContainer: FrameLayout? = null
  private var overlayParams: WindowManager.LayoutParams? = null
  private var lifecycleOwner: OverlayLifecycleOwner? = null
  private var snapAnimator: ValueAnimator? = null

  private var isExpanded by mutableStateOf(false)
  private var isAuthenticatedState by mutableStateOf(Firebase.auth.currentUser != null)
  private var tasksState by mutableStateOf<List<CloudTaskWithCategory>>(emptyList())
  private var pendingCountState by mutableStateOf(0)
  private var preferencesState by mutableStateOf(OverlayPreferences())

  private var lastBubbleX: Int = 24
  private var lastBubbleY: Int = 260
  private var positionInitialized = false

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun onCreate() {
    super.onCreate()
    app = application as FloatTaskApplication
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

    NotificationHelper.createChannels(this)
    promoteToForeground(pendingCount = 0, isTemporarilyHidden = false)

    serviceScope.launch {
      val initialPrefs = app.preferencesRepository.preferences.first()
      preferencesState = initialPrefs
      lastBubbleX = initialPrefs.overlayX
      lastBubbleY = initialPrefs.overlayY
      positionInitialized = true

      if (!Settings.canDrawOverlays(this@FloatingOverlayService)) {
        app.preferencesRepository.setOverlayEnabled(false)
        stopSelf()
        return@launch
      }

      if (!initialPrefs.isTemporarilyHidden) {
        attachOverlayWindow()
      }

      launch {
        Firebase.auth
          .authStateFlow()
          .flatMapLatest { user ->
            if (user == null) {
              isAuthenticatedState = false
              flowOf(emptyList())
            } else {
              isAuthenticatedState = true
              app.firestoreRepository
                .observeTasks(user.uid)
                .map { raw -> raw.map { CloudTaskWithCategory(it, null) } }
                .catch { error ->
                  val firestoreError =
                    generateSequence(error) { it.cause }
                      .filterIsInstance<FirebaseFirestoreException>()
                      .firstOrNull()
                  if (firestoreError == null && error is CancellationException) throw error
                  Log.w(TAG, "Overlay task sync error", firestoreError ?: error)
                  emit(emptyList())
                }
            }
          }
          .collectLatest { list ->
            tasksState = list
            val pending = list.count { !it.task.completed }
            pendingCountState = pending
            updateForegroundNotification()
          }
      }

      launch {
        app.preferencesRepository.preferences.collectLatest { prefs ->
          val wasHidden = preferencesState.isTemporarilyHidden
          preferencesState = prefs
          if (!prefs.isOverlayEnabled) {
            detachOverlayWindow()
            stopSelf()
            return@collectLatest
          }
          if (prefs.isTemporarilyHidden && !wasHidden) {
            isExpanded = false
            detachOverlayWindow()
            updateForegroundNotification()
          } else if (!prefs.isTemporarilyHidden && (wasHidden || overlayContainer == null)) {
            lastBubbleX = prefs.overlayX
            lastBubbleY = prefs.overlayY
            attachOverlayWindow()
            updateForegroundNotification()
          }
        }
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      OverlayActions.ACTION_HIDE_TEMPORARILY -> {
        serviceScope.launch {
          app.preferencesRepository.setOverlayTemporarilyHidden(true)
        }
      }
      OverlayActions.ACTION_SHOW_OVERLAY, OverlayActions.ACTION_START_OR_SHOW -> {
        if (!Settings.canDrawOverlays(this)) {
          stopSelf()
          return START_NOT_STICKY
        }
        serviceScope.launch {
          app.preferencesRepository.setOverlayEnabled(true)
          app.preferencesRepository.setOverlayTemporarilyHidden(false)
        }
      }
      OverlayActions.ACTION_STOP_OVERLAY -> {
        serviceScope.launch {
          app.preferencesRepository.setOverlayEnabled(false)
          stopSelf()
        }
      }
    }
    return START_STICKY
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    val params = overlayParams ?: return
    val container = overlayContainer ?: return
    val (screenW, screenH) = getScreenDimensions()

    if (!isExpanded) {
      val bubblePx = dpToPx(preferencesState.bubbleSize.dpValue + 16)
      val marginPx = dpToPx(10)
      val clampedY = params.y.coerceIn(marginPx, (screenH - bubblePx - marginPx).coerceAtLeast(marginPx))
      val snapLeft = params.x + bubblePx / 2 < screenW / 2
      val clampedX = if (snapLeft) marginPx else (screenW - bubblePx - marginPx).coerceAtLeast(marginPx)
      params.x = clampedX
      params.y = clampedY
      lastBubbleX = clampedX
      lastBubbleY = clampedY
      safeUpdateViewLayout(container, params)
    } else {
      val panelMaxW = dpToPx(330)
      val panelMaxH = dpToPx(430)
      params.x = ((screenW - panelMaxW) / 2).coerceAtLeast(dpToPx(8))
      params.y = params.y.coerceIn(dpToPx(24), (screenH - panelMaxH).coerceAtLeast(dpToPx(24)))
      safeUpdateViewLayout(container, params)
    }
  }

  override fun onDestroy() {
    snapAnimator?.cancel()
    detachOverlayWindow()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun promoteToForeground(pendingCount: Int, isTemporarilyHidden: Boolean) {
    val notification =
      NotificationHelper.buildOverlayForegroundNotification(
        context = this,
        pendingCount = pendingCount,
        isTemporarilyHidden = isTemporarilyHidden,
      )
    val serviceType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
      } else {
        0
      }
    ServiceCompat.startForeground(
      this,
      NotificationHelper.OVERLAY_NOTIFICATION_ID,
      notification,
      serviceType,
    )
  }

  private fun updateForegroundNotification() {
    val notification =
      NotificationHelper.buildOverlayForegroundNotification(
        context = this,
        pendingCount = pendingCountState,
        isTemporarilyHidden = preferencesState.isTemporarilyHidden,
      )
    val manager = getSystemService(NotificationManager::class.java)
    manager?.notify(NotificationHelper.OVERLAY_NOTIFICATION_ID, notification)
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun attachOverlayWindow() {
    if (overlayContainer != null || !Settings.canDrawOverlays(this)) return

    val owner = OverlayLifecycleOwner()
    owner.onCreate()
    owner.onStart()
    owner.onResume()
    lifecycleOwner = owner

    val (screenW, screenH) = getScreenDimensions()
    val bubblePx = dpToPx(preferencesState.bubbleSize.dpValue + 16)
    val marginPx = dpToPx(10)
    val startX = lastBubbleX.coerceIn(marginPx, (screenW - bubblePx - marginPx).coerceAtLeast(marginPx))
    val startY = lastBubbleY.coerceIn(marginPx, (screenH - bubblePx - marginPx).coerceAtLeast(marginPx))

    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayWindowType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = startX
        y = startY
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
      }

    val container =
      object : FrameLayout(this) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
          if (event.keyCode == KeyEvent.KEYCODE_BACK && isExpanded) {
            if (event.action == KeyEvent.ACTION_UP) {
              setExpandedMode(false)
            }
            return true
          }
          return super.dispatchKeyEvent(event)
        }
      }.apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setOnTouchListener { _, motionEvent ->
          if (isExpanded && motionEvent.action == MotionEvent.ACTION_OUTSIDE) {
            setExpandedMode(false)
            true
          } else {
            false
          }
        }
      }

    val composeView =
      ComposeView(this).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          FloatingOverlayRoot(
            isExpanded = isExpanded,
            isAuthenticated = isAuthenticatedState,
            tasks = tasksState,
            pendingCount = pendingCountState,
            preferences = preferencesState,
            onBubbleTap = { setExpandedMode(true) },
            onBubbleDragStart = { snapAnimator?.cancel() },
            onBubbleDrag = { dx, dy -> handleBubbleDrag(dx, dy) },
            onBubbleDragEnd = { snapBubbleToEdgeAndSave() },
            onMinimize = { setExpandedMode(false) },
            onHideTemporarily = {
              setExpandedMode(false)
              serviceScope.launch {
                app.preferencesRepository.setOverlayTemporarilyHidden(true)
              }
            },
            onCloseOverlay = {
              serviceScope.launch {
                app.preferencesRepository.setOverlayEnabled(false)
                stopSelf()
              }
            },
            onOpenFullApp = { openMainApp() },
            onToggleTask = { taskId ->
              if (Firebase.auth.currentUser != null) {
                serviceScope.launch {
                  app.firestoreRepository.toggleTaskCompletion(taskId)
                }
              }
            },
            onQuickAddTask = { title, priority ->
              if (Firebase.auth.currentUser != null) {
                serviceScope.launch {
                  app.firestoreRepository.quickAddTask(
                    title = title,
                    priority = priority,
                    dueDate = DateTimeUtils.startOfDayMillis(),
                  )
                }
              }
            },
          )
        }
      }

    container.addView(composeView)
    overlayContainer = container
    overlayParams = params

    try {
      windowManager.addView(container, params)
    } catch (_: Exception) {
      detachOverlayWindow()
    }
  }

  private fun detachOverlayWindow() {
    val container = overlayContainer ?: return
    try {
      windowManager.removeView(container)
    } catch (_: Exception) {
    }
    lifecycleOwner?.apply {
      onPause()
      onStop()
      onDestroy()
    }
    lifecycleOwner = null
    overlayContainer = null
    overlayParams = null
  }

  private fun setExpandedMode(expanded: Boolean) {
    val params = overlayParams ?: return
    val container = overlayContainer ?: return
    val (screenW, screenH) = getScreenDimensions()

    if (expanded && !isExpanded) {
      lastBubbleX = params.x
      lastBubbleY = params.y
      isExpanded = true

      val panelW = dpToPx(324).coerceAtMost(screenW - dpToPx(24))
      val panelEstimatedH = dpToPx(420)
      params.flags =
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
          WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
      params.x = ((screenW - panelW) / 2).coerceAtLeast(dpToPx(12))
      params.y = lastBubbleY.coerceIn(dpToPx(36), (screenH - panelEstimatedH).coerceAtLeast(dpToPx(36)))
      safeUpdateViewLayout(container, params)
    } else if (!expanded && isExpanded) {
      isExpanded = false
      params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
      params.x = lastBubbleX
      params.y = lastBubbleY
      safeUpdateViewLayout(container, params)
    }
  }

  private fun handleBubbleDrag(dx: Float, dy: Float) {
    val params = overlayParams ?: return
    val container = overlayContainer ?: return
    val (screenW, screenH) = getScreenDimensions()
    val bubblePx = dpToPx(preferencesState.bubbleSize.dpValue + 16)

    params.x = (params.x + dx.roundToInt()).coerceIn(0, (screenW - bubblePx).coerceAtLeast(0))
    params.y = (params.y + dy.roundToInt()).coerceIn(0, (screenH - bubblePx).coerceAtLeast(0))
    lastBubbleX = params.x
    lastBubbleY = params.y
    safeUpdateViewLayout(container, params)
  }

  private fun snapBubbleToEdgeAndSave() {
    val params = overlayParams ?: return
    val container = overlayContainer ?: return
    val (screenW, screenH) = getScreenDimensions()
    val bubblePx = dpToPx(preferencesState.bubbleSize.dpValue + 16)
    val marginPx = dpToPx(10)

    val targetX =
      if (params.x + bubblePx / 2 < screenW / 2) {
        marginPx
      } else {
        (screenW - bubblePx - marginPx).coerceAtLeast(marginPx)
      }
    val targetY = params.y.coerceIn(marginPx, (screenH - bubblePx - marginPx).coerceAtLeast(marginPx))
    val startX = params.x

    snapAnimator?.cancel()
    snapAnimator =
      ValueAnimator.ofInt(startX, targetX).apply {
        duration = 220L
        interpolator = DecelerateInterpolator()
        addUpdateListener { anim ->
          val currentContainer = overlayContainer ?: return@addUpdateListener
          val currentParams = overlayParams ?: return@addUpdateListener
          if (!isExpanded) {
            currentParams.x = anim.animatedValue as Int
            currentParams.y = targetY
            lastBubbleX = currentParams.x
            lastBubbleY = currentParams.y
            safeUpdateViewLayout(currentContainer, currentParams)
          }
        }
        start()
      }

    lastBubbleX = targetX
    lastBubbleY = targetY
    if (positionInitialized) {
      serviceScope.launch {
        app.preferencesRepository.saveOverlayPosition(targetX, targetY)
      }
    }
  }

  private fun openMainApp() {
    setExpandedMode(false)
    val intent =
      Intent(this, com.example.MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      }
    startActivity(intent)
  }

  private fun safeUpdateViewLayout(container: FrameLayout, params: WindowManager.LayoutParams) {
    try {
      if (container.isAttachedToWindow) {
        windowManager.updateViewLayout(container, params)
      }
    } catch (_: Exception) {
    }
  }

  private fun getScreenDimensions(): Pair<Int, Int> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val bounds = windowManager.currentWindowMetrics.bounds
      bounds.width() to bounds.height()
    } else {
      val metrics = resources.displayMetrics
      metrics.widthPixels to metrics.heightPixels
    }
  }

  private fun dpToPx(dp: Int): Int {
    return (dp * resources.displayMetrics.density).roundToInt()
  }

  private fun overlayWindowType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }
  }

  companion object {
    private const val TAG = "OverlayService"
  }
}
