package com.example.ui

import android.app.Application
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppSettings
import com.example.data.AppSettingsStore
import com.example.data.PeerExchangeMode
import com.example.data.SavedLinkEntity
import com.example.data.TagDatabase
import com.example.data.TagRepository
import com.example.nfc.ActiveCardStore
import com.example.nfc.ContactBookImporter
import com.example.nfc.DynamicSwitchController
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordDraft
import com.example.nfc.NdefRecordType
import com.example.nfc.NdefUriEncoder
import com.example.nfc.NfcHardwareHelper
import com.example.nfc.NfcHardwareStatus
import com.example.nfc.NfcToggleTileService
import com.example.nfc.ScannedTagResult
import com.example.nfc.TagScanError
import com.example.nfc.WifiAuthMode
import com.example.widget.WidgetSyncHelper
import com.example.widget.WidgetType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
  HOME,
  ACTIVATE_TAG,
  MAKE_TAG,
  COPY_TAG,
  SETTINGS,
}

enum class MakeStep {
  CHOOSE_TYPE,
  CHOOSE_SOCIAL,
  FILL_DETAILS,
}

enum class TagPresetKind(
  val title: String,
  val subtitle: String,
  val recordType: NdefRecordType,
) {
  SOCIALS("Socials", "Instagram, TikTok, X & more", NdefRecordType.URL),
  LINK("Website / Link", "Any custom URL", NdefRecordType.URL),
  CONTACT("Contact Card", "Name, phone & email", NdefRecordType.CONTACT),
  WIFI("Wi-Fi", "Instant network join", NdefRecordType.WIFI),
  PHONE("Phone Call", "Dial a phone number", NdefRecordType.PHONE),
  SMS("Text Message", "Pre-filled SMS", NdefRecordType.SMS),
  EMAIL("Email", "Address & subject", NdefRecordType.EMAIL),
  TEXT("Plain Text", "Note or short code", NdefRecordType.TEXT),
  GEO("Location", "GPS map coordinates", NdefRecordType.GEO),
  APP("Open App", "Launch Android app", NdefRecordType.APP),
  MIME("Custom Data", "Raw MIME payload", NdefRecordType.MIME),
}

enum class SocialPlatform(
  val title: String,
  val subtitle: String,
  val urlPrefix: String,
  val placeholder: String,
  val isPhoneBased: Boolean = false,
) {
  INSTAGRAM("Instagram", "@username profile", "https://instagram.com/", "@username"),
  WHATSAPP("WhatsApp", "Direct chat number", "https://wa.me/", "+1 555 123 4567", isPhoneBased = true),
  TIKTOK("TikTok", "@username profile", "https://tiktok.com/@", "@username"),
  X("X / Twitter", "@handle profile", "https://x.com/", "@handle"),
  LINKEDIN("LinkedIn", "Profile handle", "https://linkedin.com/in/", "your-name"),
  YOUTUBE("YouTube", "@channel page", "https://youtube.com/@", "@channel"),
  THREADS("Threads", "@username profile", "https://threads.net/@", "@username"),
  SNAPCHAT("Snapchat", "Snap username", "https://snapchat.com/add/", "username"),
  GITHUB("GitHub", "Developer profile", "https://github.com/", "username"),
  FACEBOOK("Facebook", "Profile or page", "https://facebook.com/", "username"),
  DISCORD("Discord", "Server invite", "https://discord.gg/", "invite-code"),
  TWITCH("Twitch", "Channel link", "https://twitch.tv/", "channel");

  fun buildProfileUrl(rawInput: String): String {
    val raw = rawInput.trim()
    if (raw.isBlank()) return ""
    if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
      return raw
    }
    if (isPhoneBased) {
      if (raw.contains("wa.me/", true)) return NdefUriEncoder.normalizeUri(raw)
      val digits = raw.filter { it.isDigit() }
      return if (digits.length >= 6) "https://wa.me/$digits" else ""
    }
    val hostToken = urlPrefix.substringAfter("://").substringBefore('/')
    if (raw.contains(hostToken, true)) {
      return NdefUriEncoder.normalizeUri(raw)
    }
    val cleanHandle = raw.removePrefix("@").trimStart('/')
    return if (cleanHandle.isBlank()) "" else "$urlPrefix$cleanHandle"
  }
}

