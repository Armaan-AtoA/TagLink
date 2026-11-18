package com.example.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef

sealed interface ScannedTagResult {
  data class Success(
    val ndefBytes: ByteArray,
    val record: NdefRecordDraft?,
    val summary: String,
    val suggestedLabel: String,
    val isReadOnly: Boolean,
  ) : ScannedTagResult {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Success) return false
      return ndefBytes.contentEquals(other.ndefBytes) &&
        record == other.record &&
        summary == other.summary &&
        suggestedLabel == other.suggestedLabel &&
        isReadOnly == other.isReadOnly
    }

    override fun hashCode(): Int {
      var result = ndefBytes.contentHashCode()
      result = 31 * result + (record?.hashCode() ?: 0)
      result = 31 * result + summary.hashCode()
      result = 31 * result + suggestedLabel.hashCode()
      result = 31 * result + isReadOnly.hashCode()
      return result
    }
  }

  data class Failure(val reason: TagScanError) : ScannedTagResult
}

enum class TagScanError {
  NOT_NDEF_FORMATTED,
  EMPTY_TAG,
  PAYLOAD_TOO_LARGE,
  READ_FAILED,
}

object NfcTagReaderHelper {

  fun readPhysicalTag(tag: Tag): ScannedTagResult {
    val ndef = Ndef.get(tag) ?: return ScannedTagResult.Failure(TagScanError.NOT_NDEF_FORMATTED)
    return try {
      ndef.connect()
      val message: NdefMessage? = ndef.ndefMessage ?: ndef.cachedNdefMessage
      val isReadOnly = !ndef.isWritable
      if (message == null) {
        ScannedTagResult.Failure(TagScanError.EMPTY_TAG)
      } else {
        parseScannedNdefBytes(message.toByteArray(), isReadOnly = isReadOnly)
      }
    } catch (_: Exception) {
      val cached = ndef.cachedNdefMessage
      if (cached != null) {
        parseScannedNdefBytes(cached.toByteArray(), isReadOnly = !ndef.isWritable)
      } else {
        ScannedTagResult.Failure(TagScanError.READ_FAILED)
      }
    } finally {
      runCatching { ndef.close() }
    }
  }

  fun parseScannedNdefBytes(
    rawBytes: ByteArray,
    isReadOnly: Boolean,
  ): ScannedTagResult {
    if (rawBytes.isEmpty()) {
      return ScannedTagResult.Failure(TagScanError.EMPTY_TAG)
    }
    val (clampedBytes, embeddedLabel) = NdefBridgeCodec.unwrapScannedMessage(rawBytes)
    val decoded = NdefMessageCodec.decodeRecords(clampedBytes)
    val singleRecord = decoded.firstOrNull()

    if (clampedBytes.size > NdefMessageCodec.MAX_NDEF_BYTES) {
      return ScannedTagResult.Failure(TagScanError.PAYLOAD_TOO_LARGE)
    }

    val summary =
      if (singleRecord != null) {
        singleRecord.summaryLine()
      } else {
        "Raw NDEF (${clampedBytes.size} B)"
      }

    val suggestedLabel =
      embeddedLabel?.takeIf { it.isNotBlank() }
        ?: when (singleRecord) {
          is NdefRecordDraft.Url -> {
            val host =
              singleRecord.url.substringAfter("://", singleRecord.url)
                .substringBefore('/')
                .removePrefix("www.")
            host.ifBlank { "Copied Link" }
          }
          is NdefRecordDraft.Text -> "Copied Text"
          is NdefRecordDraft.Contact -> singleRecord.fullName.ifBlank { "Copied Contact" }
          is NdefRecordDraft.Wifi -> singleRecord.ssid.ifBlank { "Copied Wi-Fi" }
          is NdefRecordDraft.Email -> singleRecord.address.ifBlank { "Copied Email" }
          is NdefRecordDraft.Phone -> singleRecord.number.ifBlank { "Copied Phone" }
          is NdefRecordDraft.Sms -> singleRecord.number.ifBlank { "Copied SMS" }
          is NdefRecordDraft.Geo -> singleRecord.label.ifBlank { "Copied Location" }
          is NdefRecordDraft.AppLaunch -> singleRecord.packageName.ifBlank { "Copied App" }
          is NdefRecordDraft.CustomMime -> singleRecord.mimeType.ifBlank { "Copied Tag" }
          null -> "Copied Tag"
        }

    return ScannedTagResult.Success(
      ndefBytes = clampedBytes,
      record = singleRecord,
      summary = summary,
      suggestedLabel = suggestedLabel,
      isReadOnly = isReadOnly,
    )
  }
}
