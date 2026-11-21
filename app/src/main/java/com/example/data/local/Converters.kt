package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.RecurrencePattern
import com.example.data.model.TaskPriority

class Converters {
  @TypeConverter
  fun fromPriority(priority: TaskPriority): String = priority.name

  @TypeConverter
  fun toPriority(value: String): TaskPriority = TaskPriority.fromName(value)

  @TypeConverter
  fun fromRecurrence(pattern: RecurrencePattern): String = pattern.name

  @TypeConverter
  fun toRecurrence(value: String): RecurrencePattern = RecurrencePattern.fromName(value)
}
