package com.example

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettingsStore
import com.example.data.PeerExchangeMode
import com.example.data.TagDatabase
import com.example.nfc.ActiveCardStore
import com.example.nfc.DynamicAidController
import com.example.nfc.DynamicSwitchController
import com.example.nfc.NdefBridgeCodec
import com.example.nfc.NdefHostApduService
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.NfcHardwareHelper
import com.example.nfc.NfcHardwareStatus
import com.example.nfc.NfcKeepAliveService
import com.example.nfc.NfcStateReceiver
import com.example.nfc.PeerExchangePacket
import com.example.nfc.SilentPeerBridgeCoordinator
import com.example.nfc.T4tApduEngine
import android.nfc.NfcAdapter
import android.provider.Settings
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
    AppForegroundTracker.testForegroundOverride = false
    shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    NfcHardwareHelper.testStatusProvider = { NfcHardwareStatus.READY }
    NfcKeepAliveService.isServiceRunning = false
  }

  @After
  fun tearDown() {
    AppForegroundTracker.testForegroundOverride = null
    DynamicSwitchController.resetForTest(app)
    NfcHardwareHelper.testStatusProvider = null
    NfcHardwareHelper.testDynamicAidController = null
    NfcHardwareHelper.testIgnoringBatteryOptimizations = null
    NfcHardwareHelper.testNotificationsEnabled = null
    NfcKeepAliveService.isServiceRunning = false
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

    // Verify off-screen background HCE delivery also posts heads-up notification so Android BAL cannot drop it
    val notifManager = app.getSystemService(NotificationManager::class.java)
    val posted = shadowOf(notifManager).allNotifications
    assertEquals(1, posted.size)
    assertEquals("Tag received", posted[0].extras.getString(android.app.Notification.EXTRA_TITLE))

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

  @Test
  fun dynamicMode_schedulesAidRemoveAndRestoreBounceAfterTap1() {
    val aidOps = mutableListOf<Pair<Long, String>>()
    val scheduledTasks = mutableListOf<Pair<Long, () -> Unit>>()
    NfcHardwareHelper.testDynamicAidController =
      object : DynamicAidController {
        override fun registerNdefAid(context: Context): Boolean {
          aidOps += 1L to "register"
          return true
        }

        override fun removeNdefAid(context: Context): Boolean {
          aidOps += 0L to "remove"
          return true
        }
      }
    DynamicSwitchController.testScheduleBounce = { delayMs, action ->
      scheduledTasks += delayMs to action
    }

    val activeStore = ActiveCardStore(app)
    activeStore.writeRecords(
      records = listOf(NdefRecordDraft.Url("https://instagram.com/bounce_test")),
      label = "Bounce",
      isReadOnly = true,
    )
    activeStore.setEmulating(true)
    AppSettingsStore(app).updateSettings { it.copy(peerExchangeMode = PeerExchangeMode.DYNAMIC) }

    val service = Robolectric.setupService(NdefHostApduService::class.java)
    readNdefFromService(service)

    assertTrue(DynamicSwitchController.isTrueP2pWindowActive(app))
    assertEquals(2, scheduledTasks.size)
    assertEquals(DynamicSwitchController.RF_RESET_REMOVE_DELAY_MS, scheduledTasks[0].first)
    assertEquals(DynamicSwitchController.RF_RESET_RESTORE_DELAY_MS, scheduledTasks[1].first)

    // Execute bounce remove then restore
    scheduledTasks[0].second.invoke()
    scheduledTasks[1].second.invoke()
    assertEquals(listOf("remove", "register"), aidOps.map { it.second })

    // Clearing restores AID safely
    DynamicSwitchController.clear(app)
    assertEquals("register", aidOps.last().second)
  }

  @Test
  fun keepAliveService_startsOnlyWhenEmulatingAndStopsOnActionStop() {
    val store = ActiveCardStore(app)
    store.writeRecords(
      records = listOf(NdefRecordDraft.Url("https://example.com")),
      label = "WorkCard",
      isReadOnly = true,
    )
    store.setEmulating(true)
    AppSettingsStore(app).updateSettings { it.copy(stopOnLeaveApp = false) }

    val decision = NfcKeepAliveService.inspectShouldStayAlive(app)
    assertTrue(decision.shouldRun)

    val startIntent =
      Intent(app, NfcKeepAliveService::class.java).apply {
        action = NfcKeepAliveService.ACTION_START_OR_UPDATE
      }
    val controller = Robolectric.buildService(NfcKeepAliveService::class.java, startIntent).create()
    controller.startCommand(0, 1)
    assertTrue(NfcKeepAliveService.isServiceRunning)

    // Stop action from ongoing notification clears emulation and dynamic window
    DynamicSwitchController.activateTrueP2pWindow(app)
    val stopIntent =
      Intent(app, NfcKeepAliveService::class.java).apply {
        action = NfcKeepAliveService.ACTION_STOP_EMULATION
      }
    Robolectric.buildService(NfcKeepAliveService::class.java, stopIntent).create().startCommand(0, 2)

    assertFalse(NfcKeepAliveService.isServiceRunning)
    assertFalse(store.readSnapshot().isEmulating)
    assertFalse(DynamicSwitchController.isTrueP2pWindowActive(app))

    // When stopOnLeaveApp is true, keep-alive should not stay running
    store.setEmulating(true)
    AppSettingsStore(app).updateSettings { it.copy(stopOnLeaveApp = true) }
    assertFalse(NfcKeepAliveService.inspectShouldStayAlive(app).shouldRun)
  }

  @Test
  fun nfcStateReceiver_registersAidOnBootAndDisablesEmulationWhenNfcTurnsOff() {
    val events = mutableListOf<String>()
    NfcHardwareHelper.testDynamicAidController =
      object : DynamicAidController {
        override fun registerNdefAid(context: Context): Boolean {
          events += "register"
          return true
        }

        override fun removeNdefAid(context: Context): Boolean = true
      }

    val store = ActiveCardStore(app)
    store.writeRecords(
      records = listOf(NdefRecordDraft.Url("https://example.com")),
      label = "BootCard",
      isReadOnly = true,
    )
    store.setEmulating(true)
    DynamicSwitchController.activateTrueP2pWindow(app)

    val receiver = NfcStateReceiver()
    receiver.onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
    assertTrue(events.contains("register"))

    receiver.onReceive(
      app,
      Intent(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED).apply {
        putExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
      },
    )
    assertFalse(store.readSnapshot().isEmulating)
    assertFalse(DynamicSwitchController.isTrueP2pWindowActive(app))
  }

  @Test
  fun hardwareHelper_buildsBatteryAndNotificationSettingsIntentsCleanly() {
    val batteryIntent = NfcHardwareHelper.createBatteryOptimizationSettingsIntent(app)
    assertTrue(
      batteryIntent.action == Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS ||
        batteryIntent.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    )

    val notifIntent = NfcHardwareHelper.createNotificationSettingsIntent(app)
    assertTrue(
      notifIntent.action == Settings.ACTION_APP_NOTIFICATION_SETTINGS ||
        notifIntent.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    )
  }
}
