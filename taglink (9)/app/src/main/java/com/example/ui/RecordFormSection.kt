package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.nfc.WifiAuthMode
import com.example.ui.theme.LocalTagExtendedColors
import com.example.ui.theme.MonoBodyStyle

private val URL_PREFIXES =
  listOf(
    "https://" to "https://",
    "LinkedIn" to "https://linkedin.com/in/",
    "X" to "https://x.com/",
    "GitHub" to "https://github.com/",
    "YouTube" to "https://youtube.com/@",
  )

private val APP_PRESETS =
  listOf(
    "Instagram" to "com.instagram.android",
    "WhatsApp" to "com.whatsapp",
    "LinkedIn" to "com.linkedin.android",
    "Spotify" to "com.spotify.music",
  )

private fun presetIcon(preset: TagPresetKind): ImageVector =
  when (preset) {
    TagPresetKind.SOCIALS -> Icons.Outlined.Share
    TagPresetKind.LINK -> Icons.Outlined.Language
    TagPresetKind.CONTACT -> Icons.Outlined.Badge
    TagPresetKind.WIFI -> Icons.Outlined.Wifi
    TagPresetKind.PHONE -> Icons.Outlined.Phone
    TagPresetKind.SMS -> Icons.Outlined.Sms
    TagPresetKind.EMAIL -> Icons.Outlined.Email
    TagPresetKind.TEXT -> Icons.Outlined.TextFields
    TagPresetKind.GEO -> Icons.Outlined.LocationOn
    TagPresetKind.APP -> Icons.Outlined.Apps
    TagPresetKind.MIME -> Icons.Outlined.Code
  }

private fun socialIcon(platform: SocialPlatform): ImageVector =
  when (platform) {
    SocialPlatform.INSTAGRAM -> Icons.Outlined.CameraAlt
    SocialPlatform.WHATSAPP -> Icons.AutoMirrored.Outlined.Chat
    SocialPlatform.TIKTOK -> Icons.Outlined.MusicNote
    SocialPlatform.X -> Icons.Outlined.AlternateEmail
    SocialPlatform.LINKEDIN -> Icons.Outlined.WorkOutline
    SocialPlatform.YOUTUBE -> Icons.Outlined.PlayCircleOutline
    SocialPlatform.THREADS -> Icons.Outlined.Forum
    SocialPlatform.SNAPCHAT -> Icons.Outlined.Person
    SocialPlatform.GITHUB -> Icons.Outlined.Code
    SocialPlatform.FACEBOOK -> Icons.Outlined.Public
    SocialPlatform.DISCORD -> Icons.Outlined.Group
    SocialPlatform.TWITCH -> Icons.Outlined.LiveTv
  }

