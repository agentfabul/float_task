package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.TaskPriority
import com.example.data.remote.CloudTaskWithCategory
import com.example.domain.TaskOrganizationTab
import com.example.ui.TaskUiState
import com.example.ui.components.TaskCard
import com.example.ui.components.taskPriorityColor

@Composable
fun TasksScreen(
  uiState: TaskUiState,
  onSelectTab: (TaskOrganizationTab) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelectCategory: (String?) -> Unit,
  onSelectPriority: (TaskPriority?) -> Unit,
  onManageCategories: () -> Unit,
  onToggleTask: (String) -> Unit,
  onToggleSubtask: (String, String, Boolean) -> Unit,
  onEditTask: (CloudTaskWithCategory) -> Unit,
  onDeleteTask: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .testTag("tasks_screen"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Tasks & Lists",
          style = MaterialTheme.typography.headlineMedium,
        )
        TextButton(
          onClick = onManageCategories,
          modifier = Modifier.testTag("manage_categories_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Category,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(6.dp))
          Text("Categories")
        }
      }

      if (uiState.isLoading) {
        LinearProgressIndicator(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp)),
        )
      }

      uiState.cloudError?.let { errorMsg ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.CloudOff,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = errorMsg,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }

      OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text("Search tasks, notes, or categories...") },
        leadingIcon = {
          Icon(Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
          if (uiState.searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChange("") }) {
              Icon(Icons.Rounded.Close, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("tasks_search_input"),
      )

      // Organization Tabs
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TaskOrganizationTab.entries) { tab ->
          val selected = uiState.selectedTab == tab
          FilterChip(
            selected = selected,
            onClick = { onSelectTab(tab) },
            label = { Text(tab.label) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primary,
              selectedLabelColor = Color.White,
            ),
            modifier = Modifier.testTag("org_tab_${tab.name.lowercase()}"),
          )
        }
      }

      // Categories + Priority Filter Row
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uiState.categories, key = { "cat_${it.id}" }) { cat ->
          val selected = uiState.selectedCategoryId == cat.id
          val catColor = Color(cat.color)
          FilterChip(
            selected = selected,
            onClick = { onSelectCategory(cat.id) },
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

        items(TaskPriority.entries, key = { "prio_${it.name}" }) { prio ->
          val selected = uiState.selectedPriority == prio
          val prioColor = taskPriorityColor(prio)
          FilterChip(
            selected = selected,
            onClick = { onSelectPriority(prio) },
            label = { Text(prio.label) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = prioColor.copy(alpha = 0.16f),
              selectedLabelColor = prioColor,
            ),
          )
        }
      }
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (uiState.filteredTasks.isEmpty() && !uiState.isLoading) {
        item {
          Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 12.dp),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_empty_tasks),
                contentDescription = "No matching tasks",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .size(130.dp)
                  .clip(RoundedCornerShape(18.dp)),
              )
              Text(
                text = "No tasks in ${uiState.selectedTab.label}",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "Adjust filters or tap + to add a task.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(uiState.filteredTasks, key = { it.task.id }) { item ->
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
}
