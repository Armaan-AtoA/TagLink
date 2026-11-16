package com.example.nfc

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import java.net.URLEncoder

object NdefActionExecutor {

  fun buildActionIntent(context: Context, rawNdefBytes: ByteArray): Intent? {
    if (rawNdefBytes.isEmpty()) return null
    val (canonicalBytes, _) = NdefBridgeCodec.unwrapScannedMessage(rawNdefBytes)
    val record = NdefMessageCodec.decodeRecords(canonicalBytes).firstOrNull() ?: return null
    return buildIntentForRecord(context, record)
  }

  fun buildIntentForRecord(context: Context, record: NdefRecordDraft): Intent? {
    return when (record) {
      is NdefRecordDraft.Url -> {
        val url = NdefUriEncoder.normalizeUri(record.url)
        if (url.isBlank() || NdefBridgeCodec.isBridgeUri(url)) return null
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }

      is NdefRecordDraft.Phone -> {
        val num = record.number.trim()
        if (num.isBlank()) return null
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }

      is NdefRecordDraft.Email -> {
        val addr = record.address.trim()
        if (addr.isBlank()) return null
        val params = mutableListOf<String>()
        if (record.subject.isNotBlank()) {
          params += "subject=${urlEncode(record.subject.trim())}"
        }
        if (record.body.isNotBlank()) {
          params += "body=${urlEncode(record.body.trim())}"
        }
        val uriStr = if (params.isEmpty()) "mailto:$addr" else "mailto:$addr?${params.joinToString("&")}"
        Intent(Intent.ACTION_SENDTO, Uri.parse(uriStr)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }

      is NdefRecordDraft.Sms -> {
        val num = record.number.trim()
        if (num.isBlank()) return null
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).apply {
          if (record.message.isNotBlank()) {
            putExtra("sms_body", record.message)
          }
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }

      is NdefRecordDraft.Geo -> {
        val lat = record.latitude.trim()
        val lon = record.longitude.trim()
        if (lat.isBlank() || lon.isBlank()) return null
        val geoUri =
          if (record.label.isBlank()) {
            "geo:$lat,$lon"
          } else {
            "geo:$lat,$lon?q=$lat,$lon(${urlEncode(record.label.trim())})"
          }
        Intent(Intent.ACTION_VIEW, Uri.parse(geoUri)).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }

      is NdefRecordDraft.Contact -> {
        Intent(ContactsContract.Intents.Insert.ACTION).apply {
          type = ContactsContract.RawContacts.CONTENT_TYPE
          if (record.fullName.isNotBlank()) {
            putExtra(ContactsContract.Intents.Insert.NAME, record.fullName.trim())
          }
          if (record.organization.isNotBlank()) {
            putExtra(ContactsContract.Intents.Insert.COMPANY, record.organization.trim())
          }
          if (record.title.isNotBlank()) {
            putExtra(ContactsContract.Intents.Insert.JOB_TITLE, record.title.trim())
          }
          if (record.phone.isNotBlank()) {
            putExtra(ContactsContract.Intents.Insert.PHONE, record.phone.trim())
          }
          if (record.email.isNotBlank()) {
            putExtra(ContactsContract.Intents.Insert.EMAIL, record.email.trim())
          }
          if (record.note.isNotBlank()) {
            putExtra(ContactsContract.Intents.Insert.NOTES, record.note.trim())
          }
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }

      is NdefRecordDraft.Wifi -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && record.ssid.isNotBlank()) {
          val suggestionBuilder = WifiNetworkSuggestion.Builder().setSsid(record.ssid.trim())
          if (record.authMode != WifiAuthMode.OPEN && record.password.isNotEmpty()) {
            suggestionBuilder.setWpa2Passphrase(record.password)
          }
          Intent(Settings.ACTION_WIFI_ADD_NETWORKS).apply {
            putParcelableArrayListExtra(
              Settings.EXTRA_WIFI_NETWORK_LIST,
              arrayListOf(suggestionBuilder.build()),
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        } else {
          Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
      }

      is NdefRecordDraft.AppLaunch -> {
        val pkg = record.packageName.trim()
        if (pkg.isBlank()) return null
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        (launchIntent
          ?: Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$pkg"),
          ))
          .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      }

      is NdefRecordDraft.Text -> {
        if (record.text.isBlank()) return null
        val sendIntent =
          Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, record.text)
          }
        Intent.createChooser(sendIntent, null).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }

      is NdefRecordDraft.CustomMime -> {
        val sendIntent =
          Intent(Intent.ACTION_SEND).apply {
            type = record.mimeType.ifBlank { "text/plain" }
            putExtra(Intent.EXTRA_TEXT, record.payload)
          }
        Intent.createChooser(sendIntent, null).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      }
    }
  }

  fun execute(context: Context, rawNdefBytes: ByteArray): Boolean {
    val intent = buildActionIntent(context, rawNdefBytes) ?: return false
    return try {
      context.startActivity(intent)
      true
    } catch (_: ActivityNotFoundException) {
      if (intent.action == Settings.ACTION_WIFI_ADD_NETWORKS) {
        runCatching {
          context.startActivity(
            Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          )
          true
        }.getOrDefault(false)
      } else {
        false
      }
    } catch (_: Exception) {
      false
    }
  }

  private fun urlEncode(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
