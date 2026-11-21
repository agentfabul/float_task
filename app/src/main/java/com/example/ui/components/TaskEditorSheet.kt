package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.RecurrencePattern
import com.example.data.model.TaskPriority
import com.example.data.remote.CloudCategory
import com.example.data.remote.CloudSubtask
import com.example.data.remote.CloudTask
import com.example.data.remote.CloudTaskWithCategory
import com.example.domain.DateTimeUtils
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(
  existingTask: CloudTaskWithCategory?,
  categories: List<CloudCategory>,
  onManageCategories: () -> Unit,
  onSave: (
    existingTask: CloudTask?,
    title: String,
    description: String,
    categoryId: String?,
    priority: TaskPriority,
    dueDate: Long?,
    dueTime: String?,
    isRecurring: Boolean,
    recurrencePattern: RecurrencePattern,
    subtasks: List<CloudSubtask>,
  ) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var title by remember(existingTask) { mutableStateOf(existingTask?.task?.title ?: "") }
  var description by remember(existingTask) { mutableStateOf(existingTask?.task?.description ?: "") }
  var selectedCategoryId by remember(existingTask) { mutableStateOf<String?>(existingTask?.task?.categoryId) }
  var selectedPriority by remember(existingTask) {
    mutableStateOf(existingTask?.task?.priorityEnum ?: TaskPriority.MEDIUM)
  }
  var dueDate by remember(existingTask) {
    mutableStateOf<Long?>(existingTask?.task?.dueDate ?: DateTimeUtils.startOfDayMillis())
  }
  var dueTime by remember(existingTask) { mutableStateOf(existingTask?.task?.dueTime) }
  var isRecurring by remember(existingTask) { mutableStateOf(existingTask?.task?.recurring ?: false) }
  var recurrencePattern by remember(existingTask) {
    mutableStateOf(
      existingTask?.task?.recurrenceEnum?.takeIf { it != RecurrencePattern.NONE }
        ?: RecurrencePattern.DAILY
    )
  }

  val subtasks = remember(existingTask) {
    mutableStateListOf<CloudSubtask>().apply {
      existingTask?.task?.sortedSubtasks?.let { addAll(it) }
    }
  }
  var newSubtaskTitle by remember { mutableStateOf("") }

  var showDatePicker by remember { mutableStateOf(false) }
  var showTimePicker by remember { mutableStateOf(false) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .navigationBarsPadding()
        .imePadding(),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (existingTask == null) "New Task" else "Edit Task",
          style = MaterialTheme.typography.headlineSmall,
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Close editor")
        }
      }

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Task title") },
        placeholder = { Text("What needs to get done?") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("editor_title_input"),
      )

      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text("Notes or description (optional)") },
        minLines = 2,
        maxLines = 4,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("editor_description_input"),
      )

      // Priority Selection
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Priority", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TaskPriority.entries.forEach { prio ->
            val selected = selectedPriority == prio
            val color = taskPriorityColor(prio)
            FilterChip(
              selected = selected,
              onClick = { selectedPriority = prio },
              label = { Text(prio.label) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = color.copy(alpha = 0.18f),
                selectedLabelColor = color,
              ),
              modifier = Modifier
                .weight(1f)
                .testTag("editor_priority_${prio.name.lowercase()}"),
            )
          }
        }
      }

      // Due Date & Time
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Schedule", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { showDatePicker = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("editor_date_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.CalendarToday,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = dueDate?.let { DateTimeUtils.formatDateLabel(it) } ?: "Set date",
              maxLines = 1,
            )
          }

          OutlinedButton(
            onClick = { showTimePicker = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("editor_time_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.AccessTime,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (!dueTime.isNullOrBlank()) DateTimeUtils.formatTimeDisplay(dueTime) else "Set time",
              maxLines = 1,
            )
          }
        }
        if (dueDate != null || dueTime != null) {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (dueDate != null) {
              TextButton(onClick = {
                dueDate = null
                dueTime = null
              }) {
                Text("Clear schedule")
              }
            } else if (dueTime != null) {
              TextButton(onClick = { dueTime = null }) {
                Text("Clear time")
              }
            }
          }
        }
      }

      // Categories
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Category", style = MaterialTheme.typography.labelLarge)
          TextButton(onClick = onManageCategories) {
            Text("+ Manage")
          }
        }
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          FilterChip(
            selected = selectedCategoryId == null,
            onClick = { selectedCategoryId = null },
            label = { Text("None") },
          )
          categories.forEach { cat ->
            val selected = selectedCategoryId == cat.id
            val catColor = Color(cat.color)
            FilterChip(
              selected = selected,
              onClick = { selectedCategoryId = cat.id },
              leadingIcon = {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(catColor)
                )
              },
              label = { Text(cat.name) },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = catColor.copy(alpha = 0.16f),
                selectedLabelColor = catColor,
              ),
            )
          }
        }
      }

      // Recurring Toggle
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
      ) {
        Column(modifier = Modifier.padding(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Repeat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column {
                Text("Recurring task", style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "Automatically schedule next occurrence on completion",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Switch(
              checked = isRecurring,
              onCheckedChange = { isRecurring = it },
              modifier = Modifier.testTag("editor_recurring_switch"),
            )
          }

          if (isRecurring) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(RecurrencePattern.DAILY, RecurrencePattern.WEEKLY, RecurrencePattern.MONTHLY)
                .forEach { pattern ->
                  FilterChip(
                    selected = recurrencePattern == pattern,
                    onClick = { recurrencePattern = pattern },
                    label = { Text(pattern.label) },
                  )
                }
            }
          }
        }
      }

      // Subtasks / Checklist
      HorizontalDivider()
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Subtasks & Checklist", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = newSubtaskTitle,
            onValueChange = { newSubtaskTitle = it },
            placeholder = { Text("Add checklist step...") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("editor_subtask_input"),
          )
          FilledTonalIconButton(
            onClick = {
              if (newSubtaskTitle.isNotBlank() && subtasks.size < 30) {
                subtasks.add(
                  CloudSubtask(
                    id = UUID.randomUUID().toString(),
                    title = newSubtaskTitle.trim(),
                    sortOrder = subtasks.size.toLong(),
                  )
                )
                newSubtaskTitle = ""
              }
            },
            modifier = Modifier
              .size(48.dp)
              .testTag("editor_subtask_add_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add subtask")
          }
        }

        subtasks.forEachIndexed { index, sub ->
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "${index + 1}. ${sub.title}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = { subtasks.removeAt(index) },
                modifier = Modifier.size(34.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.DeleteOutline,
                  contentDescription = "Remove subtask",
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      Button(
        onClick = {
          onSave(
            existingTask?.task,
            title,
            description,
            selectedCategoryId,
            selectedPriority,
            dueDate,
            dueTime,
            isRecurring,
            recurrencePattern,
            subtasks.toList(),
          )
        },
        enabled = title.isNotBlank(),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("editor_save_button"),
      ) {
        Text(
          text = if (existingTask == null) "Save Task" else "Update Task",
          style = MaterialTheme.typography.titleMedium,
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  if (showDatePicker) {
    val initialUtcMillis = remember(dueDate) {
      val local = Calendar.getInstance().apply {
        timeInMillis = dueDate ?: System.currentTimeMillis()
      }
      Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
      }.timeInMillis
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          datePickerState.selectedDateMillis?.let { utcMillis ->
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
              timeInMillis = utcMillis
            }
            val localCal = Calendar.getInstance().apply {
              clear()
              set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
              )
            }
            dueDate = DateTimeUtils.startOfDayMillis(localCal.timeInMillis)
          }
          showDatePicker = false
        }) {
          Text("Select")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  if (showTimePicker) {
    val parts = dueTime?.split(":")
    val initialHour = parts?.getOrNull(0)?.toIntOrNull() ?: 9
    val initialMinute = parts?.getOrNull(1)?.toIntOrNull() ?: 0
    val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      title = { Text("Select Due Time") },
      text = { TimePicker(state = timeState) },
      confirmButton = {
        TextButton(onClick = {
          dueTime = String.format(Locale.US, "%02d:%02d", timeState.hour, timeState.minute)
          if (dueDate == null) {
            dueDate = DateTimeUtils.startOfDayMillis()
          }
          showTimePicker = false
        }) {
          Text("Set Time")
        }
      },
      dismissButton = {
        TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
      },
    )
  }
}
