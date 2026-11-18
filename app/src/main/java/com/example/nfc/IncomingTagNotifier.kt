package com.example.nfc

import android.Manifest
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

object IncomingTagNotifier {

  const val CHANNEL_ID = "incoming_nfc_tag"
  private const val NOTIFICATION_ID = 4102

  fun showIncomingTagNotification(context: Context, canonicalNdefBytes: ByteArray): Boolean {
    val actionIntent = NdefActionExecutor.buildActionIntent(context, canonicalNdefBytes) ?: return false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) return false
    }

    val notificationManagerCompat = NotificationManagerCompat.from(context)
    if (!notificationManagerCompat.areNotificationsEnabled()) {
      return false
    }

    ensureChannel(context)

    val optionsBundle =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        @Suppress("DEPRECATION")
        ActivityOptions.makeBasic()
          .setPendingIntentCreatorBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
          )
          .toBundle()
      } else {
        null
      }

    val pendingIntent =
      PendingIntent.getActivity(
        context,
        canonicalNdefBytes.contentHashCode(),
        actionIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        optionsBundle,
      )

    val records = NdefMessageCodec.decodeRecords(canonicalNdefBytes)
    val summary = NdefMessageCodec.summarizeRecords(records).ifBlank {
      context.getString(R.string.notif_incoming_tag_tap)
    }

    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_qs_nfc_card)
        .setContentTitle(context.getString(R.string.notif_incoming_tag_title))
        .setContentText(summary)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

    return runCatching {
        notificationManagerCompat.notify(NOTIFICATION_ID, notification)
        true
      }
      .getOrDefault(false)
  }

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        val channel =
          NotificationChannel(
              CHANNEL_ID,
              context.getString(R.string.notif_channel_incoming_name),
              NotificationManager.IMPORTANCE_HIGH,
            )
            .apply {
              enableVibration(true)
            }
        manager.createNotificationChannel(channel)
      }
    }
  }
}
