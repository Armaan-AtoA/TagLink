package com.example

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.BaseColumns
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.example.nfc.ContactBookImporter
import com.example.ui.TagProgrammerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ContactBookImporterTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    ShadowContentResolver.registerProviderInternal(
      ContactsContract.AUTHORITY,
      FakeContactsContentProvider(),
    )
  }

  @Test
  fun readFromUri_extractsFullVCardFieldsAndPrefersPrimary() {
    val contactUri = Uri.parse("content://${ContactsContract.AUTHORITY}/contacts/lookup/k-self/1")
    val imported = ContactBookImporter.readFromUri(context, contactUri)

    assertNotNull(imported)
    imported!!
    assertEquals("Jordan Vance", imported.fullName)
    assertEquals("Studio North", imported.organization)
    assertEquals("Product Lead", imported.title)
    assertEquals("+1 555 0144", imported.phone)
    assertEquals("jordan@studionorth.example", imported.email)
    assertEquals("https://studionorth.example", imported.website)
    assertEquals("Met at NFC summit", imported.note)
  }

  @Test
  fun readFromUri_fallsBackToDisplayNameWhenDetailsBlocked() {
    val restrictedUri =
      Uri.parse("content://${ContactsContract.AUTHORITY}/contacts/lookup/k-restricted/2")
    val imported = ContactBookImporter.readFromUri(context, restrictedUri)

    assertNotNull(imported)
    imported!!
    assertEquals("Taylor Basic", imported.fullName)
    assertEquals("", imported.phone)
    assertEquals("", imported.email)
  }

  @Test
  fun viewModelImportContactUriPopulatesAllVCardFormFields() = runBlocking {
    val viewModel = TagProgrammerViewModel(context as Application)
    val collectorJob = launch { viewModel.uiState.collect {} }
    try {
      val contactUri = Uri.parse("content://${ContactsContract.AUTHORITY}/contacts/lookup/k-self/1")
      viewModel.importContactUri(contactUri)

      val deadline = System.currentTimeMillis() + 3_000L
      while (
        viewModel.uiState.value.form.contactName != "Jordan Vance" &&
          System.currentTimeMillis() < deadline
      ) {
        shadowOf(Looper.getMainLooper()).idle()
        delay(20)
      }

      val form = viewModel.uiState.value.form
      assertEquals("Jordan Vance", form.contactName)
      assertEquals("Studio North", form.contactOrg)
      assertEquals("Product Lead", form.contactTitle)
      assertEquals("+1 555 0144", form.contactPhone)
      assertEquals("jordan@studionorth.example", form.contactEmail)
      assertEquals("https://studionorth.example", form.contactWebsite)
      assertEquals("Met at NFC summit", form.contactNote)
      assertEquals("Jordan Vance", viewModel.uiState.value.cardLabelDraft)
    } finally {
      collectorJob.cancel()
    }
  }

  private class FakeContactsContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?,
    ): Cursor? {
      val path = uri.path.orEmpty()
      if (path.endsWith("/entities")) {
        if (path.contains("k-restricted")) {
          throw SecurityException("READ_CONTACTS denied")
        }
        return MatrixCursor(
            arrayOf(
              ContactsContract.Data.MIMETYPE,
              ContactsContract.Data.DATA1,
              ContactsContract.Data.DATA4,
              ContactsContract.Data.IS_PRIMARY,
              ContactsContract.Data.IS_SUPER_PRIMARY,
            )
          )
          .apply {
            addRow(
              arrayOf<Any?>(
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                "Jordan Vance",
                "",
                0,
                0,
              )
            )
            addRow(
              arrayOf<Any?>(
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                "Studio North",
                "Product Lead",
                0,
                0,
              )
            )
            addRow(
              arrayOf<Any?>(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                "+1 555 0100",
                "",
                0,
                0,
              )
            )
            addRow(
              arrayOf<Any?>(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                "+1 555 0144",
                "",
                1,
                1,
              )
            )
            addRow(
              arrayOf<Any?>(
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                "jordan@studionorth.example",
                "",
                1,
                0,
              )
            )
            addRow(
              arrayOf<Any?>(
                ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
                "https://studionorth.example",
                "",
                0,
                0,
              )
            )
            addRow(
              arrayOf<Any?>(
                ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                "Met at NFC summit",
                "",
                0,
                0,
              )
            )
          }
      }

      if (path == "/data") {
        throw SecurityException("READ_CONTACTS denied")
      }

      return MatrixCursor(
          arrayOf(
            BaseColumns._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
          )
        )
        .apply {
          if (path.contains("k-restricted")) {
            addRow(arrayOf<Any?>("2", "k-restricted", "Taylor Basic"))
          } else {
            addRow(arrayOf<Any?>("1", "k-self", "Jordan Vance"))
          }
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?,
    ): Int = 0
  }
}
