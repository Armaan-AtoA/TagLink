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

  @Query("SELECT * FROM saved_cards WHERE id = :id LIMIT 1")
  suspend fun getSavedLinkById(id: Long): SavedLinkEntity?

  @Query("SELECT * FROM saved_cards WHERE ndefBytes = :ndefBytes LIMIT 1")
  suspend fun findByNdefBytes(ndefBytes: ByteArray): SavedLinkEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSavedLink(link: SavedLinkEntity)

  @Query("UPDATE saved_cards SET coverImagePath = :coverImagePath WHERE id = :id")
  suspend fun updateCoverImagePath(id: Long, coverImagePath: String?)

  @Query("DELETE FROM saved_cards WHERE id = :id")
  suspend fun deleteSavedLinkById(id: Long)
}
