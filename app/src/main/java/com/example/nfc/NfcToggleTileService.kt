package com.example.nfc

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity
import com.example.R
import com.example.widget.WidgetSyncHelper

class NfcToggleTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    syncTileState()
  }

  override fun onClick() {
    super.onClick()
    val store = ActiveCardStore(applicationContext)
    val snapshot = store.readSnapshot()
    val nfcReady = NfcHardwareHelper.isNfcEnabled(applicationContext)

    if (!nfcReady) {
      if (snapshot.isEmulating) {
        DynamicSwitchController.clear(applicationContext)
        store.setEmulating(false)
      }
      syncTileState()
      WidgetSyncHelper.refreshWidgetsOnly(applicationContext)
      openApp()
      return
    }

    if (!snapshot.hasPayload && snapshot.isReadOnly) {
      syncTileState()
      openApp()
      return
    }

    val nextEmulating = !snapshot.isEmulating
    if (!nextEmulating) {
      DynamicSwitchController.clear(applicationContext)
    }
    store.setEmulating(nextEmulating)
    syncTileState()
    WidgetSyncHelper.refreshWidgetsOnly(applicationContext)
  }

  private fun syncTileState() {
    val tile = qsTile ?: return
    val store = ActiveCardStore(applicationContext)
    val snapshot = store.readSnapshot()
    val nfcReady = NfcHardwareHelper.isNfcEnabled(applicationContext)
    if (!nfcReady && snapshot.isEmulating) {
      store.setEmulating(false)
    }
    val hasTarget = snapshot.hasPayload || !snapshot.isReadOnly
    val isActive = nfcReady && snapshot.isEmulating && hasTarget

    tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_qs_nfc_card)
    tile.label =
      when {
        snapshot.label.isNotBlank() -> snapshot.label
        else -> getString(R.string.qs_tile_label)
      }
    tile.state =
      when {
        isActive -> Tile.STATE_ACTIVE
        else -> Tile.STATE_INACTIVE
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      tile.subtitle =
        when {
          !nfcReady -> getString(R.string.qs_tile_nfc_off)
          isActive -> getString(R.string.emulation_active_label)
          hasTarget -> getString(R.string.emulation_standby_label)
          else -> getString(R.string.qs_tile_no_tag)
        }
    }
    tile.contentDescription =
      when {
        !nfcReady -> "${tile.label} ${getString(R.string.qs_tile_nfc_off)}"
        isActive -> "${tile.label} ${getString(R.string.emulation_active_label)}"
        hasTarget -> "${tile.label} ${getString(R.string.emulation_standby_label)}"
        else -> "${tile.label} ${getString(R.string.qs_tile_no_tag)}"
      }
    tile.updateTile()
  }

  private fun openApp() {
    val intent =
      Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      val pendingIntent =
        PendingIntent.getActivity(
          this,
          0,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      startActivityAndCollapse(pendingIntent)
    } else {
      @Suppress("DEPRECATION")
      startActivityAndCollapse(intent)
    }
  }
}
