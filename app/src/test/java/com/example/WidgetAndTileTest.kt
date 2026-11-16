package com.example

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
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
import com.example.data.TagDatabase
import com.example.data.TagRepository
import com.example.data.ThemeMode
import com.example.nfc.ActiveCardStore
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
  fun `tutorial autostart guided make tag copy shortcuts settings and nfc guard flow`() {
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

    // Pin Widget dialog from Shortcuts
    composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_shortcuts_card"))
    composeRule.onNodeWithTag("pin_widget_prompt_button").performClick()
    composeRule.onNodeWithTag("pin_toggle_widget_option").assertIsDisplayed()
    composeRule.onNodeWithTag("pin_preset_widget_option").assertIsDisplayed()
    composeRule.onNodeWithTag("pin_widget_cancel_button").performClick()
    composeRule.waitForIdle()

    // Quick Tile shortcut from Shortcuts works without crashing
    composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_shortcuts_card"))
    composeRule.onNodeWithTag("pin_quick_tile_button").performClick()
    composeRule.waitForIdle()

    // Settings dark theme toggle
    composeRule.onNodeWithTag("app_settings_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("theme_mode_dark").assertIsDisplayed()
    composeRule.onNodeWithTag("theme_mode_dark").performClick()
    composeRule.waitForIdle()
    assertEquals(ThemeMode.DARK, viewModel.uiState.value.settings.themeMode)

    // Guided 2-step Make Tag: pick Instagram, then enter @username, save, verify NFC-off guard
    composeRule.onNodeWithTag("back_button").performClick()
    composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("home_make_tag_button"))
    composeRule.onNodeWithTag("home_make_tag_button").performClick()
    composeRule.onNodeWithTag("preset_instagram").assertIsDisplayed()
    composeRule.onNodeWithTag("preset_link").assertIsDisplayed()
    composeRule.onNodeWithTag("preset_contact").assertIsDisplayed()
    composeRule.onNodeWithTag("preset_instagram").performClick()

    composeRule.onNodeWithTag("instagram_handle_input").performTextInput("taglink_demo")
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
}
