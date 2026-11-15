package com.example.ui

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppSettings
import com.example.data.AppSettingsStore
import com.example.data.SavedLinkEntity
import com.example.data.TagDatabase
import com.example.data.TagRepository
import com.example.nfc.ActiveCardStore
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.NdefRecordType
import com.example.nfc.NdefUriEncoder
import com.example.nfc.NfcHardwareHelper
import com.example.nfc.NfcHardwareStatus
import com.example.nfc.NfcToggleTileService
import com.example.nfc.WifiAuthMode
import com.example.widget.WidgetSyncHelper
import com.example.widget.WidgetType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppScreen {
  HOME,
  ACTIVATE_TAG,
  MAKE_TAG,
  SETTINGS,
}

data class RecordFormState(
  val selectedType: NdefRecordType = NdefRecordType.URL,
  val urlInput: String = "",
  val textInput: String = "",
  val textLang: String = "en",
  val contactName: String = "",
  val contactOrg: String = "",
  val contactTitle: String = "",
  val contactPhone: String = "",
  val contactEmail: String = "",
  val contactWebsite: String = "",
  val contactNote: String = "",
  val wifiSsid: String = "",
  val wifiPassword: String = "",
  val wifiAuth: WifiAuthMode = WifiAuthMode.WPA2_PERSONAL,
  val emailAddress: String = "",
  val emailSubject: String = "",
  val emailBody: String = "",
  val phoneNumber: String = "",
  val smsNumber: String = "",
  val smsMessage: String = "",
  val geoLat: String = "",
  val geoLon: String = "",
  val geoLabel: String = "",
  val appPackage: String = "",
  val mimeType: String = "text/plain",
  val mimePayload: String = "",
)

data class TagUiState(
  val currentScreen: AppScreen = AppScreen.HOME,
  val showTutorial: Boolean = false,
  val showPinWidgetDialog: Boolean = false,
  val settings: AppSettings = AppSettings(),
  val nfcStatus: NfcHardwareStatus = NfcHardwareStatus.NO_HCE_HARDWARE,
  val activeLabel: String = "",
  val activeRecord: NdefRecordDraft? = null,
  val activeNdefBytes: ByteArray = ByteArray(0),
  val isEmulating: Boolean = false,
  val isReadOnly: Boolean = true,
  val form: RecordFormState = RecordFormState(),
  val cardLabelDraft: String = "",
  val makeReadOnly: Boolean = true,
  val inputError: String? = null,
  val savedCards: List<SavedLinkEntity> = emptyList(),
  val statusMessage: String? = null,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TagUiState) return false
    return currentScreen == other.currentScreen &&
      showTutorial == other.showTutorial &&
      showPinWidgetDialog == other.showPinWidgetDialog &&
      settings == other.settings &&
      nfcStatus == other.nfcStatus &&
      activeLabel == other.activeLabel &&
      activeRecord == other.activeRecord &&
      activeNdefBytes.contentEquals(other.activeNdefBytes) &&
      isEmulating == other.isEmulating &&
      isReadOnly == other.isReadOnly &&
      form == other.form &&
      cardLabelDraft == other.cardLabelDraft &&
      makeReadOnly == other.makeReadOnly &&
      inputError == other.inputError &&
      savedCards == other.savedCards &&
      statusMessage == other.statusMessage
  }

  override fun hashCode(): Int =
    activeNdefBytes.contentHashCode() + form.hashCode() + currentScreen.hashCode()
}

private data class LocalProgrammerState(
  val currentScreen: AppScreen = AppScreen.HOME,
  val showTutorial: Boolean = false,
  val showPinWidgetDialog: Boolean = false,
  val settings: AppSettings = AppSettings(),
  val nfcStatus: NfcHardwareStatus = NfcHardwareStatus.NO_HCE_HARDWARE,
  val activeLabel: String = "",
  val activeRecord: NdefRecordDraft? = null,
  val activeNdefBytes: ByteArray = ByteArray(0),
  val isEmulating: Boolean = false,
  val isReadOnly: Boolean = true,
  val form: RecordFormState = RecordFormState(),
  val cardLabelDraft: String = "",
  val makeReadOnly: Boolean = true,
  val inputError: String? = null,
  val statusMessage: String? = null,
)

class TagProgrammerViewModel(application: Application) : AndroidViewModel(application) {

  private val repository =
    TagRepository(
      context = application,
      tagDao = TagDatabase.getInstance(application).tagDao(),
      activeCardStore = ActiveCardStore(application),
      settingsStore = AppSettingsStore(application),
    )

  private val localState = MutableStateFlow(createInitialState())

