package com.example.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.provider.Settings
import androidx.annotation.VisibleForTesting

enum class NfcHardwareStatus {
  READY,
  NFC_DISABLED,
  NO_HCE_HARDWARE,
}

object NfcHardwareHelper {

  @VisibleForTesting
  internal var testStatusProvider: ((Context) -> NfcHardwareStatus)? = null

  fun getStatus(context: Context): NfcHardwareStatus {
    testStatusProvider?.let { return it(context) }
    val hasHce =
      context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
    val adapter = NfcAdapter.getDefaultAdapter(context)
    if (!hasHce || adapter == null) {
      return NfcHardwareStatus.NO_HCE_HARDWARE
    }
    return if (adapter.isEnabled) {
      NfcHardwareStatus.READY
    } else {
      NfcHardwareStatus.NFC_DISABLED
    }
  }

  fun isNfcEnabled(context: Context): Boolean = getStatus(context) == NfcHardwareStatus.READY

  fun setPreferredForegroundService(activity: Activity, enable: Boolean) {
    val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
    if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
      return
    }
    val cardEmulation = runCatching { CardEmulation.getInstance(adapter) }.getOrNull() ?: return
    val component = ComponentName(activity, NdefHostApduService::class.java)
    runCatching {
      if (enable) {
        cardEmulation.setPreferredService(activity, component)
      } else {
        cardEmulation.unsetPreferredService(activity)
      }
    }
  }

  fun createNfcSettingsIntent(context: Context): Intent {
    val nfcIntent = Intent(Settings.ACTION_NFC_SETTINGS)
    val canResolveNfc = nfcIntent.resolveActivity(context.packageManager) != null
    return if (canResolveNfc) {
      nfcIntent
    } else {
      Intent(Settings.ACTION_WIRELESS_SETTINGS)
    }
  }
}
