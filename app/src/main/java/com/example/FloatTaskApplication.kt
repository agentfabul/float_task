package com.example

import android.app.Application
import com.example.data.local.FloatTaskDatabase
import com.example.data.preferences.PreferencesRepository
import com.example.data.remote.FirestoreSyncRepository
import com.example.data.repository.LocalTaskRepository
import com.example.data.repository.TaskRepository
import com.example.notifications.NotificationHelper
import com.example.notifications.ReminderScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

class FloatTaskApplication : Application() {

  lateinit var database: FloatTaskDatabase
    private set

  lateinit var preferencesRepository: PreferencesRepository
    private set

  lateinit var reminderScheduler: ReminderScheduler
    private set

  lateinit var repository: TaskRepository
    private set

  val firestoreDb: FirebaseFirestore by lazy {
    if (FirebaseApp.getApps(this).isEmpty()) {
      FirebaseApp.initializeApp(this)
    }
    val databaseId = getString(R.string.firestore_database_id)
    FirebaseFirestore.getInstance(databaseId)
  }

  val firestoreRepository: FirestoreSyncRepository by lazy {
    FirestoreSyncRepository(db = firestoreDb) { cloudTask, shouldSchedule ->
      reminderScheduler.scheduleOrCancelCloudTaskReminder(cloudTask, shouldSchedule)
    }
  }

  override fun onCreate() {
    super.onCreate()
    NotificationHelper.createChannels(this)
    database = FloatTaskDatabase.getInstance(this)
    preferencesRepository = PreferencesRepository(this)
    reminderScheduler = ReminderScheduler(this)
    repository = LocalTaskRepository(database) { task, shouldSchedule ->
      reminderScheduler.scheduleOrCancelTaskReminder(task, shouldSchedule)
    }
  }
}
