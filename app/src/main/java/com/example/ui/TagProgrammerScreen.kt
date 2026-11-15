package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AppSettings
import com.example.data.SavedLinkEntity
import com.example.nfc.NdefRecordType
import com.example.nfc.NfcHardwareStatus
import com.example.ui.theme.LocalTagExtendedColors
import com.example.ui.theme.MonoBodyStyle
import com.example.widget.WidgetType

@Composable
fun TagProgrammerScreen(
  state: TagUiState,
  onNavigate: (AppScreen) -> Unit,
  onOpenTutorial: () -> Unit,
  onDismissTutorial: () -> Unit,
  onOpenPinWidget: () -> Unit,
  onDismissPinWidget: () -> Unit,
  onPinWidget: (WidgetType) -> Unit,
  onSelectRecordType: (NdefRecordType) -> Unit,
  onUpdateForm: ((RecordFormState) -> RecordFormState) -> Unit,
  onCardLabelChanged: (String) -> Unit,
  onMakeReadOnlyChanged: (Boolean) -> Unit,
  onApplyUrlPrefix: (String, String) -> Unit,
  onApplyAppPreset: (String) -> Unit,
  onWriteCard: () -> Unit,
  onClearForm: () -> Unit,
  onClearCard: () -> Unit,
  onToggleEmulation: (Boolean) -> Unit,
  onToggleReadOnly: (Boolean) -> Unit,
  onLoadSavedCard: (SavedLinkEntity) -> Unit,
  onDeleteSavedCard: (Long) -> Unit,
  onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
  onOpenNfcSettings: () -> Unit,
  onRequestAddQuickTile: () -> Unit,
  onConsumeStatusMessage: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val extendedColors = LocalTagExtendedColors.current

  LaunchedEffect(state.statusMessage) {
    val msg = state.statusMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      onConsumeStatusMessage()
    }
  }

  if (state.currentScreen != AppScreen.HOME) {
    BackHandler { onNavigate(AppScreen.HOME) }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        TopNavigationBar(
          currentScreen = state.currentScreen,
          nfcStatus = state.nfcStatus,
          onBack = { onNavigate(AppScreen.HOME) },
          onOpenTutorial = onOpenTutorial,
          onOpenSettings = { onNavigate(AppScreen.SETTINGS) },
        )
        HorizontalDivider(thickness = 1.dp, color = extendedColors.border)
      }
    },
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      val contentModifier = Modifier.fillMaxSize().widthIn(max = 720.dp)

      when (state.currentScreen) {
        AppScreen.HOME -> {
          HomeEntryScreen(
            state = state,
            onOpenActivate = { onNavigate(AppScreen.ACTIVATE_TAG) },
            onOpenMake = { onNavigate(AppScreen.MAKE_TAG) },
            onOpenPinWidget = onOpenPinWidget,
            onOpenNfcSettings = onOpenNfcSettings,
            modifier = contentModifier,
          )
        }

        AppScreen.ACTIVATE_TAG -> {
          ActivateTagScreen(
            state = state,
            onToggleEmulation = onToggleEmulation,
            onToggleReadOnly = onToggleReadOnly,
            onClearCard = onClearCard,
            onLoadSavedCard = onLoadSavedCard,
            onDeleteSavedCard = onDeleteSavedCard,
            onOpenMakeTag = { onNavigate(AppScreen.MAKE_TAG) },
            onOpenNfcSettings = onOpenNfcSettings,
            modifier = contentModifier,
          )
        }

        AppScreen.MAKE_TAG -> {
          LazyColumn(
            modifier = contentModifier,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            item {
              RecordComposerSection(
                state = state,
                onSelectRecordType = onSelectRecordType,
                onUpdateForm = onUpdateForm,
                onCardLabelChanged = onCardLabelChanged,
                onMakeReadOnlyChanged = onMakeReadOnlyChanged,
                onApplyUrlPrefix = onApplyUrlPrefix,
                onApplyAppPreset = onApplyAppPreset,
                onWriteCard = onWriteCard,
                onClearForm = onClearForm,
              )
            }
          }
        }

        AppScreen.SETTINGS -> {
          SettingsScreen(
            settings = state.settings,
            onUpdateSettings = onUpdateSettings,
            onOpenNfcSettings = onOpenNfcSettings,
            onOpenPinWidget = onOpenPinWidget,
            onRequestAddQuickTile = onRequestAddQuickTile,
            modifier = contentModifier,
          )
        }
      }
    }
  }

  if (state.showPinWidgetDialog) {
    PinWidgetDialog(
      onPinWidget = onPinWidget,
      onDismiss = onDismissPinWidget,
    )
  }

  if (state.showTutorial) {
    TutorialDialog(onDismiss = onDismissTutorial)
  }
}

