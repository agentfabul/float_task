package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.model.TaskEntity
import com.example.data.remote.CloudTask
import com.example.domain.DateTimeUtils
import java.util.Calendar

class ReminderScheduler(private val context: Context) {

  private val alarmManager: AlarmManager? =
    context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

  fun scheduleOrCancelTaskReminder(task: TaskEntity, shouldSchedule: Boolean) {
    val requestCode = (TASK_REQUEST_BASE + task.id).toInt()
    scheduleInternal(
      requestCode = requestCode,
      taskIdString = task.id.toString(),
      taskTitle = task.title,
      priorityLabel = task.priority.label,
      dueDate = task.dueDate,
      dueTime = task.dueTime,
      isCompleted = task.isCompleted,
      shouldSchedule = shouldSchedule,
    )
  }

  fun scheduleOrCancelCloudTaskReminder(task: CloudTask, shouldSchedule: Boolean) {
    val requestCode = (TASK_REQUEST_BASE.toInt() + (task.id.hashCode() and 0x00FFFFFF))
    scheduleInternal(
      requestCode = requestCode,
      taskIdString = task.id,
      taskTitle = task.title,
      priorityLabel = task.priorityEnum.label,
      dueDate = task.dueDate,
      dueTime = task.dueTime,
      isCompleted = task.completed,
      shouldSchedule = shouldSchedule,
    )
  }

  private fun scheduleInternal(
    requestCode: Int,
    taskIdString: String,
    taskTitle: String,
    priorityLabel: String,
    dueDate: Long?,
    dueTime: String?,
    isCompleted: Boolean,
    shouldSchedule: Boolean,
  ) {
    val intent =
      Intent(context, ReminderReceiver::class.java).apply {
        action = ReminderReceiver.ACTION_TASK_REMINDER
        putExtra(ReminderReceiver.EXTRA_TASK_ID_STRING, taskIdString)
        putExtra(ReminderReceiver.EXTRA_TASK_TITLE, taskTitle)
        putExtra(ReminderReceiver.EXTRA_TASK_PRIORITY, priorityLabel)
      }
    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    if (!shouldSchedule || isCompleted || dueDate == null) {
      alarmManager?.cancel(pendingIntent)
      return
    }

    val triggerAt =
      if (!dueTime.isNullOrBlank()) {
        DateTimeUtils.combineDateAndTime(dueDate, dueTime)
      } else {
        DateTimeUtils.combineDateAndTime(dueDate, "09:00")
      } ?: return

    if (triggerAt <= System.currentTimeMillis()) {
      alarmManager?.cancel(pendingIntent)
      return
    }

    alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
  }

  fun scheduleDailyReminder(enabled: Boolean, hour: Int, minute: Int) {
    val intent =
      Intent(context, ReminderReceiver::class.java).apply {
        action = ReminderReceiver.ACTION_DAILY_REMINDER
      }
    val pendingIntent =
      PendingIntent.getBroadcast(
        context,
        DAILY_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    if (!enabled) {
      alarmManager?.cancel(pendingIntent)
      return
    }

    val cal =
      Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) {
          add(Calendar.DAY_OF_YEAR, 1)
        }
      }

    alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
  }

  companion object {
    private const val TASK_REQUEST_BASE = 10_000L
    private const val DAILY_REQUEST_CODE = 9_001
  }
}
