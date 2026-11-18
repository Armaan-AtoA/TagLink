package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.nfc.NfcHardwareStatus
import com.example.nfc.TagScanError
import com.example.ui.theme.LocalTagExtendedColors
import com.example.ui.theme.MonoBodyStyle

@Composable
fun CopyTagScreen(
  state: TagUiState,
  onLabelChanged: (String) -> Unit,
  onReadOnlyChanged: (Boolean) -> Unit,
  onSaveCopiedTag: () -> Unit,
  onScanAgain: () -> Unit,
  onOpenNfcSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val extendedColors = LocalTagExtendedColors.current
  val scanned = state.copyState.scannedResult

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (state.nfcStatus != NfcHardwareStatus.READY) {
      item {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.errorContainer)
              .border(1.dp, MaterialTheme.colorScheme.error)
              .padding(14.dp)
              .testTag("copy_nfc_disabled_banner"),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text =
              if (state.nfcStatus == NfcHardwareStatus.NFC_DISABLED)
                stringResource(R.string.copy_nfc_off_hint)
              else stringResource(R.string.nfc_unsupported_banner),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onErrorContainer,
          )
          if (state.nfcStatus == NfcHardwareStatus.NFC_DISABLED) {
            OutlinedButton(
              onClick = onOpenNfcSettings,
              shape = MaterialTheme.shapes.small,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
              modifier = Modifier.height(48.dp).testTag("copy_open_nfc_button"),
            ) {
              Text(
                text = stringResource(R.string.action_turn_on_nfc),
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
        }
      }
    }

    if (scanned == null) {
      item {
        val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
        val wave by
          infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
              infiniteRepeatable(
                animation = tween(1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
              ),
            label = "scan_wave",
          )
        val primary = MaterialTheme.colorScheme.primary
        val activeScan = state.nfcStatus == NfcHardwareStatus.READY

        Column(
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, if (activeScan) primary else extendedColors.border)
              .padding(20.dp)
              .testTag("copy_scanning_card"),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center,
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val baseRadius = size.minDimension * 0.28f
              val maxExpand = size.minDimension * 0.20f
              if (activeScan) {
                drawCircle(
                  color = primary.copy(alpha = (1f - wave) * 0.65f),
                  radius = baseRadius + maxExpand * wave,
                  style = Stroke(width = 2.dp.toPx()),
                )
              }
              drawCircle(
                color = if (activeScan) primary else extendedColors.border,
                radius = baseRadius,
                style = Stroke(width = 1.5.dp.toPx()),
              )
            }
            Icon(
              imageVector = Icons.Outlined.Nfc,
              contentDescription = null,
              tint = if (activeScan) primary else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(28.dp),
            )
          }

          Text(
            text = stringResource(R.string.copy_scanning_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = stringResource(R.string.copy_scanning_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }

      val err = state.copyState.scanError
      if (err != null) {
        item {
          val errMsg =
            when (err) {
              TagScanError.NOT_NDEF_FORMATTED -> stringResource(R.string.copy_err_not_ndef)
              TagScanError.EMPTY_TAG -> stringResource(R.string.copy_err_empty)
              TagScanError.PAYLOAD_TOO_LARGE -> stringResource(R.string.copy_err_too_large)
              TagScanError.READ_FAILED -> stringResource(R.string.copy_err_read_failed)
            }
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer)
                .border(1.dp, MaterialTheme.colorScheme.error)
                .padding(12.dp),
          ) {
            Text(
              text = errMsg,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }

      item {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, extendedColors.border)
              .padding(12.dp),
        ) {
          Text(
            text = stringResource(R.string.copy_or_duplicate_tip),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      item {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.surface)
              .border(1.dp, extendedColors.signalActive)
              .padding(14.dp)
              .testTag("copy_scanned_result_card"),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.CheckCircleOutline,
                contentDescription = null,
                tint = extendedColors.signalActive,
                modifier = Modifier.size(20.dp),
              )
              Text(
                text = stringResource(R.string.copy_result_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text = "${scanned.ndefBytes.size} B",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          Row(
            modifier =
              Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, extendedColors.border)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = scanned.record?.type?.shortTag ?: "RAW",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = scanned.summary,
              style = MonoBodyStyle,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
          }

          OutlinedTextField(
            value = state.copyState.copyLabelDraft,
            onValueChange = onLabelChanged,
            label = { Text(stringResource(R.string.label_tag_name_input)) },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().testTag("copy_label_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            listOf(true to R.string.label_lock_readonly, false to R.string.label_lock_writable)
              .forEach { (isRo, labelRes) ->
                val selected = state.copyState.copyReadOnly == isRo
                Surface(
                  onClick = { onReadOnlyChanged(isRo) },
                  shape = MaterialTheme.shapes.small,
                  color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                  border =
                    BorderStroke(
                      1.dp,
                      if (selected) MaterialTheme.colorScheme.primary else extendedColors.border,
                    ),
                  modifier = Modifier.weight(1f).height(48.dp),
                ) {
                  Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(
                      imageVector = if (isRo) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                      contentDescription = null,
                      tint =
                        if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(15.dp),
                    )
                    Text(
                      text = " ${stringResource(labelRes)}",
                      style = MaterialTheme.typography.labelMedium,
                      color =
                        if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onScanAgain,
              shape = MaterialTheme.shapes.small,
              border = BorderStroke(1.dp, extendedColors.border),
              modifier = Modifier.weight(0.4f).height(48.dp).testTag("copy_rescan_button"),
            ) {
              Text(text = stringResource(R.string.copy_scan_again))
            }
            Button(
              onClick = onSaveCopiedTag,
              shape = MaterialTheme.shapes.small,
              modifier = Modifier.weight(0.6f).height(48.dp).testTag("copy_save_tag_button"),
            ) {
              Text(
                text = stringResource(R.string.copy_save_and_use),
                style = MaterialTheme.typography.titleMedium,
              )
            }
          }
        }
      }
    }
  }
}
