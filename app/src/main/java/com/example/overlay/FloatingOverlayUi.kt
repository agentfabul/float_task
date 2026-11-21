package com.example.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TaskPriority
import com.example.data.preferences.OverlayPreferences
import com.example.data.remote.CloudTaskWithCategory
import com.example.domain.DateTimeUtils
import com.example.domain.TaskFilterEngine
import com.example.ui.theme.FloatTaskTheme
import com.example.ui.theme.PriorityHighColor
import com.example.ui.theme.PriorityLowColor
import com.example.ui.theme.PriorityMediumColor
import com.example.ui.theme.PriorityUrgentColor

@Composable
fun FloatingOverlayRoot(
  isExpanded: Boolean,
  isAuthenticated: Boolean,
  tasks: List<CloudTaskWithCategory>,
  pendingCount: Int,
  preferences: OverlayPreferences,
  onBubbleTap: () -> Unit,
  onBubbleDragStart: () -> Unit,
  onBubbleDrag: (dx: Float, dy: Float) -> Unit,
  onBubbleDragEnd: () -> Unit,
  onMinimize: () -> Unit,
  onHideTemporarily: () -> Unit,
  onCloseOverlay: () -> Unit,
  onOpenFullApp: () -> Unit,
  onToggleTask: (String) -> Unit,
  onQuickAddTask: (String, TaskPriority) -> Unit,
) {
  FloatTaskTheme(themeMode = preferences.themeMode) {
    AnimatedContent(
      targetState = isExpanded,
      transitionSpec = {
        (fadeIn() + scaleIn(initialScale = 0.88f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.9f))
      },
      label = "overlay_mode_transition",
    ) { expanded ->
      if (!expanded) {
        FloatingBubbleView(
          pendingCount = if (isAuthenticated) pendingCount else 0,
          preferences = preferences,
          onTap = onBubbleTap,
          onDragStart = onBubbleDragStart,
          onDrag = onBubbleDrag,
          onDragEnd = onBubbleDragEnd,
        )
      } else {
        FloatingMiniPanelView(
          isAuthenticated = isAuthenticated,
          tasks = tasks,
          pendingCount = pendingCount,
          preferences = preferences,
          onMinimize = onMinimize,
          onHideTemporarily = onHideTemporarily,
          onCloseOverlay = onCloseOverlay,
          onOpenFullApp = onOpenFullApp,
          onToggleTask = onToggleTask,
          onQuickAddTask = onQuickAddTask,
        )
      }
    }
  }
}

