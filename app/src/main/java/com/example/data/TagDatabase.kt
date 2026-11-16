package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [SavedLinkEntity::class],
  version = 4,
  exportSchema = false,
)
abstract class TagDatabase : RoomDatabase() {
  abstract fun tagDao(): TagDao

  companion object {
    @Volatile private var instance: TagDatabase? = null

    private val MIGRATION_3_4 =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE saved_cards ADD COLUMN coverImagePath TEXT")
        }
      }

    fun getInstance(context: Context): TagDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                TagDatabase::class.java,
                "taglink_database.db",
              )
              .addMigrations(MIGRATION_3_4)
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { instance = it }
        }
    }
  }
}
