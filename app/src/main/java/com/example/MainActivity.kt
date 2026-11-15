package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ThemeMode
import com.example.nfc.NfcHardwareHelper
import com.example.nfc.NfcHardwareStatus
import com.example.ui.TagProgrammerScreen
import com.example.ui.TagProgrammerViewModel
import com.example.ui.theme.TagLinkTheme
import com.example.widget.WidgetSyncHelper

class MainActivity : ComponentActivity() {

  private val viewModel: TagProgrammerViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()

      DisposableEffect(Unit) {
        val receiver =
          object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
              if (intent?.action == NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) {
                viewModel.refreshState()
                WidgetSyncHelper.refreshAll(this@MainActivity)
              }
            }
          }
        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        ContextCompat.registerReceiver(
          this@MainActivity,
          receiver,
          filter,
          ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { unregisterReceiver(receiver) }
      }

      LaunchedEffect(
        uiState.isEmulating,
        uiState.activeNdefBytes,
        uiState.isReadOnly,
        uiState.nfcStatus,
        uiState.settings.preferForegroundHce,
      ) {
        val hasTarget = uiState.activeNdefBytes.isNotEmpty() || !uiState.isReadOnly
        val shouldPrefer =
          uiState.settings.preferForegroundHce &&
            uiState.isEmulating &&
            hasTarget &&
            uiState.nfcStatus == NfcHardwareStatus.READY
        NfcHardwareHelper.setPreferredForegroundService(this@MainActivity, shouldPrefer)
      }

      val darkTheme =
        when (uiState.settings.themeMode) {
          ThemeMode.SYSTEM -> isSystemInDarkTheme()
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
        }

      TagLinkTheme(darkTheme = darkTheme) {
        TagProgrammerScreen(
          state = uiState,
          onNavigate = viewModel::navigateTo,
          onOpenTutorial = viewModel::openTutorial,
          onDismissTutorial = viewModel::dismissTutorial,
          onOpenPinWidget = viewModel::openPinWidgetDialog,
          onDismissPinWidget = viewModel::dismissPinWidgetDialog,
          onPinWidget = viewModel::pinWidget,
          onSelectRecordType = viewModel::selectRecordType,
          onUpdateForm = viewModel::updateForm,
          onCardLabelChanged = viewModel::onCardLabelChanged,
          onMakeReadOnlyChanged = viewModel::onMakeReadOnlyChanged,
          onApplyUrlPrefix = viewModel::applyUrlPrefix,
          onApplyAppPreset = viewModel::applyAppPreset,
          onWriteCard = viewModel::writeToCard,
          onClearForm = viewModel::clearForm,
          onClearCard = viewModel::clearCard,
          onToggleEmulation = viewModel::toggleEmulation,
          onToggleReadOnly = viewModel::toggleReadOnly,
          onLoadSavedCard = viewModel::loadSavedCard,
          onDeleteSavedCard = viewModel::deleteSavedCard,
          onUpdateSettings = viewModel::updateSettings,
          onOpenNfcSettings = {
            startActivity(NfcHardwareHelper.createNfcSettingsIntent(this@MainActivity))
          },
          onRequestAddQuickTile = viewModel::requestAddQuickTile,
          onConsumeStatusMessage = viewModel::consumeStatusMessage,
        )
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refreshState()
    val state = viewModel.uiState.value
    val hasTarget = state.activeNdefBytes.isNotEmpty() || !state.isReadOnly
    val shouldPrefer =
      state.settings.preferForegroundHce &&
        state.isEmulating &&
        hasTarget &&
        state.nfcStatus == NfcHardwareStatus.READY
    NfcHardwareHelper.setPreferredForegroundService(this, shouldPrefer)
  }

  override fun onPause() {
    NfcHardwareHelper.setPreferredForegroundService(this, false)
    super.onPause()
  }

  override fun onStop() {
    if (!isChangingConfigurations && viewModel.uiState.value.settings.stopOnLeaveApp) {
      viewModel.toggleEmulation(false)
    }
    super.onStop()
  }
}
