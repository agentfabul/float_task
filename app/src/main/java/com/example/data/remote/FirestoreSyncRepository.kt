package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.FloatTaskDatabase
import com.example.data.model.RecurrencePattern
import com.example.data.model.TaskPriority
import com.example.data.repository.BackupManager
import com.example.data.repository.BackupPayload
import com.example.domain.DateTimeUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreSyncRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
  private val onTaskChangedForReminders: (CloudTask, Boolean) -> Unit = { _, _ -> },
) {
  constructor(
    context: Context,
    onTaskChangedForReminders: (CloudTask, Boolean) -> Unit = { _, _ -> },
  ) : this(
    db =
      FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
      ),
    auth = Firebase.auth,
    onTaskChangedForReminders = onTaskChangedForReminders,
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  private fun userDoc(uid: String) = db.collection("users").document(uid)

  private fun tasksCol(uid: String) = userDoc(uid).collection("tasks")

  private fun categoriesCol(uid: String) = userDoc(uid).collection("categories")

  private fun historyCol(uid: String) = userDoc(uid).collection("history")

  suspend fun ensureUserInitialized(user: FirebaseUser): Result<Unit> {
    val uid = user.uid
    val path = "users/$uid"
    return try {
      val currentUid = requireUserId()
      check(currentUid == uid) { "Signed-in user mismatch." }

      val docRef = userDoc(uid)
      val existingSnap = docRef.get().await()
      val displayName =
        (user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore("@") ?: "FloatTask User")
          .take(80)
      if (!existingSnap.exists()) {
        val profileMap =
          mutableMapOf<String, Any?>(
            "userId" to uid,
            "displayName" to displayName,
            "email" to user.email?.take(120),
            "photoUrl" to user.photoUrl?.toString()?.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.set(profileMap).await()
      } else {
        val updateMap =
          mutableMapOf<String, Any?>(
            "displayName" to displayName,
            "email" to user.email?.take(120),
            "photoUrl" to user.photoUrl?.toString()?.take(500),
            "updatedAt" to FieldValue.serverTimestamp(),
          ).filterValues { it != null }
        docRef.update(updateMap).await()
      }

      // Seed default categories once if user has none yet
      val existingCats = categoriesCol(uid).limit(1).get().await()
      if (existingCats.isEmpty) {
        val batch = db.batch()
        FloatTaskDatabase.DEFAULT_CATEGORIES.forEach { cat ->
          val ref = categoriesCol(uid).document()
          batch.set(
            ref,
            CloudCategory(name = cat.name, color = cat.color).toCreateMap(uid),
          )
        }
        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  fun observeTasks(userId: String): Flow<List<CloudTask>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Refusing cross-user listener." }
    val path = "users/$uid/tasks"
    emitAll(
      tasksCol(uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(CloudTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeCategories(userId: String): Flow<List<CloudCategory>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Refusing cross-user listener." }
    val path = "users/$uid/categories"
    emitAll(
      categoriesCol(uid)
        .orderBy("createdAt", Query.Direction.ASCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            CloudCategory::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  fun observeHistory(userId: String): Flow<List<CloudHistoryEntry>> = flow {
    val uid = requireUserId()
    check(uid == userId) { "Refusing cross-user listener." }
    val path = "users/$uid/history"
    emitAll(
      historyCol(uid)
        .orderBy("completedAtMillis", Query.Direction.DESCENDING)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            CloudHistoryEntry::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  suspend fun saveTask(task: CloudTask): Result<String> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks"
    return try {
      val uid = requireUserId()
      val trimmedTitle = task.title.trim().take(200)
      require(trimmedTitle.isNotEmpty()) { "Task title cannot be empty." }

      val isCreate = task.id.isBlank()
      val docRef =
        if (isCreate) tasksCol(uid).document()
        else tasksCol(uid).document(task.id)
      docPath = docRef.path

      val normalized =
        task.copy(
          id = docRef.id,
          userId = uid,
          title = trimmedTitle,
          description = task.description.trim().take(2000),
        )

      if (isCreate) {
        docRef.set(normalized.toCreateMap(uid)).await()
      } else {
        docRef.update(normalized.toUpdateMap()).await()
      }
      onTaskChangedForReminders(normalized, !normalized.completed)
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (task.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun quickAddTask(
    title: String,
    priority: TaskPriority = TaskPriority.MEDIUM,
    dueDate: Long? = DateTimeUtils.startOfDayMillis(),
    categoryId: String? = null,
  ): Result<String> {
    val trimmed = title.trim().take(200)
    if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Title cannot be empty"))
    return saveTask(
      CloudTask(
        title = trimmed,
        priority = priority.name,
        dueDate = dueDate,
        categoryId = categoryId,
      )
    )
  }

  suspend fun toggleTaskCompletion(taskId: String): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
    return try {
      val uid = requireUserId()
      require(taskId.isNotBlank()) { "Task ID cannot be blank." }
      val taskRef = tasksCol(uid).document(taskId)
      docPath = taskRef.path

      val snap = taskRef.get().await()
      val existing =
        snap.toObject(CloudTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          ?: return Result.failure(IllegalStateException("Task not found"))

      val now = System.currentTimeMillis()
      val newCompleted = !existing.completed

      val batch = db.batch()
      if (newCompleted) {
        batch.update(
          taskRef,
          mapOf(
            "completed" to true,
            "completedAt" to now,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )

        val category =
          existing.categoryId?.takeIf { it.isNotBlank() }?.let { catId ->
            categoriesCol(uid)
              .document(catId)
              .get()
              .await()
              .toObject(CloudCategory::class.java)
          }

        val histRef = historyCol(uid).document()
        val histEntry =
          CloudHistoryEntry(
            taskId = existing.id,
            taskTitle = existing.title,
            categoryName = category?.name,
            categoryColor = category?.color,
            priority = existing.priority,
            completedAtMillis = now,
          )
        batch.set(histRef, histEntry.toCreateMap(uid))

        if (existing.recurring && existing.recurrenceEnum != RecurrencePattern.NONE) {
          val nextDueDate =
            DateTimeUtils.nextRecurrenceDueDate(existing.dueDate, existing.recurrenceEnum, now)
          val nextTaskRef = tasksCol(uid).document()
          val resetSubtasks = existing.subtasks.map { it.copy(completed = false) }
          val nextTask =
            existing.copy(
              id = nextTaskRef.id,
              dueDate = nextDueDate,
              completed = false,
              completedAt = null,
              subtasks = resetSubtasks,
            )
          batch.set(nextTaskRef, nextTask.toCreateMap(uid))
          batch.commit().await()
          onTaskChangedForReminders(nextTask, true)
        } else {
          batch.commit().await()
          onTaskChangedForReminders(existing.copy(completed = true), false)
        }
      } else {
        batch.update(
          taskRef,
          mapOf(
            "completed" to false,
            "completedAt" to FieldValue.delete(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.commit().await()
        onTaskChangedForReminders(existing.copy(completed = false), true)
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun toggleSubtaskCompletion(taskId: String, subtaskId: String, completed: Boolean): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
    return try {
      val uid = requireUserId()
      require(taskId.isNotBlank()) { "Task ID cannot be blank." }
      val taskRef = tasksCol(uid).document(taskId)
      docPath = taskRef.path
      val existing =
        taskRef.get().await().toObject(CloudTask::class.java)
          ?: return Result.failure(IllegalStateException("Task not found"))
      val updatedSubs =
        existing.subtasks
          .map { if (it.id == subtaskId) it.copy(completed = completed) else it }
          .take(30)
          .mapIndexed { idx, sub -> sub.copy(sortOrder = idx.toLong()).toMap() }
      taskRef.update(
        mapOf(
          "subtasks" to updatedSubs,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteTask(taskId: String): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/tasks/$taskId"
    return try {
      val uid = requireUserId()
      require(taskId.isNotBlank()) { "Task ID cannot be blank." }
      val taskRef = tasksCol(uid).document(taskId)
      docPath = taskRef.path
      taskRef.delete().await()
      onTaskChangedForReminders(CloudTask(id = taskId), false)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun saveCategory(category: CloudCategory): Result<String> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/categories"
    return try {
      val uid = requireUserId()
      val trimmedName = category.name.trim().take(40)
      require(trimmedName.isNotEmpty()) { "Category name cannot be empty." }
      val isCreate = category.id.isBlank()
      val docRef =
        if (isCreate) categoriesCol(uid).document()
        else categoriesCol(uid).document(category.id)
      docPath = docRef.path

      val normalized = category.copy(id = docRef.id, userId = uid, name = trimmedName)
      if (isCreate) {
        docRef.set(normalized.toCreateMap(uid)).await()
      } else {
        docRef.update(normalized.toUpdateMap()).await()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, if (category.id.isBlank()) OperationType.CREATE else OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteCategory(categoryId: String): Result<Unit> {
    var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/categories/$categoryId"
    return try {
      val uid = requireUserId()
      require(categoryId.isNotBlank()) { "Category ID cannot be blank." }
      val ref = categoriesCol(uid).document(categoryId)
      docPath = ref.path
      ref.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun clearHistory(): Result<Unit> {
    var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/history"
    return try {
      val uid = requireUserId()
      path = "users/$uid/history"
      val snaps = historyCol(uid).get().await()
      if (!snaps.isEmpty) {
        snaps.documents.chunked(400).forEach { chunk ->
          val batch = db.batch()
          chunk.forEach { batch.delete(it.reference) }
          batch.commit().await()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun exportBackupJson(): Result<String> {
    return try {
      val uid = requireUserId()
      val cats =
        categoriesCol(uid)
          .get()
          .await()
          .toObjects(CloudCategory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      val tasks =
        tasksCol(uid)
          .get()
          .await()
          .toObjects(CloudTask::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      val history =
        historyCol(uid)
          .get()
          .await()
          .toObjects(CloudHistoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

      // Map string IDs to deterministic positive Longs for cross-compatible BackupPayload JSON
      val catIdMap = cats.mapIndexed { idx, cat -> cat.id to (idx + 1L) }.toMap()
      val taskIdMap = tasks.mapIndexed { idx, task -> task.id to (idx + 1L) }.toMap()

      val localCategories =
        cats.mapIndexed { idx, cat ->
          com.example.data.model.CategoryEntity(
            id = idx + 1L,
            name = cat.name,
            color = cat.color,
          )
        }
      val localTasks =
        tasks.mapIndexed { idx, task ->
          com.example.data.model.TaskEntity(
            id = idx + 1L,
            title = task.title,
            description = task.description,
            categoryId = task.categoryId?.let { catIdMap[it] },
            priority = task.priorityEnum,
            dueDate = task.dueDate,
            dueTime = task.dueTime,
            isCompleted = task.completed,
            completedAt = task.completedAt,
            createdAt = task.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
            updatedAt = task.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
            isRecurring = task.recurring,
            recurrencePattern = task.recurrenceEnum,
          )
        }
      var nextSubId = 1L
      val localSubtasks =
        tasks.flatMap { task ->
          val parentLongId = taskIdMap[task.id] ?: 0L
          task.sortedSubtasks.mapIndexed { idx, sub ->
            com.example.data.model.SubtaskEntity(
              id = nextSubId++,
              taskId = parentLongId,
              title = sub.title,
              isCompleted = sub.completed,
              sortOrder = idx,
            )
          }
        }
      val localHistory =
        history.mapIndexed { idx, entry ->
          com.example.data.model.CompletionHistoryEntity(
            id = idx + 1L,
            taskId = taskIdMap[entry.taskId] ?: 0L,
            taskTitle = entry.taskTitle,
            categoryName = entry.categoryName,
            categoryColor = entry.categoryColor,
            priority = entry.priorityEnum,
            completedAt = entry.completedAtMillis,
          )
        }

      Result.success(
        BackupManager.serializeToJson(
          BackupPayload(
            categories = localCategories,
            tasks = localTasks,
            subtasks = localSubtasks,
            history = localHistory,
          )
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "users/${auth.currentUser?.uid}/backup")
      Result.failure(e)
    }
  }

  suspend fun restoreBackupJson(json: String): Result<Unit> {
    return try {
      val uid = requireUserId()
      val payload = BackupManager.parseFromJson(json)
      val batch = db.batch()

      val catOldToNewId = mutableMapOf<Long, String>()
      payload.categories.forEach { cat ->
        val ref = categoriesCol(uid).document()
        catOldToNewId[cat.id] = ref.id
        batch.set(ref, CloudCategory(name = cat.name, color = cat.color).toCreateMap(uid))
      }

      val subtasksByTask = payload.subtasks.groupBy { it.taskId }
      val taskOldToNewId = mutableMapOf<Long, String>()
      payload.tasks.forEach { task ->
        val ref = tasksCol(uid).document()
        taskOldToNewId[task.id] = ref.id
        val subs =
          (subtasksByTask[task.id] ?: emptyList())
            .sortedBy { it.sortOrder }
            .take(30)
            .mapIndexed { idx, sub ->
              CloudSubtask(
                id = UUID.randomUUID().toString(),
                title = sub.title,
                completed = sub.isCompleted,
                sortOrder = idx.toLong(),
              )
            }
        val cloudTask =
          CloudTask(
            id = ref.id,
            userId = uid,
            title = task.title,
            description = task.description,
            categoryId = task.categoryId?.let { catOldToNewId[it] },
            priority = task.priority.name,
            dueDate = task.dueDate,
            dueTime = task.dueTime,
            completed = task.isCompleted,
            completedAt = task.completedAt,
            recurring = task.isRecurring,
            recurrencePattern = task.recurrencePattern.name,
            subtasks = subs,
          )
        batch.set(ref, cloudTask.toCreateMap(uid))
      }

      payload.history.forEach { entry ->
        val ref = historyCol(uid).document()
        val cloudEntry =
          CloudHistoryEntry(
            taskId = taskOldToNewId[entry.taskId] ?: "restored_${entry.taskId}",
            taskTitle = entry.taskTitle,
            categoryName = entry.categoryName,
            categoryColor = entry.categoryColor,
            priority = entry.priority.name,
            completedAtMillis = entry.completedAt,
          )
        batch.set(ref, cloudEntry.toCreateMap(uid))
      }

      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/restore")
      Result.failure(e)
    }
  }
}
