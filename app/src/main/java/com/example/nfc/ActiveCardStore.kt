package com.example.nfc

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

data class ActiveCardSnapshot(
  val label: String,
  val summary: String,
  val records: List<NdefRecordDraft>,
  val ndefMessageBytes: ByteArray,
  val isEmulating: Boolean,
  val isReadOnly: Boolean,
) {
  val hasPayload: Boolean get() = ndefMessageBytes.isNotEmpty()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ActiveCardSnapshot) return false
    return label == other.label &&
      summary == other.summary &&
      records == other.records &&
      ndefMessageBytes.contentEquals(other.ndefMessageBytes) &&
      isEmulating == other.isEmulating &&
      isReadOnly == other.isReadOnly
  }

  override fun hashCode(): Int {
    var result = label.hashCode()
    result = 31 * result + summary.hashCode()
    result = 31 * result + records.hashCode()
    result = 31 * result + ndefMessageBytes.contentHashCode()
    result = 31 * result + isEmulating.hashCode()
    result = 31 * result + isReadOnly.hashCode()
    return result
  }
}

class ActiveCardStore(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun readSnapshot(): ActiveCardSnapshot {
    val label = prefs.getString(KEY_ACTIVE_LABEL, "") ?: ""
    val isEmulating = prefs.getBoolean(KEY_IS_EMULATING, false)
    val isReadOnly = prefs.getBoolean(KEY_IS_READ_ONLY, true)

    val b64 = prefs.getString(KEY_NDEF_B64, null)
    val ndefBytes =
      if (!b64.isNullOrEmpty()) {
        runCatching { Base64.decode(b64, Base64.NO_WRAP) }.getOrDefault(ByteArray(0))
      } else {
        val legacyUri = prefs.getString(KEY_LEGACY_URI, "") ?: ""
        if (legacyUri.isNotEmpty()) NdefUriEncoder.encodeUriNdefMessage(legacyUri) else ByteArray(0)
      }

    val records = NdefMessageCodec.decodeRecords(ndefBytes)
    val summary = NdefMessageCodec.summarizeRecords(records)
    return ActiveCardSnapshot(
      label = label,
      summary = summary,
      records = records,
      ndefMessageBytes = ndefBytes,
      isEmulating = isEmulating,
      isReadOnly = isReadOnly,
    )
  }

  fun writeRecords(
    records: List<NdefRecordDraft>,
    label: String,
    isReadOnly: Boolean,
  ): ActiveCardSnapshot {
    val ndefBytes = NdefMessageCodec.encodeRecords(records)
    return writeRawNdefBytes(ndefBytes = ndefBytes, label = label, isReadOnly = isReadOnly)
  }

  fun writeRawNdefBytes(
    ndefBytes: ByteArray,
    label: String,
    isReadOnly: Boolean? = null,
  ): ActiveCardSnapshot {
    val encoded = Base64.encodeToString(ndefBytes, Base64.NO_WRAP)
    val editor =
      prefs
        .edit()
        .putString(KEY_NDEF_B64, encoded)
        .putString(KEY_ACTIVE_LABEL, label.trim())
        .remove(KEY_LEGACY_URI)
    if (isReadOnly != null) {
      editor.putBoolean(KEY_IS_READ_ONLY, isReadOnly)
    }
    editor.apply()
    return readSnapshot()
  }

  fun clearCard(): ActiveCardSnapshot {
    prefs
      .edit()
      .remove(KEY_NDEF_B64)
      .remove(KEY_LEGACY_URI)
      .putString(KEY_ACTIVE_LABEL, "")
      .putBoolean(KEY_IS_EMULATING, false)
      .apply()
    return readSnapshot()
  }

  fun setEmulating(enabled: Boolean): ActiveCardSnapshot {
    prefs.edit().putBoolean(KEY_IS_EMULATING, enabled).apply()
    return readSnapshot()
  }

  fun setReadOnly(readOnly: Boolean): ActiveCardSnapshot {
    prefs.edit().putBoolean(KEY_IS_READ_ONLY, readOnly).apply()
    return readSnapshot()
  }

  companion object {
    private const val PREFS_NAME = "taglink_active_card"
    private const val KEY_LEGACY_URI = "active_uri"
    private const val KEY_NDEF_B64 = "ndef_b64"
    private const val KEY_ACTIVE_LABEL = "active_label"
    private const val KEY_IS_EMULATING = "is_emulating"
    private const val KEY_IS_READ_ONLY = "is_read_only"
  }
}
