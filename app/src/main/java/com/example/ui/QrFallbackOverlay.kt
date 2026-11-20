package com.example.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.nfc.NdefRecordDraft
import com.example.qr.QrCodeGenerator
import com.example.qr.QrModuleShape
import com.example.qr.QrPayloadFormatter
import com.example.ui.theme.LocalTagExtendedColors
import com.example.ui.theme.MonoBodyStyle

@Composable
fun FallbackQrDialog(
  record: NdefRecordDraft,
  label: String,
  foregroundArgb: Long,
  backgroundArgb: Long,
  moduleShape: QrModuleShape,
  allowCustomize: Boolean,
  onSelectColors: (fgArgb: Long, bgArgb: Long) -> Unit,
  onSelectShape: (QrModuleShape) -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    FallbackQrCard(
      record = record,
      label = label,
      foregroundArgb = foregroundArgb,
      backgroundArgb = backgroundArgb,
      moduleShape = moduleShape,
      allowCustomize = allowCustomize,
      onSelectColors = onSelectColors,
      onSelectShape = onSelectShape,
      onDismiss = onDismiss,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
    )
  }
}

@Composable
fun FallbackQrCard(
  record: NdefRecordDraft,
  label: String,
  foregroundArgb: Long,
  backgroundArgb: Long,
  moduleShape: QrModuleShape,
  allowCustomize: Boolean,
  onSelectColors: (fgArgb: Long, bgArgb: Long) -> Unit,
  onSelectShape: (QrModuleShape) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  extraFooterAction: (@Composable () -> Unit)? = null,
) {
  val extendedColors = LocalTagExtendedColors.current
  val context = LocalContext.current
  val payload = remember(record) { QrPayloadFormatter.formatRecord(record) }
  val matrix = remember(payload) { QrCodeGenerator.encode(payload) }

  // Gently boost screen brightness while scan-ready QR code is presented at exhibitions
  DisposableEffect(allowCustomize) {
    val window = (context as? Activity)?.window
    val prevBrightness = window?.attributes?.screenBrightness ?: -1f
    if (!allowCustomize && window != null) {
      val attrs = window.attributes
      if (attrs.screenBrightness < 0.85f) {
        attrs.screenBrightness = 0.85f
        window.attributes = attrs
      }
    }
    onDispose {
      if (!allowCustomize && window != null) {
        val attrs = window.attributes
        attrs.screenBrightness = prevBrightness
        window.attributes = attrs
      }
    }
  }

  val scrollState = rememberScrollState()
  val displayTitle = label.ifBlank { record.type.label }
  val contrast = remember(foregroundArgb, backgroundArgb) {
    QrCodeGenerator.contrastRatio(foregroundArgb, backgroundArgb)
  }
  val lowContrast = contrast < QrCodeGenerator.MIN_RECOMMENDED_CONTRAST

  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .widthIn(max = 460.dp)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, extendedColors.border)
        .testTag("fallback_qr_dialog"),
  ) {
    // Header strip matching VirtualCardPanel aesthetic
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            if (!allowCustomize) extendedColors.signalActiveContainer
            else MaterialTheme.colorScheme.surfaceVariant
          )
          .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = if (allowCustomize) Icons.Outlined.Palette else Icons.Outlined.QrCode2,
          contentDescription = null,
          tint =
            if (!allowCustomize) extendedColors.signalActive
            else MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text =
            stringResource(
              if (allowCustomize) R.string.qr_mode_customize_badge
              else R.string.qr_mode_scan_badge
            ),
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color =
            if (!allowCustomize) extendedColors.signalActive
            else MaterialTheme.colorScheme.primary,
        )
      }

      Box(
        modifier =
          Modifier.background(MaterialTheme.colorScheme.surface)
            .border(1.dp, extendedColors.border)
            .padding(horizontal = 8.dp, vertical = 3.dp),
      ) {
        Text(
          text = record.type.shortTag,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    HorizontalDivider(thickness = 1.dp, color = extendedColors.border)

    Column(
      modifier =
        Modifier.fillMaxWidth()
          .weight(1f, fill = false)
          .verticalScroll(scrollState)
          .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          text = displayTitle,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = record.summaryLine(),
          style = MonoBodyStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }

      // Live vector QR Canvas
      val cdQr = stringResource(R.string.cd_fallback_qr_code)
      if (matrix != null) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .widthIn(max = if (allowCustomize) 220.dp else 280.dp)
              .aspectRatio(1f)
              .border(1.dp, extendedColors.border)
              .testTag("qr_matrix_canvas")
              .semantics { contentDescription = cdQr },
        ) {
          QrCodeMatrixCanvas(
            matrix = matrix,
            foregroundColor = Color(foregroundArgb.toInt()),
            backgroundColor = Color(backgroundArgb.toInt()),
            moduleShape = moduleShape,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
          )
        }
      }

      Text(
        text =
          stringResource(
            if (allowCustomize) R.string.qr_customize_standby_hint
            else R.string.qr_scan_instruction
          ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (allowCustomize) {
        QrCustomizerPanel(
          foregroundArgb = foregroundArgb,
          backgroundArgb = backgroundArgb,
          moduleShape = moduleShape,
          lowContrast = lowContrast,
          onSelectColors = onSelectColors,
          onSelectShape = onSelectShape,
        )
      }
    }

    HorizontalDivider(thickness = 1.dp, color = extendedColors.border)

    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (extraFooterAction != null) {
        Box(modifier = Modifier.weight(1f)) {
          extraFooterAction()
        }
      }
      Button(
        onClick = onDismiss,
        shape = MaterialTheme.shapes.small,
        modifier =
          Modifier.weight(1f)
            .height(48.dp)
            .testTag("qr_dialog_close_button"),
      ) {
        Text(
          text =
            stringResource(
              if (allowCustomize) R.string.action_done else R.string.action_close
            ),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
      }
    }
  }
}

