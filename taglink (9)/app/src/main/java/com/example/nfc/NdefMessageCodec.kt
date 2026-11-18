package com.example.nfc

import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object NdefMessageCodec {

  const val MAX_NDEF_BYTES = 2048

  private const val TNF_EMPTY = 0x00
  private const val TNF_WELL_KNOWN = 0x01
  private const val TNF_MEDIA = 0x02
  private const val TNF_EXTERNAL = 0x04

  private const val MIME_VCARD = "text/vcard"
  private const val MIME_VCARD_ALT = "text/x-vcard"
  private const val MIME_WIFI_WSC = "application/vnd.wfa.wsc"
  private const val EXT_ANDROID_AAR = "android.com:pkg"

  private val URI_PREFIXES =
    listOf(
      "",
      "http://www.",
      "https://www.",
      "http://",
      "https://",
      "tel:",
      "mailto:",
      "ftp://anonymous:anonymous@",
      "ftp://ftp.",
      "ftps://",
      "sftp://",
      "smb://",
      "nfs://",
      "ftp://",
      "dav://",
      "news:",
      "telnet://",
      "imap:",
      "rtsp://",
      "urn:",
      "pop:",
      "sip:",
      "sips:",
      "tftp:",
      "btspp://",
      "btl2cap://",
      "btgoep://",
      "tcpobex://",
      "irdaobex://",
      "file://",
      "urn:epc:id:",
      "urn:epc:tag:",
      "urn:epc:pat:",
      "urn:epc:raw:",
      "urn:epc:",
      "urn:nfc:",
    )

  private data class RawNdefRecord(
    val tnf: Int,
    val type: ByteArray,
    val id: ByteArray = ByteArray(0),
    val payload: ByteArray,
  )

  fun encodeRecords(records: List<NdefRecordDraft>): ByteArray {
    if (records.isEmpty()) return ByteArray(0)
    val rawRecords = records.map(::toRawRecord)
    val out = ByteArrayOutputStream()
    rawRecords.forEachIndexed { index, record ->
      val mb = index == 0
      val me = index == rawRecords.lastIndex
      val sr = record.payload.size <= 255
      val il = record.id.isNotEmpty()
      var flags = record.tnf and 0x07
      if (mb) flags = flags or 0x80
      if (me) flags = flags or 0x40
      if (sr) flags = flags or 0x10
      if (il) flags = flags or 0x08

      out.write(flags)
      out.write(record.type.size and 0xFF)
      if (sr) {
        out.write(record.payload.size and 0xFF)
      } else {
        val len = record.payload.size
        out.write((len ushr 24) and 0xFF)
        out.write((len ushr 16) and 0xFF)
        out.write((len ushr 8) and 0xFF)
        out.write(len and 0xFF)
      }
      if (il) {
        out.write(record.id.size and 0xFF)
      }
      out.write(record.type)
      if (il) {
        out.write(record.id)
      }
      out.write(record.payload)
    }
    return out.toByteArray()
  }

  fun decodeRecords(ndefBytes: ByteArray): List<NdefRecordDraft> {
    if (ndefBytes.isEmpty()) return emptyList()
    val result = mutableListOf<NdefRecordDraft>()
    var offset = 0
    while (offset < ndefBytes.size) {
      val header = ndefBytes[offset++].toInt() and 0xFF
      val me = (header and 0x40) != 0
      val sr = (header and 0x10) != 0
      val il = (header and 0x08) != 0
      val tnf = header and 0x07

      if (offset >= ndefBytes.size) break
      val typeLen = ndefBytes[offset++].toInt() and 0xFF

      val payloadLen: Int =
        if (sr) {
          if (offset >= ndefBytes.size) break
          ndefBytes[offset++].toInt() and 0xFF
        } else {
          if (offset + 4 > ndefBytes.size) break
          val len =
            ((ndefBytes[offset].toInt() and 0xFF) shl 24) or
              ((ndefBytes[offset + 1].toInt() and 0xFF) shl 16) or
              ((ndefBytes[offset + 2].toInt() and 0xFF) shl 8) or
              (ndefBytes[offset + 3].toInt() and 0xFF)
          offset += 4
          len
        }

      val idLen =
        if (il) {
          if (offset >= ndefBytes.size) break
          ndefBytes[offset++].toInt() and 0xFF
        } else {
          0
        }

      if (payloadLen < 0 || offset + typeLen + idLen + payloadLen > ndefBytes.size) break

      val typeBytes = ndefBytes.copyOfRange(offset, offset + typeLen)
      offset += typeLen
      offset += idLen
      val payloadBytes = ndefBytes.copyOfRange(offset, offset + payloadLen)
      offset += payloadLen

      if (tnf != TNF_EMPTY) {
        fromRawRecord(tnf, typeBytes, payloadBytes)?.let(result::add)
      }
      if (me) break
    }
    return result
  }

  fun summarizeRecords(records: List<NdefRecordDraft>): String {
    if (records.isEmpty()) return "Empty tag"
    return if (records.size == 1) {
      val first = records.first()
      "${first.type.shortTag} · ${first.summaryLine()}"
    } else {
      val tags = records.joinToString(" + ") { it.type.shortTag }
      "${records.size} records · $tags"
    }
  }

  fun formatHexDump(bytes: ByteArray, bytesPerLine: Int = 8): String {
    if (bytes.isEmpty()) return "(empty)"
    val sb = StringBuilder()
    var i = 0
    while (i < bytes.size) {
      val end = minOf(i + bytesPerLine, bytes.size)
      sb.append(String.format("%04X: ", i))
      for (j in i until i + bytesPerLine) {
        if (j < end) {
          sb.append(String.format("%02X ", bytes[j]))
        } else {
          sb.append("   ")
        }
      }
      sb.append("| ")
      for (j in i until end) {
        val c = bytes[j].toInt() and 0xFF
        sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
      }
      if (end < bytes.size) sb.append('\n')
      i += bytesPerLine
    }
    return sb.toString()
  }

  private fun toRawRecord(draft: NdefRecordDraft): RawNdefRecord =
    when (draft) {
      is NdefRecordDraft.Url -> encodeUriRaw(draft.url)
      is NdefRecordDraft.Text -> encodeTextRaw(draft.text, draft.languageCode)
      is NdefRecordDraft.Contact -> encodeVCardRaw(draft)
      is NdefRecordDraft.Wifi -> encodeWifiWscRaw(draft)
      is NdefRecordDraft.Email -> encodeUriRaw(buildMailtoUri(draft))
      is NdefRecordDraft.Phone -> encodeUriRaw("tel:${draft.number.trim()}")
      is NdefRecordDraft.Sms -> encodeUriRaw(buildSmsUri(draft))
      is NdefRecordDraft.Geo -> encodeUriRaw(buildGeoUri(draft))
      is NdefRecordDraft.AppLaunch ->
        RawNdefRecord(
          tnf = TNF_EXTERNAL,
          type = EXT_ANDROID_AAR.toByteArray(StandardCharsets.US_ASCII),
          payload = draft.packageName.trim().toByteArray(StandardCharsets.UTF_8),
        )
      is NdefRecordDraft.CustomMime ->
        RawNdefRecord(
          tnf = TNF_MEDIA,
          type = draft.mimeType.trim().toByteArray(StandardCharsets.US_ASCII),
          payload = draft.payload.toByteArray(StandardCharsets.UTF_8),
        )
    }

  private fun encodeUriRaw(rawUri: String): RawNdefRecord {
    val normalized = NdefUriEncoder.normalizeUri(rawUri)
    var prefixCode = 0
    var remainder = normalized
    for (index in 1 until URI_PREFIXES.size) {
      val prefix = URI_PREFIXES[index]
      if (normalized.startsWith(prefix, ignoreCase = false) &&
        prefix.length > URI_PREFIXES[prefixCode].length
      ) {
        prefixCode = index
        remainder = normalized.substring(prefix.length)
      }
    }
    val uriBytes = remainder.toByteArray(StandardCharsets.UTF_8)
    val payload = ByteArray(1 + uriBytes.size)
    payload[0] = prefixCode.toByte()
    System.arraycopy(uriBytes, 0, payload, 1, uriBytes.size)
    return RawNdefRecord(
      tnf = TNF_WELL_KNOWN,
      type = byteArrayOf(0x55),
      payload = payload,
    )
  }

  private fun encodeTextRaw(text: String, languageCode: String): RawNdefRecord {
    val cleanLang = languageCode.trim().ifEmpty { "en" }.take(31)
    val langBytes = cleanLang.toByteArray(StandardCharsets.US_ASCII)
    val textBytes = text.toByteArray(StandardCharsets.UTF_8)
    val payload = ByteArray(1 + langBytes.size + textBytes.size)
    payload[0] = (langBytes.size and 0x3F).toByte()
    System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
    System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)
    return RawNdefRecord(
      tnf = TNF_WELL_KNOWN,
      type = byteArrayOf(0x54),
      payload = payload,
    )
  }

  private fun encodeVCardRaw(contact: NdefRecordDraft.Contact): RawNdefRecord {
    val lines = buildList {
      add("BEGIN:VCARD")
      add("VERSION:3.0")
      add("FN:${contact.fullName.trim()}")
      if (contact.organization.isNotBlank()) add("ORG:${contact.organization.trim()}")
      if (contact.title.isNotBlank()) add("TITLE:${contact.title.trim()}")
      if (contact.phone.isNotBlank()) add("TEL;TYPE=CELL:${contact.phone.trim()}")
      if (contact.email.isNotBlank()) add("EMAIL:${contact.email.trim()}")
      if (contact.website.isNotBlank()) add("URL:${NdefUriEncoder.normalizeUri(contact.website)}")
      if (contact.note.isNotBlank()) add("NOTE:${contact.note.trim().replace("\n", "\\n")}")
      add("END:VCARD")
    }
    val vcardText = lines.joinToString("\n")
    return RawNdefRecord(
      tnf = TNF_MEDIA,
      type = MIME_VCARD.toByteArray(StandardCharsets.US_ASCII),
      payload = vcardText.toByteArray(StandardCharsets.UTF_8),
    )
  }

  private fun encodeWifiWscRaw(wifi: NdefRecordDraft.Wifi): RawNdefRecord {
    val inner = ByteArrayOutputStream()
    // 0x1026: Network Index (1)
    writeWscTlv(inner, 0x1026, byteArrayOf(0x01))
    // 0x1045: SSID
    writeWscTlv(inner, 0x1045, wifi.ssid.trim().toByteArray(StandardCharsets.UTF_8))
    // 0x1003: Authentication Type
    writeWscShort(inner, 0x1003, wifi.authMode.wscAuthCode)
    // 0x100F: Encryption Type
    writeWscShort(inner, 0x100F, wifi.authMode.wscEncCode)
    // 0x1027: Network Key
    val keyBytes =
      if (wifi.authMode == WifiAuthMode.OPEN) ByteArray(0)
      else wifi.password.toByteArray(StandardCharsets.UTF_8)
    writeWscTlv(inner, 0x1027, keyBytes)
    // 0x1020: MAC Address (FF:FF:FF:FF:FF:FF wildcard)
    writeWscTlv(
      inner,
      0x1020,
      byteArrayOf(
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
        0xFF.toByte(),
      ),
    )

    val outer = ByteArrayOutputStream()
    writeWscTlv(outer, 0x100E, inner.toByteArray())
    return RawNdefRecord(
      tnf = TNF_MEDIA,
      type = MIME_WIFI_WSC.toByteArray(StandardCharsets.US_ASCII),
      payload = outer.toByteArray(),
    )
  }

  private fun writeWscTlv(out: ByteArrayOutputStream, type: Int, value: ByteArray) {
    out.write((type ushr 8) and 0xFF)
    out.write(type and 0xFF)
    out.write((value.size ushr 8) and 0xFF)
    out.write(value.size and 0xFF)
    out.write(value)
  }

  private fun writeWscShort(out: ByteArrayOutputStream, type: Int, value: Int) {
    writeWscTlv(out, type, byteArrayOf(((value ushr 8) and 0xFF).toByte(), (value and 0xFF).toByte()))
  }

  private fun buildMailtoUri(email: NdefRecordDraft.Email): String {
    val base = "mailto:${email.address.trim()}"
    val params = mutableListOf<String>()
    if (email.subject.isNotBlank()) {
      params += "subject=${URLEncoder.encode(email.subject.trim(), "UTF-8").replace("+", "%20")}"
    }
    if (email.body.isNotBlank()) {
      params += "body=${URLEncoder.encode(email.body.trim(), "UTF-8").replace("+", "%20")}"
    }
    return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
  }

  private fun buildSmsUri(sms: NdefRecordDraft.Sms): String {
    val cleanNumber = sms.number.trim()
    return if (sms.message.isBlank()) {
      "smsto:$cleanNumber"
    } else {
      "smsto:$cleanNumber:${sms.message.trim()}"
    }
  }

  private fun buildGeoUri(geo: NdefRecordDraft.Geo): String {
    val lat = geo.latitude.trim()
    val lon = geo.longitude.trim()
    return if (geo.label.isBlank()) {
      "geo:$lat,$lon"
    } else {
      val encoded = URLEncoder.encode(geo.label.trim(), "UTF-8").replace("+", "%20")
      "geo:$lat,$lon?q=$lat,$lon($encoded)"
    }
  }

  private fun fromRawRecord(
    tnf: Int,
    typeBytes: ByteArray,
    payload: ByteArray,
  ): NdefRecordDraft? {
    val typeStr = String(typeBytes, StandardCharsets.US_ASCII)
    return when {
      tnf == TNF_WELL_KNOWN && typeStr == "U" && payload.isNotEmpty() -> {
        val code = payload[0].toInt() and 0xFF
        val prefix = URI_PREFIXES.getOrElse(code) { "" }
        val uri = prefix + String(payload, 1, payload.size - 1, StandardCharsets.UTF_8)
        decodeUriToDraft(uri)
      }
      tnf == TNF_WELL_KNOWN && typeStr == "T" && payload.isNotEmpty() -> {
        val status = payload[0].toInt() and 0xFF
        val isUtf16 = (status and 0x80) != 0
        val langLen = (status and 0x3F).coerceAtMost(payload.size - 1)
        val lang = String(payload, 1, langLen, StandardCharsets.US_ASCII)
        val charset = if (isUtf16) StandardCharsets.UTF_16 else StandardCharsets.UTF_8
        val text = String(payload, 1 + langLen, payload.size - 1 - langLen, charset)
        NdefRecordDraft.Text(text = text, languageCode = lang.ifBlank { "en" })
      }
      tnf == TNF_MEDIA && (typeStr.equals(MIME_VCARD, true) || typeStr.equals(MIME_VCARD_ALT, true)) -> {
        decodeVCard(String(payload, StandardCharsets.UTF_8))
      }
      tnf == TNF_MEDIA && typeStr.equals(MIME_WIFI_WSC, true) -> {
        decodeWifiWsc(payload)
      }
      tnf == TNF_MEDIA -> {
        NdefRecordDraft.CustomMime(
          mimeType = typeStr,
          payload = String(payload, StandardCharsets.UTF_8),
        )
      }
      tnf == TNF_EXTERNAL && typeStr == EXT_ANDROID_AAR -> {
        NdefRecordDraft.AppLaunch(String(payload, StandardCharsets.UTF_8))
      }
      else -> {
        NdefRecordDraft.CustomMime(
          mimeType = typeStr.ifBlank { "application/octet-stream" },
          payload = String(payload, StandardCharsets.UTF_8),
        )
      }
    }
  }

  private fun decodeUriToDraft(uri: String): NdefRecordDraft =
    when {
      uri.startsWith("tel:", ignoreCase = true) ->
        NdefRecordDraft.Phone(uri.substring(4))
      uri.startsWith("mailto:", ignoreCase = true) -> {
        val raw = uri.substring(7)
        val addr = raw.substringBefore('?')
        val query = raw.substringAfter('?', "")
        var subject = ""
        var body = ""
        if (query.isNotEmpty()) {
          query.split('&').forEach { part ->
            val key = part.substringBefore('=')
            val value =
              runCatching { URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }
                .getOrDefault(part.substringAfter('=', ""))
            when (key.lowercase()) {
              "subject" -> subject = value
              "body" -> body = value
            }
          }
        }
        NdefRecordDraft.Email(address = addr, subject = subject, body = body)
      }
      uri.startsWith("smsto:", ignoreCase = true) -> {
        val bodyPart = uri.substring(6)
        val number = bodyPart.substringBefore(':')
        val msg = bodyPart.substringAfter(':', "")
        NdefRecordDraft.Sms(number = number, message = msg)
      }
      uri.startsWith("sms:", ignoreCase = true) -> {
        val number = uri.substring(4).substringBefore('?')
        NdefRecordDraft.Sms(number = number)
      }
      uri.startsWith("geo:", ignoreCase = true) -> {
        val coordsPart = uri.substring(4).substringBefore('?')
        val lat = coordsPart.substringBefore(',').trim()
        val lon = coordsPart.substringAfter(',', "").trim()
        val qParam = uri.substringAfter("q=", "")
        val label =
          if (qParam.contains('(') && qParam.endsWith(')')) {
            val rawLabel = qParam.substringAfter('(').dropLast(1)
            runCatching { URLDecoder.decode(rawLabel, "UTF-8") }.getOrDefault(rawLabel)
          } else {
            ""
          }
        NdefRecordDraft.Geo(latitude = lat, longitude = lon, label = label)
      }
      else -> NdefRecordDraft.Url(uri)
    }

  private fun decodeVCard(vcard: String): NdefRecordDraft.Contact {
    var fullName = ""
    var org = ""
    var title = ""
    var phone = ""
    var email = ""
    var website = ""
    var note = ""

    vcard.lines().forEach { line ->
      val key = line.substringBefore(':').substringBefore(';').uppercase()
      val value = line.substringAfter(':', "").trim()
      when (key) {
        "FN" -> fullName = value
        "N" -> if (fullName.isBlank()) fullName = value.replace(';', ' ').trim()
        "ORG" -> org = value
        "TITLE" -> title = value
        "TEL" -> if (phone.isBlank()) phone = value
        "EMAIL" -> if (email.isBlank()) email = value
        "URL" -> if (website.isBlank()) website = value
        "NOTE" -> note = value.replace("\\n", "\n")
      }
    }

    return NdefRecordDraft.Contact(
      fullName = fullName.ifBlank { "Contact" },
      organization = org,
      title = title,
      phone = phone,
      email = email,
      website = website,
      note = note,
    )
  }

  private fun decodeWifiWsc(payload: ByteArray): NdefRecordDraft.Wifi {
    var ssid = ""
    var password = ""
    var authCode = WifiAuthMode.WPA2_PERSONAL.wscAuthCode

    fun parseTlvs(bytes: ByteArray, start: Int, end: Int) {
      var idx = start
      while (idx + 4 <= end) {
        val type = ((bytes[idx].toInt() and 0xFF) shl 8) or (bytes[idx + 1].toInt() and 0xFF)
        val len = ((bytes[idx + 2].toInt() and 0xFF) shl 8) or (bytes[idx + 3].toInt() and 0xFF)
        idx += 4
        if (idx + len > end) break
        when (type) {
          0x100E -> parseTlvs(bytes, idx, idx + len)
          0x1045 -> ssid = String(bytes, idx, len, StandardCharsets.UTF_8)
          0x1027 -> password = String(bytes, idx, len, StandardCharsets.UTF_8)
          0x1003 ->
            if (len >= 2) {
              authCode = ((bytes[idx].toInt() and 0xFF) shl 8) or (bytes[idx + 1].toInt() and 0xFF)
            }
        }
        idx += len
      }
    }

    parseTlvs(payload, 0, payload.size)
    val mode =
      WifiAuthMode.entries.firstOrNull { it.wscAuthCode == authCode } ?: WifiAuthMode.WPA2_PERSONAL

    return NdefRecordDraft.Wifi(
      ssid = ssid.ifBlank { "Wi-Fi" },
      password = password,
      authMode = mode,
    )
  }
}
