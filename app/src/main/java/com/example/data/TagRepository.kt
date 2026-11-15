package com.example.data

import android.content.Context
import com.example.nfc.ActiveCardSnapshot
import com.example.nfc.ActiveCardStore
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.NfcHardwareHelper
import com.example.widget.WidgetSyncHelper
import kotlinx.coroutines.flow.Flow

class TagRepository(
  context: Context,
  private val tagDao: TagDao,
  private val activeCardStore: ActiveCardStore,
  private val settingsStore: AppSettingsStore = AppSettingsStore(context),
) {
  private val appContext = context.applicationContext
  val savedLinks: Flow<List<SavedLinkEntity>> = tagDao.observeSavedLinks()

  fun readActiveCard(): ActiveCardSnapshot {
    val snapshot = activeCardStore.readSnapshot()
    if (snapshot.isEmulating && !NfcHardwareHelper.isNfcEnabled(appContext)) {
      return activeCardStore.setEmulating(false).also {
        WidgetSyncHelper.refreshAll(appContext)
      }
    }
    return snapshot
  }

  fun readSettings(): AppSettings = settingsStore.readSettings()

  fun updateSettings(transform: (AppSettings) -> AppSettings): AppSettings =
    settingsStore.updateSettings(transform)

  suspend fun programCard(
    record: NdefRecordDraft,
    label: String,
    isReadOnly: Boolean,
  ): ActiveCardSnapshot {
    val snapshot = activeCardStore.writeRecords(listOf(record), label, isReadOnly)
    if (snapshot.hasPayload) {
      val resolvedLabel = snapshot.label.ifBlank { deriveDefaultLabel(record) }
      tagDao.upsertSavedLink(
        SavedLinkEntity(
          label = resolvedLabel,
          summary = snapshot.summary,
          recordCount = 1,
          ndefBytes = snapshot.ndefMessageBytes,
          ndefByteSize = snapshot.ndefMessageBytes.size,
          isReadOnly = snapshot.isReadOnly,
          lastProgrammedAt = System.currentTimeMillis(),
        )
      )
    }
    WidgetSyncHelper.refreshAll(appContext)
    return snapshot
  }

  fun loadSavedBytes(card: SavedLinkEntity): ActiveCardSnapshot {
    val singleRecord = NdefMessageCodec.decodeRecords(card.ndefBytes).take(1)
    val singleBytes =
      if (singleRecord.isNotEmpty()) NdefMessageCodec.encodeRecords(singleRecord)
      else card.ndefBytes
    val snapshot =
      activeCardStore.writeRawNdefBytes(
        ndefBytes = singleBytes,
        label = card.label,
        isReadOnly = card.isReadOnly,
      )
    WidgetSyncHelper.refreshAll(appContext)
    return snapshot
  }

  fun clearActiveCard(): ActiveCardSnapshot {
    val snapshot = activeCardStore.clearCard()
    WidgetSyncHelper.refreshAll(appContext)
    return snapshot
  }

  fun setEmulating(enabled: Boolean): ActiveCardSnapshot {
    val safeEnable = enabled && NfcHardwareHelper.isNfcEnabled(appContext)
    val snapshot = activeCardStore.setEmulating(safeEnable)
    WidgetSyncHelper.refreshAll(appContext)
    return snapshot
  }

  fun setReadOnly(readOnly: Boolean): ActiveCardSnapshot {
    val snapshot = activeCardStore.setReadOnly(readOnly)
    WidgetSyncHelper.refreshAll(appContext)
    return snapshot
  }

  suspend fun deleteSavedLink(id: Long) = tagDao.deleteSavedLinkById(id)

  private fun deriveDefaultLabel(record: NdefRecordDraft): String =
    when (record) {
      is NdefRecordDraft.Url -> {
        val host =
          record.url.substringAfter("://", record.url)
            .substringBefore('/')
            .removePrefix("www.")
            .lowercase()
        when {
          host.contains("instagram.com") -> "Instagram"
          host.contains("wa.me") || host.contains("whatsapp.com") -> "WhatsApp"
          host.contains("linkedin.com") -> "LinkedIn"
          host.contains("x.com") || host.contains("twitter.com") -> "X"
          host.contains("github.com") -> "GitHub"
          host.contains("youtube.com") || host.contains("youtu.be") -> "YouTube"
          host.isNotBlank() -> host
          else -> "URL Card"
        }
      }
      is NdefRecordDraft.Text -> "Text"
      is NdefRecordDraft.Contact -> record.fullName.ifBlank { "Contact" }
      is NdefRecordDraft.Wifi -> record.ssid
      is NdefRecordDraft.Email -> record.address
      is NdefRecordDraft.Phone -> record.number
      is NdefRecordDraft.Sms -> record.number
      is NdefRecordDraft.Geo -> record.label.ifBlank { "Location" }
      is NdefRecordDraft.AppLaunch -> record.packageName
      is NdefRecordDraft.CustomMime -> record.mimeType
    }
}
