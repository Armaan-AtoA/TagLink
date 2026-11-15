package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService
import com.example.nfc.NfcToggleTileService

enum class WidgetType {
  TOGGLE,
  PRESET,
}

object WidgetSyncHelper {

  fun refreshAll(context: Context) {
    refreshWidgetsOnly(context)
    runCatching {
      TileService.requestListeningState(
        context.applicationContext,
        ComponentName(context.applicationContext, NfcToggleTileService::class.java),
      )
    }
  }

  fun refreshWidgetsOnly(context: Context) {
    val appContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(appContext)

    val toggleIds =
      manager.getAppWidgetIds(ComponentName(appContext, TagToggleWidgetProvider::class.java))
    toggleIds.forEach { id -> TagToggleWidgetProvider.updateWidget(appContext, manager, id) }

    val presetIds =
      manager.getAppWidgetIds(ComponentName(appContext, TagPresetWidgetProvider::class.java))
    presetIds.forEach { id -> TagPresetWidgetProvider.updateWidget(appContext, manager, id) }
  }

  fun canRequestPinWidget(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val manager = AppWidgetManager.getInstance(context) ?: return false
    return runCatching { manager.isRequestPinAppWidgetSupported }.getOrDefault(false)
  }

  fun requestPinWidget(context: Context, type: WidgetType): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val manager = AppWidgetManager.getInstance(context) ?: return false
    if (!runCatching { manager.isRequestPinAppWidgetSupported }.getOrDefault(false)) return false
    val provider =
      when (type) {
        WidgetType.TOGGLE -> ComponentName(context, TagToggleWidgetProvider::class.java)
        WidgetType.PRESET -> ComponentName(context, TagPresetWidgetProvider::class.java)
      }
    return runCatching { manager.requestPinAppWidget(provider, null, null) }.getOrDefault(false)
  }
}
