package com.example.widget

import android.content.Context
import android.util.Base64
import com.example.data.SavedLinkEntity
import com.example.nfc.ActiveCardSnapshot

data class PinnedWidgetCard(
  val label: String,
  val summary: String,
  val ndefBytes: ByteArray,
  val isReadOnly: Boolean,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PinnedWidgetCard) return false
    return label == other.label &&
      summary == other.summary &&
      ndefBytes.contentEquals(other.ndefBytes) &&
      isReadOnly == other.isReadOnly
  }

  override fun hashCode(): Int {
    var result = label.hashCode()
    result = 31 * result + summary.hashCode()
    result = 31 * result + ndefBytes.contentHashCode()
    result = 31 * result + isReadOnly.hashCode()
    return result
  }
}

object WidgetPrefs {
  private const val PREFS_NAME = "taglink_widget_prefs"

  fun savePinnedCard(context: Context, appWidgetId: Int, card: SavedLinkEntity) {
    savePinnedRaw(
      context = context,
      appWidgetId = appWidgetId,
      label = card.label,
      summary = card.summary,
      ndefBytes = card.ndefBytes,
      isReadOnly = card.isReadOnly,
    )
  }

  fun savePinnedSnapshot(context: Context, appWidgetId: Int, snapshot: ActiveCardSnapshot) {
    savePinnedRaw(
      context = context,
      appWidgetId = appWidgetId,
      label = snapshot.label.ifBlank { snapshot.summary },
      summary = snapshot.summary,
      ndefBytes = snapshot.ndefMessageBytes,
      isReadOnly = snapshot.isReadOnly,
    )
  }

  private fun savePinnedRaw(
    context: Context,
    appWidgetId: Int,
    label: String,
    summary: String,
    ndefBytes: ByteArray,
    isReadOnly: Boolean,
  ) {
    val encoded = Base64.encodeToString(ndefBytes, Base64.NO_WRAP)
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString("label_$appWidgetId", label)
      .putString("summary_$appWidgetId", summary)
      .putString("bytes_$appWidgetId", encoded)
      .putBoolean("ro_$appWidgetId", isReadOnly)
      .apply()
  }

  fun loadPinnedCard(context: Context, appWidgetId: Int): PinnedWidgetCard? {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val b64 = prefs.getString("bytes_$appWidgetId", null) ?: return null
    val bytes = runCatching { Base64.decode(b64, Base64.NO_WRAP) }.getOrNull() ?: return null
    if (bytes.isEmpty()) return null
    return PinnedWidgetCard(
      label = prefs.getString("label_$appWidgetId", "NFC Card") ?: "NFC Card",
      summary = prefs.getString("summary_$appWidgetId", "") ?: "",
      ndefBytes = bytes,
      isReadOnly = prefs.getBoolean("ro_$appWidgetId", true),
    )
  }

  fun deletePinnedCard(context: Context, appWidgetId: Int) {
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .remove("label_$appWidgetId")
      .remove("summary_$appWidgetId")
      .remove("bytes_$appWidgetId")
      .remove("ro_$appWidgetId")
      .apply()
  }
}
