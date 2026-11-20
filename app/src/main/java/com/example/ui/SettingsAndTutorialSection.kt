package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.AppSettings
import com.example.data.PeerExchangeMode
import com.example.data.SavedTagViewMode
import com.example.data.ThemeMode
import com.example.nfc.NfcHardwareStatus
import com.example.ui.theme.LocalTagExtendedColors
import com.example.widget.WidgetType

@Composable
fun SettingsScreen(
  settings: AppSettings,
  isIgnoringBatteryOptimizations: Boolean,
  areNotificationsEnabled: Boolean,
  onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
  onToggleTapNotifications: (Boolean) -> Unit,
  onOpenBatterySettings: () -> Unit,
  onRequestBackgroundAlerts: () -> Unit,
  onOpenNfcSettings: () -> Unit,
  onOpenPinWidget: () -> Unit,
  onRequestAddQuickTile: () -> Unit,
  onRequestAddQrQuickTile: () -> Unit,
  onOpenGithub: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extendedColors = LocalTagExtendedColors.current
  var showTwoWayInfoDialog by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("settings_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      SettingsSectionCard(title = stringResource(R.string.settings_section_appearance)) {
        Text(
          text = stringResource(R.string.settings_theme_label),
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ThemeMode.entries.forEach { mode ->
            val selected = settings.themeMode == mode
            OutlinedButton(
              onClick = { onUpdateSettings { it.copy(themeMode = mode) } },
              shape = MaterialTheme.shapes.small,
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                  contentColor =
                    if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                ),
              border =
                BorderStroke(
                  1.dp,
                  if (selected) MaterialTheme.colorScheme.primary else extendedColors.border,
                ),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
              modifier =
                Modifier.weight(1f)
                  .height(48.dp)
                  .testTag("theme_mode_${mode.name.lowercase()}"),
            ) {
              Text(
                text = mode.label,
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }
        }

        SettingsDivider()

        Text(
          text = stringResource(R.string.settings_saved_view_label),
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          SavedTagViewMode.entries.forEach { viewMode ->
            val selected = settings.savedTagViewMode == viewMode
            OutlinedButton(
              onClick = { onUpdateSettings { it.copy(savedTagViewMode = viewMode) } },
              shape = MaterialTheme.shapes.small,
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                  contentColor =
                    if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                ),
              border =
                BorderStroke(
                  1.dp,
                  if (selected) MaterialTheme.colorScheme.primary else extendedColors.border,
                ),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
              modifier =
                Modifier.weight(1f)
                  .height(48.dp)
                  .testTag("tag_view_${viewMode.name.lowercase()}"),
            ) {
              Text(
                text = viewMode.label,
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }
        }
        Text(
          text = stringResource(R.string.settings_saved_view_hint),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      SettingsSectionCard(title = stringResource(R.string.settings_section_tag)) {
        Text(
          text = stringResource(R.string.settings_default_lock_label),
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf(true to R.string.label_lock_readonly, false to R.string.label_lock_writable)
            .forEach { (isRo, labelRes) ->
              val selected = settings.defaultReadOnly == isRo
              OutlinedButton(
                onClick = { onUpdateSettings { it.copy(defaultReadOnly = isRo) } },
                shape = MaterialTheme.shapes.small,
                colors =
                  ButtonDefaults.outlinedButtonColors(
                    containerColor =
                      if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor =
                      if (selected) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurface,
                  ),
                border =
                  BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else extendedColors.border,
                  ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f).height(48.dp),
              ) {
                Text(
                  text = stringResource(labelRes),
                  style = MaterialTheme.typography.labelMedium,
                )
              }
            }
        }

        SettingsDivider()

        SettingsToggleRow(
          label = stringResource(R.string.settings_auto_activate_write),
          checked = settings.autoActivateOnWrite,
          onCheckedChange = { v -> onUpdateSettings { it.copy(autoActivateOnWrite = v) } },
          testTag = "setting_auto_activate_write",
        )
        SettingsDivider()
        SettingsToggleRow(
          label = stringResource(R.string.settings_auto_activate_select),
          checked = settings.autoActivateOnSelect,
          onCheckedChange = { v -> onUpdateSettings { it.copy(autoActivateOnSelect = v) } },
          testTag = "setting_auto_activate_select",
        )
        SettingsDivider()
        SettingsToggleRow(
          label = stringResource(R.string.settings_prefer_foreground),
          checked = settings.preferForegroundHce,
          onCheckedChange = { v -> onUpdateSettings { it.copy(preferForegroundHce = v) } },
          testTag = "setting_prefer_foreground",
        )
        SettingsDivider()
        SettingsToggleRow(
          label = stringResource(R.string.settings_stop_on_leave),
          checked = settings.stopOnLeaveApp,
          onCheckedChange = { v -> onUpdateSettings { it.copy(stopOnLeaveApp = v) } },
          testTag = "setting_stop_on_leave",
        )
        SettingsDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.settings_peer_exchange_label),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
          )
          IconButton(
            onClick = { showTwoWayInfoDialog = true },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("two_way_tap_info_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = stringResource(R.string.cd_two_way_tap_info),
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          PeerExchangeMode.entries.forEach { peerMode ->
            val selected = settings.peerExchangeMode == peerMode
            OutlinedButton(
              onClick = { onUpdateSettings { it.copy(peerExchangeMode = peerMode) } },
              shape = MaterialTheme.shapes.small,
              colors =
                ButtonDefaults.outlinedButtonColors(
                  containerColor =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                  contentColor =
                    if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                ),
              border =
                BorderStroke(
                  1.dp,
                  if (selected) MaterialTheme.colorScheme.primary else extendedColors.border,
                ),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
              modifier =
                Modifier.weight(1f)
                  .height(48.dp)
                  .testTag("peer_mode_${peerMode.name.lowercase()}"),
            ) {
              Text(
                text = peerMode.label,
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }
        }
      }
    }

    item {
      SettingsSectionCard(title = stringResource(R.string.settings_section_background)) {
        SettingsToggleRow(
          label = stringResource(R.string.settings_tap_notifications_label),
          subtitle = stringResource(R.string.settings_tap_notifications_hint),
          checked = settings.incomingTagNotificationsEnabled,
          onCheckedChange = onToggleTapNotifications,
          testTag = "setting_tap_notifications",
        )
        SettingsDivider()
        SettingsStatusActionRow(
          icon = Icons.Outlined.BatteryChargingFull,
          label = stringResource(R.string.settings_battery_unrestricted_label),
          statusText =
            if (isIgnoringBatteryOptimizations) {
              stringResource(R.string.settings_battery_status_unrestricted)
            } else {
              stringResource(R.string.settings_battery_status_restricted)
            },
          statusOk = isIgnoringBatteryOptimizations,
          onClick = onOpenBatterySettings,
          testTag = "settings_battery_optimization_button",
        )
        Text(
          text = stringResource(R.string.settings_battery_hint),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsDivider()
        SettingsStatusActionRow(
          icon = Icons.Outlined.NotificationsActive,
          label = stringResource(R.string.settings_notifications_label),
          statusText =
            if (areNotificationsEnabled) {
              stringResource(R.string.settings_notifications_status_on)
            } else {
              stringResource(R.string.settings_notifications_status_off)
            },
          statusOk = areNotificationsEnabled,
          onClick = onRequestBackgroundAlerts,
          testTag = "settings_notifications_button",
        )
      }
    }

    item {
      SettingsSectionCard(title = stringResource(R.string.settings_section_shortcuts)) {
        SettingsActionRow(
          icon = Icons.Outlined.Nfc,
          label = stringResource(R.string.settings_open_nfc),
          onClick = onOpenNfcSettings,
          testTag = "settings_open_nfc_button",
        )
        SettingsDivider()
        SettingsActionRow(
          icon = Icons.Outlined.Widgets,
          label = stringResource(R.string.settings_pin_widget),
          onClick = onOpenPinWidget,
          testTag = "settings_pin_widget_button",
        )
        SettingsDivider()
        SettingsActionRow(
          icon = Icons.Outlined.AddBox,
          label = stringResource(R.string.settings_add_qs_tile),
          onClick = onRequestAddQuickTile,
          testTag = "settings_add_qs_tile_button",
        )
        SettingsDivider()
        SettingsActionRow(
          icon = Icons.Outlined.QrCode2,
          label = stringResource(R.string.settings_add_qr_tile),
          onClick = onRequestAddQrQuickTile,
          testTag = "settings_add_qr_tile_button",
        )
      }
    }

    item {
      SettingsSectionCard(title = stringResource(R.string.settings_section_about)) {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .minimumInteractiveComponentSize()
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, extendedColors.border)
              .clickable(onClick = onOpenGithub)
              .padding(horizontal = 12.dp, vertical = 12.dp)
              .testTag("settings_star_github_button"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.StarOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
          )
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = stringResource(R.string.settings_star_github_title),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.settings_star_github_sub),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }

  if (showTwoWayInfoDialog) {
    TwoWayTapInfoDialog(onDismiss = { showTwoWayInfoDialog = false })
  }
}

@Composable
fun WelcomeSetupDialog(
  nfcStatus: NfcHardwareStatus,
  areNotificationsEnabled: Boolean,
  isIgnoringBatteryOptimizations: Boolean,
  onOpenNfcSettings: () -> Unit,
  onRequestNotifications: () -> Unit,
  onOpenBatterySettings: () -> Unit,
  onContinue: () -> Unit,
  onSkip: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  Dialog(onDismissRequest = onSkip) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, extendedColors.border)
          .padding(16.dp)
          .testTag("welcome_setup_dialog"),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier =
            Modifier.size(36.dp)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, MaterialTheme.colorScheme.primary),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.Nfc,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.setup_dialog_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      Text(
        text = stringResource(R.string.setup_dialog_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (nfcStatus == NfcHardwareStatus.NFC_DISABLED) {
        SetupPermissionRow(
          icon = Icons.Outlined.Nfc,
          title = stringResource(R.string.setup_perm_nfc_title),
          subtitle = stringResource(R.string.nfc_required_banner),
          isComplete = false,
          completeLabel = stringResource(R.string.setup_perm_nfc_ready),
          actionLabel = stringResource(R.string.setup_perm_nfc_off),
          onClick = onOpenNfcSettings,
          testTag = "setup_nfc_row",
        )
      }

      SetupPermissionRow(
        icon = Icons.Outlined.NotificationsActive,
        title = stringResource(R.string.setup_perm_notif_title),
        subtitle = stringResource(R.string.setup_perm_notif_desc),
        isComplete = areNotificationsEnabled,
        completeLabel = stringResource(R.string.setup_status_allowed),
        actionLabel = stringResource(R.string.setup_status_allow),
        onClick = onRequestNotifications,
        testTag = "setup_notifications_row",
      )

      SetupPermissionRow(
        icon = Icons.Outlined.BatteryChargingFull,
        title = stringResource(R.string.setup_perm_battery_title),
        subtitle = stringResource(R.string.setup_perm_battery_desc),
        isComplete = isIgnoringBatteryOptimizations,
        completeLabel = stringResource(R.string.setup_status_unrestricted),
        actionLabel = stringResource(R.string.setup_status_configure),
        onClick = onOpenBatterySettings,
        testTag = "setup_battery_row",
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = onSkip,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("setup_skip_button"),
        ) {
          Text(text = stringResource(R.string.setup_skip))
        }
        Button(
          onClick = onContinue,
          shape = MaterialTheme.shapes.small,
          modifier =
            Modifier.weight(1f).height(48.dp).testTag("setup_continue_button"),
        ) {
          Text(
            text = stringResource(R.string.setup_continue),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          )
        }
      }
    }
  }
}

