package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.FloatTaskApplication
import com.example.data.remote.CloudTask
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class BootCompletedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
    val app = context.applicationContext as? FloatTaskApplication ?: return
    val pendingResult = goAsync()

    CoroutineScope(Dispatchers.IO).launch {
      try {
        val prefs = app.preferencesRepository.preferences.first()
        if (prefs.dailyReminderEnabled) {
          app.reminderScheduler.scheduleDailyReminder(
            enabled = true,
            hour = prefs.dailyReminderHour,
            minute = prefs.dailyReminderMinute,
          )
        }
        val uid = Firebase.auth.currentUser?.uid
        if (uid != null) {
          val tasks =
            app.firestoreDb
              .collection("users")
              .document(uid)
              .collection("tasks")
              .whereEqualTo("completed", false)
              .get()
              .await()
              .toObjects(CloudTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          tasks
            .filter { it.dueDate != null }
            .forEach { task ->
              app.reminderScheduler.scheduleOrCancelCloudTaskReminder(task, true)
            }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Failed to reschedule reminders on boot", e)
      } finally {
        pendingResult.finish()
      }
    }
  }

  private companion object {
    const val TAG = "BootReceiver"
  }
}
