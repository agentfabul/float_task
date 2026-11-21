package com.example.data.model

enum class TaskPriority(val label: String, val sortWeight: Int) {
  LOW("Low", 1),
  MEDIUM("Medium", 2),
  HIGH("High", 3),
  URGENT("Urgent", 4);

  companion object {
    fun fromName(value: String?): TaskPriority =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MEDIUM
  }
}
