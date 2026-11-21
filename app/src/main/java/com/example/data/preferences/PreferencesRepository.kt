package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "floattask_prefs")

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark");

  companion object {
    fun fromName(value: String?): ThemeMode =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
  }
}

enum class OverlayBubbleSize(val label: String, val dpValue: Int) {
  COMPACT("Compact", 48),
  STANDARD("Standard", 56),
  LARGE("Large", 64);

  companion object {
    fun fromName(value: String?): OverlayBubbleSize =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STANDARD
  }
}

data class OverlayPreferences(
  val isOverlayEnabled: Boolean = false,
  val isTemporarilyHidden: Boolean = false,
  val overlayX: Int = 24,
  val overlayY: Int = 260,
  val overlayOpacity: Float = 0.96f,
  val bubbleSize: OverlayBubbleSize = OverlayBubbleSize.STANDARD,
  val accentColorHex: Long = 0xFF6366F1,
  val showPendingBadge: Boolean = true,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val dailyReminderEnabled: Boolean = false,
  val dailyReminderHour: Int = 9,
  val dailyReminderMinute: Int = 0,
)

class PreferencesRepository(private val context: Context) {

  private object Keys {
    val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
    val OVERLAY_HIDDEN = booleanPreferencesKey("overlay_temporarily_hidden")
    val OVERLAY_X = intPreferencesKey("overlay_x")
    val OVERLAY_Y = intPreferencesKey("overlay_y")
    val OVERLAY_OPACITY = floatPreferencesKey("overlay_opacity")
    val BUBBLE_SIZE = stringPreferencesKey("bubble_size")
    val ACCENT_COLOR = longPreferencesKey("accent_color")
    val SHOW_BADGE = booleanPreferencesKey("show_pending_badge")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
    val DAILY_REMINDER_HOUR = intPreferencesKey("daily_reminder_hour")
    val DAILY_REMINDER_MINUTE = intPreferencesKey("daily_reminder_minute")
  }

  val preferences: Flow<OverlayPreferences> =
    context.dataStore.data.map { prefs ->
      OverlayPreferences(
        isOverlayEnabled = prefs[Keys.OVERLAY_ENABLED] ?: false,
        isTemporarilyHidden = prefs[Keys.OVERLAY_HIDDEN] ?: false,
        overlayX = prefs[Keys.OVERLAY_X] ?: 24,
        overlayY = prefs[Keys.OVERLAY_Y] ?: 260,
        overlayOpacity = (prefs[Keys.OVERLAY_OPACITY] ?: 0.96f).coerceIn(0.4f, 1.0f),
        bubbleSize = OverlayBubbleSize.fromName(prefs[Keys.BUBBLE_SIZE]),
        accentColorHex = prefs[Keys.ACCENT_COLOR] ?: 0xFF6366F1,
        showPendingBadge = prefs[Keys.SHOW_BADGE] ?: true,
        themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE]),
        dailyReminderEnabled = prefs[Keys.DAILY_REMINDER_ENABLED] ?: false,
        dailyReminderHour = prefs[Keys.DAILY_REMINDER_HOUR] ?: 9,
        dailyReminderMinute = prefs[Keys.DAILY_REMINDER_MINUTE] ?: 0,
      )
    }

  suspend fun setOverlayEnabled(enabled: Boolean) {
    context.dataStore.edit {
      it[Keys.OVERLAY_ENABLED] = enabled
      if (!enabled) {
        it[Keys.OVERLAY_HIDDEN] = false
      }
    }
  }

  suspend fun setOverlayTemporarilyHidden(hidden: Boolean) {
    context.dataStore.edit { it[Keys.OVERLAY_HIDDEN] = hidden }
  }

  suspend fun saveOverlayPosition(x: Int, y: Int) {
    context.dataStore.edit {
      it[Keys.OVERLAY_X] = x
      it[Keys.OVERLAY_Y] = y
    }
  }

  suspend fun resetOverlayPosition() {
    context.dataStore.edit {
      it[Keys.OVERLAY_X] = 24
      it[Keys.OVERLAY_Y] = 260
    }
  }

  suspend fun setOverlayOpacity(opacity: Float) {
    context.dataStore.edit { it[Keys.OVERLAY_OPACITY] = opacity.coerceIn(0.4f, 1.0f) }
  }

  suspend fun setBubbleSize(size: OverlayBubbleSize) {
    context.dataStore.edit { it[Keys.BUBBLE_SIZE] = size.name }
  }

  suspend fun setAccentColor(colorHex: Long) {
    context.dataStore.edit { it[Keys.ACCENT_COLOR] = colorHex }
  }

  suspend fun setShowPendingBadge(show: Boolean) {
    context.dataStore.edit { it[Keys.SHOW_BADGE] = show }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setDailyReminder(enabled: Boolean, hour: Int, minute: Int) {
    context.dataStore.edit {
      it[Keys.DAILY_REMINDER_ENABLED] = enabled
      it[Keys.DAILY_REMINDER_HOUR] = hour.coerceIn(0, 23)
      it[Keys.DAILY_REMINDER_MINUTE] = minute.coerceIn(0, 59)
    }
  }
}
