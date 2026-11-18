package com.example.nfc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettingsStore
import com.example.widget.WidgetSyncHelper

class NfcKeepAliveService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    isServiceRunning = true
    ensureNotificationChannel(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP_EMULATION) {
      val store = ActiveCardStore(applicationContext)
      DynamicSwitchController.clear(applicationContext)
      store.setEmulating(false)
      WidgetSyncHelper.refreshAll(applicationContext)
      stopKeepAlive()
      return START_NOT_STICKY
    }

    val state = inspectShouldStayAlive(applicationContext)
    if (!state.shouldRun) {
      stopKeepAlive()
      return START_NOT_STICKY
    }

    NfcHardwareHelper.ensureDynamicNdefAidRegistered(applicationContext)
    val notification = buildNotification(state.snapshot)
    val fgsType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
      } else {
        0
      }

    val started =
      runCatching {
          ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
          true
        }
        .getOrDefault(false)

    if (!started) {
      stopKeepAlive()
      return START_NOT_STICKY
    }

    isServiceRunning = true
    return START_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    super.onTaskRemoved(rootIntent)
    val state = inspectShouldStayAlive(applicationContext)
    if (state.shouldRun) {
      NfcHardwareHelper.ensureDynamicNdefAidRegistered(applicationContext)
    } else {
      stopKeepAlive()
    }
  }

  override fun onDestroy() {
    isServiceRunning = false
    super.onDestroy()
  }

  private fun stopKeepAlive() {
    isServiceRunning = false
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun buildNotification(snapshot: ActiveCardSnapshot): Notification {
    val openIntent =
      Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
    val openPendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      Intent(this, NfcKeepAliveService::class.java).apply { action = ACTION_STOP_EMULATION }
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        1,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val cardName =
      when {
        snapshot.label.isNotBlank() -> snapshot.label
        snapshot.summary.isNotBlank() -> snapshot.summary
        else -> getString(R.string.app_name)
      }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_qs_nfc_card)
      .setContentTitle(getString(R.string.notif_keep_alive_title, cardName))
      .setContentText(getString(R.string.notif_keep_alive_body))
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(openPendingIntent)
      .addAction(0, getString(R.string.emulation_toggle_stop), stopPendingIntent)
      .build()
  }

  companion object {
    const val CHANNEL_ID = "nfc_keep_alive"
    const val ACTION_START_OR_UPDATE = "com.example.nfc.ACTION_KEEP_ALIVE_SYNC"
    const val ACTION_STOP_EMULATION = "com.example.nfc.ACTION_KEEP_ALIVE_STOP"
    private const val NOTIFICATION_ID = 4101

    @Volatile var isServiceRunning: Boolean = false
      internal set

    data class KeepAliveDecision(
      val shouldRun: Boolean,
      val snapshot: ActiveCardSnapshot,
    )

    fun inspectShouldStayAlive(context: Context): KeepAliveDecision {
      val appContext = context.applicationContext
      val snapshot = ActiveCardStore(appContext).readSnapshot()
      val settings = AppSettingsStore(appContext).readSettings()
      val nfcReady = NfcHardwareHelper.isNfcEnabled(appContext)
      val hasTarget = snapshot.hasPayload || !snapshot.isReadOnly
      val shouldRun = nfcReady && snapshot.isEmulating && hasTarget && !settings.stopOnLeaveApp
      return KeepAliveDecision(shouldRun = shouldRun, snapshot = snapshot)
    }

    fun sync(context: Context): Boolean {
      val appContext = context.applicationContext
      if (NfcHardwareHelper.isNfcEnabled(appContext)) {
        NfcHardwareHelper.ensureDynamicNdefAidRegistered(appContext)
      }
      val decision = inspectShouldStayAlive(appContext)
      val serviceIntent =
        Intent(appContext, NfcKeepAliveService::class.java).apply { action = ACTION_START_OR_UPDATE }
      return if (decision.shouldRun) {
        runCatching {
            ContextCompat.startForegroundService(appContext, serviceIntent)
            true
          }
          .getOrDefault(false)
      } else {
        if (isServiceRunning) {
          runCatching { appContext.stopService(serviceIntent) }
        }
        false
      }
    }

    private fun ensureNotificationChannel(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
          val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_keep_alive_name),
                NotificationManager.IMPORTANCE_LOW,
              )
              .apply { setShowBadge(false) }
          manager.createNotificationChannel(channel)
        }
      }
    }
  }
}
