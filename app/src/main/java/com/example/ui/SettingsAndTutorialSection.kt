package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.AppSettings
import com.example.data.ThemeMode
import com.example.ui.theme.LocalTagExtendedColors
import com.example.widget.WidgetType

@Composable
fun SettingsScreen(
  settings: AppSettings,
  onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
  onOpenNfcSettings: () -> Unit,
  onOpenPinWidget: () -> Unit,
  onRequestAddQuickTile: () -> Unit,
  onReplayTutorial: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extendedColors = LocalTagExtendedColors.current

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
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

        HorizontalDivider(thickness = 1.dp, color = extendedColors.border)

        SettingsToggleRow(
          label = stringResource(R.string.settings_auto_activate_write),
          checked = settings.autoActivateOnWrite,
          onCheckedChange = { v -> onUpdateSettings { it.copy(autoActivateOnWrite = v) } },
          testTag = "setting_auto_activate_write",
        )
        HorizontalDivider(thickness = 1.dp, color = extendedColors.border)
        SettingsToggleRow(
          label = stringResource(R.string.settings_auto_activate_select),
          checked = settings.autoActivateOnSelect,
          onCheckedChange = { v -> onUpdateSettings { it.copy(autoActivateOnSelect = v) } },
          testTag = "setting_auto_activate_select",
        )
        HorizontalDivider(thickness = 1.dp, color = extendedColors.border)
        SettingsToggleRow(
          label = stringResource(R.string.settings_prefer_foreground),
          checked = settings.preferForegroundHce,
          onCheckedChange = { v -> onUpdateSettings { it.copy(preferForegroundHce = v) } },
          testTag = "setting_prefer_foreground",
        )
        HorizontalDivider(thickness = 1.dp, color = extendedColors.border)
        SettingsToggleRow(
          label = stringResource(R.string.settings_stop_on_leave),
          checked = settings.stopOnLeaveApp,
          onCheckedChange = { v -> onUpdateSettings { it.copy(stopOnLeaveApp = v) } },
          testTag = "setting_stop_on_leave",
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
        HorizontalDivider(thickness = 1.dp, color = extendedColors.border)
        SettingsActionRow(
          icon = Icons.Outlined.Widgets,
          label = stringResource(R.string.settings_pin_widget),
          onClick = onOpenPinWidget,
          testTag = "settings_pin_widget_button",
        )
        HorizontalDivider(thickness = 1.dp, color = extendedColors.border)
        SettingsActionRow(
          icon = Icons.Outlined.AddBox,
          label = stringResource(R.string.settings_add_qs_tile),
          onClick = onRequestAddQuickTile,
          testTag = "settings_add_qs_tile_button",
        )
        HorizontalDivider(thickness = 1.dp, color = extendedColors.border)
        SettingsActionRow(
          icon = Icons.Outlined.Info,
          label = stringResource(R.string.settings_replay_tutorial),
          onClick = onReplayTutorial,
          testTag = "settings_replay_tutorial_button",
        )
      }
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
        .padding(12.dp),
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
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f).padding(end = 12.dp),
    )
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
        .padding(vertical = 6.dp)
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
