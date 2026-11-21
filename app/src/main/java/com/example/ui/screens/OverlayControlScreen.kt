package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.OverlayBubbleSize
import com.example.data.preferences.ThemeMode
import com.example.domain.DateTimeUtils
import com.example.overlay.FloatingBubbleView
import com.example.ui.TaskUiState
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.OverlayAccentOptions
import com.example.ui.theme.PriorityHighColor
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OverlayControlScreen(
  uiState: TaskUiState,
  hasOverlayPermission: Boolean,
  userName: String?,
  userEmail: String?,
  onSignOut: () -> Unit,
  onToggleOverlayEnabled: (Boolean) -> Unit,
  onToggleTemporarilyHidden: (Boolean) -> Unit,
  onRequestOverlayPermission: () -> Unit,
  onOpacityChange: (Float) -> Unit,
  onBubbleSizeChange: (OverlayBubbleSize) -> Unit,
  onAccentColorChange: (Long) -> Unit,
  onShowBadgeChange: (Boolean) -> Unit,
  onResetPosition: () -> Unit,
  onThemeModeChange: (ThemeMode) -> Unit,
  onDailyReminderChange: (Boolean, Int, Int) -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val prefs = uiState.preferences
  var showReminderTimePicker by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("overlay_control_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Floating Widget & Preferences",
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          text = "Customize your always-on-top task bubble, cloud account, and reminders.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Google Account & Cloud Sync Card
    item {
      Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.AccountCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp),
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = userName?.takeIf { it.isNotBlank() } ?: "Google Account",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            if (!userEmail.isNullOrBlank()) {
              Text(
                text = userEmail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier.padding(top = 2.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.CloudDone,
                contentDescription = null,
                tint = EmeraldTertiary,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "Firestore real-time sync active",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldTertiary,
              )
            }
          }
          OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("sign_out_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.Logout,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Sign Out")
          }
        }
      }
    }

    // Hero & Permission / Master Switch Card
    item {
      Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(144.dp),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_overlay_hero),
              contentDescription = "Floating task widget illustration",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = if (hasOverlayPermission) EmeraldTertiary else PriorityHighColor,
              modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              ) {
                Icon(
                  imageVector = if (hasOverlayPermission) Icons.Rounded.VerifiedUser else Icons.Rounded.WarningAmber,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = if (hasOverlayPermission) "Overlay Permitted" else "Permission Needed",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White,
                )
              }
            }
          }

          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Floating Task Widget",
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = if (prefs.isOverlayEnabled && hasOverlayPermission) {
                    "Active over other apps"
                  } else {
                    "Display floating bubble & mini task panel"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = prefs.isOverlayEnabled && hasOverlayPermission,
                onCheckedChange = { enabled ->
                  if (enabled && !hasOverlayPermission) {
                    onRequestOverlayPermission()
                  } else {
                    onToggleOverlayEnabled(enabled)
                  }
                },
                modifier = Modifier.testTag("overlay_master_switch"),
              )
            }

            if (!hasOverlayPermission) {
              Button(
                onClick = onRequestOverlayPermission,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("overlay_grant_permission_button"),
              ) {
                Text("Grant \"Display Over Other Apps\" Permission")
              }
            } else if (prefs.isOverlayEnabled) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedButton(
                  onClick = { onToggleTemporarilyHidden(!prefs.isTemporarilyHidden) },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("overlay_hide_temporarily_button"),
                ) {
                  Icon(
                    imageVector = if (prefs.isTemporarilyHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (prefs.isTemporarilyHidden) "Show Bubble" else "Hide Temporarily")
                }

                OutlinedButton(
                  onClick = onResetPosition,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.testTag("overlay_reset_position_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = "Reset bubble position",
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Reset Pos")
                }
              }
            }
          }
        }
      }
    }

    // Live Bubble Appearance Customizer
    item {
      Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Text(
            text = "Widget Appearance",
            style = MaterialTheme.typography.titleMedium,
          )

          // Live Preview Stage
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Live Bubble Preview", style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "Opacity ${(prefs.overlayOpacity * 100).roundToInt()}% · ${prefs.bubbleSize.label}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              FloatingBubbleView(
                pendingCount = uiState.stats.pendingCount.coerceAtLeast(3),
                preferences = prefs,
                onTap = {},
                onDragStart = {},
                onDrag = { _, _ -> },
                onDragEnd = {},
              )
            }
          }

          // Opacity Slider
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Widget Opacity", style = MaterialTheme.typography.labelLarge)
              Text(
                text = "${(prefs.overlayOpacity * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Slider(
              value = prefs.overlayOpacity,
              onValueChange = onOpacityChange,
              valueRange = 0.4f..1.0f,
              modifier = Modifier.testTag("overlay_opacity_slider"),
            )
          }

          // Bubble Size
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Bubble Size", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OverlayBubbleSize.entries.forEach { size ->
                FilterChip(
                  selected = prefs.bubbleSize == size,
                  onClick = { onBubbleSizeChange(size) },
                  label = { Text(size.label) },
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }

          // Accent Color
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Accent Color", style = MaterialTheme.typography.labelLarge)
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OverlayAccentOptions.forEach { hex ->
                val selected = prefs.accentColorHex == hex
                Box(
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(hex))
                    .border(
                      width = if (selected) 2.5.dp else 0.dp,
                      color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                      shape = CircleShape,
                    )
                    .clickable { onAccentColorChange(hex) },
                  contentAlignment = Alignment.Center,
                ) {
                  if (selected) {
                    Icon(
                      imageVector = Icons.Rounded.Check,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                }
              }
            }
          }

          // Pending Badge Switch
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Show Pending Counter Badge", style = MaterialTheme.typography.titleSmall)
              Text(
                text = "Display pending task count on floating bubble",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = prefs.showPendingBadge,
              onCheckedChange = onShowBadgeChange,
            )
          }
        }
      }
    }

    // Daily Reminder & Theme
    item {
      Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Text("Reminders & Appearance", style = MaterialTheme.typography.titleMedium)

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column {
                Text("Daily Task Reminder", style = MaterialTheme.typography.titleSmall)
                val formattedTime = DateTimeUtils.formatTimeDisplay(
                  String.format(Locale.US, "%02d:%02d", prefs.dailyReminderHour, prefs.dailyReminderMinute)
                )
                Text(
                  text = if (prefs.dailyReminderEnabled) "Scheduled daily at $formattedTime" else "Off",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Switch(
              checked = prefs.dailyReminderEnabled,
              onCheckedChange = { enabled ->
                onDailyReminderChange(enabled, prefs.dailyReminderHour, prefs.dailyReminderMinute)
              },
              modifier = Modifier.testTag("daily_reminder_switch"),
            )
          }

          if (prefs.dailyReminderEnabled) {
            OutlinedButton(
              onClick = { showReminderTimePicker = true },
              shape = RoundedCornerShape(12.dp),
            ) {
              Text("Change Reminder Time")
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("App Theme", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ThemeMode.entries.forEach { mode ->
                FilterChip(
                  selected = prefs.themeMode == mode,
                  onClick = { onThemeModeChange(mode) },
                  label = { Text(mode.label) },
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }
    }

    // Offline Backup & Restore
    item {
      Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("Local Backup & Restore", style = MaterialTheme.typography.titleMedium)
          Text(
            text = "Export all tasks, categories, checklists, and history to a local JSON file or restore anytime offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onExportBackup,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("backup_export_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Backup,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Backup")
            }
            OutlinedButton(
              onClick = onImportBackup,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .weight(1f)
                .testTag("backup_restore_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Restore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Restore")
            }
          }
        }
      }
    }
  }

  if (showReminderTimePicker) {
    val timeState = rememberTimePickerState(
      initialHour = prefs.dailyReminderHour,
      initialMinute = prefs.dailyReminderMinute,
    )
    AlertDialog(
      onDismissRequest = { showReminderTimePicker = false },
      title = { Text("Daily Reminder Time") },
      text = { TimePicker(state = timeState) },
      confirmButton = {
        TextButton(onClick = {
          onDailyReminderChange(true, timeState.hour, timeState.minute)
          showReminderTimePicker = false
        }) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showReminderTimePicker = false }) { Text("Cancel") }
      },
    )
  }
}
