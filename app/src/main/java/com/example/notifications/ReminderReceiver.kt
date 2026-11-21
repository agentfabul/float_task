package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.FloatTaskApplication
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val app = context.applicationContext as? FloatTaskApplication ?: return
    val pendingResult = goAsync()

    CoroutineScope(Dispatchers.IO).launch {
      try {
        when (intent.action) {
          ACTION_TASK_REMINDER -> {
            val taskId = intent.getStringExtra(EXTRA_TASK_ID_STRING).orEmpty()
            val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Due"
            val priority = intent.getStringExtra(EXTRA_TASK_PRIORITY) ?: "Medium"
            val uid = Firebase.auth.currentUser?.uid
            if (uid != null && taskId.isNotBlank()) {
              val snap =
                try {
                  app.firestoreDb
                    .collection("users")
                    .document(uid)
                    .collection("tasks")
                    .document(taskId)
                    .get()
                    .await()
                } catch (_: Exception) {
                  null
                }
              val completed = snap?.getBoolean("completed") ?: false
              if (snap != null && snap.exists() && !completed) {
                NotificationHelper.showTaskReminderNotification(
                  context = context,
                  notificationId = 30_000 + (taskId.hashCode() and 0x00FFFFFF),
                  title = "Task Due: $title",
                  body = "$priority priority · Tap to view or check off in FloatTask",
                )
              }
            }
          }
          ACTION_DAILY_REMINDER -> {
            val prefs = app.preferencesRepository.preferences.first()
            val uid = Firebase.auth.currentUser?.uid
            val body =
              if (uid != null) {
                val pendingCount =
                  try {
                    app.firestoreDb
                      .collection("users")
                      .document(uid)
                      .collection("tasks")
                      .whereEqualTo("completed", false)
                      .get()
                      .await()
                      .size()
                  } catch (_: Exception) {
                    null
                  }
                when {
                  pendingCount == null -> "Check in on your tasks for today in FloatTask."
                  pendingCount == 0 -> "Your task board is clear today. Plan something meaningful!"
                  else -> "You have $pendingCount pending ${if (pendingCount == 1) "task" else "tasks"} waiting for your focus."
                }
              } else {
                "Open FloatTask to review and float your tasks for today."
              }

            NotificationHelper.showTaskReminderNotification(
              context = context,
              notificationId = NotificationHelper.DAILY_REMINDER_NOTIFICATION_ID,
              title = "FloatTask Daily Check-In",
              body = body,
            )
            if (prefs.dailyReminderEnabled) {
              app.reminderScheduler.scheduleDailyReminder(
                enabled = true,
                hour = prefs.dailyReminderHour,
                minute = prefs.dailyReminderMinute,
              )
            }
          }
        }
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    const val ACTION_TASK_REMINDER = "com.example.notifications.ACTION_TASK_REMINDER"
    const val ACTION_DAILY_REMINDER = "com.example.notifications.ACTION_DAILY_REMINDER"
    const val EXTRA_TASK_ID_STRING = "extra_task_id_string"
    const val EXTRA_TASK_TITLE = "extra_task_title"
    const val EXTRA_TASK_PRIORITY = "extra_task_priority"
  }
}
