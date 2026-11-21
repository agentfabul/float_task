package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.remote.CloudTaskWithCategory
import com.example.domain.DateTimeUtils
import com.example.ui.TaskUiState
import com.example.ui.components.CompletionRing
import com.example.ui.components.TaskCard
import com.example.ui.components.taskPriorityColor
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.PriorityUrgentColor

@Composable
fun DashboardScreen(
  uiState: TaskUiState,
  hasOverlayPermission: Boolean,
  onQuickAddTask: (String) -> Unit,
  onToggleTask: (String) -> Unit,
  onToggleSubtask: (String, String, Boolean) -> Unit,
  onEditTask: (CloudTaskWithCategory) -> Unit,
  onDeleteTask: (String) -> Unit,
  onToggleOverlay: () -> Unit,
  onToggleHideOverlay: () -> Unit,
  onOpenOverlayTab: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var quickInput by remember { mutableStateOf("") }
  val stats = uiState.stats
  val prefs = uiState.preferences

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("dashboard_screen"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Header
    item {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = DateTimeUtils.formatFullDate(System.currentTimeMillis()),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = "Today's Focus",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        if (uiState.isLoading) {
          Spacer(modifier = Modifier.height(6.dp))
          LinearProgressIndicator(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(4.dp)),
          )
        }
      }
    }

    // Cloud Sync Error Banner (if any)
    uiState.cloudError?.let { errorMsg ->
      item {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_cloud_error"),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.CloudOff,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = errorMsg,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }
    }

    // Hero Progress Card
    item {
      Surface(
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          modifier = Modifier
            .background(
              Brush.linearGradient(
                colors = listOf(
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                  MaterialTheme.colorScheme.surface,
                )
              )
            )
            .padding(18.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            CompletionRing(
              percentage = stats.completionPercentage,
              size = 86.dp,
            )

            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = if (stats.todayTotalCount == 0) {
                  "Plan your day with clarity"
                } else {
                  "${stats.todayCompletedCount} of ${stats.todayTotalCount} today tasks done"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                StatChip(
                  label = "Pending",
                  count = stats.pendingCount,
                  color = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.weight(1f),
                )
                StatChip(
                  label = "Done",
                  count = stats.completedCount,
                  color = EmeraldTertiary,
                  modifier = Modifier.weight(1f),
                )
                StatChip(
                  label = "Overdue",
                  count = stats.overdueCount,
                  color = if (stats.overdueCount > 0) PriorityUrgentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
        }
      }
    }

    // Floating Overlay Quick-Control Card
    item {
      val isActive = prefs.isOverlayEnabled && hasOverlayPermission
      val isHidden = isActive && prefs.isTemporarilyHidden

      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(
          1.dp,
          if (isActive && !isHidden) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
          else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
        ),
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onOpenOverlayTab() }
          .testTag("dashboard_overlay_banner"),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(
                if (isActive && !isHidden) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
              ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.Layers,
              contentDescription = null,
              tint = if (isActive && !isHidden) Color.White else MaterialTheme.colorScheme.primary,
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = when {
                isHidden -> "Floating Bubble Hidden"
                isActive -> "Floating Bubble Active"
                !hasOverlayPermission -> "Enable Floating Bubble"
                else -> "Floating Mode Off"
              },
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = when {
                isHidden -> "Tap Show to bring it back on screen"
                isActive -> "Visible above other apps · Tap to customize"
                !hasOverlayPermission -> "Grant overlay permission to float tasks anywhere"
                else -> "Launch floating widget over other apps"
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (isActive) {
            FilledTonalButton(
              onClick = onToggleHideOverlay,
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
              modifier = Modifier.testTag("dashboard_overlay_hide_toggle"),
            ) {
              Icon(
                imageVector = if (isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (isHidden) "Show" else "Hide")
            }
          } else {
            Button(
              onClick = onToggleOverlay,
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              modifier = Modifier.testTag("dashboard_overlay_launch_button"),
            ) {
              Text(if (hasOverlayPermission) "Launch" else "Enable")
            }
          }
        }
      }
    }

    // Quick-Add Bar
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = quickInput,
          onValueChange = { quickInput = it },
          placeholder = { Text("Quick add a task for today...") },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          ),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(
            onDone = {
              if (quickInput.isNotBlank()) {
                onQuickAddTask(quickInput)
                quickInput = ""
              }
            }
          ),
          modifier = Modifier
            .weight(1f)
            .testTag("dashboard_quick_add_input"),
        )

        FilledIconButton(
          onClick = {
            if (quickInput.isNotBlank()) {
              onQuickAddTask(quickInput)
              quickInput = ""
            }
          },
          modifier = Modifier
            .size(52.dp)
            .testTag("dashboard_quick_add_button"),
        ) {
          Icon(Icons.Rounded.Add, contentDescription = "Quick add task")
        }
      }
    }

    // Upcoming Deadlines
    if (uiState.upcomingDeadlines.isNotEmpty()) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Schedule,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Upcoming Deadlines",
              style = MaterialTheme.typography.titleMedium,
            )
          }

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(uiState.upcomingDeadlines, key = { "deadline_${it.task.id}" }) { item ->
              val task = item.task
              val priorityColor = taskPriorityColor(task.priorityEnum)
              Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
                modifier = Modifier
                  .width(196.dp)
                  .clip(MaterialTheme.shapes.medium)
                  .clickable { onEditTask(item) },
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Box(
                      modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(priorityColor)
                    )
                    Text(
                      text = DateTimeUtils.formatDateLabel(task.dueDate),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary,
                    )
                    if (!task.dueTime.isNullOrBlank()) {
                      Text(
                        text = "· ${DateTimeUtils.formatTimeDisplay(task.dueTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
    }

    // Today's Task Section Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Today's Tasks",
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = "${uiState.todayTasks.count { !it.task.completed }} remaining",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (uiState.todayTasks.isEmpty() && !uiState.isLoading) {
      item {
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_empty_tasks),
              contentDescription = "No tasks scheduled for today",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(20.dp)),
            )
            Text(
              text = "Your today board is clear",
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "Add a quick task above or tap + to schedule tasks with priorities, checklists, and reminders.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(uiState.todayTasks, key = { it.task.id }) { item ->
        TaskCard(
          item = item,
          onToggleTask = onToggleTask,
          onToggleSubtask = { subId, done -> onToggleSubtask(item.task.id, subId, done) },
          onClick = { onEditTask(item) },
          onDelete = { onDeleteTask(item.task.id) },
        )
      }
    }
  }
}

@Composable
private fun StatChip(
  label: String,
  count: Int,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = color,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
