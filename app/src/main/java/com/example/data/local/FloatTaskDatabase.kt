package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CategoryEntity
import com.example.data.model.CompletionHistoryEntity
import com.example.data.model.SubtaskEntity
import com.example.data.model.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
  entities = [
    TaskEntity::class,
    CategoryEntity::class,
    SubtaskEntity::class,
    CompletionHistoryEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FloatTaskDatabase : RoomDatabase() {
  abstract fun taskDao(): TaskDao
  abstract fun categoryDao(): CategoryDao
  abstract fun subtaskDao(): SubtaskDao
  abstract fun completionHistoryDao(): CompletionHistoryDao

  companion object {
    private const val DATABASE_NAME = "floattask.db"

    @Volatile private var INSTANCE: FloatTaskDatabase? = null

    fun getInstance(context: Context): FloatTaskDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance =
          Room.databaseBuilder(
              context.applicationContext,
              FloatTaskDatabase::class.java,
              DATABASE_NAME,
            )
            .addCallback(SeedCategoriesCallback { INSTANCE })
            .build()
        INSTANCE = instance
        instance
      }
    }

    val DEFAULT_CATEGORIES = listOf(
      CategoryEntity(name = "Work", color = 0xFF6366F1),
      CategoryEntity(name = "Personal", color = 0xFF10B981),
      CategoryEntity(name = "Deep Focus", color = 0xFF0EA5E9),
      CategoryEntity(name = "Errands", color = 0xFFF59E0B),
    )

    private class SeedCategoriesCallback(
      private val dbProvider: () -> FloatTaskDatabase?
    ) : Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        CoroutineScope(Dispatchers.IO).launch {
          dbProvider()?.categoryDao()?.insertCategories(DEFAULT_CATEGORIES)
        }
      }
    }
  }
}
