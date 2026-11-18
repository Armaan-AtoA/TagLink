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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ThemeMode
import com.example.nfc.NfcHardwareHelper
import com.example.nfc.NfcHardwareStatus
import com.example.nfc.NfcTagReaderHelper
import com.example.nfc.ScannedTagResult
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

      val lifecycleOwner = LocalLifecycleOwner.current
      var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
      }
      DisposableEffect(lifecycleOwner) {
        isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
          when (event) {
            Lifecycle.Event.ON_RESUME -> isResumed = true
            Lifecycle.Event.ON_PAUSE -> isResumed = false
            else -> Unit
          }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
      }

      val hasTarget = uiState.activeNdefBytes.isNotEmpty() || !uiState.isReadOnly
      val emulationReady =
        uiState.isEmulating && hasTarget && uiState.nfcStatus == NfcHardwareStatus.READY

      DisposableEffect(
        isResumed,
        uiState.isCopyScanning,
        emulationReady,
        uiState.settings.preferForegroundHce,
      ) {
        val adapter = NfcAdapter.getDefaultAdapter(this@MainActivity)
        val readerFlags =
          NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE

        fun disableReader() {
          if (adapter != null) {
            runCatching { adapter.disableReaderMode(this@MainActivity) }
          }
        }

        when {
          !isResumed -> {
            NfcHardwareHelper.setPreferredForegroundService(this@MainActivity, false)
            disableReader()
          }
          uiState.isCopyScanning -> {
            NfcHardwareHelper.setPreferredForegroundService(this@MainActivity, false)
            if (adapter != null) {
              runCatching {
                adapter.enableReaderMode(
                  this@MainActivity,
                  { tag ->
                    val result = NfcTagReaderHelper.readPhysicalTag(tag)
                    runOnUiThread { viewModel.onPhysicalTagScanned(result) }
                  },
                  readerFlags,
                  null,
                )
              }
            }
          }
          else -> {
            disableReader()
            NfcHardwareHelper.setPreferredForegroundService(
              this@MainActivity,
              uiState.settings.preferForegroundHce && emulationReady,
            )
          }
        }

        onDispose {
          disableReader()
        }
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
          onNextTutorialStep = viewModel::nextTutorialStep,
          onPrevTutorialStep = viewModel::prevTutorialStep,
          onDismissTutorial = viewModel::dismissTutorial,
          onOpenPinWidget = viewModel::openPinWidgetDialog,
          onDismissPinWidget = viewModel::dismissPinWidgetDialog,
          onPinWidget = viewModel::pinWidget,
          onSelectMakePreset = viewModel::selectMakePreset,
          onSelectSocialPlatform = viewModel::selectSocialPlatform,
          onChangeMakeType = viewModel::backToPreviousMakeStep,
          onImportContactUri = viewModel::importContactUri,
          onUpdateForm = viewModel::updateForm,
          onCardLabelChanged = viewModel::onCardLabelChanged,
          onMakeReadOnlyChanged = viewModel::onMakeReadOnlyChanged,
          onApplyUrlPrefix = viewModel::applyUrlPrefix,
          onApplyAppPreset = viewModel::applyAppPreset,
          onWriteCard = viewModel::writeToCard,
          onClearForm = viewModel::clearForm,
          onCopyLabelChanged = viewModel::onCopyLabelChanged,
          onCopyReadOnlyChanged = viewModel::onCopyReadOnlyChanged,
          onSaveCopiedTag = viewModel::saveScannedTag,
          onScanAgain = viewModel::resetCopyScan,
          onClearCard = viewModel::clearCard,
          onToggleEmulation = viewModel::toggleEmulation,
          onToggleReadOnly = viewModel::toggleReadOnly,
          onLoadSavedCard = viewModel::loadSavedCard,
          onUpdateCardCoverPhoto = viewModel::updateCardCoverPhoto,
          onRemoveCardCoverPhoto = viewModel::removeCardCoverPhoto,
          onDuplicateSavedCard = viewModel::duplicateSavedCard,
          onDeleteSavedCard = viewModel::deleteSavedCard,
          onUpdateSettings = viewModel::updateSettings,
          onOpenBatterySettings = {
            startActivity(NfcHardwareHelper.createBatteryOptimizationSettingsIntent(this@MainActivity))
          },
          onOpenNotificationSettings = {
            startActivity(NfcHardwareHelper.createNotificationSettingsIntent(this@MainActivity))
          },
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
    NfcHardwareHelper.ensureDynamicNdefAidRegistered(this)
    viewModel.refreshState()
    val state = viewModel.uiState.value
    val hasTarget = state.activeNdefBytes.isNotEmpty() || !state.isReadOnly
    val shouldPrefer =
      !state.isCopyScanning &&
        state.settings.preferForegroundHce &&
        state.isEmulating &&
        hasTarget &&
        state.nfcStatus == NfcHardwareStatus.READY
    NfcHardwareHelper.setPreferredForegroundService(this, shouldPrefer)
  }

  override fun onPause() {
    NfcHardwareHelper.setPreferredForegroundService(this, false)
    NfcAdapter.getDefaultAdapter(this)?.let { adapter ->
      runCatching { adapter.disableReaderMode(this) }
    }
    super.onPause()
  }

  override fun onStop() {
    if (!isChangingConfigurations && viewModel.uiState.value.settings.stopOnLeaveApp) {
      viewModel.toggleEmulation(false)
    }
    super.onStop()
  }
}