@Composable
private fun SetupPermissionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  isComplete: Boolean,
  completeLabel: String,
  actionLabel: String,
  onClick: () -> Unit,
  testTag: String,
) {
  val extendedColors = LocalTagExtendedColors.current
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .minimumInteractiveComponentSize()
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, if (isComplete) extendedColors.signalActive else extendedColors.border)
        .clickable(onClick = onClick)
        .padding(12.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (isComplete) extendedColors.signalActive else MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Box(
      modifier =
        Modifier.background(
            if (isComplete) extendedColors.signalActiveContainer
            else MaterialTheme.colorScheme.surface
          )
          .border(1.dp, if (isComplete) extendedColors.signalActive else MaterialTheme.colorScheme.primary)
          .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
      Text(
        text = if (isComplete) completeLabel else actionLabel,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = if (isComplete) extendedColors.signalActive else MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
fun TwoWayTapInfoDialog(
  onDismiss: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, extendedColors.border)
          .padding(16.dp)
          .testTag("two_way_tap_info_dialog"),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = stringResource(R.string.two_way_info_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      ModeExplainCard(
        title = stringResource(R.string.two_way_info_off_title),
        body = stringResource(R.string.two_way_info_off_body),
      )
      ModeExplainCard(
        title = stringResource(R.string.two_way_info_dynamic_title),
        body = stringResource(R.string.two_way_info_dynamic_body),
      )
      ModeExplainCard(
        title = stringResource(R.string.two_way_info_true_p2p_title),
        body = stringResource(R.string.two_way_info_true_p2p_body),
      )

      Button(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.small,
        modifier =
          Modifier.fillMaxWidth().height(48.dp).testTag("two_way_info_close_button"),
      ) {
        Text(text = stringResource(R.string.action_got_it))
      }
    }
  }
}

@Composable
private fun ModeExplainCard(
  title: String,
  body: String,
) {
  val extendedColors = LocalTagExtendedColors.current
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, extendedColors.border)
        .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SettingsDivider() {
  val extendedColors = LocalTagExtendedColors.current
  HorizontalDivider(thickness = 1.dp, color = extendedColors.border.copy(alpha = 0.65f))
}

@Composable
private fun SettingsStatusActionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  statusText: String,
  statusOk: Boolean,
  onClick: () -> Unit,
  testTag: String,
) {
  val extendedColors = LocalTagExtendedColors.current
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clickable(onClick = onClick)
        .padding(vertical = 4.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = statusText,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = if (statusOk) extendedColors.signalActive else MaterialTheme.colorScheme.error,
      )
    }
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  content: @Composable () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, extendedColors.border)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.primary,
    )
    content()
  }
}

