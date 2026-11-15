package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [SavedLinkEntity::class],
  version = 3,
  exportSchema = false,
)
abstract class TagDatabase : RoomDatabase() {
  abstract fun tagDao(): TagDao

  companion object {
    @Volatile private var instance: TagDatabase? = null

    fun getInstance(context: Context): TagDatabase {
      return instance
        ?: synchronized(this) {
          instance
            ?: Room.databaseBuilder(
                context.applicationContext,
                TagDatabase::class.java,
                "taglink_database.db",
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { instance = it }
        }
    }
  }
}
