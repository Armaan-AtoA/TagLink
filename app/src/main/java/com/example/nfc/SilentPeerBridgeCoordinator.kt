package com.example.nfc

import android.content.Context
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.example.data.AppSettingsStore
import com.example.data.PeerExchangeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object SilentPeerBridgeCoordinator {

  private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private const val MAX_ISO_DEP_CHUNK = 220
  private const val DEDUPE_WINDOW_MS = 2_500L
  private const val ISO_DEP_EXCHANGE_TIMEOUT_MS = 450L

  @Volatile private var lastExecutedKey: String? = null
  @Volatile private var lastExecutedAtMs: Long = 0L

  fun handleIncomingBridgeTap(
    context: Context,
    uriString: String?,
    tag: Tag?,
    onComplete: () -> Unit = {},
  ) {
    val appContext = context.applicationContext
    val parsed = NdefBridgeCodec.parseBridgeUri(uriString)
    val settings = AppSettingsStore(appContext).readSettings()
    val snapshot = ActiveCardStore(appContext).readSnapshot()

    if (tag == null) {
      if (parsed != null && parsed.primaryNdefBytes.isNotEmpty()) {
        executeIncomingPeerCard(
          context = context,
          rawNdefBytes = parsed.primaryNdefBytes,
          senderId = parsed.senderId,
        )
      }
      onComplete()
      return
    }

    bridgeScope.launch {
      try {
        var targetBytes = parsed?.primaryNdefBytes
        var targetSenderId = parsed?.senderId.orEmpty()

        if (settings.peerExchangeMode == PeerExchangeMode.TRUE_P2P && snapshot.hasPayload) {
          val replyPacket =
            NdefBridgeCodec.encodePeerPacket(
              PeerExchangePacket(
                senderId = settings.bridgeDeviceId,
                label = snapshot.label,
                ndefBytes = snapshot.ndefMessageBytes,
              )
            )
          val peerReply =
            withTimeoutOrNull(ISO_DEP_EXCHANGE_TIMEOUT_MS) {
              sendReturnCardOverIsoDep(tag, replyPacket)
            }
          if ((targetBytes == null || targetBytes.isEmpty()) && peerReply != null) {
            targetBytes = peerReply.ndefBytes
            targetSenderId = peerReply.senderId
          }
        }

        if (targetBytes != null && targetBytes.isNotEmpty()) {
          withContext(Dispatchers.Main.immediate) {
            executeIncomingPeerCard(
              context = context,
              rawNdefBytes = targetBytes,
              senderId = targetSenderId,
            )
          }
        }
      } finally {
        withContext(Dispatchers.Main.immediate) {
          onComplete()
        }
      }
    }
  }

  fun executeIncomingPeerCard(
    context: Context,
    rawNdefBytes: ByteArray,
    senderId: String,
  ): Boolean {
    if (rawNdefBytes.isEmpty()) return false
    val ownId = AppSettingsStore(context).readSettings().bridgeDeviceId
    if (senderId.isNotBlank() && senderId == ownId) {
      return false
    }

    val (canonicalBytes, _) = NdefBridgeCodec.unwrapScannedMessage(rawNdefBytes)
    if (canonicalBytes.isEmpty() || canonicalBytes.size > NdefMessageCodec.MAX_NDEF_BYTES) {
      return false
    }

    val firstRecord = NdefMessageCodec.decodeRecords(canonicalBytes).firstOrNull() ?: return false
    if (firstRecord is NdefRecordDraft.Url && NdefBridgeCodec.isBridgeUri(firstRecord.url)) {
      return false
    }

    val key = "${canonicalBytes.contentHashCode()}:$senderId"
    val now = System.currentTimeMillis()
    synchronized(this) {
      if (lastExecutedKey == key && (now - lastExecutedAtMs) < DEDUPE_WINDOW_MS) {
        return false
      }
      lastExecutedKey = key
      lastExecutedAtMs = now
    }

    return NdefActionExecutor.execute(context, canonicalBytes)
  }

  internal fun resetDedupeForTest() {
    synchronized(this) {
      lastExecutedKey = null
      lastExecutedAtMs = 0L
    }
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
}
