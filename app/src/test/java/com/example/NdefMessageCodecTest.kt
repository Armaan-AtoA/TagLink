package com.example

import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.WifiAuthMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NdefMessageCodecTest {

  @Test
  fun encodeAndDecode_allRecordTypesRoundtrip() {
    val records =
      listOf(
        NdefRecordDraft.Url("https://instagram.com/taglink"),
        NdefRecordDraft.Text("Hello NFC World", languageCode = "en"),
        NdefRecordDraft.Contact(
          fullName = "Ada Lovelace",
          organization = "Analytical Engine Lab",
          title = "Lead Engineer",
          phone = "+15550101",
          email = "ada@example.com",
          website = "https://example.com",
          note = "Meetup card",
        ),
        NdefRecordDraft.Wifi(
          ssid = "StudioGuest",
          password = "correcthorsebatterystaple",
          authMode = WifiAuthMode.WPA2_PERSONAL,
        ),
        NdefRecordDraft.Email(
          address = "hello@example.com",
          subject = "NFC Intro",
          body = "Nice meeting you",
        ),
        NdefRecordDraft.Phone("+15550199"),
        NdefRecordDraft.Sms(number = "+15550188", message = "Hey there"),
        NdefRecordDraft.Geo(latitude = "37.7749", longitude = "-122.4194", label = "SF"),
        NdefRecordDraft.AppLaunch("com.instagram.android"),
        NdefRecordDraft.CustomMime("application/json", "{\"tag\":4}"),
      )

    val bytes = NdefMessageCodec.encodeRecords(records)
    assertTrue(bytes.isNotEmpty())

    val decoded = NdefMessageCodec.decodeRecords(bytes)
    assertEquals(records.size, decoded.size)
    assertEquals(records[0], decoded[0])
    assertEquals(records[1], decoded[1])
    assertEquals(records[2], decoded[2])
    assertEquals(records[3], decoded[3])
    assertEquals(records[4], decoded[4])
    assertEquals(records[5], decoded[5])
    assertEquals(records[6], decoded[6])
    assertEquals(records[7], decoded[7])
    assertEquals(records[8], decoded[8])
    assertEquals(records[9], decoded[9])
  }

  @Test
  fun hexDump_formatsExpectedHeader() {
    val bytes = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Text("Hi", "en")))
    val dump = NdefMessageCodec.formatHexDump(bytes)
    assertTrue(dump.startsWith("0000: D1 01"))
    assertTrue(dump.contains("T.enH"))
    assertTrue(dump.contains("0008: 69"))
  }
}
