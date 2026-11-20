package com.example.nfc

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.R

class QrFallbackTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    syncTileState()
  }

  override fun onClick() {
    super.onClick()
    syncTileState()
    val intent =
      Intent(this, QrFallbackActivity::class.java).apply {
        addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
      }
    val launchedViaPendingIntent =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val pendingIntent =
          PendingIntent.getActivity(
            this,
            QR_TILE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
        runCatching { startActivityAndCollapse(pendingIntent) }.isSuccess
      } else {
        false
      }
    if (!launchedViaPendingIntent) {
      runCatching {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
      }.onFailure {
        startActivity(intent)
      }
    }
  }

  private fun syncTileState() {
    val tile = qsTile ?: return
    val snapshot = ActiveCardStore(applicationContext).readSnapshot()
    val hasCard = snapshot.records.isNotEmpty()
    tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_qs_qr_code)
    tile.label =
      if (snapshot.label.isNotBlank()) snapshot.label
      else getString(R.string.qs_qr_tile_label)
    tile.state = if (hasCard) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      tile.subtitle =
        if (hasCard) getString(R.string.qs_qr_tile_ready)
        else getString(R.string.qs_qr_tile_no_tag)
    }
    tile.contentDescription =
      if (hasCard) "${tile.label} · ${getString(R.string.qs_qr_tile_ready)}"
      else "${tile.label} · ${getString(R.string.qs_qr_tile_no_tag)}"
    tile.updateTile()
  }

  companion object {
    private const val QR_TILE_REQUEST_CODE = 4201

    fun requestSync(context: Context) {
      runCatching {
        requestListeningState(
          context.applicationContext,
          ComponentName(context.applicationContext, QrFallbackTileService::class.java),
        )
      }
    }
  }
}
