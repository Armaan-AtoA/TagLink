package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
  @Query("SELECT * FROM saved_cards ORDER BY lastProgrammedAt DESC")
  fun observeSavedLinks(): Flow<List<SavedLinkEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSavedLink(link: SavedLinkEntity)

  @Query("DELETE FROM saved_cards WHERE id = :id")
  suspend fun deleteSavedLinkById(id: Long)
}
