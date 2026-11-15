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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
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
    AppSettingsStore(context).updateSettings { com.example.data.AppSettings() }
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
  fun `home shows activate and make tag options tutorial settings and disables activate when nfc off`() {
    simulatedNfcStatus = NfcHardwareStatus.NFC_DISABLED
    store.writeRecords(
      records = listOf(NdefRecordDraft.Url("https://example.com")),
      label = "Site",
      isReadOnly = true,
    )

    composeRule.onNodeWithTag("home_activate_tag_button").assertIsDisplayed()
    composeRule.onNodeWithTag("home_make_tag_button").assertIsDisplayed()
    composeRule.onNodeWithTag("pin_widget_prompt_button").assertIsDisplayed()

    composeRule.onNodeWithTag("tutorial_button").performClick()
    composeRule.onNodeWithTag("tutorial_done_button").assertIsDisplayed()
    composeRule.onNodeWithTag("tutorial_done_button").performClick()

    composeRule.onNodeWithTag("pin_widget_prompt_button").performClick()
    composeRule.onNodeWithTag("pin_toggle_widget_option").assertIsDisplayed()
    composeRule.onNodeWithTag("pin_preset_widget_option").assertIsDisplayed()
    composeRule.onNodeWithTag("pin_widget_cancel_button").performClick()
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("pin_widget_cancel_button").assertDoesNotExist()

    composeRule.onNodeWithTag("app_settings_button").performClick()
    composeRule.waitForIdle()
    val viewModel = ViewModelProvider(composeRule.activity)[TagProgrammerViewModel::class.java]
    composeRule.onNodeWithTag("theme_mode_dark").assertIsDisplayed()
    composeRule.onNodeWithTag("theme_mode_dark").performClick()
    composeRule.waitForIdle()
    assertEquals(ThemeMode.DARK, viewModel.uiState.value.settings.themeMode)
    assertEquals(
      ThemeMode.DARK,
      AppSettingsStore(viewModel.getApplication()).readSettings().themeMode,
    )

    composeRule.onNodeWithTag("back_button").performClick()
    composeRule.onNodeWithTag("home_make_tag_button").performClick()
    composeRule.onNodeWithTag("link_url_input").performTextInput("https://taglink.test")
    composeRule.onNodeWithTag("write_card_button").performScrollTo().performClick()
    composeRule.waitUntil(3_000) {
      composeRule.onAllNodes(hasTestTag("emulation_toggle_button")).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("nfc_disabled_banner").assertIsDisplayed()
    composeRule.onNodeWithTag("emulation_toggle_button").assertIsNotEnabled()
  }
}
