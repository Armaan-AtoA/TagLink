package com.example.qr

import com.example.nfc.NdefRecordDraft
import com.example.nfc.NdefUriEncoder
import com.example.nfc.WifiAuthMode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class QrModuleShape(val label: String) {
  SQUARE("Classic"),
  ROUNDED("Rounded"),
}

data class QrMatrix(
  val size: Int,
  val modules: BooleanArray,
) {
  fun get(x: Int, y: Int): Boolean {
    if (x !in 0 until size || y !in 0 until size) return false
    return modules[y * size + x]
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is QrMatrix) return false
    return size == other.size && modules.contentEquals(other.modules)
  }

  override fun hashCode(): Int = 31 * size + modules.contentHashCode()
}

object QrPayloadFormatter {

  fun formatRecord(record: NdefRecordDraft): String =
    when (record) {
      is NdefRecordDraft.Url -> NdefUriEncoder.normalizeUri(record.url)
      is NdefRecordDraft.Text -> record.text
      is NdefRecordDraft.Contact -> buildVCard(record)
      is NdefRecordDraft.Wifi -> buildWifiQr(record)
      is NdefRecordDraft.Email -> buildMailto(record)
      is NdefRecordDraft.Phone -> "tel:${record.number.trim()}"
      is NdefRecordDraft.Sms -> buildSms(record)
      is NdefRecordDraft.Geo -> buildGeo(record)
      is NdefRecordDraft.AppLaunch ->
        "https://play.google.com/store/apps/details?id=${record.packageName.trim()}"
      is NdefRecordDraft.CustomMime -> record.payload.ifBlank { record.mimeType }
    }

  private fun buildVCard(contact: NdefRecordDraft.Contact): String =
    buildList {
        add("BEGIN:VCARD")
        add("VERSION:3.0")
        add("FN:${contact.fullName.trim().ifBlank { "Contact" }}")
        if (contact.organization.isNotBlank()) add("ORG:${contact.organization.trim()}")
        if (contact.title.isNotBlank()) add("TITLE:${contact.title.trim()}")
        if (contact.phone.isNotBlank()) add("TEL;TYPE=CELL:${contact.phone.trim()}")
        if (contact.email.isNotBlank()) add("EMAIL:${contact.email.trim()}")
        if (contact.website.isNotBlank()) add("URL:${NdefUriEncoder.normalizeUri(contact.website)}")
        if (contact.note.isNotBlank()) add("NOTE:${contact.note.trim().replace("\n", "\\n")}")
        add("END:VCARD")
      }
      .joinToString("\n")

  private fun buildWifiQr(wifi: NdefRecordDraft.Wifi): String {
    val authToken =
      when (wifi.authMode) {
        WifiAuthMode.OPEN -> "nopass"
        WifiAuthMode.WPA_PERSONAL,
        WifiAuthMode.WPA2_PERSONAL,
        WifiAuthMode.WPA_WPA2_MIXED -> "WPA"
      }
    val ssidEscaped = escapeWifiField(wifi.ssid.trim())
    val passPart =
      if (wifi.authMode == WifiAuthMode.OPEN) ""
      else ";P:${escapeWifiField(wifi.password)}"
    val hiddenPart = if (wifi.hidden) ";H:true" else ""
    return "WIFI:T:$authToken;S:$ssidEscaped$passPart$hiddenPart;;"
  }

  private fun escapeWifiField(value: String): String =
    buildString(value.length) {
      value.forEach { ch ->
        when (ch) {
          '\\', ';', ',', '"', ':' -> {
            append('\\')
            append(ch)
          }
          else -> append(ch)
        }
      }
    }

  private fun buildMailto(email: NdefRecordDraft.Email): String {
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

  private fun buildSms(sms: NdefRecordDraft.Sms): String {
    val number = sms.number.trim()
    return if (sms.message.isBlank()) "SMSTO:$number" else "SMSTO:$number:${sms.message.trim()}"
  }

  private fun buildGeo(geo: NdefRecordDraft.Geo): String {
    val lat = geo.latitude.trim()
    val lon = geo.longitude.trim()
    return if (geo.label.isBlank()) {
      "geo:$lat,$lon"
    } else {
      val encoded = URLEncoder.encode(geo.label.trim(), "UTF-8").replace("+", "%20")
      "geo:$lat,$lon?q=$lat,$lon($encoded)"
    }
  }
}

object QrCodeGenerator {

