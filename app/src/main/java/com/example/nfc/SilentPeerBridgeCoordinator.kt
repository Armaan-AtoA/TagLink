package com.example.nfc

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.example.data.AppSettingsStore
import com.example.data.SavedLinkEntity
import com.example.data.TagDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SilentPeerBridgeCoordinator {

  private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private const val MAX_ISO_DEP_CHUNK = 220

  fun handleIncomingBridgeTap(
    context: Context,
    uriString: String?,
    tag: Tag?,
  ) {
    val appContext = context.applicationContext
    bridgeScope.launch {
      val parsed = NdefBridgeCodec.parseBridgeUri(uriString)
      if (parsed != null && parsed.primaryNdefBytes.isNotEmpty()) {
        savePeerCardInternal(
          context = appContext,
          rawNdefBytes = parsed.primaryNdefBytes,
          suggestedLabel = parsed.label,
          senderId = parsed.senderId,
        )
      }

      if (tag != null) {
        val settings = AppSettingsStore(appContext).readSettings()
        val snapshot = ActiveCardStore(appContext).readSnapshot()
        if (settings.multiRecordBridgeEnabled && snapshot.hasPayload) {
          val replyPacket =
            NdefBridgeCodec.encodePeerPacket(
              PeerExchangePacket(
                senderId = settings.bridgeDeviceId,
                label = snapshot.label,
                ndefBytes = snapshot.ndefMessageBytes,
              )
            )
          val peerReply = sendReturnCardOverIsoDep(tag, replyPacket)
          if (peerReply != null && parsed == null) {
            savePeerCardInternal(
              context = appContext,
              rawNdefBytes = peerReply.ndefBytes,
              suggestedLabel = peerReply.label,
              senderId = peerReply.senderId,
            )
          }
        }
      }
    }
  }

  fun replyOverIsoDepIfBridge(
    context: Context,
    tag: Tag,
    rawNdefBytes: ByteArray,
  ) {
    if (!NdefBridgeCodec.isBridgeMessage(rawNdefBytes)) return
    val appContext = context.applicationContext
    val settings = AppSettingsStore(appContext).readSettings()
    if (!settings.multiRecordBridgeEnabled) return
    val snapshot = ActiveCardStore(appContext).readSnapshot()
    if (!snapshot.hasPayload) return

    val replyPacket =
      NdefBridgeCodec.encodePeerPacket(
        PeerExchangePacket(
          senderId = settings.bridgeDeviceId,
          label = snapshot.label,
          ndefBytes = snapshot.ndefMessageBytes,
        )
      )
    sendReturnCardOverIsoDep(tag, replyPacket)
  }

  fun recordIncomingPeerCardAsync(
    context: Context,
    rawNdefBytes: ByteArray,
    suggestedLabel: String,
    senderId: String,
  ) {
    val appContext = context.applicationContext
    bridgeScope.launch {
      savePeerCardInternal(
        context = appContext,
        rawNdefBytes = rawNdefBytes,
        suggestedLabel = suggestedLabel,
        senderId = senderId,
      )
    }
  }

  internal suspend fun savePeerCardInternal(
    context: Context,
    rawNdefBytes: ByteArray,
    suggestedLabel: String,
    senderId: String,
  ): Boolean {
    if (rawNdefBytes.isEmpty()) return false
    val ownId = AppSettingsStore(context).readSettings().bridgeDeviceId
    if (senderId.isNotBlank() && senderId == ownId) {
      return false
    }

    val (canonicalBytes, embeddedLabel) = NdefBridgeCodec.unwrapScannedMessage(rawNdefBytes)
    if (canonicalBytes.isEmpty() || canonicalBytes.size > NdefMessageCodec.MAX_NDEF_BYTES) {
      return false
    }

    val records = NdefMessageCodec.decodeRecords(canonicalBytes)
    val firstRecord = records.firstOrNull() ?: return false
    if (firstRecord is NdefRecordDraft.Url && NdefBridgeCodec.isBridgeUri(firstRecord.url)) {
      return false
    }

    val summary = NdefMessageCodec.summarizeRecords(records)
    val resolvedLabel =
      suggestedLabel.trim().ifBlank {
        embeddedLabel?.trim().orEmpty().ifBlank { deriveCleanCardLabel(firstRecord) }
      }

    val dao = TagDatabase.getInstance(context).tagDao()
    val existing = dao.findByNdefBytes(canonicalBytes)
    val now = System.currentTimeMillis()
    val entity =
      if (existing != null) {
        existing.copy(
          label = existing.label.ifBlank { resolvedLabel },
          summary = summary,
          lastProgrammedAt = now,
        )
      } else {
        SavedLinkEntity(
          label = resolvedLabel,
          summary = summary,
          recordCount = 1,
          ndefBytes = canonicalBytes,
          ndefByteSize = canonicalBytes.size,
          isReadOnly = true,
          lastProgrammedAt = now,
        )
      }
    dao.upsertSavedLink(entity)
    return true
  }

  private fun sendReturnCardOverIsoDep(tag: Tag, packetBytes: ByteArray): PeerExchangePacket? {
    val isoDep = IsoDep.get(tag) ?: return null
    return runCatching {
        isoDep.timeout = 1500
        isoDep.connect()
        val selectCmd =
          byteArrayOf(
            0x00,
            0xA4.toByte(),
            0x04,
            0x00,
            T4tApduEngine.NDEF_AID.size.toByte(),
          ) + T4tApduEngine.NDEF_AID + byteArrayOf(0x00)
        val selectResp = isoDep.transceive(selectCmd)
        if (!isSuccessSw(selectResp)) return null

        var offset = 0
        var chunkIndex = 0
        var finalReply: ByteArray? = null
        while (offset < packetBytes.size) {
          val end = minOf(offset + MAX_ISO_DEP_CHUNK, packetBytes.size)
          val chunk = packetBytes.copyOfRange(offset, end)
          val isLast = end >= packetBytes.size
          val p1: Byte = if (isLast) 0x00 else 0x01
          val p2: Byte = (chunkIndex and 0xFF).toByte()
          val apdu =
            byteArrayOf(
              T4tApduEngine.CLA_PROPRIETARY.toByte(),
              T4tApduEngine.INS_PEER_EXCHANGE.toByte(),
              p1,
              p2,
              chunk.size.toByte(),
            ) + chunk
          val resp = isoDep.transceive(apdu)
          if (!isSuccessSw(resp)) return null
          if (isLast) {
            finalReply = resp
          }
          offset = end
          chunkIndex++
        }

        val replyBytes = finalReply
        if (replyBytes != null && replyBytes.size > 2) {
          NdefBridgeCodec.decodePeerPacket(replyBytes.copyOf(replyBytes.size - 2))
        } else {
          null
        }
      }
      .getOrNull()
      .also { runCatching { isoDep.close() } }
  }

  private fun isSuccessSw(response: ByteArray): Boolean =
    response.size >= 2 &&
      response[response.size - 2] == 0x90.toByte() &&
      response[response.size - 1] == 0x00.toByte()

  private fun deriveCleanCardLabel(record: NdefRecordDraft): String =
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
          host.contains("tiktok.com") -> "TikTok"
          host.contains("linkedin.com") -> "LinkedIn"
          host.contains("x.com") || host.contains("twitter.com") -> "X"
          host.contains("github.com") -> "GitHub"
          host.contains("youtube.com") || host.contains("youtu.be") -> "YouTube"
          host.contains("threads.net") -> "Threads"
          host.contains("snapchat.com") -> "Snapchat"
          host.contains("facebook.com") -> "Facebook"
          host.contains("discord.gg") || host.contains("discord.com") -> "Discord"
          host.contains("twitch.tv") -> "Twitch"
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
