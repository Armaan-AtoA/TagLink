package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_cards")
data class SavedLinkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val label: String,
  val summary: String,
  val recordCount: Int,
  val ndefBytes: ByteArray,
  val ndefByteSize: Int,
  val isReadOnly: Boolean,
  val lastProgrammedAt: Long,
  val coverImagePath: String? = null,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SavedLinkEntity) return false
    return id == other.id &&
      label == other.label &&
      summary == other.summary &&
      recordCount == other.recordCount &&
      ndefBytes.contentEquals(other.ndefBytes) &&
      ndefByteSize == other.ndefByteSize &&
      isReadOnly == other.isReadOnly &&
      lastProgrammedAt == other.lastProgrammedAt &&
      coverImagePath == other.coverImagePath
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + label.hashCode()
    result = 31 * result + summary.hashCode()
    result = 31 * result + recordCount
    result = 31 * result + ndefBytes.contentHashCode()
    result = 31 * result + ndefByteSize
    result = 31 * result + isReadOnly.hashCode()
    result = 31 * result + lastProgrammedAt.hashCode()
    result = 31 * result + (coverImagePath?.hashCode() ?: 0)
    return result
  }
}
