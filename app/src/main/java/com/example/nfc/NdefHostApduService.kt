package com.example.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.example.widget.WidgetSyncHelper

class NdefHostApduService : HostApduService() {

  private val apduEngine = T4tApduEngine()
  private lateinit var activeCardStore: ActiveCardStore

  override fun onCreate() {
    super.onCreate()
    activeCardStore = ActiveCardStore(applicationContext)
  }

  override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
    if (commandApdu == null) {
      return T4tApduEngine.SW_WRONG_LENGTH
    }
    if (!NfcHardwareHelper.isNfcEnabled(applicationContext)) {
      return T4tApduEngine.SW_FILE_NOT_FOUND
    }

    val snapshot = activeCardStore.readSnapshot()
    val outcome =
      apduEngine.processApdu(
        commandApdu = commandApdu,
        isEmulationEnabled = snapshot.isEmulating,
        ndefMessageBytes = snapshot.ndefMessageBytes,
        isReadOnly = snapshot.isReadOnly,
      )

    if (outcome.completedNdefWrite != null) {
      val singleRecord = NdefMessageCodec.decodeRecords(outcome.completedNdefWrite).take(1)
      val sanitizedBytes =
        if (singleRecord.isNotEmpty()) NdefMessageCodec.encodeRecords(singleRecord)
        else outcome.completedNdefWrite
      activeCardStore.writeRawNdefBytes(
        ndefBytes = sanitizedBytes,
        label = snapshot.label.ifBlank { "External Card" },
        isReadOnly = snapshot.isReadOnly,
      )
      WidgetSyncHelper.refreshAll(applicationContext)
    }

    return outcome.responseApdu
  }

  override fun onDeactivated(reason: Int) {
    apduEngine.reset()
  }
}
