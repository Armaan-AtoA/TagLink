package com.example.nfc

import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class DecodedBridgePayload(
  val primaryNdefBytes: ByteArray,
  val label: String,
  val senderId: String,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DecodedBridgePayload) return false
    return primaryNdefBytes.contentEquals(other.primaryNdefBytes) &&
      label == other.label &&
      senderId == other.senderId
  }

  override fun hashCode(): Int {
    var result = primaryNdefBytes.contentHashCode()
    result = 31 * result + label.hashCode()
    result = 31 * result + senderId.hashCode()
    return result
  }
}

data class PeerExchangePacket(
  val senderId: String,
  val label: String,
  val ndefBytes: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is PeerExchangePacket) return false
    return senderId == other.senderId &&
      label == other.label &&
      ndefBytes.contentEquals(other.ndefBytes)
  }

  override fun hashCode(): Int {
    var result = senderId.hashCode()
    result = 31 * result + label.hashCode()
    result = 31 * result + ndefBytes.contentHashCode()
    return result
  }
}

object NdefBridgeCodec {

  const val BRIDGE_SCHEME = "tapshare"
  const val BRIDGE_HOST = "exchange"
  const val BRIDGE_BASE_URI = "$BRIDGE_SCHEME://$BRIDGE_HOST"
  const val FALLBACK_HTTPS_BASE = "https://taglink.app/share"

  private const val PARAM_VERSION = "v"
  private const val PARAM_SENDER = "sid"
  private const val PARAM_LABEL = "lbl"
  private const val PARAM_PAYLOAD = "p"

  private val PACKET_MAGIC = byteArrayOf(0x54, 0x4C, 0x01) // "TL\x01"

  fun buildMultiRecordNdef(
    primaryNdefBytes: ByteArray,
    label: String,
    senderId: String = "",
  ): ByteArray {
    if (primaryNdefBytes.isEmpty()) return ByteArray(0)

    val primaryRecords = NdefMessageCodec.decodeRecords(primaryNdefBytes)
    val firstRecord = primaryRecords.firstOrNull()
    val fallbackUrl = deriveHttpsFallbackUrl(firstRecord, label)
    val fallbackRecord = NdefRecordDraft.Url(fallbackUrl)

    val cleanLabel = label.trim().take(48)
    val fullBridgeUri =
      buildBridgeUri(
        primaryNdefBytes = primaryNdefBytes,
        label = cleanLabel,
        senderId = senderId.trim().take(24),
      )
    val fullMessage =
      NdefMessageCodec.encodeRecords(
        listOf(NdefRecordDraft.Url(fullBridgeUri), fallbackRecord)
      )
    if (fullMessage.size <= NdefMessageCodec.MAX_NDEF_BYTES) {
      return fullMessage
    }

    // Compact label/fallback query if payload is near 2048 B limit
    val compactBridgeUri =
      buildBridgeUri(
        primaryNdefBytes = primaryNdefBytes,
        label = "",
        senderId = senderId.trim().take(16),
      )
    val compactFallbackUrl =
      if (fallbackUrl.startsWith(FALLBACK_HTTPS_BASE)) FALLBACK_HTTPS_BASE else fallbackUrl
    val compactMessage =
      NdefMessageCodec.encodeRecords(
        listOf(NdefRecordDraft.Url(compactBridgeUri), NdefRecordDraft.Url(compactFallbackUrl))
      )
    if (compactMessage.size <= NdefMessageCodec.MAX_NDEF_BYTES) {
      return compactMessage
    }

    // Minimal bridge URI when payload alone leaves no room for inline Base64 copy
    val minimalBridgeUri =
      buildBridgeUri(
        primaryNdefBytes = null,
        label = cleanLabel.take(24),
        senderId = senderId.trim().take(16),
      )
    return NdefMessageCodec.encodeRecords(
      listOf(NdefRecordDraft.Url(minimalBridgeUri), NdefRecordDraft.Url(compactFallbackUrl))
    )
  }

  fun buildBridgeUri(
    primaryNdefBytes: ByteArray?,
    label: String,
    senderId: String,
  ): String {
    val params = mutableListOf<String>()
    params += "$PARAM_VERSION=1"
    if (senderId.isNotBlank()) {
      params += "$PARAM_SENDER=${urlEncode(senderId)}"
    }
    if (label.isNotBlank()) {
      params += "$PARAM_LABEL=${urlEncode(label)}"
    }
    if (primaryNdefBytes != null && primaryNdefBytes.isNotEmpty()) {
      params += "$PARAM_PAYLOAD=${encodeBase64Url(primaryNdefBytes)}"
    }
    return "$BRIDGE_BASE_URI?${params.joinToString("&")}"
  }

