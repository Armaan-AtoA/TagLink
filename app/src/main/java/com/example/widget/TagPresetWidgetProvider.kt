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

class TagPresetWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
  }

  override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    appWidgetIds.forEach { widgetId -> WidgetPrefs.deletePinnedCard(context, widgetId) }
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_ACTIVATE_PRESET) {
      val widgetId =
        intent.getIntExtra(
          AppWidgetManager.EXTRA_APPWIDGET_ID,
          AppWidgetManager.INVALID_APPWIDGET_ID,
        )
      if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

      val pinned = WidgetPrefs.loadPinnedCard(context, widgetId)
      val store = ActiveCardStore(context)
      val current = store.readSnapshot()
      val nfcReady = NfcHardwareHelper.isNfcEnabled(context)

      if (!nfcReady) {
        if (current.isEmulating) {
          DynamicSwitchController.clear(context)
          store.setEmulating(false)
        }
        WidgetSyncHelper.refreshAll(context)
        val launchIntent =
          Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
          }
        context.startActivity(launchIntent)
        return
      }

      if (pinned != null) {
        DynamicSwitchController.clear(context)
        val isAlreadyActive =
          current.isEmulating && current.ndefMessageBytes.contentEquals(pinned.ndefBytes)
        if (isAlreadyActive) {
          store.setEmulating(false)
        } else {
          store.writeRawNdefBytes(
            ndefBytes = pinned.ndefBytes,
            label = pinned.label,
            isReadOnly = pinned.isReadOnly,
          )
          store.setEmulating(true)
        }
        WidgetSyncHelper.refreshAll(context)
      } else if (current.hasPayload || !current.isReadOnly) {
        val nextEmulating = !current.isEmulating
        if (!nextEmulating) {
          DynamicSwitchController.clear(context)
        }
        store.setEmulating(nextEmulating)
        WidgetSyncHelper.refreshAll(context)
      } else {
        val launchIntent =
          Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
          }
        context.startActivity(launchIntent)
      }
    }
  }

  companion object {
    const val ACTION_ACTIVATE_PRESET = "com.example.widget.ACTION_ACTIVATE_PRESET"

    fun updateWidget(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetId: Int,
    ) {
      val pinned = WidgetPrefs.loadPinnedCard(context, appWidgetId)
      val store = ActiveCardStore(context)
      val current = store.readSnapshot()
      val nfcReady = NfcHardwareHelper.isNfcEnabled(context)
      if (!nfcReady && current.isEmulating) {
        store.setEmulating(false)
      }

      val isActive =
        nfcReady &&
          if (pinned != null) {
            current.isEmulating && current.ndefMessageBytes.contentEquals(pinned.ndefBytes)
          } else {
            current.isEmulating && (current.hasPayload || !current.isReadOnly)
          }

      val views = RemoteViews(context.packageName, R.layout.widget_tag_preset)
      views.setInt(
        R.id.preset_widget_root,
        "setBackgroundResource",
        if (isActive) R.drawable.widget_bg_active else R.drawable.widget_bg_inactive,
      )
      views.setInt(
        R.id.preset_widget_icon,
        "setColorFilter",
        when {
          !nfcReady -> Color.parseColor("#EF5350")
          isActive -> Color.parseColor("#22C55E")
          else -> Color.parseColor("#5E9BFF")
        },
      )
      views.setTextViewText(
        R.id.preset_widget_badge,
        when {
          !nfcReady -> context.getString(R.string.nfc_status_disabled)
          isActive -> context.getString(R.string.emulation_active_label)
          else -> context.getString(R.string.widget_tap_to_activate)
        },
      )
      views.setTextColor(
        R.id.preset_widget_badge,
        when {
          !nfcReady -> Color.parseColor("#EF5350")
          isActive -> Color.parseColor("#22C55E")
          else -> Color.parseColor("#98A0AA")
        },
      )

      val title =
        pinned?.label?.ifBlank { pinned.summary }
          ?: current.label.ifBlank { current.summary }
            .takeIf { current.hasPayload }
          ?: context.getString(R.string.widget_select_card_prompt)

      views.setTextViewText(R.id.preset_widget_title, title)

      val clickIntent =
        Intent(context, TagPresetWidgetProvider::class.java).apply {
          action = ACTION_ACTIVATE_PRESET
          putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
      val pendingIntent =
        PendingIntent.getBroadcast(
          context,
          10_000 + appWidgetId,
          clickIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      views.setOnClickPendingIntent(R.id.preset_widget_root, pendingIntent)

      appWidgetManager.updateAppWidget(appWidgetId, views)
    }
  }
}