@Composable
private fun SettingsToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
  subtitle: String? = null,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clickable { onCheckedChange(!checked) }
        .padding(vertical = 2.dp)
        .testTag(testTag),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(
      modifier = Modifier.weight(1f).padding(end = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
    )
  }
}

@Composable
private fun SettingsActionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .minimumInteractiveComponentSize()
        .clickable(onClick = onClick)
        .padding(vertical = 4.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
fun PinWidgetDialog(
  onPinWidget: (WidgetType) -> Unit,
  onDismiss: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, extendedColors.border)
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Widgets,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text = stringResource(R.string.pin_dialog_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      Text(
        text = stringResource(R.string.pin_dialog_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Column(
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, extendedColors.border)
            .clickable { onPinWidget(WidgetType.TOGGLE) }
            .padding(12.dp)
            .testTag("pin_toggle_widget_option"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = stringResource(R.string.pin_widget_toggle_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(R.string.pin_widget_toggle_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Column(
        modifier =
          Modifier.fillMaxWidth()
            .minimumInteractiveComponentSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, extendedColors.border)
            .clickable { onPinWidget(WidgetType.PRESET) }
            .padding(12.dp)
            .testTag("pin_preset_widget_option"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = stringResource(R.string.pin_widget_preset_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(R.string.pin_widget_preset_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      OutlinedButton(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, extendedColors.border),
        modifier =
          Modifier.fillMaxWidth()
            .height(48.dp)
            .testTag("pin_widget_cancel_button"),
      ) {
        Text(text = stringResource(R.string.action_cancel))
      }
    }
  }
}
