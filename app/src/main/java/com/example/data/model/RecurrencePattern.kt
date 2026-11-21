package com.example.data.model

enum class RecurrencePattern(val label: String) {
  NONE("Does not repeat"),
  DAILY("Daily"),
  WEEKLY("Weekly"),
  MONTHLY("Monthly");

  companion object {
    fun fromName(value: String?): RecurrencePattern =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
  }
}
