package com.example

import com.example.nfc.NdefBridgeCodec
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.PeerExchangePacket
import com.example.nfc.WifiAuthMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NdefBridgeCodecTest {

  @Test
  fun buildMultiRecordNdef_putsTapshareRecordFirstAndHttpsFallbackSecond() {
    val primary = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Url("https://instagram.com/taglink")))
    val multiBytes =
      NdefBridgeCodec.buildMultiRecordNdef(
        primaryNdefBytes = primary,
        label = "Instagram (@taglink)",
        senderId = "peer1234",
      )

    val decoded = NdefMessageCodec.decodeRecords(multiBytes)
    assertEquals(2, decoded.size)

    val record1 = decoded[0] as NdefRecordDraft.Url
    val record2 = decoded[1] as NdefRecordDraft.Url
    assertTrue(record1.url.startsWith("tapshare://exchange?"))
    assertEquals("https://instagram.com/taglink", record2.url)

    val parsed = NdefBridgeCodec.parseBridgeUri(record1.url)
    assertNotNull(parsed)
    assertArrayEquals(primary, parsed!!.primaryNdefBytes)
    assertEquals("Instagram (@taglink)", parsed.label)
    assertEquals("peer1234", parsed.senderId)

    val (unwrapped, label) = NdefBridgeCodec.unwrapScannedMessage(multiBytes)
    assertArrayEquals(primary, unwrapped)
    assertEquals("Instagram (@taglink)", label)
  }

  @Test
  fun buildMultiRecordNdef_nonUrlRecordUsesValidHttpsFallbackAndFitsLimit() {
    val wifiPrimary =
      NdefMessageCodec.encodeRecords(
        listOf(
          NdefRecordDraft.Wifi(
            ssid = "StudioNet",
            password = "secret-password",
            authMode = WifiAuthMode.WPA2_PERSONAL,
          )
        )
      )
    val multiBytes =
      NdefBridgeCodec.buildMultiRecordNdef(
        primaryNdefBytes = wifiPrimary,
        label = "Studio Wi-Fi",
        senderId = "dev99",
      )

    assertTrue(multiBytes.size <= NdefMessageCodec.MAX_NDEF_BYTES)
    val decoded = NdefMessageCodec.decodeRecords(multiBytes)
    assertEquals(2, decoded.size)
    assertTrue((decoded[0] as NdefRecordDraft.Url).url.startsWith("tapshare://exchange?"))
    assertTrue((decoded[1] as NdefRecordDraft.Url).url.startsWith("https://"))

    val (unwrapped, _) = NdefBridgeCodec.unwrapScannedMessage(multiBytes)
    assertArrayEquals(wifiPrimary, unwrapped)
  }

  @Test
  fun peerExchangePacket_roundtripsAccurately() {
    val ndef = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Url("https://x.com/taglink")))
    val packet = PeerExchangePacket(senderId = "abc123", label = "X (@taglink)", ndefBytes = ndef)
    val encoded = NdefBridgeCodec.encodePeerPacket(packet)
    val decoded = NdefBridgeCodec.decodePeerPacket(encoded)
    assertEquals(packet, decoded)
  }
}