  const val DEFAULT_FOREGROUND_ARGB = 0xFF111315L
  const val DEFAULT_BACKGROUND_ARGB = 0xFFFFFFFFL
  const val MIN_RECOMMENDED_CONTRAST = 3.0f

  val FOREGROUND_SWATCHES: List<Long> =
    listOf(
      0xFF111315L, // Ink Black
      0xFFFFFFFFL, // Pure White
      0xFF1449B8L, // Cobalt Blue
      0xFF0E5E31L, // Forest Green
      0xFF0F766EL, // Deep Teal
      0xFF3730A3L, // Indigo
      0xFF7E22CEL, // Royal Purple
      0xFFBE185DL, // Berry Pink
      0xFF991B1BL, // Crimson Red
      0xFFC2410CL, // Burnt Orange
      0xFF78350FL, // Amber Ink
      0xFF5E9BFFL, // Sky Blue
    )

  val BACKGROUND_SWATCHES: List<Long> =
    listOf(
      0xFFFFFFFFL, // Pure White
      0xFF101214L, // Carbon Black
      0xFF1449B8L, // Cobalt Blue
      0xFF0E5E31L, // Emerald Green
      0xFF0F766EL, // Teal
      0xFF4338CAL, // Indigo
      0xFF7E22CEL, // Violet
      0xFFDB2777L, // Magenta
      0xFFDC2626L, // Red
      0xFFEA580CL, // Orange
      0xFFF59E0BL, // Gold
      0xFFDCEBFFL, // Soft Sky
      0xFFDCFCE7L, // Mint
      0xFFFEF3C7L, // Warm Cream
      0xFFFCE7F3L, // Blush Pink
      0xFF0A1628L, // Midnight Navy
    )

  fun encode(payload: String): QrMatrix? {
    if (payload.isBlank()) return null
    return runCatching {
      val hints =
        mapOf(
          EncodeHintType.CHARACTER_SET to "UTF-8",
          EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
          EncodeHintType.MARGIN to 0,
        )
      val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints)
      val size = bitMatrix.width
      val modules = BooleanArray(size * size)
      for (y in 0 until size) {
        for (x in 0 until size) {
          modules[y * size + x] = bitMatrix.get(x, y)
        }
      }
      QrMatrix(size = size, modules = modules)
    }.getOrNull()
  }

  fun hsvToArgb(hue: Float, saturation: Float, value: Float): Long {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
    val m = v - c
    val (rf, gf, bf) =
      when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
      }
    val r = ((rf + m) * 255f).roundToInt().coerceIn(0, 255)
    val g = ((gf + m) * 255f).roundToInt().coerceIn(0, 255)
    val b = ((bf + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
  }

  fun argbToHsv(argb: Long): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val delta = maxC - minC
    val h =
      when {
        delta <= 0.0001f -> 0f
        maxC == r -> 60f * (((g - b) / delta) % 6f)
        maxC == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
      }.let { if (it < 0f) it + 360f else it }
    val s = if (maxC <= 0.0001f) 0f else delta / maxC
    return floatArrayOf(h, s, maxC)
  }

  fun formatHexColor(argb: Long): String {
    val rgb = (argb and 0xFFFFFFL).toInt()
    return "#%06X".format(rgb)
  }

  fun contrastRatio(fgArgb: Long, bgArgb: Long): Float {
    val l1 = relativeLuminance(fgArgb)
    val l2 = relativeLuminance(bgArgb)
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return ((lighter + 0.05) / (darker + 0.05)).toFloat()
  }

  private fun relativeLuminance(argb: Long): Double {
    val r = linearize(((argb shr 16) and 0xFF) / 255.0)
    val g = linearize(((argb shr 8) and 0xFF) / 255.0)
    val b = linearize((argb and 0xFF) / 255.0)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
  }

  private fun linearize(channel: Double): Double =
    if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
}
