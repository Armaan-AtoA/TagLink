package com.example.nfc

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle

class BridgeExchangeActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    dispatchBridgeIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    dispatchBridgeIntent(intent)
  }

  private fun dispatchBridgeIntent(incomingIntent: Intent?) {
    if (incomingIntent == null) {
      finishSilently()
      return
    }
    val uriString = incomingIntent.dataString
    val tag = extractNfcTag(incomingIntent)
    if (!uriString.isNullOrBlank() || tag != null) {
      SilentPeerBridgeCoordinator.handleIncomingBridgeTap(
        context = this,
        uriString = uriString,
        tag = tag,
        onComplete = { finishSilently() },
      )
    } else {
      finishSilently()
    }
  }

  private fun extractNfcTag(intent: Intent): Tag? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
    } else {
      @Suppress("DEPRECATION")
      intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }

  private fun finishSilently() {
    finish()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
      overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
      @Suppress("DEPRECATION")
      overridePendingTransition(0, 0)
    }
  }
}
