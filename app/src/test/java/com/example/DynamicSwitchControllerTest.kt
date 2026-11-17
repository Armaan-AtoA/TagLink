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
import com.example.nfc.NdefHostApduService
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.NfcHardwareHelper
import com.example.nfc.NfcHardwareStatus
import com.example.nfc.SilentPeerBridgeCoordinator
import com.example.nfc.T4tApduEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    DynamicSwitchController.resetForTest()
    SilentPeerBridgeCoordinator.resetDedupeForTest()
    NfcHardwareHelper.testStatusProvider = { NfcHardwareStatus.READY }
  }

  @After
  fun tearDown() {
    DynamicSwitchController.resetForTest()
    NfcHardwareHelper.testStatusProvider = null
  }

  @Test
  fun receiveWindow_suppressesHceForFullThreeSecondsThenResumesSend() {
    var fakeNow = 10_000L
    DynamicSwitchController.testClockMs = { fakeNow }

    assertFalse(DynamicSwitchController.isSuppressingHce())
    DynamicSwitchController.activateReceiveWindow(now = fakeNow, scheduleAutoClear = false)

    assertTrue(DynamicSwitchController.isSuppressingHce())
    assertEquals(3_000L, DynamicSwitchController.remainingWindowMs())

    fakeNow = 11_500L
    assertTrue(DynamicSwitchController.isSuppressingHce())
    assertEquals(1_500L, DynamicSwitchController.remainingWindowMs())

    fakeNow = 12_999L
    assertTrue(DynamicSwitchController.isSuppressingHce())
    assertEquals(1L, DynamicSwitchController.remainingWindowMs())

    fakeNow = 13_000L
    assertFalse(DynamicSwitchController.isSuppressingHce())
    assertEquals(0L, DynamicSwitchController.remainingWindowMs())
  }

  @Test
  fun incomingTagDuringReceiveWindow_executesActionWithoutSavingAndKeepsWindowUntilFull3s() = runBlocking {
    var fakeNow = 20_000L
    DynamicSwitchController.testClockMs = { fakeNow }

    val dao = TagDatabase.getInstance(app).tagDao()
    val beforeCount = dao.observeSavedLinks().first().size

    DynamicSwitchController.activateReceiveWindow(now = fakeNow, scheduleAutoClear = false)
    assertTrue(DynamicSwitchController.isSuppressingHce())

    fakeNow = 20_700L
    val incomingBytes =
      NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Url("https://github.com/dynamic_peer")))
    val firstLaunch = SilentPeerBridgeCoordinator.executeIncomingPeerCard(app, incomingBytes, "")
    assertTrue(firstLaunch)

    val started = shadowOf(app).nextStartedActivity
    assertNotNull(started)
    assertEquals(Intent.ACTION_VIEW, started!!.action)
    assertEquals("https://github.com/dynamic_peer", started.dataString)
    assertEquals(beforeCount, dao.observeSavedLinks().first().size)

    // Still inside 3-second window after receiving incoming tag (full 3s wait)
    assertTrue(DynamicSwitchController.isSuppressingHce())
    assertEquals(2_300L, DynamicSwitchController.remainingWindowMs())

    // Duplicate tap within the same 3-second window is ignored
    val dupLaunch = SilentPeerBridgeCoordinator.executeIncomingPeerCard(app, incomingBytes, "")
    assertFalse(dupLaunch)
    assertNull(shadowOf(app).nextStartedActivity)

    // After full 3 seconds pass, clears back to Send
    fakeNow = 23_000L
    DynamicSwitchController.clear()
    assertFalse(DynamicSwitchController.isSuppressingHce())
  }

  @Test
  fun hceService_triggers3SecondSuppressionAfterCompletedReadAndResumesAfterClear() {
    var fakeNow = 50_000L
    DynamicSwitchController.testClockMs = { fakeNow }

    val activeStore = ActiveCardStore(app)
    activeStore.writeRecords(
      records = listOf(NdefRecordDraft.Url("https://instagram.com/dynamic_sender")),
      label = "Dynamic Card",
      isReadOnly = true,
    )
    activeStore.setEmulating(true)
    AppSettingsStore(app).updateSettings { it.copy(peerExchangeMode = PeerExchangeMode.DYNAMIC) }

    val service = Robolectric.setupService(NdefHostApduService::class.java)
    val selectAid =
      byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07) +
        T4tApduEngine.NDEF_AID +
        byteArrayOf(0x00)
    assertArrayEquals(T4tApduEngine.SW_OK, service.processCommandApdu(selectAid, null))

    val selectNdef = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)
    assertArrayEquals(T4tApduEngine.SW_OK, service.processCommandApdu(selectNdef, null))

    val lenRes = service.processCommandApdu(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x02), null)
    val nlen = ((lenRes[0].toInt() and 0xFF) shl 8) or (lenRes[1].toInt() and 0xFF)
    val readPayload =
      service.processCommandApdu(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x02, nlen.toByte()), null)
    val sw = readPayload.copyOfRange(readPayload.size - 2, readPayload.size)
    assertArrayEquals(T4tApduEngine.SW_OK, sw)

    // Link deactivation after read immediately starts the 3-second HCE suppression window
    service.onDeactivated(0)
    assertTrue(DynamicSwitchController.isSuppressingHce())

    // During the 3-second wait, card refuses AID selection
    assertArrayEquals(T4tApduEngine.SW_FILE_NOT_FOUND, service.processCommandApdu(selectAid, null))

    // Once the 3 seconds expire, HCE answers normally again
    fakeNow += 3_000L
    DynamicSwitchController.clear()
    assertFalse(DynamicSwitchController.isSuppressingHce())
    assertArrayEquals(T4tApduEngine.SW_OK, service.processCommandApdu(selectAid, null))
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
