package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettingsStore
import com.example.data.PeerExchangeMode
import com.example.data.TagDatabase
import com.example.nfc.ActiveCardStore
import com.example.nfc.DynamicSwitchController
import com.example.nfc.NdefBridgeCodec
import com.example.nfc.NdefHostApduService
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.NfcHardwareHelper
import com.example.nfc.NfcHardwareStatus
import com.example.nfc.PeerExchangePacket
import com.example.nfc.SilentPeerBridgeCoordinator
import com.example.nfc.T4tApduEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DynamicSwitchControllerTest {

  private lateinit var app: Application

  @Before
  fun setUp() {
    app = ApplicationProvider.getApplicationContext()
    DynamicSwitchController.resetForTest(app)
    SilentPeerBridgeCoordinator.resetDedupeForTest()
    NfcHardwareHelper.testStatusProvider = { NfcHardwareStatus.READY }
  }

  @After
  fun tearDown() {
    DynamicSwitchController.resetForTest(app)
    NfcHardwareHelper.testStatusProvider = null
  }

  private fun readNdefFromService(service: NdefHostApduService): List<NdefRecordDraft> {
    val selectAid =
      byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07) +
        T4tApduEngine.NDEF_AID +
        byteArrayOf(0x00)
    assertArrayEquals(T4tApduEngine.SW_OK, service.processCommandApdu(selectAid, null))

    val selectNdef = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)
    assertArrayEquals(T4tApduEngine.SW_OK, service.processCommandApdu(selectNdef, null))

    val lenRes = service.processCommandApdu(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x02), null)
    val nlen = ((lenRes[0].toInt() and 0xFF) shl 8) or (lenRes[1].toInt() and 0xFF)
    val payloadRes =
      service.processCommandApdu(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x02, nlen.toByte()), null)
    val sw = payloadRes.copyOfRange(payloadRes.size - 2, payloadRes.size)
    assertArrayEquals(T4tApduEngine.SW_OK, sw)
    val ndefBytes = payloadRes.copyOf(payloadRes.size - 2)
    return NdefMessageCodec.decodeRecords(ndefBytes)
  }

  @Test
  fun dynamicTrueP2pWindow_persistsFiveSecondsAcrossMemoryClearAndExpiresCleanly() {
    var fakeNow = 1_000_000L
    DynamicSwitchController.testWallClockMs = { fakeNow }

    assertFalse(DynamicSwitchController.isTrueP2pWindowActive(app))
    DynamicSwitchController.activateTrueP2pWindow(app, now = fakeNow)
    assertTrue(DynamicSwitchController.isTrueP2pWindowActive(app))
    assertEquals(5_000L, DynamicSwitchController.remainingWindowMs(app))

    // Simulate process kill / app swiped from recents by wiping RAM cache
    DynamicSwitchController.resetMemoryCacheForTest()
    fakeNow = 1_002_500L
    assertTrue(DynamicSwitchController.isTrueP2pWindowActive(app))
    assertEquals(2_500L, DynamicSwitchController.remainingWindowMs(app))

    fakeNow = 1_004_999L
    assertTrue(DynamicSwitchController.isTrueP2pWindowActive(app))
    assertEquals(1L, DynamicSwitchController.remainingWindowMs(app))

    fakeNow = 1_005_000L
    assertFalse(DynamicSwitchController.isTrueP2pWindowActive(app))
    assertEquals(0L, DynamicSwitchController.remainingWindowMs(app))
  }

  @Test
  fun dynamicHce_keepsSendingOnFirstTapSwitchesToTrueP2pFor5SecondsAndReturnsToStandard() = runBlocking {
    var fakeNow = 2_000_000L
    DynamicSwitchController.testWallClockMs = { fakeNow }

    val activeStore = ActiveCardStore(app)
    activeStore.writeRecords(
      records = listOf(NdefRecordDraft.Url("https://instagram.com/alice_dyn")),
      label = "Alice",
      isReadOnly = true,
    )
    activeStore.setEmulating(true)
    AppSettingsStore(app).updateSettings { it.copy(peerExchangeMode = PeerExchangeMode.DYNAMIC) }

    val dao = TagDatabase.getInstance(app).tagDao()
    val beforeCount = dao.observeSavedLinks().first().size

    // 1. First tap (no Activity running): broadcasts single standard NDEF record
    val serviceProcess1 = Robolectric.setupService(NdefHostApduService::class.java)
    val tap1Records = readNdefFromService(serviceProcess1)
    assertEquals(1, tap1Records.size)
    assertEquals("https://instagram.com/alice_dyn", (tap1Records[0] as NdefRecordDraft.Url).url)

    // Immediately activated True P2P window for 5 seconds
    assertTrue(DynamicSwitchController.isTrueP2pWindowActive(app))

    // 2. Simulate fresh HCE service instance (app swiped from recents) at t=+1.5s:
    // serves True P2P multi-record card (tapshare://exchange + HTTPS fallback)
    DynamicSwitchController.resetMemoryCacheForTest()
    fakeNow += 1_500L
    val serviceProcess2 = Robolectric.setupService(NdefHostApduService::class.java)
    val tap2Records = readNdefFromService(serviceProcess2)
    assertEquals(2, tap2Records.size)
    assertTrue((tap2Records[0] as NdefRecordDraft.Url).url.startsWith("tapshare://exchange?"))
    assertEquals("https://instagram.com/alice_dyn", (tap2Records[1] as NdefRecordDraft.Url).url)

    // 3. During the 5-second True P2P window, incoming peer exchange APDU (0x80 0xE2) is accepted
    // and executes peer's tag action directly without creating a saved card
    val peerPacket =
      NdefBridgeCodec.encodePeerPacket(
        PeerExchangePacket(
          senderId = "peer-bob",
          label = "Bob",
          ndefBytes = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Url("https://github.com/bob_dyn"))),
        )
      )
    val peerApdu =
      byteArrayOf(
        T4tApduEngine.CLA_PROPRIETARY.toByte(),
        T4tApduEngine.INS_PEER_EXCHANGE.toByte(),
        0x00,
        0x00,
        peerPacket.size.toByte(),
      ) + peerPacket
    val peerResp = serviceProcess2.processCommandApdu(peerApdu, null)
    assertTrue(peerResp.size >= 2)
    assertEquals(T4tApduEngine.SW_OK[0], peerResp[peerResp.size - 2])
    assertEquals(T4tApduEngine.SW_OK[1], peerResp[peerResp.size - 1])

    val started = shadowOf(app).nextStartedActivity
    assertNotNull(started)
    assertEquals(Intent.ACTION_VIEW, started!!.action)
    assertEquals("https://github.com/bob_dyn", started.dataString)
    assertEquals(beforeCount, dao.observeSavedLinks().first().size)

    // 4. At t=+5.0s, window expires and HCE reverts cleanly to single-record standard NDEF
    fakeNow = 2_005_000L
    serviceProcess2.onDeactivated(0)
    val tap3Records = readNdefFromService(serviceProcess2)
    assertEquals(1, tap3Records.size)
    assertEquals("https://instagram.com/alice_dyn", (tap3Records[0] as NdefRecordDraft.Url).url)
  }

  @Test
  fun legacyStoredPulsePreference_migratesToDynamicByDefault() {
    app
      .getSharedPreferences("taglink_app_settings", Context.MODE_PRIVATE)
      .edit()
      .putString("peer_exchange_mode", "PULSE")
      .apply()
    val settings = AppSettingsStore(app).readSettings()
    assertEquals(PeerExchangeMode.DYNAMIC, settings.peerExchangeMode)
  }
}