data class RecordFormState(
  val makeStep: MakeStep = MakeStep.CHOOSE_TYPE,
  val selectedPreset: TagPresetKind = TagPresetKind.LINK,
  val selectedSocial: SocialPlatform = SocialPlatform.INSTAGRAM,
  val selectedType: NdefRecordType = NdefRecordType.URL,
  val socialHandleInput: String = "",
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

data class CopyTagUiState(
  val scannedResult: ScannedTagResult.Success? = null,
  val scanError: TagScanError? = null,
  val copyLabelDraft: String = "",
  val copyReadOnly: Boolean = true,
)

data class TagUiState(
  val currentScreen: AppScreen = AppScreen.HOME,
  val showTutorial: Boolean = false,
  val tutorialStep: Int = 0,
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
  val copyState: CopyTagUiState = CopyTagUiState(),
  val savedCards: List<SavedLinkEntity> = emptyList(),
  val statusMessage: String? = null,
) {
  val isCopyScanning: Boolean
    get() =
      currentScreen == AppScreen.COPY_TAG &&
        nfcStatus == NfcHardwareStatus.READY &&
        copyState.scannedResult == null &&
        !showTutorial

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TagUiState) return false
    return currentScreen == other.currentScreen &&
      showTutorial == other.showTutorial &&
      tutorialStep == other.tutorialStep &&
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
      copyState == other.copyState &&
      savedCards == other.savedCards &&
      statusMessage == other.statusMessage
  }

  override fun hashCode(): Int =
    activeNdefBytes.contentHashCode() + form.hashCode() + currentScreen.hashCode()
}

