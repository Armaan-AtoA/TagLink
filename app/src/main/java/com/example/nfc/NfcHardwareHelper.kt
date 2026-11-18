package com.example.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat

enum class NfcHardwareStatus {
  READY,
  NFC_DISABLED,
  NO_HCE_HARDWARE,
}

interface DynamicAidController {
  fun registerNdefAid(context: Context): Boolean

  fun removeNdefAid(context: Context): Boolean
}

object NfcHardwareHelper {

  const val NDEF_AID_HEX = "D2760000850101"

  @VisibleForTesting internal var testStatusProvider: ((Context) -> NfcHardwareStatus)? = null

  @VisibleForTesting internal var testDynamicAidController: DynamicAidController? = null

  @VisibleForTesting internal var testIgnoringBatteryOptimizations: Boolean? = null

  @VisibleForTesting internal var testNotificationsEnabled: Boolean? = null

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

  fun ensureDynamicNdefAidRegistered(context: Context): Boolean {
    testDynamicAidController?.let { return it.registerNdefAid(context) }
    val cardEmulation = getCardEmulation(context) ?: return false
    val component = ComponentName(context.applicationContext, NdefHostApduService::class.java)
    return runCatching {
        val current = cardEmulation.getAidsForService(component, CardEmulation.CATEGORY_OTHER)
        if (current != null && current.any { it.equals(NDEF_AID_HEX, ignoreCase = true) }) {
          true
        } else {
          cardEmulation.registerAidsForService(
            component,
            CardEmulation.CATEGORY_OTHER,
            listOf(NDEF_AID_HEX),
          )
        }
      }
      .getOrDefault(false)
  }

  fun removeDynamicNdefAid(context: Context): Boolean {
    testDynamicAidController?.let { return it.removeNdefAid(context) }
    val cardEmulation = getCardEmulation(context) ?: return false
    val component = ComponentName(context.applicationContext, NdefHostApduService::class.java)
    return runCatching {
        cardEmulation.removeAidsForService(component, CardEmulation.CATEGORY_OTHER)
      }
      .getOrDefault(false)
  }

  fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    testIgnoringBatteryOptimizations?.let { return it }
    val powerManager =
      context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        ?: return false
    return runCatching { powerManager.isIgnoringBatteryOptimizations(context.packageName) }
      .getOrDefault(false)
  }

  fun areNotificationsEnabled(context: Context): Boolean {
    testNotificationsEnabled?.let { return it }
    return runCatching { NotificationManagerCompat.from(context.applicationContext).areNotificationsEnabled() }
      .getOrDefault(false)
  }

  fun setPreferredForegroundService(activity: Activity, enable: Boolean) {
    val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
    if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
      return
    }
    val cardEmulation = runCatching { CardEmulation.getInstance(adapter) }.getOrNull() ?: return
    val component = ComponentName(activity, NdefHostApduService::class.java)
    runCatching {
      if (enable) {
        ensureDynamicNdefAidRegistered(activity)
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

  fun createBatteryOptimizationSettingsIntent(context: Context): Intent {
    val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    if (batteryIntent.resolveActivity(context.packageManager) != null) {
      return batteryIntent
    }
    return createAppDetailsSettingsIntent(context)
  }

  fun createAppDetailsSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
    }

  fun createNotificationSettingsIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notifIntent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
          putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
      if (notifIntent.resolveActivity(context.packageManager) != null) {
        return notifIntent
      }
    }
    return createAppDetailsSettingsIntent(context)
  }

  private fun getCardEmulation(context: Context): CardEmulation? {
    val appContext = context.applicationContext
    val adapter = NfcAdapter.getDefaultAdapter(appContext) ?: return null
    if (!appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)) {
      return null
    }
    return runCatching { CardEmulation.getInstance(adapter) }.getOrNull()
  }
}