@Composable
fun FloatingBubbleView(
  pendingCount: Int,
  preferences: OverlayPreferences,
  onTap: () -> Unit,
  onDragStart: () -> Unit,
  onDrag: (Float, Float) -> Unit,
  onDragEnd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var isDragging by remember { mutableStateOf(false) }
  val scale by animateFloatAsState(
    targetValue = if (isDragging) 1.09f else 1f,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
    label = "bubble_drag_scale",
  )

  val accent = Color(preferences.accentColorHex)
  val bubbleSize = preferences.bubbleSize.dpValue.dp

  Box(
    modifier = modifier
      .padding(6.dp)
      .scale(scale)
      .alpha(preferences.overlayOpacity)
      .testTag("floating_bubble"),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = CircleShape,
      shadowElevation = 10.dp,
      tonalElevation = 4.dp,
      color = Color.Transparent,
      modifier = Modifier
        .size(bubbleSize)
        .shadow(10.dp, CircleShape, ambientColor = accent, spotColor = accent)
        .clip(CircleShape)
        .background(
          brush = Brush.linearGradient(
            colors = listOf(accent, accent.copy(alpha = 0.84f))
          )
        )
        .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = {
              isDragging = true
              onDragStart()
            },
            onDrag = { change, dragAmount ->
              change.consume()
              onDrag(dragAmount.x, dragAmount.y)
            },
            onDragEnd = {
              isDragging = false
              onDragEnd()
            },
            onDragCancel = {
              isDragging = false
              onDragEnd()
            },
          )
        }
        .clickable { onTap() },
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = Icons.Rounded.TaskAlt,
          contentDescription = "Open FloatTask mini panel",
          tint = Color.White,
          modifier = Modifier.size((preferences.bubbleSize.dpValue * 0.48f).dp),
        )
      }
    }

    if (preferences.showPendingBadge && pendingCount > 0) {
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .offset(x = 2.dp, y = (-2).dp)
          .size(22.dp)
          .clip(CircleShape)
          .background(PriorityUrgentColor)
          .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = if (pendingCount > 99) "99+" else pendingCount.toString(),
          color = Color.White,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun FloatingMiniPanelView(
  isAuthenticated: Boolean,
  tasks: List<CloudTaskWithCategory>,
  pendingCount: Int,
  preferences: OverlayPreferences,
  onMinimize: () -> Unit,
  onHideTemporarily: () -> Unit,
  onCloseOverlay: () -> Unit,
  onOpenFullApp: () -> Unit,
  onToggleTask: (String) -> Unit,
  onQuickAddTask: (String, TaskPriority) -> Unit,
) {
  var quickTitle by remember { mutableStateOf("") }
  var quickPriority by remember { mutableStateOf(TaskPriority.MEDIUM) }
  var showAllPending by remember { mutableStateOf(false) }

  val filteredTasks = remember(tasks, showAllPending) {
    TaskFilterEngine.todayAndUrgentCloudForOverlay(tasks, includeAllPending = showAllPending)
  }
  val accent = Color(preferences.accentColorHex)

  Surface(
    shape = RoundedCornerShape(24.dp),
    shadowElevation = 16.dp,
    tonalElevation = 6.dp,
    color = MaterialTheme.colorScheme.surface.copy(alpha = preferences.overlayOpacity.coerceAtLeast(0.85f)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    modifier = Modifier
      .widthIn(min = 280.dp, max = 330.dp)
      .padding(6.dp)
      .testTag("floating_mini_panel"),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Compact Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(accent),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.TaskAlt,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(16.dp),
            )
          }
          Column {
            Text(
              text = "FloatTask",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = if (isAuthenticated) "$pendingCount pending" else "Sign in required",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onOpenFullApp,
            modifier = Modifier
              .size(34.dp)
              .testTag("overlay_open_full_app_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
              contentDescription = "Expand to full app",
              tint = accent,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onHideTemporarily,
            modifier = Modifier
              .size(34.dp)
              .testTag("overlay_hide_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.VisibilityOff,
              contentDescription = "Hide floating widget temporarily",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
          IconButton(
            onClick = onMinimize,
            modifier = Modifier
              .size(34.dp)
              .testTag("overlay_minimize_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.KeyboardArrowDown,
              contentDescription = "Minimize to floating bubble",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp),
            )
          }
          IconButton(
            onClick = onCloseOverlay,
            modifier = Modifier
              .size(34.dp)
              .testTag("overlay_close_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Turn off floating widget",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      if (!isAuthenticated) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(32.dp),
          )
          Text(
            text = "Sign in with Google",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Open FloatTask to sign in and sync your tasks in real time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(
            onClick = onOpenFullApp,
            shape = RoundedCornerShape(12.dp),
          ) {
            Text("Open FloatTask")
          }
        }
      } else {
        // Quick-Add Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          OutlinedTextField(
            value = quickTitle,
            onValueChange = { quickTitle = it },
            placeholder = {
              Text(
                text = "Quick add task...",
                style = MaterialTheme.typography.bodySmall,
              )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = accent,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
              onDone = {
                if (quickTitle.isNotBlank()) {
                  onQuickAddTask(quickTitle, quickPriority)
                  quickTitle = ""
                }
              }
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("overlay_quick_add_input"),
          )

          val priorityColor = priorityBadgeColor(quickPriority)
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = priorityColor.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, priorityColor.copy(alpha = 0.4f)),
            modifier = Modifier
              .clickable {
                quickPriority = when (quickPriority) {
                  TaskPriority.LOW -> TaskPriority.MEDIUM
                  TaskPriority.MEDIUM -> TaskPriority.HIGH
                  TaskPriority.HIGH -> TaskPriority.URGENT
                  TaskPriority.URGENT -> TaskPriority.LOW
                }
              }
              .testTag("overlay_priority_cycle"),
          ) {
            Text(
              text = quickPriority.label.take(3).uppercase(),
              color = priorityColor,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
          }

          FilledIconButton(
            onClick = {
              if (quickTitle.isNotBlank()) {
                onQuickAddTask(quickTitle, quickPriority)
                quickTitle = ""
              }
            },
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = accent,
              contentColor = Color.White,
            ),
            modifier = Modifier
              .size(42.dp)
              .testTag("overlay_quick_add_submit"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = "Add task",
              modifier = Modifier.size(20.dp),
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Scope toggle pills
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OverlayScopeChip(
            label = "Today & Focus",
            selected = !showAllPending,
            accent = accent,
            onClick = { showAllPending = false },
          )
          OverlayScopeChip(
            label = "All Pending ($pendingCount)",
            selected = showAllPending,
            accent = accent,
            onClick = { showAllPending = true },
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredTasks.isEmpty()) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(30.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "All caught up!",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Add a quick task above or open FloatTask",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 240.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            items(filteredTasks, key = { it.task.id }) { item ->
              OverlayTaskRow(
                item = item,
                accent = accent,
                onToggle = { onToggleTask(item.task.id) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun OverlayScopeChip(
  label: String,
  selected: Boolean,
  accent: Color,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
    border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.45f)) else null,
    modifier = Modifier.clickable { onClick() },
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    )
  }
}

@Composable
private fun OverlayTaskRow(
  item: CloudTaskWithCategory,
  accent: Color,
  onToggle: () -> Unit,
) {
  val task = item.task
  val priorityColor = priorityBadgeColor(task.priorityEnum)
  val isOverdue = DateTimeUtils.isOverdue(task.dueDate, task.dueTime, task.completed)

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onToggle() },
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier = Modifier
          .size(22.dp)
          .clip(CircleShape)
          .border(1.8.dp, priorityColor, CircleShape)
          .background(if (task.completed) accent else Color.Transparent),
        contentAlignment = Alignment.Center,
      ) {
        if (task.completed) {
          Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = "Completed",
            tint = Color.White,
            modifier = Modifier.size(14.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = task.title,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(priorityColor)
          )
          Text(
            text = task.priorityEnum.label,
            style = MaterialTheme.typography.labelSmall,
            color = priorityColor,
          )
          if (task.dueDate != null) {
            val timeStr = if (!task.dueTime.isNullOrBlank()) " · ${DateTimeUtils.formatTimeDisplay(task.dueTime)}" else ""
            Text(
              text = "${DateTimeUtils.formatDateLabel(task.dueDate)}$timeStr",
              style = MaterialTheme.typography.labelSmall,
              color = if (isOverdue) PriorityUrgentColor else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

private fun priorityBadgeColor(priority: TaskPriority): Color {
  return when (priority) {
    TaskPriority.LOW -> PriorityLowColor
    TaskPriority.MEDIUM -> PriorityMediumColor
    TaskPriority.HIGH -> PriorityHighColor
    TaskPriority.URGENT -> PriorityUrgentColor
  }
}