  fun deriveHttpsFallbackUrl(record: NdefRecordDraft?, label: String): String {
    when (record) {
      is NdefRecordDraft.Url -> {
        val raw = record.url.trim()
        if (raw.startsWith("https://", ignoreCase = true)) {
          return "https://" + raw.substring(8)
        }
        if (raw.startsWith("http://", ignoreCase = true)) {
          return "https://" + raw.substring(7)
        }
        if (raw.isNotBlank()) {
          return "$FALLBACK_HTTPS_BASE?uri=${urlEncode(raw)}"
        }
      }
      is NdefRecordDraft.Contact -> {
        val site = record.website.trim()
        if (site.isNotBlank()) {
          val normalized = NdefUriEncoder.normalizeUri(site)
          if (normalized.startsWith("https://", ignoreCase = true)) {
            return "https://" + normalized.substring(8)
          }
          if (normalized.startsWith("http://", ignoreCase = true)) {
            return "https://" + normalized.substring(7)
          }
        }
      }
      else -> Unit
    }

    val params = mutableListOf<String>()
    val cleanTitle = label.trim().ifBlank { record?.summaryLine().orEmpty() }.take(48)
    if (cleanTitle.isNotBlank()) {
      params += "t=${urlEncode(cleanTitle)}"
    }
    if (record != null) {
      params += "k=${record.type.name.lowercase()}"
    }
    return if (params.isEmpty()) FALLBACK_HTTPS_BASE else "$FALLBACK_HTTPS_BASE?${params.joinToString("&")}"
  }

  fun isBridgeUri(uri: String?): Boolean {
    if (uri.isNullOrBlank()) return false
    val trimmed = uri.trim().lowercase()
    return trimmed == BRIDGE_BASE_URI ||
      trimmed.startsWith("$BRIDGE_BASE_URI?") ||
      trimmed.startsWith("$BRIDGE_BASE_URI/")
  }

  fun isBridgeMessage(rawNdefBytes: ByteArray): Boolean {
    val first = NdefMessageCodec.decodeRecords(rawNdefBytes).firstOrNull() as? NdefRecordDraft.Url
    return first != null && isBridgeUri(first.url)
  }

  fun parseBridgeUri(uri: String?): DecodedBridgePayload? {
    if (!isBridgeUri(uri) || uri == null) return null
    val query = uri.substringAfter('?', "")
    if (query.isEmpty()) return null

    var label = ""
    var senderId = ""
    var payloadB64 = ""
    query.split('&').forEach { pair ->
      val key = pair.substringBefore('=')
      val rawVal = pair.substringAfter('=', "")
      when (key) {
        PARAM_LABEL -> label = urlDecode(rawVal)
        PARAM_SENDER -> senderId = urlDecode(rawVal)
        PARAM_PAYLOAD -> payloadB64 = rawVal
      }
    }
    if (payloadB64.isBlank()) return null
    val primaryBytes = decodeBase64Url(payloadB64) ?: return null
    if (primaryBytes.isEmpty()) return null
    return DecodedBridgePayload(
      primaryNdefBytes = primaryBytes,
      label = label,
      senderId = senderId,
    )
  }

  fun unwrapScannedMessage(rawNdefBytes: ByteArray): Pair<ByteArray, String?> {
    if (rawNdefBytes.isEmpty()) return ByteArray(0) to null
    val records = NdefMessageCodec.decodeRecords(rawNdefBytes)
    val first = records.firstOrNull()
    if (first is NdefRecordDraft.Url && isBridgeUri(first.url)) {
      val parsed = parseBridgeUri(first.url)
      if (parsed != null && parsed.primaryNdefBytes.isNotEmpty()) {
        val canonical = NdefMessageCodec.decodeRecords(parsed.primaryNdefBytes).take(1)
        val bytes =
          if (canonical.isNotEmpty()) NdefMessageCodec.encodeRecords(canonical)
          else parsed.primaryNdefBytes
        return bytes to parsed.label.ifBlank { null }
      }
      val fallback = records.getOrNull(1)
      if (fallback != null) {
        return NdefMessageCodec.encodeRecords(listOf(fallback)) to null
      }
    }
    val single = records.take(1)
    val singleBytes =
      if (single.isNotEmpty()) NdefMessageCodec.encodeRecords(single) else rawNdefBytes
    return singleBytes to null
  }