@Composable
private fun TopNavigationBar(
  currentScreen: AppScreen,
  nfcStatus: NfcHardwareStatus,
  onBack: () -> Unit,
  onOpenTutorial: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  val statusLabel =
    when (nfcStatus) {
      NfcHardwareStatus.READY -> stringResource(R.string.nfc_status_ready)
      NfcHardwareStatus.NFC_DISABLED -> stringResource(R.string.nfc_status_disabled)
      NfcHardwareStatus.NO_HCE_HARDWARE -> stringResource(R.string.nfc_status_no_hardware)
    }
  val statusColor =
    when (nfcStatus) {
      NfcHardwareStatus.READY -> extendedColors.signalActive
      NfcHardwareStatus.NFC_DISABLED -> MaterialTheme.colorScheme.error
      NfcHardwareStatus.NO_HCE_HARDWARE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  val screenTitle =
    when (currentScreen) {
      AppScreen.HOME -> stringResource(R.string.app_name)
      AppScreen.ACTIVATE_TAG -> stringResource(R.string.home_activate_title)
      AppScreen.MAKE_TAG -> stringResource(R.string.home_make_title)
      AppScreen.SETTINGS -> stringResource(R.string.settings_title)
    }

  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      if (currentScreen != AppScreen.HOME) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
          )
        }
      }
      Text(
        text = screenTitle,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Box(modifier = Modifier.size(7.dp).background(statusColor))
        Text(
          text = statusLabel,
          style = MaterialTheme.typography.labelMedium,
          color = statusColor,
          modifier = Modifier.testTag("nfc_hardware_status_text"),
        )
      }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(
        onClick = onOpenTutorial,
        modifier =
          Modifier.minimumInteractiveComponentSize()
            .border(1.dp, extendedColors.border)
            .size(44.dp)
            .testTag("tutorial_button"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = stringResource(R.string.cd_tutorial),
          modifier = Modifier.size(19.dp),
        )
      }

      if (currentScreen != AppScreen.SETTINGS) {
        IconButton(
          onClick = onOpenSettings,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .border(1.dp, extendedColors.border)
              .size(44.dp)
              .testTag("app_settings_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.cd_app_settings),
            modifier = Modifier.size(19.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun HomeEntryScreen(
  state: TagUiState,
  onOpenActivate: () -> Unit,
  onOpenMake: () -> Unit,
  onOpenPinWidget: () -> Unit,
  onOpenNfcSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extendedColors = LocalTagExtendedColors.current
  val hasTarget = state.activeNdefBytes.isNotEmpty() || !state.isReadOnly
  val isActive = state.nfcStatus == NfcHardwareStatus.READY && state.isEmulating && hasTarget

  val activeSummary =
    when {
      state.activeLabel.isNotBlank() -> state.activeLabel
      state.activeRecord != null -> state.activeRecord.summaryLine()
      else -> stringResource(R.string.virtual_card_empty)
    }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (state.nfcStatus != NfcHardwareStatus.READY) {
      item {
        NfcDisabledBanner(
          nfcStatus = state.nfcStatus,
          onOpenNfcSettings = onOpenNfcSettings,
        )
      }
    }

    item {
      HomeChoiceCard(
        icon = Icons.Outlined.Nfc,
        title = stringResource(R.string.home_activate_title),
        subtitle =
          if (state.activeRecord != null) {
            "$activeSummary · ${state.savedCards.size} saved"
          } else {
            stringResource(R.string.home_activate_subtitle)
          },
        badgeText =
          when {
            state.nfcStatus != NfcHardwareStatus.READY -> stringResource(R.string.nfc_status_disabled)
            isActive -> stringResource(R.string.emulation_active_label)
            else -> stringResource(R.string.emulation_standby_label)
          },
        badgeActive = isActive,
        borderColor = if (isActive) extendedColors.signalActive else extendedColors.border,
        onClick = onOpenActivate,
        testTag = "home_activate_tag_button",
      )
    }

    item {
      HomeChoiceCard(
        icon = Icons.Outlined.EditNote,
        title = stringResource(R.string.home_make_title),
        subtitle = stringResource(R.string.home_make_subtitle),
        badgeText = stringResource(R.string.single_record_rule),
        badgeActive = false,
        borderColor = extendedColors.border,
        onClick = onOpenMake,
        testTag = "home_make_tag_button",
      )
    }

    item {
      OutlinedButton(
        onClick = onOpenPinWidget,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, extendedColors.border),
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("pin_widget_prompt_button"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Widgets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.home_pin_widget_button),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.home_pin_widget_sub),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HomeChoiceCard(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  badgeText: String,
  badgeActive: Boolean,
  borderColor: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit,
  testTag: String,
) {
  val extendedColors = LocalTagExtendedColors.current
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .minimumInteractiveComponentSize()
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, borderColor)
        .clickable(onClick = onClick)
        .padding(16.dp)
        .testTag(testTag),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint =
            if (badgeActive) extendedColors.signalActive
            else MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(24.dp),
        )
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      Box(
        modifier =
          Modifier.background(
              if (badgeActive) extendedColors.signalActiveContainer
              else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(1.dp, if (badgeActive) extendedColors.signalActive else extendedColors.border)
            .padding(horizontal = 8.dp, vertical = 4.dp),
      ) {
        Text(
          text = badgeText,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color =
            if (badgeActive) extendedColors.signalActive
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    Text(
      text = subtitle,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun ActivateTagScreen(
  state: TagUiState,
  onToggleEmulation: (Boolean) -> Unit,
  onToggleReadOnly: (Boolean) -> Unit,
  onClearCard: () -> Unit,
  onLoadSavedCard: (SavedLinkEntity) -> Unit,
  onDeleteSavedCard: (Long) -> Unit,
  onOpenMakeTag: () -> Unit,
  onOpenNfcSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extendedColors = LocalTagExtendedColors.current

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (state.nfcStatus != NfcHardwareStatus.READY) {
      item {
        NfcDisabledBanner(
          nfcStatus = state.nfcStatus,
          onOpenNfcSettings = onOpenNfcSettings,
        )
      }
    }

    item {
      VirtualCardPanel(
        state = state,
        onToggleEmulation = onToggleEmulation,
        onToggleReadOnly = onToggleReadOnly,
        onClearCard = onClearCard,
        onOpenMakeTag = onOpenMakeTag,
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${stringResource(R.string.section_saved_cards)} (${state.savedCards.size})",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
      }
    }

    if (state.savedCards.isEmpty()) {
      item {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, extendedColors.border)
              .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = stringResource(R.string.empty_saved_cards),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedButton(
            onClick = onOpenMakeTag,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, extendedColors.border),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Text(text = stringResource(R.string.action_go_make_tag))
          }
        }
      }
    } else {
      items(state.savedCards, key = { it.id }) { card ->
        SavedCardRow(
          card = card,
          isActive =
            card.ndefBytes.contentEquals(state.activeNdefBytes) && card.ndefBytes.isNotEmpty(),
          onSelect = { onLoadSavedCard(card) },
          onDelete = { onDeleteSavedCard(card.id) },
        )
      }
    }
  }
}

@Composable
private fun NfcDisabledBanner(
  nfcStatus: NfcHardwareStatus,
  onOpenNfcSettings: () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.errorContainer)
        .border(1.dp, MaterialTheme.colorScheme.error)
        .padding(12.dp)
        .testTag("nfc_disabled_banner"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text =
        if (nfcStatus == NfcHardwareStatus.NFC_DISABLED) stringResource(R.string.nfc_required_banner)
        else stringResource(R.string.nfc_unsupported_banner),
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.onErrorContainer,
    )
    if (nfcStatus == NfcHardwareStatus.NFC_DISABLED) {
      OutlinedButton(
        onClick = onOpenNfcSettings,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("banner_nfc_settings_button"),
      ) {
        Text(
          text = stringResource(R.string.action_turn_on_nfc),
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }
  }
}

@Composable
private fun VirtualCardPanel(
  state: TagUiState,
  onToggleEmulation: (Boolean) -> Unit,
  onToggleReadOnly: (Boolean) -> Unit,
  onClearCard: () -> Unit,
  onOpenMakeTag: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  val hasPayload = state.activeNdefBytes.isNotEmpty() || !state.isReadOnly
  val nfcReady = state.nfcStatus == NfcHardwareStatus.READY
  val isActive = nfcReady && state.isEmulating && hasPayload
  val canActivate = nfcReady && hasPayload

  val borderColor = if (isActive) extendedColors.signalActive else extendedColors.border
  val headerBg =
    if (isActive) extendedColors.signalActiveContainer else MaterialTheme.colorScheme.surfaceVariant

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, borderColor),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(headerBg)
          .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Nfc,
          contentDescription = null,
          tint =
            if (isActive) extendedColors.signalActive
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text =
            if (isActive) stringResource(R.string.emulation_active_label)
            else stringResource(R.string.emulation_standby_label),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color =
            if (isActive) extendedColors.signalActive
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("emulation_state_label"),
        )
      }

      Surface(
        onClick = { onToggleReadOnly(!state.isReadOnly) },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.border),
        modifier = Modifier.testTag("lock_mode_button"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = if (state.isReadOnly) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text =
              if (state.isReadOnly) stringResource(R.string.label_lock_readonly)
              else stringResource(R.string.label_lock_writable),
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }
    }

    HorizontalDivider(thickness = 1.dp, color = borderColor)

    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val record = state.activeRecord
      if (record != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (state.activeLabel.isNotBlank()) {
            Text(
              text = state.activeLabel,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
          } else {
            Spacer(modifier = Modifier.weight(1f))
          }
          Text(
            text = "${state.activeNdefBytes.size} B",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = record.type.shortTag,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = record.summaryLine(),
            style = MonoBodyStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("active_card_uri_text"),
          )
        }
      } else {
        Text(
          text = stringResource(R.string.virtual_card_empty),
          style = MonoBodyStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("active_card_uri_text"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = { onToggleEmulation(!state.isEmulating) },
          enabled = isActive || canActivate,
          shape = MaterialTheme.shapes.small,
          colors =
            ButtonDefaults.buttonColors(
              containerColor =
                if (isActive) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primary,
              contentColor =
                if (isActive) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onPrimary,
            ),
          border = if (isActive) BorderStroke(1.dp, extendedColors.border) else null,
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .height(48.dp)
              .testTag("emulation_toggle_button"),
        ) {
          Text(
            text =
              if (isActive) stringResource(R.string.emulation_toggle_stop)
              else stringResource(R.string.emulation_toggle_start),
            style = MaterialTheme.typography.titleMedium,
          )
        }

        if (state.activeNdefBytes.isNotEmpty()) {
          OutlinedButton(
            onClick = onClearCard,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, extendedColors.border),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .height(48.dp)
                .testTag("clear_card_button"),
          ) {
            Text(
              text = stringResource(R.string.action_clear_inputs),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        } else {
          OutlinedButton(
            onClick = onOpenMakeTag,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, extendedColors.border),
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .height(48.dp)
                .testTag("quick_make_tag_button"),
          ) {
            Text(
              text = stringResource(R.string.action_go_make_tag),
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SavedCardRow(
  card: SavedLinkEntity,
  isActive: Boolean,
  onSelect: () -> Unit,
  onDelete: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  val borderColor = if (isActive) MaterialTheme.colorScheme.primary else extendedColors.border

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, borderColor)
        .clickable(onClick = onSelect)
        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        .testTag("saved_link_row_${card.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.Tag,
      contentDescription = stringResource(R.string.cd_load_saved_link),
      tint =
        if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = card.label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = card.summary,
        style = MonoBodyStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    IconButton(
      onClick = onDelete,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_saved_link_${card.id}"),
    ) {
      Icon(
        imageVector = Icons.Outlined.DeleteOutline,
        contentDescription = stringResource(R.string.cd_delete_saved_link),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
