package com.example.nfc

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import android.provider.ContactsContract

data class ImportedContact(
  val fullName: String = "",
  val organization: String = "",
  val title: String = "",
  val phone: String = "",
  val email: String = "",
  val website: String = "",
  val note: String = "",
) {
  val hasAnyField: Boolean
    get() =
      fullName.isNotBlank() ||
        organization.isNotBlank() ||
        title.isNotBlank() ||
        phone.isNotBlank() ||
        email.isNotBlank() ||
        website.isNotBlank() ||
        note.isNotBlank()
}

object ContactBookImporter {

  fun readFromUri(context: Context, contactUri: Uri): ImportedContact? {
    val resolver = context.contentResolver
    val acc = Accumulator()

    var contactId: String? = null
    var lookupKey: String? = null

    runCatching {
      resolver.query(contactUri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val idIdx = cursor.getColumnIndex(BaseColumns._ID)
          val lookupIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
          val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
          val fallbackNameIdx = cursor.getColumnIndex("display_name")

          if (idIdx >= 0) contactId = cursor.getString(idIdx)?.trim()
          if (lookupIdx >= 0) lookupKey = cursor.getString(lookupIdx)?.trim()
          val rowName =
            when {
              nameIdx >= 0 -> cursor.getString(nameIdx)
              fallbackNameIdx >= 0 -> cursor.getString(fallbackNameIdx)
              else -> null
            }?.trim().orEmpty()
          if (rowName.isNotBlank()) {
            acc.displayNameFallback = rowName
          }

          if (cursor.getColumnIndex(ContactsContract.Data.MIMETYPE) >= 0) {
            cursor.moveToPosition(-1)
            acc.consumeDataCursor(cursor)
          }
        }
      }
    }

    if (!acc.hasRichFields()) {
      runCatching {
        val entityUri =
          Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Entity.CONTENT_DIRECTORY)
        resolver.query(entityUri, null, null, null, null)?.use { cursor ->
          acc.consumeDataCursor(cursor)
        }
      }
    }

    if (!acc.hasRichFields()) {
      queryDataTableFallback(resolver, lookupKey, contactId, acc)
    }

    val result = acc.toImportedContact()
    return if (result.hasAnyField) result else null
  }

  private fun queryDataTableFallback(
    resolver: ContentResolver,
    lookupKey: String?,
    contactId: String?,
    acc: Accumulator,
  ) {
    val selection: String
    val selectionArgs: Array<String>
    when {
      !lookupKey.isNullOrBlank() -> {
        selection = "${ContactsContract.Data.LOOKUP_KEY} = ?"
        selectionArgs = arrayOf(lookupKey)
      }
      !contactId.isNullOrBlank() -> {
        selection = "${ContactsContract.Data.CONTACT_ID} = ?"
        selectionArgs = arrayOf(contactId)
      }
      else -> return
    }

    runCatching {
      resolver
        .query(
          ContactsContract.Data.CONTENT_URI,
          null,
          selection,
          selectionArgs,
          "${ContactsContract.Data.IS_SUPER_PRIMARY} DESC, ${ContactsContract.Data.IS_PRIMARY} DESC",
        )
        ?.use { cursor -> acc.consumeDataCursor(cursor) }
    }
  }

  private class Accumulator {
    var displayNameFallback: String = ""
    var fullName: String = ""
    var organization: String = ""
    var title: String = ""
    var phone: String = ""
    private var phoneScore: Int = -1
    var email: String = ""
    private var emailScore: Int = -1
    var website: String = ""
    var note: String = ""

    fun hasRichFields(): Boolean =
      phone.isNotBlank() ||
        email.isNotBlank() ||
        organization.isNotBlank() ||
        title.isNotBlank() ||
        website.isNotBlank() ||
        note.isNotBlank()

    fun consumeDataCursor(cursor: Cursor) {
      val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
      if (mimeIdx < 0) return

      val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
      val data4Idx = cursor.getColumnIndex(ContactsContract.Data.DATA4)
      val displayNameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
      val primaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_PRIMARY)
      val superPrimaryIdx = cursor.getColumnIndex(ContactsContract.Data.IS_SUPER_PRIMARY)

      while (cursor.moveToNext()) {
        if (displayNameIdx >= 0 && displayNameFallback.isBlank()) {
          cursor.getString(displayNameIdx)?.trim()?.takeIf { it.isNotBlank() }?.let {
            displayNameFallback = it
          }
        }
        val mime = cursor.getString(mimeIdx) ?: continue
        val data1 = if (data1Idx >= 0) cursor.getString(data1Idx)?.trim().orEmpty() else ""
        val data4 = if (data4Idx >= 0) cursor.getString(data4Idx)?.trim().orEmpty() else ""
        val isPrimary = if (primaryIdx >= 0) cursor.getInt(primaryIdx) else 0
        val isSuperPrimary = if (superPrimaryIdx >= 0) cursor.getInt(superPrimaryIdx) else 0
        val priority = (if (isSuperPrimary != 0) 2 else 0) + (if (isPrimary != 0) 1 else 0)

        when (mime) {
          ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
            if (fullName.isBlank() && data1.isNotBlank()) {
              fullName = data1
            }
          }
          ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
            if (organization.isBlank() && data1.isNotBlank()) {
              organization = data1
            }
            if (title.isBlank() && data4.isNotBlank()) {
              title = data4
            }
          }
          ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
            if (data1.isNotBlank() && priority > phoneScore) {
              phone = data1
              phoneScore = priority
            }
          }
          ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> {
            if (data1.isNotBlank() && priority > emailScore) {
              email = data1
              emailScore = priority
            }
          }
          ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE -> {
            if (website.isBlank() && data1.isNotBlank()) {
              website = data1
            }
          }
          ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE -> {
            if (note.isBlank() && data1.isNotBlank()) {
              note = data1
            }
          }
        }
      }
    }

    fun toImportedContact(): ImportedContact =
      ImportedContact(
        fullName = fullName.ifBlank { displayNameFallback },
        organization = organization,
        title = title,
        phone = phone,
        email = email,
        website = website,
        note = note,
      )
  }
}