private enum class QrColorTarget {
  FOREGROUND,
  BACKGROUND,
}

@Composable
private fun QrCustomizerPanel(
  foregroundArgb: Long,
  backgroundArgb: Long,
  moduleShape: QrModuleShape,
  lowContrast: Boolean,
  onSelectColors: (fgArgb: Long, bgArgb: Long) -> Unit,
  onSelectShape: (QrModuleShape) -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  var activeCustomTarget by remember { mutableStateOf<QrColorTarget?>(null) }

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, extendedColors.border)
        .padding(12.dp)
        .testTag("qr_customizer_panel"),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (lowContrast) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .border(1.dp, MaterialTheme.colorScheme.error)
            .padding(8.dp)
            .testTag("qr_low_contrast_warning"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.WarningAmber,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = stringResource(R.string.qr_low_contrast_warning),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }

    // Code (foreground) color swatches + custom picker toggle
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.qr_foreground_label),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = QrCodeGenerator.formatHexColor(foregroundArgb),
        style = MonoBodyStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      QrCustomColorButton(
        selected = activeCustomTarget == QrColorTarget.FOREGROUND,
        cdText = stringResource(R.string.cd_custom_fg_color),
        onClick = {
          activeCustomTarget =
            if (activeCustomTarget == QrColorTarget.FOREGROUND) null else QrColorTarget.FOREGROUND
        },
        testTag = "qr_fg_custom_button",
      )
      QrCodeGenerator.FOREGROUND_SWATCHES.forEachIndexed { index, swatchArgb ->
        QrSwatchButton(
          swatchArgb = swatchArgb,
          selected = foregroundArgb == swatchArgb,
          cdText = stringResource(R.string.cd_color_swatch, index + 1),
          onClick = { onSelectColors(swatchArgb, backgroundArgb) },
          testTag = "qr_fg_swatch_$index",
        )
      }
    }
    if (activeCustomTarget == QrColorTarget.FOREGROUND) {
      QrHsvColorMixer(
        currentArgb = foregroundArgb,
        onColorChange = { newFg -> onSelectColors(newFg, backgroundArgb) },
      )
    }

    // Background color swatches + custom any-color picker toggle
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.qr_background_label),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = QrCodeGenerator.formatHexColor(backgroundArgb),
        style = MonoBodyStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      QrCustomColorButton(
        selected = activeCustomTarget == QrColorTarget.BACKGROUND,
        cdText = stringResource(R.string.cd_custom_bg_color),
        onClick = {
          activeCustomTarget =
            if (activeCustomTarget == QrColorTarget.BACKGROUND) null else QrColorTarget.BACKGROUND
        },
        testTag = "qr_bg_custom_button",
      )
      QrCodeGenerator.BACKGROUND_SWATCHES.forEachIndexed { index, swatchArgb ->
        QrSwatchButton(
          swatchArgb = swatchArgb,
          selected = backgroundArgb == swatchArgb,
          cdText = stringResource(R.string.cd_color_swatch, index + 1),
          onClick = { onSelectColors(foregroundArgb, swatchArgb) },
          testTag = "qr_bg_swatch_$index",
        )
      }
    }
    if (activeCustomTarget == QrColorTarget.BACKGROUND) {
      QrHsvColorMixer(
        currentArgb = backgroundArgb,
        onColorChange = { newBg -> onSelectColors(foregroundArgb, newBg) },
      )
    }

    // Module shape toggle
    Text(
      text = stringResource(R.string.qr_style_label),
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      QrModuleShape.entries.forEach { shape ->
        val selected = moduleShape == shape
        OutlinedButton(
          onClick = { onSelectShape(shape) },
          shape = MaterialTheme.shapes.small,
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
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
              .testTag("qr_shape_${shape.name.lowercase()}"),
        ) {
          Text(
            text =
              stringResource(
                if (shape == QrModuleShape.SQUARE) R.string.qr_style_square
                else R.string.qr_style_rounded
              ),
            style = MaterialTheme.typography.labelMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun QrCustomColorButton(
  selected: Boolean,
  cdText: String,
  onClick: () -> Unit,
  testTag: String,
) {
  val extendedColors = LocalTagExtendedColors.current
  val rainbowBrush =
    remember {
      Brush.sweepGradient(
        listOf(
          Color(0xFFFF3B30),
          Color(0xFFFF9500),
          Color(0xFFFFCC00),
          Color(0xFF34C759),
          Color(0xFF00C7BE),
          Color(0xFF007AFF),
          Color(0xFFAF52DE),
          Color(0xFFFF3B30),
        )
      )
    }

  Box(
    modifier =
      Modifier.size(48.dp)
        .border(
          width = if (selected) 2.dp else 1.dp,
          color = if (selected) MaterialTheme.colorScheme.primary else extendedColors.border,
        )
        .padding(4.dp)
        .background(rainbowBrush)
        .border(1.dp, extendedColors.border.copy(alpha = 0.6f))
        .clickable(onClick = onClick)
        .testTag(testTag)
        .semantics { contentDescription = cdText },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.size(22.dp)
          .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = Icons.Outlined.Colorize,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(14.dp),
      )
    }
  }
}

@Composable
private fun QrHsvColorMixer(
  currentArgb: Long,
  onColorChange: (Long) -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  val initialHsv = remember { QrCodeGenerator.argbToHsv(currentArgb) }
  var hue by remember { mutableFloatStateOf(initialHsv[0]) }
  var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
  var value by remember { mutableFloatStateOf(initialHsv[2]) }

  LaunchedEffect(currentArgb) {
    val currentComputed = QrCodeGenerator.hsvToArgb(hue, saturation, value)
    if (currentComputed != currentArgb) {
      val synced = QrCodeGenerator.argbToHsv(currentArgb)
      if (synced[1] > 0.01f && synced[2] > 0.01f) {
        hue = synced[0]
      }
      if (synced[2] > 0.01f) {
        saturation = synced[1]
      }
      value = synced[2]
    }
  }

  val hueGradient =
    remember {
      Brush.horizontalGradient(
        listOf(
          Color(0xFFFF0000),
          Color(0xFFFFFF00),
          Color(0xFF00FF00),
          Color(0xFF00FFFF),
          Color(0xFF0000FF),
          Color(0xFFFF00FF),
          Color(0xFFFF0000),
        )
      )
    }
  val pureHueColor = Color(QrCodeGenerator.hsvToArgb(hue, 1f, 1f).toInt())
  val fullSatColor = Color(QrCodeGenerator.hsvToArgb(hue, saturation, 1f).toInt())

  Column(
    modifier =
      Modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, extendedColors.border)
        .padding(10.dp)
        .testTag("qr_color_mixer"),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = "${stringResource(R.string.qr_custom_color)} · ${QrCodeGenerator.formatHexColor(currentArgb)}",
      style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface,
    )

    // Hue
    Text(
      text = stringResource(R.string.qr_hue_label),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(10.dp)
          .background(hueGradient)
          .border(1.dp, extendedColors.border.copy(alpha = 0.5f)),
    )
    Slider(
      value = hue,
      onValueChange = { nextHue ->
        hue = nextHue
        val nextSat = if (saturation < 0.05f && value > 0.95f) 0.85f else saturation
        saturation = nextSat
        onColorChange(QrCodeGenerator.hsvToArgb(nextHue, nextSat, value))
      },
      valueRange = 0f..360f,
      colors =
        SliderDefaults.colors(
          thumbColor = pureHueColor,
          activeTrackColor = pureHueColor,
        ),
      modifier = Modifier.fillMaxWidth().testTag("qr_hue_slider"),
    )

    // Saturation
    Text(
      text = stringResource(R.string.qr_saturation_label),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(10.dp)
          .background(
            Brush.horizontalGradient(
              listOf(
                Color(QrCodeGenerator.hsvToArgb(hue, 0f, value).toInt()),
                Color(QrCodeGenerator.hsvToArgb(hue, 1f, value).toInt()),
              )
            )
          )
          .border(1.dp, extendedColors.border.copy(alpha = 0.5f)),
    )
    Slider(
      value = saturation,
      onValueChange = { nextSat ->
        saturation = nextSat
        onColorChange(QrCodeGenerator.hsvToArgb(hue, nextSat, value))
      },
      valueRange = 0f..1f,
      modifier = Modifier.fillMaxWidth().testTag("qr_sat_slider"),
    )

    // Brightness
    Text(
      text = stringResource(R.string.qr_brightness_label),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(10.dp)
          .background(
            Brush.horizontalGradient(
              listOf(Color.Black, fullSatColor)
            )
          )
          .border(1.dp, extendedColors.border.copy(alpha = 0.5f)),
    )
    Slider(
      value = value,
      onValueChange = { nextVal ->
        value = nextVal
        onColorChange(QrCodeGenerator.hsvToArgb(hue, saturation, nextVal))
      },
      valueRange = 0f..1f,
      modifier = Modifier.fillMaxWidth().testTag("qr_val_slider"),
    )
  }
}