@Composable
fun RecordComposerSection(
  state: TagUiState,
  onSelectPreset: (TagPresetKind) -> Unit,
  onSelectSocialPlatform: (SocialPlatform) -> Unit,
  onChangeMakeType: () -> Unit,
  onOpenCopyTag: () -> Unit,
  onPickContact: () -> Unit,
  onUpdateForm: ((RecordFormState) -> RecordFormState) -> Unit,
  onCardLabelChanged: (String) -> Unit,
  onMakeReadOnlyChanged: (Boolean) -> Unit,
  onApplyUrlPrefix: (String, String) -> Unit,
  onApplyAppPreset: (String) -> Unit,
  onWriteCard: () -> Unit,
  onClearForm: () -> Unit,
) {
  when (state.form.makeStep) {
    MakeStep.CHOOSE_TYPE -> {
      ChoosePresetStep(
        onSelectPreset = onSelectPreset,
        onOpenCopyTag = onOpenCopyTag,
      )
    }

    MakeStep.CHOOSE_SOCIAL -> {
      ChooseSocialStep(
        onSelectSocial = onSelectSocialPlatform,
        onBackToTypes = onChangeMakeType,
      )
    }

    MakeStep.FILL_DETAILS -> {
      FillDetailsStep(
        state = state,
        onChangeMakeType = onChangeMakeType,
        onPickContact = onPickContact,
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

@Composable
private fun ChoosePresetStep(
  onSelectPreset: (TagPresetKind) -> Unit,
  onOpenCopyTag: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  val rows = TagPresetKind.entries.chunked(2)

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = stringResource(R.string.make_step1_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
      )
      Text(
        text = stringResource(R.string.make_step1_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    rows.forEach { pair ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        pair.forEach { preset ->
          Surface(
            onClick = { onSelectPreset(preset) },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extendedColors.border),
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("preset_${preset.name.lowercase()}"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = presetIcon(preset),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
              )
              Text(
                text = preset.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = preset.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        if (pair.size == 1) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }

    OutlinedButton(
      onClick = onOpenCopyTag,
      shape = MaterialTheme.shapes.small,
      border = BorderStroke(1.dp, extendedColors.border),
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag("make_copy_instead_button"),
    ) {
      Icon(
        imageVector = Icons.Outlined.ContentCopy,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = "  ${stringResource(R.string.make_copy_instead)}",
        style = MaterialTheme.typography.bodyMedium,
      )
    }
  }
}

@Composable
private fun ChooseSocialStep(
  onSelectSocial: (SocialPlatform) -> Unit,
  onBackToTypes: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  val rows = SocialPlatform.entries.chunked(2)

  Column(
    modifier = Modifier.fillMaxWidth().testTag("choose_social_step"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = stringResource(R.string.make_social_step_title),
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = stringResource(R.string.make_social_step_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      TextButton(
        onClick = onBackToTypes,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("make_social_back_button"),
      ) {
        Text(text = stringResource(R.string.make_step2_change_category))
      }
    }

    rows.forEach { pair ->
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        pair.forEach { platform ->
          Surface(
            onClick = { onSelectSocial(platform) },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, extendedColors.border),
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("social_${platform.name.lowercase()}"),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = socialIcon(platform),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
              )
              Text(
                text = platform.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = platform.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        if (pair.size == 1) {
          Spacer(modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun FillDetailsStep(
  state: TagUiState,
  onChangeMakeType: () -> Unit,
  onPickContact: () -> Unit,
  onUpdateForm: ((RecordFormState) -> RecordFormState) -> Unit,
  onCardLabelChanged: (String) -> Unit,
  onMakeReadOnlyChanged: (Boolean) -> Unit,
  onApplyUrlPrefix: (String, String) -> Unit,
  onApplyAppPreset: (String) -> Unit,
  onWriteCard: () -> Unit,
  onClearForm: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  val preset = state.form.selectedPreset
  val isSocial = preset == TagPresetKind.SOCIALS
  val headerIcon =
    if (isSocial) socialIcon(state.form.selectedSocial) else presetIcon(preset)
  val headerTitle =
    if (isSocial) state.form.selectedSocial.title else preset.title
  val headerSubtitle =
    if (isSocial) state.form.selectedSocial.subtitle else preset.subtitle

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, extendedColors.border)
        .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f),
      ) {
        Box(
          modifier =
            Modifier.size(36.dp)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, extendedColors.border),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = headerIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
          )
        }
        Column {
          Text(
            text = headerTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = headerSubtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      TextButton(
        onClick = onChangeMakeType,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("make_change_type_button"),
      ) {
        Text(
          text =
            stringResource(
              if (isSocial) R.string.make_step2_change_social
              else R.string.make_step2_change_type
            )
        )
      }
    }

    HorizontalDivider(thickness = 1.dp, color = extendedColors.border)

    PresetFields(
      form = state.form,
      onPickContact = onPickContact,
      onUpdateForm = onUpdateForm,
      onApplyUrlPrefix = onApplyUrlPrefix,
      onApplyAppPreset = onApplyAppPreset,
    )

    if (state.inputError != null) {
      Text(
        text = state.inputError,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("form_input_error"),
      )
    }

    OutlinedTextField(
      value = state.cardLabelDraft,
      onValueChange = onCardLabelChanged,
      label = { Text(stringResource(R.string.label_tag_name_input)) },
      singleLine = true,
      shape = MaterialTheme.shapes.small,
      modifier = Modifier.fillMaxWidth().testTag("link_label_input"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = stringResource(R.string.make_protection_title),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LockOptionChip(
          label = stringResource(R.string.label_lock_readonly),
          icon = Icons.Outlined.Lock,
          selected = state.makeReadOnly,
          onClick = { onMakeReadOnlyChanged(true) },
          modifier = Modifier.weight(1f).testTag("make_lock_readonly"),
        )
        LockOptionChip(
          label = stringResource(R.string.label_lock_writable),
          icon = Icons.Outlined.LockOpen,
          selected = !state.makeReadOnly,
          onClick = { onMakeReadOnlyChanged(false) },
          modifier = Modifier.weight(1f).testTag("make_lock_writable"),
        )
      }
      Text(
        text =
          stringResource(
            if (state.makeReadOnly) R.string.lock_readonly_desc else R.string.lock_writable_desc
          ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = onClearForm,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, extendedColors.border),
        modifier =
          Modifier.weight(0.38f)
            .height(48.dp)
            .testTag("clear_form_button"),
      ) {
        Text(
          text = stringResource(R.string.action_clear_inputs),
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      Button(
        onClick = onWriteCard,
        shape = MaterialTheme.shapes.small,
        modifier =
          Modifier.weight(0.62f)
            .height(48.dp)
            .testTag("write_card_button"),
      ) {
        Text(
          text = stringResource(R.string.action_write_card),
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }
  }
}

@Composable
private fun LockOptionChip(
  label: String,
  icon: ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extendedColors = LocalTagExtendedColors.current
  Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.small,
    color =
      if (selected) MaterialTheme.colorScheme.primary
      else MaterialTheme.colorScheme.surfaceVariant,
    border =
      BorderStroke(
        1.dp,
        if (selected) MaterialTheme.colorScheme.primary else extendedColors.border,
      ),
    modifier = modifier.height(48.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint =
          if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(15.dp),
      )
      Text(
        text = " $label",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color =
          if (selected) MaterialTheme.colorScheme.onPrimary
          else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun PresetFields(
  form: RecordFormState,
  onPickContact: () -> Unit,
  onUpdateForm: ((RecordFormState) -> RecordFormState) -> Unit,
  onApplyUrlPrefix: (String, String) -> Unit,
  onApplyAppPreset: (String) -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  when (form.selectedPreset) {
    TagPresetKind.SOCIALS -> {
      val platform = form.selectedSocial
      OutlinedTextField(
        value = form.socialHandleInput,
        onValueChange = { v -> onUpdateForm { it.copy(socialHandleInput = v) } },
        label = {
          Text(
            if (platform.isPhoneBased) "${platform.title} phone number"
            else "${platform.title} username or link"
          )
        },
        placeholder = { Text(platform.placeholder) },
        singleLine = true,
        textStyle = MonoBodyStyle,
        keyboardOptions =
          KeyboardOptions(
            keyboardType = if (platform.isPhoneBased) KeyboardType.Phone else KeyboardType.Uri
          ),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("social_handle_input"),
      )
      val previewUrl = platform.buildProfileUrl(form.socialHandleInput)
      if (previewUrl.isNotBlank()) {
        Text(
          text = "Link: $previewUrl",
          style = MonoBodyStyle,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }

    TagPresetKind.LINK -> {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        URL_PREFIXES.forEach { (title, prefix) ->
          Surface(
            onClick = { onApplyUrlPrefix(prefix, if (title == "https://") "" else title) },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, extendedColors.border),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
              Text(text = title, style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
      OutlinedTextField(
        value = form.urlInput,
        onValueChange = { v -> onUpdateForm { it.copy(urlInput = v) } },
        label = { Text("Website or profile URL") },
        placeholder = { Text("https://example.com") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().testTag("link_url_input"),
      )
    }

    TagPresetKind.TEXT -> {
      OutlinedTextField(
        value = form.textLang,
        onValueChange = { v -> onUpdateForm { it.copy(textLang = v) } },
        label = { Text("Language code") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("text_lang_input"),
      )
      OutlinedTextField(
        value = form.textInput,
        onValueChange = { v -> onUpdateForm { it.copy(textInput = v) } },
        label = { Text("Text message") },
        minLines = 2,
        maxLines = 4,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("text_body_input"),
      )
    }

    TagPresetKind.CONTACT -> {
      OutlinedButton(
        onClick = onPickContact,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, extendedColors.border),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pick_contact_button"),
      ) {
        Icon(
          imageVector = Icons.Outlined.Contacts,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = "  ${stringResource(R.string.action_pick_contact)}",
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      OutlinedTextField(
        value = form.contactName,
        onValueChange = { v -> onUpdateForm { it.copy(contactName = v) } },
        label = { Text("Full name") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("contact_name_input"),
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = form.contactOrg,
          onValueChange = { v -> onUpdateForm { it.copy(contactOrg = v) } },
          label = { Text("Company") },
          singleLine = true,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = form.contactTitle,
          onValueChange = { v -> onUpdateForm { it.copy(contactTitle = v) } },
          label = { Text("Job title") },
          singleLine = true,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.weight(1f),
        )
      }
      OutlinedTextField(
        value = form.contactPhone,
        onValueChange = { v -> onUpdateForm { it.copy(contactPhone = v) } },
        label = { Text("Phone") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("contact_phone_input"),
      )
      OutlinedTextField(
        value = form.contactEmail,
        onValueChange = { v -> onUpdateForm { it.copy(contactEmail = v) } },
        label = { Text("Email") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("contact_email_input"),
      )
      OutlinedTextField(
        value = form.contactWebsite,
        onValueChange = { v -> onUpdateForm { it.copy(contactWebsite = v) } },
        label = { Text("Website") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = form.contactNote,
        onValueChange = { v -> onUpdateForm { it.copy(contactNote = v) } },
        label = { Text("Note") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    TagPresetKind.WIFI -> {
      OutlinedTextField(
        value = form.wifiSsid,
        onValueChange = { v -> onUpdateForm { it.copy(wifiSsid = v) } },
        label = { Text("Wi-Fi name (SSID)") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("wifi_ssid_input"),
      )
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        WifiAuthMode.entries.forEach { mode ->
          val selected = form.wifiAuth == mode
          Surface(
            onClick = { onUpdateForm { it.copy(wifiAuth = mode) } },
            shape = MaterialTheme.shapes.small,
            color =
              if (selected) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceVariant,
            border =
              BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else extendedColors.border,
              ),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
              Text(
                text = mode.label,
                style = MaterialTheme.typography.labelMedium,
                color =
                  if (selected) MaterialTheme.colorScheme.onPrimary
                  else MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
      if (form.wifiAuth != WifiAuthMode.OPEN) {
        OutlinedTextField(
          value = form.wifiPassword,
          onValueChange = { v -> onUpdateForm { it.copy(wifiPassword = v) } },
          label = { Text("Password") },
          singleLine = true,
          textStyle = MonoBodyStyle,
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.fillMaxWidth().testTag("wifi_password_input"),
        )
      }
    }

    TagPresetKind.EMAIL -> {
      OutlinedTextField(
        value = form.emailAddress,
        onValueChange = { v -> onUpdateForm { it.copy(emailAddress = v) } },
        label = { Text("Recipient email") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("email_address_input"),
      )
      OutlinedTextField(
        value = form.emailSubject,
        onValueChange = { v -> onUpdateForm { it.copy(emailSubject = v) } },
        label = { Text("Subject") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = form.emailBody,
        onValueChange = { v -> onUpdateForm { it.copy(emailBody = v) } },
        label = { Text("Body") },
        minLines = 2,
        maxLines = 4,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    TagPresetKind.PHONE -> {
      OutlinedTextField(
        value = form.phoneNumber,
        onValueChange = { v -> onUpdateForm { it.copy(phoneNumber = v) } },
        label = { Text("Phone number") },
        placeholder = { Text("+1 555 123 4567") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("phone_number_input"),
      )
    }

    TagPresetKind.SMS -> {
      OutlinedTextField(
        value = form.smsNumber,
        onValueChange = { v -> onUpdateForm { it.copy(smsNumber = v) } },
        label = { Text("Phone number") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("sms_number_input"),
      )
      OutlinedTextField(
        value = form.smsMessage,
        onValueChange = { v -> onUpdateForm { it.copy(smsMessage = v) } },
        label = { Text("Message") },
        minLines = 2,
        maxLines = 3,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    TagPresetKind.GEO -> {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = form.geoLat,
          onValueChange = { v -> onUpdateForm { it.copy(geoLat = v) } },
          label = { Text("Latitude") },
          singleLine = true,
          textStyle = MonoBodyStyle,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.weight(1f).testTag("geo_lat_input"),
        )
        OutlinedTextField(
          value = form.geoLon,
          onValueChange = { v -> onUpdateForm { it.copy(geoLon = v) } },
          label = { Text("Longitude") },
          singleLine = true,
          textStyle = MonoBodyStyle,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          shape = MaterialTheme.shapes.small,
          modifier = Modifier.weight(1f).testTag("geo_lon_input"),
        )
      }
      OutlinedTextField(
        value = form.geoLabel,
        onValueChange = { v -> onUpdateForm { it.copy(geoLabel = v) } },
        label = { Text("Place name (optional)") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    TagPresetKind.APP -> {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        APP_PRESETS.forEach { (name, pkg) ->
          Surface(
            onClick = { onApplyAppPreset(pkg) },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, extendedColors.border),
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
              Text(text = name, style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
      OutlinedTextField(
        value = form.appPackage,
        onValueChange = { v -> onUpdateForm { it.copy(appPackage = v) } },
        label = { Text("Android package name") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("app_package_input"),
      )
    }

    TagPresetKind.MIME -> {
      OutlinedTextField(
        value = form.mimeType,
        onValueChange = { v -> onUpdateForm { it.copy(mimeType = v) } },
        label = { Text("MIME type") },
        singleLine = true,
        textStyle = MonoBodyStyle,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("mime_type_input"),
      )
      OutlinedTextField(
        value = form.mimePayload,
        onValueChange = { v -> onUpdateForm { it.copy(mimePayload = v) } },
        label = { Text("Data") },
        minLines = 2,
        maxLines = 4,
        textStyle = MonoBodyStyle,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag("mime_payload_input"),
      )
    }
  }
}
