package com.example

import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.NdefUriEncoder
import com.example.nfc.T4tApduEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class T4tApduEngineTest {

  @Test
  fun encodeUriNdefMessage_usesHttpsPrefixCompression() {
    val encoded = NdefUriEncoder.encodeUriNdefMessage("instagram.com/taglink")
    assertEquals(0xD1.toByte(), encoded[0])
    assertEquals(0x01.toByte(), encoded[1])
    assertEquals(22.toByte(), encoded[2])
    assertEquals(0x55.toByte(), encoded[3])
    assertEquals(0x04.toByte(), encoded[4])
    assertEquals("instagram.com/taglink", String(encoded, 5, encoded.size - 5, Charsets.UTF_8))
  }

  @Test
  fun fullType4TagExchange_servesNdefAndFlagsCompletedRead() {
    val engine = T4tApduEngine()
    val ndef = NdefUriEncoder.encodeUriNdefMessage("https://wa.me/15550199")

    val selectAid =
      byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07) + T4tApduEngine.NDEF_AID + byteArrayOf(0x00)
    val aidRes = engine.processApdu(selectAid, isEmulationEnabled = true, ndefMessageBytes = ndef)
    assertArrayEquals(T4tApduEngine.SW_OK, aidRes.responseApdu)
    assertFalse(aidRes.completedNdefRead)

    val selectCc = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03)
    assertArrayEquals(
      T4tApduEngine.SW_OK,
      engine.processApdu(selectCc, isEmulationEnabled = true, ndefMessageBytes = ndef).responseApdu,
    )

    val readCc = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x0F)
    val ccRes = engine.processApdu(readCc, isEmulationEnabled = true, ndefMessageBytes = ndef)
    assertArrayEquals(T4tApduEngine.CAPABILITY_CONTAINER_FILE + T4tApduEngine.SW_OK, ccRes.responseApdu)
    assertFalse(ccRes.completedNdefRead)

    val selectNdef = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)
    assertArrayEquals(
      T4tApduEngine.SW_OK,
      engine.processApdu(selectNdef, isEmulationEnabled = true, ndefMessageBytes = ndef).responseApdu,
    )

    val readNlen = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x02)
    val nlenRes = engine.processApdu(readNlen, isEmulationEnabled = true, ndefMessageBytes = ndef)
    assertFalse(nlenRes.completedNdefRead)
    val decodedLen =
      ((nlenRes.responseApdu[0].toInt() and 0xFF) shl 8) or (nlenRes.responseApdu[1].toInt() and 0xFF)
    assertEquals(ndef.size, decodedLen)

    val readPayload = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x02, ndef.size.toByte())
    val payloadRes = engine.processApdu(readPayload, isEmulationEnabled = true, ndefMessageBytes = ndef)
    assertTrue(payloadRes.completedNdefRead)
    assertArrayEquals(ndef + T4tApduEngine.SW_OK, payloadRes.responseApdu)
  }

  @Test
  fun writableTag_acceptsUpdateBinarySequence() {
    val engine = T4tApduEngine()
    val initialNdef = ByteArray(0)
    val newNdef = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Text("External", "en")))

    val selectAid =
      byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07) + T4tApduEngine.NDEF_AID
    assertArrayEquals(
      T4tApduEngine.SW_OK,
      engine.processApdu(selectAid, isEmulationEnabled = true, ndefMessageBytes = initialNdef, isReadOnly = false).responseApdu,
    )

    val selectCc = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03)
    engine.processApdu(selectCc, isEmulationEnabled = true, ndefMessageBytes = initialNdef, isReadOnly = false)
    val ccRes =
      engine.processApdu(
        byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x0F),
        isEmulationEnabled = true,
        ndefMessageBytes = initialNdef,
        isReadOnly = false,
      )
    // Byte 14 in CC must be 0x00 when writable
    assertEquals(0x00.toByte(), ccRes.responseApdu[14])

    val selectNdef = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)
    engine.processApdu(selectNdef, isEmulationEnabled = true, ndefMessageBytes = initialNdef, isReadOnly = false)

    // 1. Zero NLEN at offset 0
    val clearLen = byteArrayOf(0x00, 0xD6.toByte(), 0x00, 0x00, 0x02, 0x00, 0x00)
    val clearRes =
      engine.processApdu(clearLen, isEmulationEnabled = true, ndefMessageBytes = initialNdef, isReadOnly = false)
    assertArrayEquals(T4tApduEngine.SW_OK, clearRes.responseApdu)
    assertNull(clearRes.completedNdefWrite)

    // 2. Write payload at offset 2
    val writePayload =
      byteArrayOf(0x00, 0xD6.toByte(), 0x00, 0x02, newNdef.size.toByte()) + newNdef
    val payloadRes =
      engine.processApdu(writePayload, isEmulationEnabled = true, ndefMessageBytes = initialNdef, isReadOnly = false)
    assertArrayEquals(T4tApduEngine.SW_OK, payloadRes.responseApdu)
    assertNull(payloadRes.completedNdefWrite)

    // 3. Commit NLEN at offset 0
    val commitLen =
      byteArrayOf(0x00, 0xD6.toByte(), 0x00, 0x00, 0x02, 0x00, newNdef.size.toByte())
    val commitRes =
      engine.processApdu(commitLen, isEmulationEnabled = true, ndefMessageBytes = initialNdef, isReadOnly = false)
    assertArrayEquals(T4tApduEngine.SW_OK, commitRes.responseApdu)
    assertNotNull(commitRes.completedNdefWrite)
    assertArrayEquals(newNdef, commitRes.completedNdefWrite)
  }

  @Test
  fun selectAidWhenStandby_returnsFileNotFoundAndFlagsRejected() {
    val engine = T4tApduEngine()
    val ndef = NdefUriEncoder.encodeUriNdefMessage("https://example.com")
    val selectAid = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07) + T4tApduEngine.NDEF_AID

    val res = engine.processApdu(selectAid, isEmulationEnabled = false, ndefMessageBytes = ndef)
    assertArrayEquals(T4tApduEngine.SW_FILE_NOT_FOUND, res.responseApdu)
    assertTrue(res.rejectedBecauseStandby)
  }

  @Test
  fun updateBinary_isRejectedAsReadOnly() {
    val engine = T4tApduEngine()
    val ndef = NdefUriEncoder.encodeUriNdefMessage("https://example.com")
    val updateCmd = byteArrayOf(0x00, 0xD6.toByte(), 0x00, 0x00, 0x01, 0x00)
    val res = engine.processApdu(updateCmd, isEmulationEnabled = true, ndefMessageBytes = ndef, isReadOnly = true)
    assertArrayEquals(T4tApduEngine.SW_SECURITY_STATUS_NOT_SATISFIED, res.responseApdu)
  }

  @Test
  fun peerExchangeApdu_acceptsChunkedTransferWhenBridgeEnabledAndRejectsWhenDisabled() {
    val engine = T4tApduEngine()
    val hostNdef = NdefUriEncoder.encodeUriNdefMessage("https://instagram.com/host")
    val hostPacket =
      com.example.nfc.NdefBridgeCodec.encodePeerPacket(
        com.example.nfc.PeerExchangePacket(
          senderId = "host1",
          label = "Host",
          ndefBytes = hostNdef,
        )
      )
    val peerNdef = NdefUriEncoder.encodeUriNdefMessage("https://tiktok.com/@peer")
    val peerPacket =
      com.example.nfc.PeerExchangePacket(
        senderId = "peer1",
        label = "Peer",
        ndefBytes = peerNdef,
      )
    val peerBytes = com.example.nfc.NdefBridgeCodec.encodePeerPacket(peerPacket)
    val split = peerBytes.size / 2
    val chunk1 = peerBytes.copyOfRange(0, split)
    val chunk2 = peerBytes.copyOfRange(split, peerBytes.size)

    val apdu1 =
      byteArrayOf(
        T4tApduEngine.CLA_PROPRIETARY.toByte(),
        T4tApduEngine.INS_PEER_EXCHANGE.toByte(),
        0x01,
        0x00,
        chunk1.size.toByte(),
      ) + chunk1
    val apdu2 =
      byteArrayOf(
        T4tApduEngine.CLA_PROPRIETARY.toByte(),
        T4tApduEngine.INS_PEER_EXCHANGE.toByte(),
        0x00,
        0x01,
        chunk2.size.toByte(),
      ) + chunk2

    // Disabled -> rejected
    val rejected =
      engine.processApdu(
        commandApdu = apdu1,
        isEmulationEnabled = true,
        ndefMessageBytes = hostNdef,
        isBridgeEnabled = false,
        localExchangePacket = hostPacket,
      )
    assertArrayEquals(T4tApduEngine.SW_CLA_NOT_SUPPORTED, rejected.responseApdu)

    // Enabled -> two-chunk exchange succeeds and returns host reply packet
    val res1 =
      engine.processApdu(
        commandApdu = apdu1,
        isEmulationEnabled = true,
        ndefMessageBytes = hostNdef,
        isBridgeEnabled = true,
        localExchangePacket = hostPacket,
      )
    assertArrayEquals(T4tApduEngine.SW_OK, res1.responseApdu)
    assertNull(res1.completedPeerExchange)

    val res2 =
      engine.processApdu(
        commandApdu = apdu2,
        isEmulationEnabled = true,
        ndefMessageBytes = hostNdef,
        isBridgeEnabled = true,
        localExchangePacket = hostPacket,
      )
    assertEquals(peerPacket, res2.completedPeerExchange)
    assertArrayEquals(hostPacket + T4tApduEngine.SW_OK, res2.responseApdu)
  }
}
