package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.nfc.ActiveCardStore
import com.example.nfc.DynamicSwitchController
import com.example.nfc.NfcHardwareHelper

class TagToggleWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_TOGGLE_EMULATION) {
      val store = ActiveCardStore(context)
      val snapshot = store.readSnapshot()
      val nfcReady = NfcHardwareHelper.isNfcEnabled(context)

      if (!nfcReady) {
        if (snapshot.isEmulating) {
          DynamicSwitchController.clear(context)
          store.setEmulating(false)
        }
        com.example.nfc.NfcKeepAliveService.sync(context)
        WidgetSyncHelper.refreshAll(context)
        val launchIntent =
          Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
          }
        context.startActivity(launchIntent)
        return
      }

      if (!snapshot.hasPayload && snapshot.isReadOnly) {
        val launchIntent =
          Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
          }
        context.startActivity(launchIntent)
      } else {
        val nextEmulating = !snapshot.isEmulating
        if (!nextEmulating) {
          DynamicSwitchController.clear(context)
        }
        store.setEmulating(nextEmulating)
        com.example.nfc.NfcKeepAliveService.sync(context)
        WidgetSyncHelper.refreshAll(context)
      }
    }
  }

  companion object {
    const val ACTION_TOGGLE_EMULATION = "com.example.widget.ACTION_TOGGLE_EMULATION"

    fun updateWidget(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetId: Int,
    ) {
      val store = ActiveCardStore(context)
      val snapshot = store.readSnapshot()
      val nfcReady = NfcHardwareHelper.isNfcEnabled(context)
      if (!nfcReady && snapshot.isEmulating) {
        store.setEmulating(false)
      }
      val hasTarget = snapshot.hasPayload || !snapshot.isReadOnly
      val isActive = nfcReady && snapshot.isEmulating && hasTarget

      val views = RemoteViews(context.packageName, R.layout.widget_tag_toggle)
      views.setInt(
        R.id.widget_root,
        "setBackgroundResource",
        if (isActive) R.drawable.widget_bg_active else R.drawable.widget_bg_inactive,
      )
      views.setInt(
        R.id.widget_icon,
        "setColorFilter",
        when {
          !nfcReady -> Color.parseColor("#EF5350")
          isActive -> Color.parseColor("#22C55E")
          else -> Color.parseColor("#98A0AA")
        },
      )
      views.setTextViewText(
        R.id.widget_status,
        when {
          !nfcReady -> context.getString(R.string.nfc_status_disabled)
          isActive -> context.getString(R.string.emulation_active_label)
          else -> context.getString(R.string.emulation_standby_label)
        },
      )
      views.setTextColor(
        R.id.widget_status,
        when {
          !nfcReady -> Color.parseColor("#EF5350")
          isActive -> Color.parseColor("#22C55E")
          else -> Color.parseColor("#98A0AA")
        },
      )

      val titleText =
        when {
          snapshot.label.isNotBlank() -> snapshot.label
          snapshot.hasPayload -> snapshot.summary
          else -> context.getString(R.string.virtual_card_empty)
        }
      views.setTextViewText(R.id.widget_label, titleText)

      val toggleIntent =
        Intent(context, TagToggleWidgetProvider::class.java).apply {
          action = ACTION_TOGGLE_EMULATION
        }
      val pendingIntent =
        PendingIntent.getBroadcast(
          context,
          appWidgetId,
          toggleIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }
}
