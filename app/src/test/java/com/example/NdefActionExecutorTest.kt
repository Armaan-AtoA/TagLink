package com.example

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.example.nfc.NdefActionExecutor
import com.example.nfc.NdefBridgeCodec
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NdefActionExecutorTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun buildActionIntent_resolvesUrlAndUnwrapsBridgePayload() {
    val primary = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Url("https://instagram.com/alice")))
    val bridged = NdefBridgeCodec.buildMultiRecordNdef(primary, "Instagram", "sender1")

    val intent = NdefActionExecutor.buildActionIntent(context, bridged)
    assertNotNull(intent)
    assertEquals(Intent.ACTION_VIEW, intent!!.action)
    assertEquals("https://instagram.com/alice", intent.dataString)
  }

  @Test
  fun buildActionIntent_neverExecutesRawBridgeUriDirectly() {
    val rawBridgeOnly = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Url("tapshare://exchange?v=1")))
    val intent = NdefActionExecutor.buildActionIntent(context, rawBridgeOnly)
    assertNull(intent)
  }

  @Test
  fun buildActionIntent_resolvesPhoneEmailSmsGeoContactAndText() {
    val phoneBytes = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Phone("+15550199")))
    val phoneIntent = NdefActionExecutor.buildActionIntent(context, phoneBytes)!!
    assertEquals(Intent.ACTION_DIAL, phoneIntent.action)
    assertEquals("tel:+15550199", phoneIntent.dataString)

    val emailBytes =
      NdefMessageCodec.encodeRecords(
        listOf(NdefRecordDraft.Email("ada@example.com", subject = "Hi", body = "Test"))
      )
    val emailIntent = NdefActionExecutor.buildActionIntent(context, emailBytes)!!
    assertEquals(Intent.ACTION_SENDTO, emailIntent.action)
    assertEquals(true, emailIntent.dataString?.startsWith("mailto:ada@example.com"))

    val smsBytes = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Sms("+15550188", "Hey")))
    val smsIntent = NdefActionExecutor.buildActionIntent(context, smsBytes)!!
    assertEquals(Intent.ACTION_SENDTO, smsIntent.action)
    assertEquals("smsto:+15550188", smsIntent.dataString)
    assertEquals("Hey", smsIntent.getStringExtra("sms_body"))

    val geoBytes = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Geo("37.7749", "-122.4194", "SF")))
    val geoIntent = NdefActionExecutor.buildActionIntent(context, geoBytes)!!
    assertEquals(Intent.ACTION_VIEW, geoIntent.action)
    assertEquals(true, geoIntent.dataString?.startsWith("geo:37.7749,-122.4194"))

    val contactBytes =
      NdefMessageCodec.encodeRecords(
        listOf(
          NdefRecordDraft.Contact(
            fullName = "Ada Lovelace",
            phone = "+15550101",
            email = "ada@example.com",
          )
        )
      )
    val contactIntent = NdefActionExecutor.buildActionIntent(context, contactBytes)!!
    assertEquals(ContactsContract.Intents.Insert.ACTION, contactIntent.action)
    assertEquals("Ada Lovelace", contactIntent.getStringExtra(ContactsContract.Intents.Insert.NAME))
    assertEquals("+15550101", contactIntent.getStringExtra(ContactsContract.Intents.Insert.PHONE))

    val textBytes = NdefMessageCodec.encodeRecords(listOf(NdefRecordDraft.Text("Hello NFC", "en")))
    val textIntent = NdefActionExecutor.buildActionIntent(context, textBytes)!!
    assertEquals(Intent.ACTION_CHOOSER, textIntent.action)
  }
}