  val uiState: StateFlow<TagUiState> =
    combine(localState, repository.savedLinks) { local, saved ->
        TagUiState(
          currentScreen = local.currentScreen,
          showTutorial = local.showTutorial,
          showPinWidgetDialog = local.showPinWidgetDialog,
          settings = local.settings,
          nfcStatus = local.nfcStatus,
          activeLabel = local.activeLabel,
          activeRecord = local.activeRecord,
          activeNdefBytes = local.activeNdefBytes,
          isEmulating = local.isEmulating,
          isReadOnly = local.isReadOnly,
          form = local.form,
          cardLabelDraft = local.cardLabelDraft,
          makeReadOnly = local.makeReadOnly,
          inputError = local.inputError,
          savedCards = saved,
          statusMessage = local.statusMessage,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TagUiState(),
      )

  private fun createInitialState(): LocalProgrammerState {
    val active = repository.readActiveCard()
    val settings = repository.readSettings()
    val status = NfcHardwareHelper.getStatus(getApplication())
    val firstRecord = active.records.firstOrNull()
    val initialForm =
      when (firstRecord) {
        is NdefRecordDraft.Url -> RecordFormState(selectedType = NdefRecordType.URL, urlInput = firstRecord.url)
        else -> RecordFormState()
      }
    return LocalProgrammerState(
      settings = settings,
      nfcStatus = status,
      activeLabel = active.label,
      activeRecord = firstRecord,
      activeNdefBytes = active.ndefMessageBytes,
      isEmulating = active.isEmulating && status == NfcHardwareStatus.READY,
      isReadOnly = active.isReadOnly,
      form = initialForm,
      cardLabelDraft = active.label,
      makeReadOnly = settings.defaultReadOnly,
    )
  }

  fun refreshState() {
    val status = NfcHardwareHelper.getStatus(getApplication())
    val active = repository.readActiveCard()
    val settings = repository.readSettings()
    localState.update {
      it.copy(
        settings = settings,
        nfcStatus = status,
        activeLabel = active.label,
        activeRecord = active.records.firstOrNull(),
        activeNdefBytes = active.ndefMessageBytes,
        isEmulating = active.isEmulating && status == NfcHardwareStatus.READY,
        isReadOnly = active.isReadOnly,
      )
    }
  }

  fun navigateTo(screen: AppScreen) {
    localState.update {
      it.copy(
        currentScreen = screen,
        inputError = null,
        makeReadOnly =
          if (screen == AppScreen.MAKE_TAG) it.settings.defaultReadOnly else it.makeReadOnly,
      )
    }
  }

  fun openTutorial() {
    localState.update { it.copy(showTutorial = true) }
  }

  fun dismissTutorial() {
    localState.update { it.copy(showTutorial = false) }
  }

  fun openPinWidgetDialog() {
    localState.update { it.copy(showPinWidgetDialog = true) }
  }

  fun dismissPinWidgetDialog() {
    localState.update { it.copy(showPinWidgetDialog = false) }
  }

  fun pinWidget(type: WidgetType) {
    val app = getApplication<Application>()
    val requested = WidgetSyncHelper.requestPinWidget(app, type)
    localState.update {
      it.copy(
        showPinWidgetDialog = false,
        statusMessage =
          app.getString(
            if (requested) R.string.msg_widget_pin_requested
            else R.string.msg_widget_pin_unsupported
          ),
      )
    }
  }

  fun selectRecordType(type: NdefRecordType) {
    localState.update {
      it.copy(form = it.form.copy(selectedType = type), inputError = null)
    }
  }

  fun updateForm(transform: (RecordFormState) -> RecordFormState) {
    localState.update { it.copy(form = transform(it.form), inputError = null) }
  }

  fun onCardLabelChanged(value: String) {
    localState.update { it.copy(cardLabelDraft = value) }
  }

  fun onMakeReadOnlyChanged(readOnly: Boolean) {
    localState.update { it.copy(makeReadOnly = readOnly) }
  }

  fun clearForm() {
    localState.update {
      it.copy(
        form = RecordFormState(selectedType = it.form.selectedType),
        cardLabelDraft = "",
        inputError = null,
      )
    }
  }

  fun applyUrlPrefix(prefix: String, suggestedLabel: String) {
    localState.update {
      it.copy(
        form = it.form.copy(selectedType = NdefRecordType.URL, urlInput = prefix),
        cardLabelDraft = it.cardLabelDraft.ifBlank { suggestedLabel },
        inputError = null,
      )
    }
  }

  fun applyAppPreset(packageName: String) {
    localState.update {
      it.copy(
        form = it.form.copy(selectedType = NdefRecordType.APP, appPackage = packageName),
        inputError = null,
      )
    }
  }

  fun writeToCard() {
    val state = localState.value
    val record = buildValidatedRecord(state.form) ?: return
    val encoded = NdefMessageCodec.encodeRecords(listOf(record))
    if (encoded.size > NdefMessageCodec.MAX_NDEF_BYTES) {
      localState.update {
        it.copy(inputError = getApplication<Application>().getString(R.string.error_payload_too_long))
      }
      return
    }

    viewModelScope.launch {
      val written =
        repository.programCard(
          record = record,
          label = state.cardLabelDraft,
          isReadOnly = state.makeReadOnly,
        )
      val app = getApplication<Application>()
      val nfcStatus = NfcHardwareHelper.getStatus(app)
      val finalSnapshot =
        if (state.settings.autoActivateOnWrite && nfcStatus == NfcHardwareStatus.READY) {
          repository.setEmulating(true)
        } else {
          written
        }
      localState.update {
        it.copy(
          nfcStatus = nfcStatus,
          activeLabel = finalSnapshot.label,
          activeRecord = finalSnapshot.records.firstOrNull(),
          activeNdefBytes = finalSnapshot.ndefMessageBytes,
          isEmulating = finalSnapshot.isEmulating,
          isReadOnly = finalSnapshot.isReadOnly,
          inputError = null,
          currentScreen = AppScreen.ACTIVATE_TAG,
          statusMessage = app.getString(R.string.msg_card_written),
        )
      }
    }
  }

  fun loadSavedCard(card: SavedLinkEntity) {
    val app = getApplication<Application>()
    val nfcStatus = NfcHardwareHelper.getStatus(app)
    val loaded = repository.loadSavedBytes(card)
    val shouldAutoActivate = localState.value.settings.autoActivateOnSelect
    val finalSnapshot =
      if (shouldAutoActivate && nfcStatus == NfcHardwareStatus.READY) {
        repository.setEmulating(true)
      } else {
        loaded
      }
    val notice =
      if (shouldAutoActivate && nfcStatus != NfcHardwareStatus.READY) {
        app.getString(R.string.error_nfc_off)
      } else {
        app.getString(R.string.msg_card_written)
      }
    localState.update {
      it.copy(
        nfcStatus = nfcStatus,
        activeLabel = finalSnapshot.label,
        activeRecord = finalSnapshot.records.firstOrNull(),
        activeNdefBytes = finalSnapshot.ndefMessageBytes,
        isEmulating = finalSnapshot.isEmulating,
        isReadOnly = finalSnapshot.isReadOnly,
        cardLabelDraft = finalSnapshot.label,
        inputError = null,
        statusMessage = notice,
      )
    }
  }

  fun deleteSavedCard(id: Long) {
    viewModelScope.launch { repository.deleteSavedLink(id) }
  }

  fun clearCard() {
    val snapshot = repository.clearActiveCard()
    localState.update {
      it.copy(
        activeLabel = snapshot.label,
        activeRecord = null,
        activeNdefBytes = ByteArray(0),
        isEmulating = false,
        inputError = null,
        statusMessage = getApplication<Application>().getString(R.string.msg_card_cleared),
      )
    }
  }

  fun toggleEmulation(enabled: Boolean) {
    val app = getApplication<Application>()
    val status = NfcHardwareHelper.getStatus(app)
    val state = localState.value

    if (!enabled) {
      val snapshot = repository.setEmulating(false)
      localState.update { it.copy(nfcStatus = status, isEmulating = snapshot.isEmulating) }
      return
    }

    if (status != NfcHardwareStatus.READY) {
      repository.setEmulating(false)
      localState.update {
        it.copy(
          nfcStatus = status,
          isEmulating = false,
          statusMessage = app.getString(R.string.error_nfc_off),
        )
      }
      return
    }

    if (state.activeNdefBytes.isEmpty() && state.isReadOnly) {
      localState.update {
        it.copy(
          nfcStatus = status,
          isEmulating = false,
          statusMessage = app.getString(R.string.error_no_card_loaded),
        )
      }
      return
    }

    val snapshot = repository.setEmulating(true)
    localState.update { it.copy(nfcStatus = status, isEmulating = snapshot.isEmulating) }
  }

  fun toggleReadOnly(readOnly: Boolean) {
    val snapshot = repository.setReadOnly(readOnly)
    localState.update { it.copy(isReadOnly = snapshot.isReadOnly) }
  }

  fun updateSettings(transform: (AppSettings) -> AppSettings) {
    val updated = repository.updateSettings(transform)
    localState.update { it.copy(settings = updated) }
  }

  fun requestAddQuickTile() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val app = getApplication<Application>()
    val statusBarManager = app.getSystemService(StatusBarManager::class.java) ?: return
    statusBarManager.requestAddTileService(
      ComponentName(app, NfcToggleTileService::class.java),
      app.getString(R.string.qs_tile_label),
      Icon.createWithResource(app, R.drawable.ic_qs_nfc_card),
      app.mainExecutor,
    ) { resultCode ->
      if (resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
        resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
      ) {
        localState.update { it.copy(statusMessage = app.getString(R.string.msg_qs_tile_added)) }
      }
    }
  }

