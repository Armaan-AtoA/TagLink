package com.example.nfc

enum class NdefRecordType(val label: String, val shortTag: String) {
  URL("URL / Link", "URI"),
  TEXT("Text", "TXT"),
  CONTACT("Contact (vCard)", "VCF"),
  WIFI("Wi-Fi", "WIFI"),
  EMAIL("Email", "MAIL"),
  PHONE("Phone", "TEL"),
  SMS("SMS", "SMS"),
  GEO("Location", "GEO"),
  APP("App (AAR)", "AAR"),
  MIME("Custom MIME", "MIME"),
}

enum class WifiAuthMode(val label: String, val wscAuthCode: Int, val wscEncCode: Int) {
  OPEN("Open (No password)", 0x0001, 0x0001),
  WPA_PERSONAL("WPA-Personal", 0x0002, 0x0004),
  WPA2_PERSONAL("WPA2-Personal", 0x0020, 0x0008),
  WPA_WPA2_MIXED("WPA/WPA2", 0x0022, 0x000C),
}

sealed interface NdefRecordDraft {
  val type: NdefRecordType

  fun summaryLine(): String

  data class Url(val url: String) : NdefRecordDraft {
    override val type = NdefRecordType.URL

    override fun summaryLine(): String = url
  }

  data class Text(
    val text: String,
    val languageCode: String = "en",
  ) : NdefRecordDraft {
    override val type = NdefRecordType.TEXT

    override fun summaryLine(): String = "[$languageCode] $text"
  }

  data class Contact(
    val fullName: String,
    val organization: String = "",
    val title: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val note: String = "",
  ) : NdefRecordDraft {
    override val type = NdefRecordType.CONTACT

    override fun summaryLine(): String {
      val details = listOfNotNull(
        phone.takeIf { it.isNotBlank() },
        email.takeIf { it.isNotBlank() },
        organization.takeIf { it.isNotBlank() },
      )
      return if (details.isEmpty()) fullName else "$fullName · ${details.joinToString(" · ")}"
    }
  }

  data class Wifi(
    val ssid: String,
    val password: String = "",
    val authMode: WifiAuthMode = WifiAuthMode.WPA2_PERSONAL,
    val hidden: Boolean = false,
  ) : NdefRecordDraft {
    override val type = NdefRecordType.WIFI

    override fun summaryLine(): String = "$ssid (${authMode.label})"
  }

  data class Email(
    val address: String,
    val subject: String = "",
    val body: String = "",
  ) : NdefRecordDraft {
    override val type = NdefRecordType.EMAIL

    override fun summaryLine(): String =
      if (subject.isBlank()) address else "$address · $subject"
  }

  data class Phone(val number: String) : NdefRecordDraft {
    override val type = NdefRecordType.PHONE

    override fun summaryLine(): String = number
  }

  data class Sms(
    val number: String,
    val message: String = "",
  ) : NdefRecordDraft {
    override val type = NdefRecordType.SMS

    override fun summaryLine(): String =
      if (message.isBlank()) number else "$number · $message"
  }

  data class Geo(
    val latitude: String,
    val longitude: String,
    val label: String = "",
  ) : NdefRecordDraft {
    override val type = NdefRecordType.GEO

    override fun summaryLine(): String =
      if (label.isBlank()) "$latitude, $longitude" else "$label ($latitude, $longitude)"
  }

  data class AppLaunch(val packageName: String) : NdefRecordDraft {
    override val type = NdefRecordType.APP

    override fun summaryLine(): String = packageName
  }

  data class CustomMime(
    val mimeType: String,
    val payload: String,
  ) : NdefRecordDraft {
    override val type = NdefRecordType.MIME

    override fun summaryLine(): String = "$mimeType · $payload"
  }
}
