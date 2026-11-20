package com.example

import android.app.Application
import android.service.quicksettings.Tile
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettingsStore
import com.example.nfc.ActiveCardStore
import com.example.nfc.NdefRecordDraft
import com.example.nfc.QrFallbackActivity
import com.example.nfc.QrFallbackTileService
import com.example.nfc.WifiAuthMode
import com.example.qr.QrCodeGenerator
import com.example.qr.QrModuleShape
import com.example.qr.QrPayloadFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QrFallbackTest {

  @get:Rule val composeRule = createAndroidComposeRule<QrFallbackActivity>()

  @Test
  fun payloadFormatter_formatsAllTagTypesForStandardCameraScanners() {
    assertEquals(
      "https://example.com/booth",
      QrPayloadFormatter.formatRecord(NdefRecordDraft.Url("example.com/booth")),
    )
    assertEquals(
      "Booth #42 Pass",
      QrPayloadFormatter.formatRecord(NdefRecordDraft.Text("Booth #42 Pass")),
    )

    val vcard =
      QrPayloadFormatter.formatRecord(
        NdefRecordDraft.Contact(
          fullName = "Armaan Exhibitor",
          organization = "TagLink Labs",
          phone = "+15550199",
          email = "booth@taglink.dev",
        )
      )
    assertTrue(vcard.startsWith("BEGIN:VCARD"))
    assertTrue(vcard.contains("FN:Armaan Exhibitor"))
    assertTrue(vcard.contains("TEL;TYPE=CELL:+15550199"))
    assertTrue(vcard.contains("EMAIL:booth@taglink.dev"))
    assertTrue(vcard.endsWith("END:VCARD"))

    val wifi =
      QrPayloadFormatter.formatRecord(
        NdefRecordDraft.Wifi(
          ssid = "Expo;Guest:Net",
          password = "p@ss\\word",
          authMode = WifiAuthMode.WPA2_PERSONAL,
        )
      )
    assertEquals("WIFI:T:WPA;S:Expo\\;Guest\\:Net;P:p@ss\\\\word;;", wifi)

    val appUrl = QrPayloadFormatter.formatRecord(NdefRecordDraft.AppLaunch("com.instagram.android"))
    assertEquals("https://play.google.com/store/apps/details?id=com.instagram.android", appUrl)
  }

  @Test
  fun qrGenerator_createsValidMatrixAndCalculatesContrast() {
    assertNull(QrCodeGenerator.encode(""))
    val matrix = QrCodeGenerator.encode("https://github.com/Armaan-AtoA/TagLink")
    assertNotNull(matrix)
    assertTrue(matrix!!.size >= 21)
    assertTrue(matrix.get(0, 0)) // Top-left finder corner module is always dark

    val highContrast = QrCodeGenerator.contrastRatio(0xFF111315L, 0xFFFFFFFFL)
    val lowContrast = QrCodeGenerator.contrastRatio(0xFF999999L, 0xFFAAAAAAL)
    assertTrue(highContrast > 10f)
    assertTrue(lowContrast < QrCodeGenerator.MIN_RECOMMENDED_CONTRAST)

    val tealArgb = QrCodeGenerator.hsvToArgb(180f, 1f, 0.5f)
    assertEquals("#008080", QrCodeGenerator.formatHexColor(tealArgb))
    val roundTripHsv = QrCodeGenerator.argbToHsv(tealArgb)
    assertEquals(180f, roundTripHsv[0], 1.5f)
    assertEquals(1f, roundTripHsv[1], 0.02f)
    assertEquals(0.5f, roundTripHsv[2], 0.02f)
  }

  @Test
  fun qrCustomizationSettings_persistColorsAndModuleShape() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val store = AppSettingsStore(app)
    store.updateSettings {
      it.copy(
        qrForegroundArgb = 0xFF0E5E31L,
        qrBackgroundArgb = 0xFFF2FBF5L,
        qrModuleShape = QrModuleShape.ROUNDED,
      )
    }
    val reloaded = store.readSettings()
    assertEquals(0xFF0E5E31L, reloaded.qrForegroundArgb)
    assertEquals(0xFFF2FBF5L, reloaded.qrBackgroundArgb)
    assertEquals(QrModuleShape.ROUNDED, reloaded.qrModuleShape)
  }

  @Test
  fun qrQuickTile_andOverlayActivity_displayLoadedTagAndEmptyState() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val cardStore = ActiveCardStore(app)
    cardStore.clearCard()

    val controller = Robolectric.buildService(QrFallbackTileService::class.java).create()
    val service = controller.get()

    service.onStartListening()
    val tile = service.qsTile
    assertNotNull(tile)
    assertEquals(Tile.STATE_INACTIVE, tile.state)

    // Empty activity fallback card when no tag is loaded
    composeRule.runOnUiThread { composeRule.activity.reloadState() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("qr_overlay_empty_card").assertIsDisplayed()

    // Load a tag and verify tile updates and overlay shows customized scan-ready QR
    cardStore.writeRecords(
      records = listOf(NdefRecordDraft.Url("https://taglink.expo/booth")),
      label = "Expo Booth",
      isReadOnly = true,
    )
    service.onStartListening()
    assertEquals(Tile.STATE_ACTIVE, tile.state)
    assertEquals("Expo Booth", tile.label.toString())

    composeRule.runOnUiThread { composeRule.activity.reloadState() }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("fallback_qr_dialog").assertIsDisplayed()
    composeRule.onNodeWithTag("qr_matrix_canvas").assertIsDisplayed()
    composeRule.onNodeWithTag("qr_customizer_panel").assertDoesNotExist()

    service.onClick()
    val launched = shadowOf(app).nextStartedActivity
    assertNotNull(launched)
    assertEquals(QrFallbackActivity::class.java.name, launched!!.component?.className)
  }
}