  fun consumeStatusMessage() {
    localState.update { it.copy(statusMessage = null) }
  }

  private fun buildValidatedRecord(form: RecordFormState): NdefRecordDraft? {
    val app = getApplication<Application>()
    return when (form.selectedType) {
      NdefRecordType.URL -> {
        val normalized = NdefUriEncoder.normalizeUri(form.urlInput)
        if (!NdefUriEncoder.isValidUri(normalized)) {
          setError(app.getString(R.string.error_invalid_url))
          null
        } else {
          NdefRecordDraft.Url(normalized)
        }
      }
      NdefRecordType.TEXT -> {
        if (form.textInput.isBlank()) {
          setError(app.getString(R.string.error_empty_text))
          null
        } else {
          NdefRecordDraft.Text(
            text = form.textInput.trim(),
            languageCode = form.textLang.trim().ifEmpty { "en" },
          )
        }
      }
      NdefRecordType.CONTACT -> {
        if (form.contactName.isBlank() && form.contactPhone.isBlank() && form.contactEmail.isBlank()) {
          setError(app.getString(R.string.error_empty_contact))
          null
        } else {
          NdefRecordDraft.Contact(
            fullName = form.contactName.trim().ifBlank { "Contact" },
            organization = form.contactOrg.trim(),
            title = form.contactTitle.trim(),
            phone = form.contactPhone.trim(),
            email = form.contactEmail.trim(),
            website = form.contactWebsite.trim(),
            note = form.contactNote.trim(),
          )
        }
      }
      NdefRecordType.WIFI -> {
        if (form.wifiSsid.isBlank()) {
          setError(app.getString(R.string.error_empty_wifi_ssid))
          null
        } else {
          NdefRecordDraft.Wifi(
            ssid = form.wifiSsid.trim(),
            password = form.wifiPassword,
            authMode = form.wifiAuth,
          )
        }
      }
      NdefRecordType.EMAIL -> {
        if (form.emailAddress.isBlank() || !form.emailAddress.contains("@")) {
          setError(app.getString(R.string.error_empty_email))
          null
        } else {
          NdefRecordDraft.Email(
            address = form.emailAddress.trim(),
            subject = form.emailSubject.trim(),
            body = form.emailBody.trim(),
          )
        }
      }
      NdefRecordType.PHONE -> {
        if (form.phoneNumber.isBlank()) {
          setError(app.getString(R.string.error_empty_phone))
          null
        } else {
          NdefRecordDraft.Phone(form.phoneNumber.trim())
        }
      }
      NdefRecordType.SMS -> {
        if (form.smsNumber.isBlank()) {
          setError(app.getString(R.string.error_empty_phone))
          null
        } else {
          NdefRecordDraft.Sms(
            number = form.smsNumber.trim(),
            message = form.smsMessage.trim(),
          )
        }
      }
      NdefRecordType.GEO -> {
        val lat = form.geoLat.trim().toDoubleOrNull()
        val lon = form.geoLon.trim().toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
          setError(app.getString(R.string.error_empty_geo))
          null
        } else {
          NdefRecordDraft.Geo(
            latitude = form.geoLat.trim(),
            longitude = form.geoLon.trim(),
            label = form.geoLabel.trim(),
          )
        }
      }
      NdefRecordType.APP -> {
        val pkg = form.appPackage.trim()
        if (pkg.isBlank() || !pkg.contains('.')) {
          setError(app.getString(R.string.error_empty_package))
          null
        } else {
          NdefRecordDraft.AppLaunch(pkg)
        }
      }
      NdefRecordType.MIME -> {
        if (form.mimeType.isBlank() || form.mimePayload.isBlank()) {
          setError(app.getString(R.string.error_empty_mime))
          null
        } else {
          NdefRecordDraft.CustomMime(
            mimeType = form.mimeType.trim(),
            payload = form.mimePayload,
          )
        }
      }
    }
  }

  private fun setError(message: String) {
    localState.update { it.copy(inputError = message) }
  }
}