@Composable
private fun QrSwatchButton(
  swatchArgb: Long,
  selected: Boolean,
  cdText: String,
  onClick: () -> Unit,
  testTag: String,
) {
  val extendedColors = LocalTagExtendedColors.current
  val swatchColor = Color(swatchArgb.toInt())
  val checkTint =
    if (QrCodeGenerator.contrastRatio(0xFF111315L, swatchArgb) >= 4.5f) {
      Color(0xFF111315)
    } else {
      Color.White
    }

  Box(
    modifier =
      Modifier.size(48.dp)
        .border(
          width = if (selected) 2.dp else 1.dp,
          color = if (selected) MaterialTheme.colorScheme.primary else extendedColors.border,
        )
        .padding(4.dp)
        .background(swatchColor)
        .border(1.dp, extendedColors.border.copy(alpha = 0.6f))
        .clickable(onClick = onClick)
        .testTag(testTag)
        .semantics { contentDescription = cdText },
    contentAlignment = Alignment.Center,
  ) {
    if (selected) {
      Icon(
        imageVector = Icons.Outlined.Check,
        contentDescription = null,
        tint = checkTint,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
fun QrCodeMatrixCanvas(
  matrix: com.example.qr.QrMatrix,
  foregroundColor: Color,
  backgroundColor: Color,
  moduleShape: QrModuleShape,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    drawRect(color = backgroundColor)
    val quietModules = 3f
    val totalModules = matrix.size + quietModules * 2f
    val cellSize = size.minDimension / totalModules
    val overlap = if (moduleShape == QrModuleShape.SQUARE) 0.5f else 0f
    val roundRadius =
      if (moduleShape == QrModuleShape.ROUNDED) CornerRadius(cellSize * 0.36f, cellSize * 0.36f)
      else CornerRadius.Zero

    for (y in 0 until matrix.size) {
      for (x in 0 until matrix.size) {
        if (!matrix.get(x, y)) continue
        val isFinderArea =
          (x < 7 && y < 7) ||
            (x >= matrix.size - 7 && y < 7) ||
            (x < 7 && y >= matrix.size - 7)
        val left = (x + quietModules) * cellSize
        val top = (y + quietModules) * cellSize
        if (moduleShape == QrModuleShape.SQUARE || isFinderArea) {
          drawRect(
            color = foregroundColor,
            topLeft = Offset(left, top),
            size = Size(cellSize + 0.5f, cellSize + 0.5f),
          )
        } else {
          val inset = cellSize * 0.06f
          drawRoundRect(
            color = foregroundColor,
            topLeft = Offset(left + inset, top + inset),
            size = Size(cellSize - inset * 2f + overlap, cellSize - inset * 2f + overlap),
            cornerRadius = roundRadius,
          )
        }
      }
    }
  }
}