private data class LocalProgrammerState(
  val currentScreen: AppScreen = AppScreen.HOME,
  val showTutorial: Boolean = false,
  val tutorialStep: Int = 0,
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
  val copyState: CopyTagUiState = CopyTagUiState(),
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
          tutorialStep = local.tutorialStep,
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
          copyState = local.copyState,
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
    return LocalProgrammerState(
      showTutorial = !settings.hasCompletedTutorial,
      tutorialStep = 0,
      settings = settings,
      nfcStatus = status,
      activeLabel = active.label,
      activeRecord = firstRecord,
      activeNdefBytes = active.ndefMessageBytes,
      isEmulating = active.isEmulating && status == NfcHardwareStatus.READY,
      isReadOnly = active.isReadOnly,
      form = RecordFormState(),
      cardLabelDraft = "",
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
    localState.update { state ->
      val nextForm =
        if (screen == AppScreen.MAKE_TAG && state.currentScreen != AppScreen.MAKE_TAG) {
          RecordFormState(makeStep = MakeStep.CHOOSE_TYPE)
        } else {
          state.form
        }
      val nextLabel =
        if (screen == AppScreen.MAKE_TAG && state.currentScreen != AppScreen.MAKE_TAG) {
          ""
        } else {
          state.cardLabelDraft
        }
      val nextCopy =
        if (screen == AppScreen.COPY_TAG) {
          CopyTagUiState(copyReadOnly = state.settings.defaultReadOnly)
        } else {
          state.copyState
        }
      state.copy(
        currentScreen = screen,
        inputError = null,
        form = nextForm,
        cardLabelDraft = nextLabel,
        copyState = nextCopy,
        makeReadOnly =
          if (screen == AppScreen.MAKE_TAG) state.settings.defaultReadOnly else state.makeReadOnly,
      )
    }
  }

  fun openTutorial() {
    localState.update {
      it.copy(
        currentScreen = AppScreen.HOME,
        showTutorial = true,
        tutorialStep = 0,
      )
    }
  }

  fun nextTutorialStep() {
    val current = localState.value.tutorialStep
    if (current >= TUTORIAL_STEP_COUNT - 1) {
      dismissTutorial()
    } else {
      localState.update { it.copy(tutorialStep = current + 1) }
    }
  }

  fun prevTutorialStep() {
    localState.update { it.copy(tutorialStep = (it.tutorialStep - 1).coerceAtLeast(0)) }
  }

  fun dismissTutorial() {
    val updated = repository.updateSettings { it.copy(hasCompletedTutorial = true) }
    localState.update {
      it.copy(
        showTutorial = false,
        tutorialStep = 0,
        settings = updated,
      )
    }
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

  fun selectMakePreset(preset: TagPresetKind) {
    localState.update { state ->
      val nextStep =
        if (preset == TagPresetKind.SOCIALS) MakeStep.CHOOSE_SOCIAL
        else MakeStep.FILL_DETAILS
      state.copy(
        form =
          RecordFormState(
            makeStep = nextStep,
            selectedPreset = preset,
            selectedType = preset.recordType,
          ),
        cardLabelDraft = "",
        inputError = null,
      )
    }
  }

  fun selectSocialPlatform(platform: SocialPlatform) {
    localState.update { state ->
      state.copy(
        form =
          RecordFormState(
            makeStep = MakeStep.FILL_DETAILS,
            selectedPreset = TagPresetKind.SOCIALS,
            selectedSocial = platform,
            selectedType = NdefRecordType.URL,
          ),
        cardLabelDraft = "",
        inputError = null,
      )
    }
  }

  fun backToPreviousMakeStep() {
    localState.update { state ->
      val nextStep =
        when (state.form.makeStep) {
          MakeStep.FILL_DETAILS ->
            if (state.form.selectedPreset == TagPresetKind.SOCIALS) MakeStep.CHOOSE_SOCIAL
            else MakeStep.CHOOSE_TYPE
          MakeStep.CHOOSE_SOCIAL -> MakeStep.CHOOSE_TYPE
          MakeStep.CHOOSE_TYPE -> MakeStep.CHOOSE_TYPE
        }
      state.copy(
        form = state.form.copy(makeStep = nextStep),
        inputError = null,
      )
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
        form =
          RecordFormState(
            makeStep = it.form.makeStep,
            selectedPreset = it.form.selectedPreset,
            selectedSocial = it.form.selectedSocial,
            selectedType = it.form.selectedType,
          ),
        cardLabelDraft = "",
        inputError = null,
      )
    }
  }

  fun applyUrlPrefix(prefix: String, suggestedLabel: String) {
    localState.update {
      it.copy(
        form =
          it.form.copy(
            makeStep = MakeStep.FILL_DETAILS,
            selectedPreset = TagPresetKind.LINK,
            selectedType = NdefRecordType.URL,
            urlInput = prefix,
          ),
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

  fun importContactUri(uri: Uri) {
    val app = getApplication<Application>()
    viewModelScope.launch {
      val imported = withContext(Dispatchers.IO) { ContactBookImporter.readFromUri(app, uri) }
      if (imported == null || !imported.hasAnyField) {
        localState.update {
          it.copy(statusMessage = app.getString(R.string.msg_contact_import_failed))
        }
        return@launch
      }
      localState.update { state ->
        state.copy(
          form =
            state.form.copy(
              makeStep = MakeStep.FILL_DETAILS,
              selectedPreset = TagPresetKind.CONTACT,
              selectedType = NdefRecordType.CONTACT,
              contactName = imported.fullName,
              contactOrg = imported.organization,
              contactTitle = imported.title,
              contactPhone = imported.phone,
              contactEmail = imported.email,
              contactWebsite = imported.website,
              contactNote = imported.note,
            ),
          cardLabelDraft = state.cardLabelDraft.ifBlank { imported.fullName },
          inputError = null,
          statusMessage = app.getString(R.string.msg_contact_imported),
        )
      }
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

    val effectiveLabel =
      state.cardLabelDraft.ifBlank { deriveFriendlyLabel(state.form, record) }

    viewModelScope.launch {
      val written =
        repository.programCard(
          record = record,
          label = effectiveLabel,
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

  fun onPhysicalTagScanned(result: ScannedTagResult) {
    localState.update { state ->
      when (result) {
        is ScannedTagResult.Success ->
          state.copy(
            copyState =
              CopyTagUiState(
                scannedResult = result,
                scanError = null,
                copyLabelDraft = result.suggestedLabel,
                copyReadOnly = result.isReadOnly,
              )
          )
        is ScannedTagResult.Failure ->
          state.copy(
            copyState =
              state.copyState.copy(
                scannedResult = null,
                scanError = result.reason,
              )
          )
      }
    }
  }

  fun onCopyLabelChanged(label: String) {
    localState.update {
      it.copy(copyState = it.copyState.copy(copyLabelDraft = label))
    }
  }

  fun onCopyReadOnlyChanged(readOnly: Boolean) {
    localState.update {
      it.copy(copyState = it.copyState.copy(copyReadOnly = readOnly))
    }
  }

  fun resetCopyScan() {
    localState.update {
      it.copy(copyState = CopyTagUiState(copyReadOnly = it.settings.defaultReadOnly))
    }
  }

  fun saveScannedTag() {
    val state = localState.value
    val scanned = state.copyState.scannedResult ?: return
    val app = getApplication<Application>()
    val label = state.copyState.copyLabelDraft.trim().ifBlank { scanned.suggestedLabel }

    viewModelScope.launch {
      val saved =
        repository.saveCopiedTagBytes(
          ndefBytes = scanned.ndefBytes,
          label = label,
          isReadOnly = state.copyState.copyReadOnly,
        )
      val nfcStatus = NfcHardwareHelper.getStatus(app)
      val finalSnapshot =
        if (state.settings.autoActivateOnWrite && nfcStatus == NfcHardwareStatus.READY) {
          repository.setEmulating(true)
        } else {
          saved
        }
      localState.update {
        it.copy(
          nfcStatus = nfcStatus,
          activeLabel = finalSnapshot.label,
          activeRecord = finalSnapshot.records.firstOrNull(),
          activeNdefBytes = finalSnapshot.ndefMessageBytes,
          isEmulating = finalSnapshot.isEmulating,
          isReadOnly = finalSnapshot.isReadOnly,
          copyState = CopyTagUiState(copyReadOnly = state.settings.defaultReadOnly),
          currentScreen = AppScreen.ACTIVATE_TAG,
          statusMessage = app.getString(R.string.msg_card_copied),
        )
      }
    }
  }

  fun duplicateSavedCard(card: SavedLinkEntity) {
    val firstRecord = NdefMessageCodec.decodeRecords(card.ndefBytes).firstOrNull()
    val populatedForm = formFromDraft(firstRecord)
    localState.update {
      it.copy(
        currentScreen = AppScreen.MAKE_TAG,
        form = populatedForm,
        cardLabelDraft = "${card.label} Copy",
        makeReadOnly = card.isReadOnly,
        inputError = null,
      )
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
        inputError = null,
        statusMessage = notice,
      )
    }
  }

  fun updateCardCoverPhoto(cardId: Long, uri: Uri?) {
    if (uri == null) return
    val app = getApplication<Application>()
    viewModelScope.launch {
      val updated = repository.updateCardCoverImage(cardId, uri)
      localState.update {
        it.copy(
          statusMessage =
            app.getString(if (updated) R.string.msg_cover_updated else R.string.msg_cover_failed)
        )
      }
    }
  }

  fun removeCardCoverPhoto(cardId: Long) {
    val app = getApplication<Application>()
    viewModelScope.launch {
      repository.updateCardCoverImage(cardId, null)
      localState.update { it.copy(statusMessage = app.getString(R.string.msg_cover_removed)) }
    }
  }

  fun deleteSavedCard(id: Long) {
    viewModelScope.launch { repository.deleteSavedLink(id) }
  }

  fun clearCard() {
    DynamicSwitchController.clear(getApplication())
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
      DynamicSwitchController.clear(app)
      val snapshot = repository.setEmulating(false)
      localState.update { it.copy(nfcStatus = status, isEmulating = snapshot.isEmulating) }
      return
    }

    if (status != NfcHardwareStatus.READY) {
      DynamicSwitchController.clear(app)
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
    if (updated.peerExchangeMode != PeerExchangeMode.DYNAMIC) {
      DynamicSwitchController.clear(getApplication())
    }
    localState.update { it.copy(settings = updated) }
  }

  fun requestAddQuickTile() {
    val app = getApplication<Application>()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      localState.update { it.copy(statusMessage = app.getString(R.string.msg_qs_tile_manual)) }
      return
    }
    val statusBarManager = app.getSystemService(StatusBarManager::class.java)
    if (statusBarManager == null) {
      localState.update { it.copy(statusMessage = app.getString(R.string.msg_qs_tile_manual)) }
      return
    }
    runCatching {
      statusBarManager.requestAddTileService(
        ComponentName(app, NfcToggleTileService::class.java),
        app.getString(R.string.qs_tile_label),
        Icon.createWithResource(app, R.drawable.ic_qs_nfc_card),
        app.mainExecutor,
      ) { resultCode ->
        val msg =
          when (resultCode) {
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
              app.getString(R.string.msg_qs_tile_added)
            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
              app.getString(R.string.msg_qs_tile_already)
            else -> app.getString(R.string.msg_qs_tile_manual)
          }
        localState.update { it.copy(statusMessage = msg) }
      }
    }.onFailure {
      localState.update { it.copy(statusMessage = app.getString(R.string.msg_qs_tile_manual)) }
    }
  }

  fun consumeStatusMessage() {
    localState.update { it.copy(statusMessage = null) }
  }

  private fun deriveFriendlyLabel(form: RecordFormState, record: NdefRecordDraft): String =
    when (form.selectedPreset) {
      TagPresetKind.SOCIALS -> {
        val platform = form.selectedSocial
        if (platform.isPhoneBased) {
          platform.title
        } else {
          val handle =
            form.socialHandleInput.trim().removePrefix("@").substringAfterLast('/').take(24)
          if (handle.isNotBlank()) "${platform.title} (@$handle)" else platform.title
        }
      }
      else -> when (record) {
        is NdefRecordDraft.Url -> ""
        is NdefRecordDraft.Text -> "Text Note"
        is NdefRecordDraft.Contact -> record.fullName
        is NdefRecordDraft.Wifi -> "${record.ssid} Wi-Fi"
        is NdefRecordDraft.Email -> record.address
        is NdefRecordDraft.Phone -> record.number
        is NdefRecordDraft.Sms -> "SMS ${record.number}"
        is NdefRecordDraft.Geo -> record.label.ifBlank { "Location" }
        is NdefRecordDraft.AppLaunch -> record.packageName
        is NdefRecordDraft.CustomMime -> record.mimeType
      }
    }

  private fun formFromDraft(draft: NdefRecordDraft?): RecordFormState =
    when (draft) {
      is NdefRecordDraft.Url -> {
        val url = draft.url
        val matchedSocial =
          when {
            url.contains("instagram.com/", true) ->
              SocialPlatform.INSTAGRAM to url.substringAfter("instagram.com/").trim('/')
            url.contains("wa.me/", true) ->
              SocialPlatform.WHATSAPP to url.substringAfter("wa.me/").trim('/')
            url.contains("tiktok.com/@", true) ->
              SocialPlatform.TIKTOK to url.substringAfter("tiktok.com/@").trim('/')
            url.contains("x.com/", true) ->
              SocialPlatform.X to url.substringAfter("x.com/").trim('/')
            url.contains("linkedin.com/in/", true) ->
              SocialPlatform.LINKEDIN to url.substringAfter("linkedin.com/in/").trim('/')
            url.contains("youtube.com/@", true) ->
              SocialPlatform.YOUTUBE to url.substringAfter("youtube.com/@").trim('/')
            url.contains("threads.net/@", true) ->
              SocialPlatform.THREADS to url.substringAfter("threads.net/@").trim('/')
            url.contains("snapchat.com/add/", true) ->
              SocialPlatform.SNAPCHAT to url.substringAfter("snapchat.com/add/").trim('/')
            url.contains("github.com/", true) ->
              SocialPlatform.GITHUB to url.substringAfter("github.com/").trim('/')
            url.contains("facebook.com/", true) ->
              SocialPlatform.FACEBOOK to url.substringAfter("facebook.com/").trim('/')
            url.contains("discord.gg/", true) ->
              SocialPlatform.DISCORD to url.substringAfter("discord.gg/").trim('/')
            url.contains("twitch.tv/", true) ->
              SocialPlatform.TWITCH to url.substringAfter("twitch.tv/").trim('/')
            else -> null
          }
        if (matchedSocial != null) {
          RecordFormState(
            makeStep = MakeStep.FILL_DETAILS,
            selectedPreset = TagPresetKind.SOCIALS,
            selectedSocial = matchedSocial.first,
            selectedType = NdefRecordType.URL,
            socialHandleInput = matchedSocial.second,
          )
        } else {
          RecordFormState(
            makeStep = MakeStep.FILL_DETAILS,
            selectedPreset = TagPresetKind.LINK,
            selectedType = NdefRecordType.URL,
            urlInput = url,
          )
        }
      }
      is NdefRecordDraft.Text ->
        RecordFormState(
          makeStep = MakeStep.FILL_DETAILS,
          selectedPreset = TagPresetKind.TEXT,
          selectedType = NdefRecordType.TEXT,
          textInput = draft.text,
          textLang = draft.languageCode,
        )
      is NdefRecordDraft.Contact ->
        RecordFormState(
          makeStep = MakeStep.FILL_DETAILS,
          selectedPreset = TagPresetKind.CONTACT,
          selectedType = NdefRecordType.CONTACT,
          contactName = draft.fullName,
          contactOrg = draft.organization,
          contactTitle = draft.title,
          contactPhone = draft.phone,
          contactEmail = draft.email,
          contactWebsite = draft.website,
          contactNote = draft.note,
        )
      is NdefRecordDraft.Wifi ->
        RecordFormState(
          makeStep = MakeStep.FILL_DETAILS,
          selectedPreset = TagPresetKind.WIFI,
          selectedType = NdefRecordType.WIFI,
          wifiSsid = draft.ssid,
          wifiPassword = draft.password,
          wifiAuth = draft.authMode,
        )
      is NdefRecordDraft.Email ->
        RecordFormState(
          makeStep = MakeStep.FILL_DETAILS,
          selectedPreset = TagPresetKind.EMAIL,
          selectedType = NdefRecordType.EMAIL,
          emailAddress = draft.address,
          emailSubject = draft.subject,
          emailBody = draft.body,
        )
      is NdefRecordDraft.Phone ->
        RecordFormState(
          makeStep = MakeStep.FILL_DETAILS,
          selectedPreset = TagPresetKind.PHONE,
          selectedType = NdefRecordType.PHONE,
          phoneNumber = draft.number,
        )
      is NdefRecordDraft.Sms ->
        RecordFormState(
          makeStep = MakeStep.FILL_DETAILS,
          selectedPreset = TagPresetKind.SMS,
          selectedType = NdefRecordType.SMS,
          smsNumber = draft.number,
          smsMessage = draft.message,
        )
      is NdefRecordDraft.Geo ->
        RecordFormState(
          makeStep = MakeStep.FILL_DETAILS,
          selectedPreset = TagPresetKind.GEO,
          selectedType = NdefRecordType.GEO,
          geoLat = draft.latitude,
          geoLon = draft.longitude,
          geoLabel = draft.label,
        )
      is NdefRecordDraft.AppLaunch ->
        RecordFormState(
          makeStep = MakeStep.FILL_DETAILS,
          selectedPreset = TagPresetKind.APP,
          selectedType = NdefRecordType.APP,
          appPackage = draft.packageName,
        )
      is NdefRecordDraft.CustomMime ->
        RecordFormState(
          makeStep = MakeStep.FILL_DETAILS,
          selectedPreset = TagPresetKind.MIME,
          selectedType = NdefRecordType.MIME,
          mimeType = draft.mimeType,
          mimePayload = draft.payload,
        )
      null -> RecordFormState(makeStep = MakeStep.CHOOSE_TYPE)
    }

  private fun buildValidatedRecord(form: RecordFormState): NdefRecordDraft? {
    val app = getApplication<Application>()
    return when (form.selectedPreset) {
      TagPresetKind.SOCIALS -> {
        val platform = form.selectedSocial
        val url = platform.buildProfileUrl(form.socialHandleInput)
        if (url.isBlank() || !NdefUriEncoder.isValidUri(url)) {
          val errRes =
            if (platform.isPhoneBased) R.string.error_invalid_whatsapp
            else R.string.error_invalid_social
          setError(app.getString(errRes))
          null
        } else {
          NdefRecordDraft.Url(url)
        }
      }
      TagPresetKind.LINK -> {
        val normalized = NdefUriEncoder.normalizeUri(form.urlInput)
        if (!NdefUriEncoder.isValidUri(normalized)) {
          setError(app.getString(R.string.error_invalid_url))
          null
        } else {
          NdefRecordDraft.Url(normalized)
        }
      }
      TagPresetKind.TEXT -> {
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
      TagPresetKind.CONTACT -> {
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
      TagPresetKind.WIFI -> {
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
      TagPresetKind.EMAIL -> {
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
      TagPresetKind.PHONE -> {
        if (form.phoneNumber.isBlank()) {
          setError(app.getString(R.string.error_empty_phone))
          null
        } else {
          NdefRecordDraft.Phone(form.phoneNumber.trim())
        }
      }
      TagPresetKind.SMS -> {
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
      TagPresetKind.GEO -> {
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
      TagPresetKind.APP -> {
        val pkg = form.appPackage.trim()
        if (pkg.isBlank() || !pkg.contains('.')) {
          setError(app.getString(R.string.error_empty_package))
          null
        } else {
          NdefRecordDraft.AppLaunch(pkg)
        }
      }
      TagPresetKind.MIME -> {
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

  companion object {
    const val TUTORIAL_STEP_COUNT = 5
  }
}
