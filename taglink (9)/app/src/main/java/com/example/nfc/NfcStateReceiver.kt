package com.example.nfc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import com.example.widget.WidgetSyncHelper

class NfcStateReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val appContext = context.applicationContext
    when (intent.action) {
      NfcAdapter.ACTION_ADAPTER_STATE_CHANGED -> {
        val adapterState =
          intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
        if (adapterState == NfcAdapter.STATE_OFF || adapterState == NfcAdapter.STATE_TURNING_OFF) {
          DynamicSwitchController.clear(appContext)
          ActiveCardStore(appContext).setEmulating(false)
          NfcKeepAliveService.sync(appContext)
          WidgetSyncHelper.refreshAll(appContext)
        } else if (adapterState == NfcAdapter.STATE_ON) {
          NfcHardwareHelper.ensureDynamicNdefAidRegistered(appContext)
          NfcKeepAliveService.sync(appContext)
          WidgetSyncHelper.refreshAll(appContext)
        }
      }

      Intent.ACTION_BOOT_COMPLETED,
      Intent.ACTION_MY_PACKAGE_REPLACED -> {
        if (NfcHardwareHelper.isNfcEnabled(appContext)) {
          NfcHardwareHelper.ensureDynamicNdefAidRegistered(appContext)
          NfcKeepAliveService.sync(appContext)
        }
        WidgetSyncHelper.refreshAll(appContext)
      }
    }
  }
}
