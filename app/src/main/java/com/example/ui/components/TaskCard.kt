package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TaskPriority
import com.example.data.remote.CloudTaskWithCategory
import com.example.domain.DateTimeUtils
import com.example.ui.theme.CompletedMutedColor
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.PriorityUrgentColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
  item: CloudTaskWithCategory,
  onToggleTask: (String) -> Unit,
  onToggleSubtask: (String, Boolean) -> Unit,
  onClick: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val task = item.task
  val subtasks = task.sortedSubtasks
  var expandSubtasks by remember { mutableStateOf(false) }

  val isOverdue = DateTimeUtils.isOverdue(task.dueDate, task.dueTime, task.completed)
  val priorityColor = taskPriorityColor(task.priorityEnum)
  val cardContainer by animateColorAsState(
    targetValue = if (task.completed) {
      MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    } else {
      MaterialTheme.colorScheme.surface
    },
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "task_card_bg",
  )

  Surface(
    shape = MaterialTheme.shapes.medium,
    color = cardContainer,
    tonalElevation = if (task.completed) 0.dp else 1.dp,
    border = BorderStroke(
      width = 1.dp,
      color = when {
        isOverdue -> PriorityUrgentColor.copy(alpha = 0.45f)
        task.completed -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
      },
    ),
    modifier = modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .clickable { onClick() }
      .testTag("task_card_${task.id}"),
  ) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
      Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Accessible 48dp touch target around circular completion checkbox
        Box(
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable { onToggleTask(task.id) }
            .testTag("task_checkbox_${task.id}"),
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier
              .size(24.dp)
              .clip(CircleShape)
              .border(
                width = 2.dp,
                color = if (task.completed) MaterialTheme.colorScheme.primary else priorityColor,
                shape = CircleShape,
              )
              .background(if (task.completed) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
          ) {
            if (task.completed) {
              Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Mark task incomplete",
                tint = Color.White,
                modifier = Modifier.size(15.dp),
              )
            }
          }
        }

        Column(modifier = Modifier.weight(1f)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = task.title,
              style = MaterialTheme.typography.titleMedium.copy(
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
              ),
              color = if (task.completed) CompletedMutedColor else MaterialTheme.colorScheme.onSurface,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )

            IconButton(
              onClick = onDelete,
              modifier = Modifier
                .size(36.dp)
                .testTag("task_delete_${task.id}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete task",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
              )
            }
          }

          if (task.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = task.description,
              style = MaterialTheme.typography.bodyMedium,
              color = if (task.completed) CompletedMutedColor else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            // Priority Pill
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = priorityColor.copy(alpha = 0.12f),
            ) {
              Text(
                text = task.priorityEnum.label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = priorityColor,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }

            // Category Pill
            item.category?.let { cat ->
              val catColor = Color(cat.color)
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = catColor.copy(alpha = 0.12f),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .size(6.dp)
                      .clip(CircleShape)
                      .background(catColor)
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = cat.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = catColor,
                  )
                }
              }
            }

            // Due Date & Time Pill
            if (task.dueDate != null) {
              val dateLabel = DateTimeUtils.formatDateLabel(task.dueDate)
              val timeLabel = DateTimeUtils.formatTimeDisplay(task.dueTime)
              val combined = if (timeLabel.isNotBlank()) "$dateLabel · $timeLabel" else dateLabel
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isOverdue) {
                  PriorityUrgentColor.copy(alpha = 0.12f)
                } else {
                  MaterialTheme.colorScheme.surfaceVariant
                },
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    tint = if (isOverdue) PriorityUrgentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(11.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isOverdue) "Overdue · $combined" else combined,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOverdue) PriorityUrgentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }

            // Recurring badge
            if (task.recurring) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = "Recurring task",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = task.recurrenceEnum.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
              }
            }

            // Subtask pill (tappable to expand checklist inline!)
            if (subtasks.isNotEmpty()) {
              val doneCount = subtasks.count { it.completed }
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.clickable { expandSubtasks = !expandSubtasks },
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Checklist,
                    contentDescription = "Toggle checklist",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(12.dp),
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "$doneCount/${subtasks.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                  )
                }
              }
            }
          }

          AnimatedVisibility(visible = expandSubtasks && subtasks.isNotEmpty()) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              subtasks.forEach { sub ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleSubtask(sub.id, !sub.completed) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .size(18.dp)
                      .clip(RoundedCornerShape(5.dp))
                      .border(
                        1.5.dp,
                        if (sub.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(5.dp),
                      )
                      .background(if (sub.completed) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                  ) {
                    if (sub.completed) {
                      Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                      )
                    }
                  }
                  Text(
                    text = sub.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                      textDecoration = if (sub.completed) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = if (sub.completed) CompletedMutedColor else MaterialTheme.colorScheme.onSurface,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

fun taskPriorityColor(priority: TaskPriority): Color {
  return when (priority) {
    TaskPriority.LOW -> PriorityLowColor
    TaskPriority.MEDIUM -> PriorityMediumColor
    TaskPriority.HIGH -> PriorityHighColor
    TaskPriority.URGENT -> PriorityUrgentColor
  }
}
