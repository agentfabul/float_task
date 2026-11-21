package com.example.domain

import com.example.data.model.RecurrencePattern
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

  fun startOfDayMillis(timeMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = timeMillis
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
  }

  fun endOfDayMillis(timeMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
      timeInMillis = timeMillis
      set(Calendar.HOUR_OF_DAY, 23)
      set(Calendar.MINUTE, 59)
      set(Calendar.SECOND, 59)
      set(Calendar.MILLISECOND, 999)
    }
    return cal.timeInMillis
  }

  fun isToday(dueDateMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (dueDateMillis == null) return false
    val start = startOfDayMillis(nowMillis)
    val end = endOfDayMillis(nowMillis)
    return dueDateMillis in start..end
  }

  fun isUpcoming(dueDateMillis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (dueDateMillis == null) return false
    return dueDateMillis > endOfDayMillis(nowMillis)
  }

  fun combineDateAndTime(dueDateMillis: Long?, dueTime: String?): Long? {
    if (dueDateMillis == null) return null
    val cal = Calendar.getInstance().apply {
      timeInMillis = startOfDayMillis(dueDateMillis)
    }
    if (!dueTime.isNullOrBlank()) {
      val parts = dueTime.split(":")
      val hour = parts.getOrNull(0)?.toIntOrNull()
      val minute = parts.getOrNull(1)?.toIntOrNull()
      if (hour != null && minute != null) {
        cal.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        cal.set(Calendar.MINUTE, minute.coerceIn(0, 59))
        return cal.timeInMillis
      }
    }
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    return cal.timeInMillis
  }

  fun isOverdue(
    dueDateMillis: Long?,
    dueTime: String?,
    isCompleted: Boolean,
    nowMillis: Long = System.currentTimeMillis(),
  ): Boolean {
    if (isCompleted || dueDateMillis == null) return false
    val effectiveDeadline = combineDateAndTime(dueDateMillis, dueTime) ?: return false
    return effectiveDeadline < nowMillis
  }

  fun nextRecurrenceDueDate(
    currentDueDateMillis: Long?,
    pattern: RecurrencePattern,
    nowMillis: Long = System.currentTimeMillis(),
  ): Long {
    val base = startOfDayMillis(currentDueDateMillis ?: nowMillis)
    val cal = Calendar.getInstance().apply { timeInMillis = base }
    when (pattern) {
      RecurrencePattern.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
      RecurrencePattern.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
      RecurrencePattern.MONTHLY -> cal.add(Calendar.MONTH, 1)
      RecurrencePattern.NONE -> Unit
    }
    // If the resulting date is still in the past, advance from today instead
    val todayStart = startOfDayMillis(nowMillis)
    if (cal.timeInMillis < todayStart) {
      val todayCal = Calendar.getInstance().apply { timeInMillis = todayStart }
      when (pattern) {
        RecurrencePattern.DAILY -> todayCal.add(Calendar.DAY_OF_YEAR, 1)
        RecurrencePattern.WEEKLY -> todayCal.add(Calendar.WEEK_OF_YEAR, 1)
        RecurrencePattern.MONTHLY -> todayCal.add(Calendar.MONTH, 1)
        RecurrencePattern.NONE -> Unit
      }
      return todayCal.timeInMillis
    }
    return cal.timeInMillis
  }

  fun formatDateLabel(dueDateMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
    if (dueDateMillis == null) return "No date"
    val todayStart = startOfDayMillis(nowMillis)
    val tomorrowStart = todayStart + 24L * 60 * 60 * 1000
    val yesterdayStart = todayStart - 24L * 60 * 60 * 1000
    val targetStart = startOfDayMillis(dueDateMillis)
    return when (targetStart) {
      todayStart -> "Today"
      tomorrowStart -> "Tomorrow"
      yesterdayStart -> "Yesterday"
      else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(dueDateMillis))
    }
  }

  fun formatFullDate(millis: Long): String =
    SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(Date(millis))

  fun formatTimeDisplay(dueTime: String?): String {
    if (dueTime.isNullOrBlank()) return ""
    val parts = dueTime.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return dueTime
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return dueTime
    val cal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
    }
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
  }

  fun formatTimestamp(millis: Long): String =
    SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(millis))
}