  fun encodePeerPacket(packet: PeerExchangePacket): ByteArray {
    val senderBytes = packet.senderId.trim().toByteArray(StandardCharsets.UTF_8).take(64).toByteArray()
    val labelBytes = packet.label.trim().toByteArray(StandardCharsets.UTF_8).take(96).toByteArray()
    val ndefClamped =
      if (packet.ndefBytes.size > NdefMessageCodec.MAX_NDEF_BYTES) {
        packet.ndefBytes.copyOf(NdefMessageCodec.MAX_NDEF_BYTES)
      } else {
        packet.ndefBytes
      }

    val out = ByteArrayOutputStream(PACKET_MAGIC.size + 4 + senderBytes.size + labelBytes.size + ndefClamped.size)
    out.write(PACKET_MAGIC)
    out.write(senderBytes.size and 0xFF)
    out.write(senderBytes)
    out.write(labelBytes.size and 0xFF)
    out.write(labelBytes)
    out.write((ndefClamped.size ushr 8) and 0xFF)
    out.write(ndefClamped.size and 0xFF)
    out.write(ndefClamped)
    return out.toByteArray()
  }

  fun decodePeerPacket(bytes: ByteArray): PeerExchangePacket? {
    if (bytes.size < PACKET_MAGIC.size + 4) return null
    for (i in PACKET_MAGIC.indices) {
      if (bytes[i] != PACKET_MAGIC[i]) return null
    }
    var offset = PACKET_MAGIC.size

    val senderLen = bytes[offset++].toInt() and 0xFF
    if (offset + senderLen + 1 > bytes.size) return null
    val senderId = String(bytes, offset, senderLen, StandardCharsets.UTF_8)
    offset += senderLen

    val labelLen = bytes[offset++].toInt() and 0xFF
    if (offset + labelLen + 2 > bytes.size) return null
    val label = String(bytes, offset, labelLen, StandardCharsets.UTF_8)
    offset += labelLen

    val ndefLen = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    offset += 2
    if (ndefLen <= 0 || offset + ndefLen > bytes.size) return null
    val ndefBytes = bytes.copyOfRange(offset, offset + ndefLen)
    return PeerExchangePacket(senderId = senderId, label = label, ndefBytes = ndefBytes)
  }

  private fun urlEncode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

  private fun urlDecode(value: String): String =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

  private const val B64_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

  internal fun encodeBase64Url(data: ByteArray): String {
    if (data.isEmpty()) return ""
    val sb = StringBuilder((data.size * 4 + 2) / 3)
    var i = 0
    while (i < data.size) {
      val b0 = data[i].toInt() and 0xFF
      val hasB1 = i + 1 < data.size
      val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
      val hasB2 = i + 2 < data.size
      val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0

      sb.append(B64_ALPHABET[(b0 ushr 2) and 0x3F])
      sb.append(B64_ALPHABET[((b0 shl 4) or (b1 ushr 4)) and 0x3F])
      if (hasB1) {
        sb.append(B64_ALPHABET[((b1 shl 2) or (b2 ushr 6)) and 0x3F])
      }
      if (hasB2) {
        sb.append(B64_ALPHABET[b2 and 0x3F])
      }
      i += 3
    }
    return sb.toString()
  }

  internal fun decodeBase64Url(input: String): ByteArray? {
    if (input.isEmpty()) return ByteArray(0)
    val out = ByteArrayOutputStream((input.length * 3) / 4 + 1)
    var buffer = 0
    var bitsLeft = 0
    for (ch in input) {
      if (ch == '=') break
      val code =
        when (ch) {
          in 'A'..'Z' -> ch - 'A'
          in 'a'..'z' -> ch - 'a' + 26
          in '0'..'9' -> ch - '0' + 52
          '-', '+' -> 62
          '_', '/' -> 63
          else -> return null
        }
      buffer = (buffer shl 6) or code
      bitsLeft += 6
      if (bitsLeft >= 8) {
        bitsLeft -= 8
        out.write((buffer ushr bitsLeft) and 0xFF)
      }
    }
    return out.toByteArray()
  }
}
