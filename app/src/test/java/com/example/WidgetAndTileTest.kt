package com.example

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettings
import com.example.data.AppSettingsStore
import com.example.data.SavedLinkEntity
import com.example.data.SavedTagViewMode
import com.example.data.TagDatabase
import com.example.data.TagRepository
import com.example.data.ThemeMode
import com.example.nfc.ActiveCardStore
import com.example.nfc.NdefBridgeCodec
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.NfcHardwareHelper
import com.example.nfc.NfcHardwareStatus
import com.example.nfc.NfcTagReaderHelper
import com.example.nfc.ScannedTagResult
import com.example.nfc.TagScanError
import com.example.nfc.WifiAuthMode
import com.example.ui.TagProgrammerViewModel
import com.example.widget.TagPresetWidgetProvider
import com.example.widget.TagToggleWidgetProvider
import com.example.widget.WidgetPrefs
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetAndTileTest {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  private lateinit var context: Context
  private lateinit var store: ActiveCardStore
  private var simulatedNfcStatus = NfcHardwareStatus.READY

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    simulatedNfcStatus = NfcHardwareStatus.READY
    NfcHardwareHelper.testStatusProvider = { simulatedNfcStatus }
    store = ActiveCardStore(context)
    store.clearCard()
    store.setReadOnly(true)
    AppSettingsStore(context).updateSettings { AppSettings(hasCompletedTutorial = false) }
  }

  @After
  fun tearDown() {
    NfcHardwareHelper.testStatusProvider = null
  }

  @Test
  fun `toggle widget cannot activate emulation when nfc is off`() {
    simulatedNfcStatus = NfcHardwareStatus.NFC_DISABLED
    store.writeRecords(
      records = listOf(NdefRecordDraft.Url("https://instagram.com/taglink")),
      label = "Instagram",
      isReadOnly = true,
    )

    val provider = TagToggleWidgetProvider()
    val intent =
      Intent(context, TagToggleWidgetProvider::class.java).apply {
        action = TagToggleWidgetProvider.ACTION_TOGGLE_EMULATION
      }
    provider.onReceive(context, intent)

    assertFalse(store.readSnapshot().isEmulating)
    val nextActivity = shadowOf(context as Application).nextStartedActivity
    assertNotNull(nextActivity)
    assertEquals(MainActivity::class.java.name, nextActivity.component?.className)
  }

  @Test
  fun `toggle widget activates emulation immediately when nfc is on`() {
    simulatedNfcStatus = NfcHardwareStatus.READY
    store.writeRecords(
      records = listOf(NdefRecordDraft.Url("https://instagram.com/taglink")),
      label = "Instagram",
      isReadOnly = true,
    )

    val provider = TagToggleWidgetProvider()
    val intent =
      Intent(context, TagToggleWidgetProvider::class.java).apply {
        action = TagToggleWidgetProvider.ACTION_TOGGLE_EMULATION
      }
    provider.onReceive(context, intent)
    assertTrue(store.readSnapshot().isEmulating)

    provider.onReceive(context, intent)
    assertFalse(store.readSnapshot().isEmulating)
  }

  @Test
  fun `preset widget loads pinned card and activates only when nfc is on`() {
    val widgetId = 42
    val wifiRecords =
      listOf(
        NdefRecordDraft.Wifi(
          ssid = "StudioGuest",
          password = "pass1234",
          authMode = WifiAuthMode.WPA2_PERSONAL,
        )
      )
    val wifiBytes = NdefMessageCodec.encodeRecords(wifiRecords)
    val savedCard =
      SavedLinkEntity(
        id = 7L,
        label = "Guest WiFi",
        summary = "Wi-Fi: StudioGuest",
        recordCount = 1,
        ndefBytes = wifiBytes,
        ndefByteSize = wifiBytes.size,
        isReadOnly = true,
        lastProgrammedAt = 1_700_000_000_000L,
      )
    WidgetPrefs.savePinnedCard(context, widgetId, savedCard)

    val provider = TagPresetWidgetProvider()
    val intent =
      Intent(context, TagPresetWidgetProvider::class.java).apply {
        action = TagPresetWidgetProvider.ACTION_ACTIVATE_PRESET
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
      }

    simulatedNfcStatus = NfcHardwareStatus.NFC_DISABLED
    provider.onReceive(context, intent)
    assertFalse(store.readSnapshot().isEmulating)

    simulatedNfcStatus = NfcHardwareStatus.READY
    provider.onReceive(context, intent)
    val activated = store.readSnapshot()
    assertTrue(activated.isEmulating)
    assertEquals("Guest WiFi", activated.label)
    assertArrayEquals(wifiBytes, activated.ndefMessageBytes)

    provider.onReceive(context, intent)
    assertFalse(store.readSnapshot().isEmulating)
  }

  @Test
  fun `repository blocks emulation when nfc is off and saves single record`() = runBlocking {
    val repo = TagRepository(context, TagDatabase.getInstance(context).tagDao(), store)
    simulatedNfcStatus = NfcHardwareStatus.NFC_DISABLED
    val programmed =
      repo.programCard(
        record = NdefRecordDraft.Text("Hello NFC", "en"),
        label = "Note",
        isReadOnly = true,
      )
    assertEquals(1, programmed.records.size)

    val blocked = repo.setEmulating(true)
    assertFalse(blocked.isEmulating)

    simulatedNfcStatus = NfcHardwareStatus.READY
    val enabled = repo.setEmulating(true)
    assertTrue(enabled.isEmulating)
  }

  @Test
  fun `scanned physical tag bytes are clamped to single record and saved cleanly`() = runBlocking {
    val multiBytes =
      NdefMessageCodec.encodeRecords(
        listOf(
          NdefRecordDraft.Url("https://instagram.com/taglink"),
          NdefRecordDraft.Text("Extra", "en"),
        )
      )
    val parsed = NfcTagReaderHelper.parseScannedNdefBytes(multiBytes, isReadOnly = true)
    assertTrue(parsed is ScannedTagResult.Success)
    val success = parsed as ScannedTagResult.Success
    assertEquals("https://instagram.com/taglink", success.summary)
    assertEquals(1, NdefMessageCodec.decodeRecords(success.ndefBytes).size)

    val emptyResult = NfcTagReaderHelper.parseScannedNdefBytes(ByteArray(0), isReadOnly = false)
    assertEquals(ScannedTagResult.Failure(TagScanError.EMPTY_TAG), emptyResult)

    val repo = TagRepository(context, TagDatabase.getInstance(context).tagDao(), store)
    val saved = repo.saveCopiedTagBytes(multiBytes, label = "Cloned Card", isReadOnly = true)
    assertEquals("Cloned Card", saved.label)
    assertEquals(1, saved.records.size)
  }

  @Test
  fun `wallet card cover photo saves locally and removes cleanly`() = runBlocking {
    val repo = TagRepository(context, TagDatabase.getInstance(context).tagDao(), store)
    repo.programCard(
      record = NdefRecordDraft.Url("https://instagram.com/wallet_test"),
      label = "Wallet Test",
      isReadOnly = true,
    )
    val savedCard = repo.savedLinks.first().first { it.label == "Wallet Test" }

    val tempSource = File(context.cacheDir, "sample_cover_input.jpg")
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    FileOutputStream(tempSource).use { fos ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
    }
    bitmap.recycle()

    val updated = repo.updateCardCoverImage(savedCard.id, Uri.fromFile(tempSource))
    assertTrue(updated)
    val withCover = repo.savedLinks.first().first { it.id == savedCard.id }
    assertNotNull(withCover.coverImagePath)
    assertTrue(File(withCover.coverImagePath!!).exists())

    val removed = repo.updateCardCoverImage(savedCard.id, null)
    assertTrue(removed)
    val cleared = repo.savedLinks.first().first { it.id == savedCard.id }
    assertEquals(null, cleared.coverImagePath)
    assertFalse(File(withCover.coverImagePath!!).exists())
  }

  @Test
  fun `tutorial autostart 3step socials wallet mode settings and nfc guard flow`() {
    simulatedNfcStatus = NfcHardwareStatus.NFC_DISABLED
    val viewModel = ViewModelProvider(composeRule.activity)[TagProgrammerViewModel::class.java]
    viewModel.refreshState()

    // Interactive tutorial auto-starts on first launch
    composeRule.onNodeWithTag("tutorial_overlay").assertIsDisplayed()
    repeat(TagProgrammerViewModel.TUTORIAL_STEP_COUNT - 1) {
      composeRule.onNodeWithTag("tutorial_next_button").performClick()
    }
    composeRule.onNodeWithTag("tutorial_done_button").assertIsDisplayed()
    composeRule.onNodeWithTag("tutorial_done_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("tutorial_overlay").assertDoesNotExist()
    assertTrue(AppSettingsStore(viewModel.getApplication()).readSettings().hasCompletedTutorial)

    // Verify home options and Shortcuts (Home Widget + Quick Tile) and absence of "1 record per tag"
    composeRule.onNodeWithTag("home_activate_tag_button").assertIsDisplayed()
    composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_make_tag_button"))
    composeRule.onNodeWithTag("home_make_tag_button").assertIsDisplayed()
    composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_copy_tag_button"))
    composeRule.onNodeWithTag("home_copy_tag_button").assertIsDisplayed()
    composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_shortcuts_card"))
    composeRule.onNodeWithTag("home_shortcuts_card").assertIsDisplayed()
    composeRule.onNodeWithTag("pin_widget_prompt_button").assertIsDisplayed()
    composeRule.onNodeWithTag("pin_quick_tile_button").assertIsDisplayed()
    composeRule.onNodeWithText("1 record per tag").assertDoesNotExist()

    // Info button replays interactive tutorial; Skip closes it
    composeRule.onNodeWithTag("tutorial_button").performClick()
    composeRule.onNodeWithTag("tutorial_overlay").assertIsDisplayed()
    composeRule.onNodeWithTag("tutorial_skip_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("tutorial_overlay").assertDoesNotExist()

    // Settings: dark theme toggle, default Simplified view -> switch to Wallet, verify Replay Tutorial removed
    composeRule.onNodeWithTag("app_settings_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("settings_replay_tutorial_button").assertDoesNotExist()
    composeRule.onNodeWithText("Replay Interactive Tutorial").assertDoesNotExist()
    composeRule.onNodeWithTag("theme_mode_dark").performClick()
    assertEquals(SavedTagViewMode.SIMPLIFIED, viewModel.uiState.value.settings.savedTagViewMode)
    composeRule.onNodeWithTag("tag_view_wallet").performScrollTo().performClick()
    composeRule.waitForIdle()
    assertEquals(ThemeMode.DARK, viewModel.uiState.value.settings.themeMode)
    assertEquals(SavedTagViewMode.WALLET, viewModel.uiState.value.settings.savedTagViewMode)
    assertTrue(viewModel.uiState.value.settings.multiRecordBridgeEnabled)
    composeRule.onNodeWithTag("setting_multi_record_bridge").performScrollTo().performClick()
    composeRule.waitForIdle()
    assertFalse(viewModel.uiState.value.settings.multiRecordBridgeEnabled)
    composeRule.onNodeWithTag("setting_multi_record_bridge").performClick()
    composeRule.waitForIdle()
    assertTrue(viewModel.uiState.value.settings.multiRecordBridgeEnabled)

    // Guided 3-step Make Tag: verify Contact Card shows Pick from Contacts, then Socials -> Instagram -> @username -> Save Tag
    composeRule.onNodeWithTag("back_button").performClick()
    composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_make_tag_button"))
    composeRule.onNodeWithTag("home_make_tag_button").performClick()
    composeRule.onNodeWithTag("preset_contact").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("pick_contact_button").assertIsDisplayed()
    composeRule.onNodeWithTag("make_change_type_button").performClick()
    composeRule.onNodeWithTag("preset_socials").assertIsDisplayed()
    composeRule.onNodeWithTag("preset_link").assertIsDisplayed()
    composeRule.onNodeWithTag("preset_socials").performClick()

    // Step 2: List of Socials
    composeRule.onNodeWithTag("choose_social_step").assertIsDisplayed()
    composeRule.onNodeWithTag("social_instagram").assertIsDisplayed()
    composeRule.onNodeWithTag("social_tiktok").assertIsDisplayed()
    composeRule.onNodeWithTag("social_instagram").performClick()

    // Step 3: Details
    composeRule.onNodeWithTag("social_handle_input").performTextInput("taglink_demo")
    composeRule.onNodeWithTag("write_card_button").performScrollTo().performClick()
    composeRule.waitUntil(3_000) {
      composeRule.onAllNodes(hasTestTag("emulation_toggle_button")).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("nfc_disabled_banner").assertIsDisplayed()
    composeRule.onNodeWithTag("emulation_toggle_button").assertIsNotEnabled()
    assertEquals(
      "https://instagram.com/taglink_demo",
      (viewModel.uiState.value.activeRecord as NdefRecordDraft.Url).url,
    )

    // Wallet view renders stacked rectangular cards + photo customize button under Saved Tags
    composeRule.onNodeWithTag("activate_list").performScrollToNode(hasTestTag("wallet_card_stack"))
    composeRule.onNodeWithTag("wallet_card_stack").assertIsDisplayed()
    val savedId = viewModel.uiState.value.savedCards.first().id
    composeRule.onNodeWithTag("wallet_card_$savedId").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("wallet_photo_button_$savedId").performScrollTo().assertIsDisplayed()

    // Copy Tag screen: shows NFC banner when NFC off and accepts physical scanned NDEF result
    composeRule.onNodeWithTag("back_button").performClick()
    composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_copy_tag_button"))
    composeRule.onNodeWithTag("home_copy_tag_button").performClick()
    composeRule.onNodeWithTag("copy_nfc_disabled_banner").assertIsDisplayed()

    val scanned =
      NfcTagReaderHelper.parseScannedNdefBytes(
        NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Url("https://copied.example"))),
        isReadOnly = true,
      )
    composeRule.runOnUiThread { viewModel.onPhysicalTagScanned(scanned) }
    composeRule.onNodeWithTag("copy_scanned_result_card").assertIsDisplayed()
    composeRule.onNodeWithTag("copy_save_tag_button").performScrollTo().performClick()
    composeRule.waitUntil(3_000) {
      composeRule.onAllNodes(hasTestTag("emulation_toggle_button")).fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(
      "https://copied.example",
      (viewModel.uiState.value.activeRecord as NdefRecordDraft.Url).url,
    )
  }

  @Test
  fun `hce service broadcasts multi-record bridge by default and falls back to single record when off`() {
    simulatedNfcStatus = NfcHardwareStatus.READY
    store.writeRecords(
      records = listOf(NdefRecordDraft.Url("https://instagram.com/taglink")),
      label = "Instagram",
      isReadOnly = true,
    )
    store.setEmulating(true)

    val settingsStore = AppSettingsStore(context)
    assertTrue(settingsStore.readSettings().multiRecordBridgeEnabled)

    val service = org.robolectric.Robolectric.setupService(com.example.nfc.NdefHostApduService::class.java)
    fun readNdefFromService(): List<NdefRecordDraft> {
      val selectAid =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07) +
          com.example.nfc.T4tApduEngine.NDEF_AID +
          byteArrayOf(0x00)
      service.processCommandApdu(selectAid, null)
      val selectNdef = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)
      service.processCommandApdu(selectNdef, null)
      val lenRes = service.processCommandApdu(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x02), null)
      val nlen = ((lenRes[0].toInt() and 0xFF) shl 8) or (lenRes[1].toInt() and 0xFF)
      val payloadRes =
        service.processCommandApdu(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x02, nlen.toByte()), null)
      val ndefBytes = payloadRes.copyOf(payloadRes.size - 2)
      return NdefMessageCodec.decodeRecords(ndefBytes)
    }

    val multiRecords = readNdefFromService()
    assertEquals(2, multiRecords.size)
    assertTrue((multiRecords[0] as NdefRecordDraft.Url).url.startsWith("tapshare://exchange?"))
    assertEquals("https://instagram.com/taglink", (multiRecords[1] as NdefRecordDraft.Url).url)

    settingsStore.updateSettings { it.copy(multiRecordBridgeEnabled = false) }
    val singleRecords = readNdefFromService()
    assertEquals(1, singleRecords.size)
    assertEquals("https://instagram.com/taglink", (singleRecords[0] as NdefRecordDraft.Url).url)
  }

  @Test
  fun `app-to-app bridge executes tapped tag action on both phones without adding new cards`() = runBlocking {
    simulatedNfcStatus = NfcHardwareStatus.READY
    com.example.nfc.SilentPeerBridgeCoordinator.resetDedupeForTest()

    val app = ApplicationProvider.getApplicationContext<Application>()
    val dao = TagDatabase.getInstance(context).tagDao()
    val beforeCount = dao.observeSavedLinks().first().size

    // Phone A (emulating) has Owner card loaded
    val ownerRecord = NdefRecordDraft.Url("https://instagram.com/owner_a")
    store.writeRecords(records = listOf(ownerRecord), label = "Owner", isReadOnly = true)
    store.setEmulating(true)

    // 1. Receiving phone (Phone B) gets tapshare://exchange URI from Phone A -> executes Phone A's action, adds 0 cards
    val peerARecordBytes = NdefMessageCodec.encodeRecords(listOf(ownerRecord))
    val bridgeUriFromA =
      NdefBridgeCodec.buildBridgeUri(
        primaryNdefBytes = peerARecordBytes,
        label = "Instagram (@owner_a)",
        senderId = "phone-a-id",
      )
    val bridgeIntent =
      Intent(android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED, Uri.parse(bridgeUriFromA))
    org.robolectric.Robolectric.buildActivity(com.example.nfc.BridgeExchangeActivity::class.java, bridgeIntent).create()

    val startedOnB = shadowOf(app).nextStartedActivity
    assertNotNull(startedOnB)
    assertEquals(Intent.ACTION_VIEW, startedOnB!!.action)
    assertEquals("https://instagram.com/owner_a", startedOnB.dataString)
    assertEquals(beforeCount, dao.observeSavedLinks().first().size)

    // 2. Emulating phone (Phone A) receives Phone B's return card via NdefHostApduService -> executes Phone B's action, adds 0 cards
    val service = org.robolectric.Robolectric.setupService(com.example.nfc.NdefHostApduService::class.java)
    val peerBPacket =
      NdefBridgeCodec.encodePeerPacket(
        com.example.nfc.PeerExchangePacket(
          senderId = "phone-b-id",
          label = "LinkedIn (@peer_b)",
          ndefBytes = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Url("https://linkedin.com/in/peer_b"))),
        )
      )
    val peerExchangeApdu =
      byteArrayOf(
        com.example.nfc.T4tApduEngine.CLA_PROPRIETARY.toByte(),
        com.example.nfc.T4tApduEngine.INS_PEER_EXCHANGE.toByte(),
        0x00,
        0x00,
        peerBPacket.size.toByte(),
      ) + peerBPacket
    val apduResponse = service.processCommandApdu(peerExchangeApdu, null)
    assertTrue(apduResponse.size >= 2)

    val startedOnA = shadowOf(app).nextStartedActivity
    assertNotNull(startedOnA)
    assertEquals(Intent.ACTION_VIEW, startedOnA!!.action)
    assertEquals("https://linkedin.com/in/peer_b", startedOnA.dataString)
    assertEquals(beforeCount, dao.observeSavedLinks().first().size)
    assertEquals("Owner", store.readSnapshot().label)
  }
}
