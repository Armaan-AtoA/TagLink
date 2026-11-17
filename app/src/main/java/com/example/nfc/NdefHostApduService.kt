package com.example.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.example.data.AppSettingsStore
import com.example.data.PeerExchangeMode
import com.example.widget.WidgetSyncHelper

class NdefHostApduService : HostApduService() {

  private val apduEngine = T4tApduEngine()
  private lateinit var activeCardStore: ActiveCardStore
  private lateinit var settingsStore: AppSettingsStore
  private var sessionHasStarted = false
  private var sessionTrueP2p = false

  override fun onCreate() {
    super.onCreate()
    activeCardStore = ActiveCardStore(applicationContext)
    settingsStore = AppSettingsStore(applicationContext)
  }

  override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
    if (commandApdu == null) {
      return T4tApduEngine.SW_WRONG_LENGTH
    }
    if (!NfcHardwareHelper.isNfcEnabled(applicationContext)) {
      return T4tApduEngine.SW_FILE_NOT_FOUND
    }

    val snapshot = activeCardStore.readSnapshot()
    val settings = settingsStore.readSettings()
    val isDynamicMode = settings.peerExchangeMode == PeerExchangeMode.DYNAMIC

    val currentDynamicTrueP2p =
      isDynamicMode && DynamicSwitchController.isTrueP2pWindowActive(applicationContext)
    val computedTrueP2p =
      (settings.peerExchangeMode == PeerExchangeMode.TRUE_P2P) || currentDynamicTrueP2p

    if (!sessionHasStarted || isSelectAidApdu(commandApdu)) {
      sessionHasStarted = true
      sessionTrueP2p = computedTrueP2p
    }

    val isProprietaryExchange =
      commandApdu.size >= 2 && (commandApdu[0].toInt() and 0xFF) == T4tApduEngine.CLA_PROPRIETARY
    val bridgeEnabled =
      if (isProprietaryExchange) {
        sessionTrueP2p || computedTrueP2p
      } else {
        sessionTrueP2p
      }

    val broadcastBytes =
      if (bridgeEnabled && snapshot.ndefMessageBytes.isNotEmpty()) {
        NdefBridgeCodec.buildMultiRecordNdef(
          primaryNdefBytes = snapshot.ndefMessageBytes,
          label = snapshot.label,
          senderId = settings.bridgeDeviceId,
        )
      } else {
        snapshot.ndefMessageBytes
      }

    val localPacketBytes =
      if (bridgeEnabled && snapshot.hasPayload) {
        NdefBridgeCodec.encodePeerPacket(
          PeerExchangePacket(
            senderId = settings.bridgeDeviceId,
            label = snapshot.label,
            ndefBytes = snapshot.ndefMessageBytes,
          )
        )
      } else {
        ByteArray(0)
      }

    val outcome =
      apduEngine.processApdu(
        commandApdu = commandApdu,
        isEmulationEnabled = snapshot.isEmulating,
        ndefMessageBytes = broadcastBytes,
        isReadOnly = snapshot.isReadOnly,
        isBridgeEnabled = bridgeEnabled,
        localExchangePacket = localPacketBytes,
      )

    if (isDynamicMode && snapshot.isEmulating && !sessionTrueP2p && outcome.completedNdefRead) {
      DynamicSwitchController.activateTrueP2pWindow(applicationContext)
    }

    if (outcome.completedPeerExchange != null) {
      val exchanged = outcome.completedPeerExchange
      SilentPeerBridgeCoordinator.executeIncomingPeerCard(
        context = applicationContext,
        rawNdefBytes = exchanged.ndefBytes,
        senderId = exchanged.senderId,
      )
    }

    if (outcome.completedNdefWrite != null) {
      val (unwrappedBytes, _) = NdefBridgeCodec.unwrapScannedMessage(outcome.completedNdefWrite)
      activeCardStore.writeRawNdefBytes(
        ndefBytes = unwrappedBytes,
        label = snapshot.label.ifBlank { "External Card" },
        isReadOnly = snapshot.isReadOnly,
      )
      WidgetSyncHelper.refreshAll(applicationContext)
    }

    return outcome.responseApdu
  }

  override fun onDeactivated(reason: Int) {
    apduEngine.reset()
    sessionHasStarted = false
    sessionTrueP2p = false
  }

  private fun isSelectAidApdu(apdu: ByteArray): Boolean =
    apdu.size >= 4 &&
      (apdu[0].toInt() and 0xFF) == 0x00 &&
      (apdu[1].toInt() and 0xFF) == 0xA4 &&
      (apdu[2].toInt() and 0xFF) == 0x04
}
