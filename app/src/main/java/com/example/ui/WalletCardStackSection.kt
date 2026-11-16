package com.example.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CardCoverImageHelper
import com.example.data.SavedLinkEntity
import com.example.nfc.NdefMessageCodec
import com.example.nfc.NdefRecordType
import com.example.ui.theme.LocalTagExtendedColors
import com.example.ui.theme.MonoBodyStyle
import kotlin.math.roundToInt

private val CARD_FRONT_HEIGHT = 188.dp
private val STACKED_PEEK_HEIGHT = 76.dp
private val EXPANDED_GAP = 10.dp

@Composable
fun WalletCardStack(
  cards: List<SavedLinkEntity>,
  activeNdefBytes: ByteArray,
  onLoadCard: (SavedLinkEntity) -> Unit,
  onPickPhoto: (SavedLinkEntity) -> Unit,
  onRemovePhoto: (SavedLinkEntity) -> Unit,
  onDuplicateCard: (SavedLinkEntity) -> Unit,
  onDeleteCard: (Long) -> Unit,
) {
  if (cards.isEmpty()) return

  val activeMatchingId =
    cards.firstOrNull { it.ndefBytes.contentEquals(activeNdefBytes) && activeNdefBytes.isNotEmpty() }?.id
  var focusedCardId by remember { mutableStateOf(activeMatchingId ?: cards.first().id) }

  LaunchedEffect(cards, activeMatchingId) {
    val stillExists = cards.any { it.id == focusedCardId }
    if (!stillExists) {
      focusedCardId = activeMatchingId ?: cards.first().id
    }
  }

  val stepSpacingPxList =
    cards.mapIndexed { index, card ->
      val isFocused = card.id == focusedCardId
      val isLast = index == cards.lastIndex
      val targetPeek =
        if (isFocused || isLast) {
          null
        } else {
          STACKED_PEEK_HEIGHT
        }
      val animatedPeek by
        animateDpAsState(
          targetValue = targetPeek ?: CARD_FRONT_HEIGHT,
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioNoBouncy,
              stiffness = Spring.StiffnessMediumLow,
            ),
          label = "wallet_card_step_${card.id}",
        )
      Pair(isFocused, if (targetPeek != null) animatedPeek else null)
    }

  Column(
    modifier = Modifier.fillMaxWidth().testTag("wallet_card_stack"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Layout(
      content = {
        cards.forEach { card ->
          val isActive =
            card.ndefBytes.contentEquals(activeNdefBytes) && card.ndefBytes.isNotEmpty()
          val isFocused = card.id == focusedCardId
          WalletCardItem(
            card = card,
            isActive = isActive,
            isFocused = isFocused,
            onCardClick = {
              focusedCardId = card.id
              onLoadCard(card)
            },
            onPickPhoto = { onPickPhoto(card) },
            onRemovePhoto = { onRemovePhoto(card) },
            onDuplicate = { onDuplicateCard(card) },
            onDelete = { onDeleteCard(card.id) },
          )
        }
      },
      modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
      val itemConstraints = constraints.copy(minHeight = 0)
      val placeables = measurables.map { it.measure(itemConstraints) }
      val yPositions = IntArray(placeables.size)
      var currentY = 0
      val expandedGapPx = EXPANDED_GAP.toPx().roundToInt()

      placeables.forEachIndexed { index, placeable ->
        yPositions[index] = currentY
        val (isFocused, animatedPeekDp) = stepSpacingPxList[index]
        val isLast = index == placeables.lastIndex
        currentY +=
          when {
            isLast -> placeable.height
            isFocused -> placeable.height + expandedGapPx
            else -> animatedPeekDp?.toPx()?.roundToInt() ?: (placeable.height + expandedGapPx)
          }
      }

      layout(constraints.maxWidth, currentY.coerceAtLeast(0)) {
        placeables.forEachIndexed { index, placeable ->
          placeable.placeRelative(x = 0, y = yPositions[index], zIndex = index.toFloat())
        }
      }
    }

    if (cards.size > 1) {
      Text(
        text = stringResource(R.string.wallet_tap_to_expand),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun WalletCardItem(
  card: SavedLinkEntity,
  isActive: Boolean,
  isFocused: Boolean,
  onCardClick: () -> Unit,
  onPickPhoto: () -> Unit,
  onRemovePhoto: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
) {
  val extendedColors = LocalTagExtendedColors.current
  val primaryRecord = remember(card.ndefBytes) { NdefMessageCodec.decodeRecords(card.ndefBytes).firstOrNull() }
  val recordTag = primaryRecord?.type?.shortTag ?: "NDEF"
  val baseCardColor = remember(card.id, primaryRecord?.type) { walletCardTone(card.id, primaryRecord?.type) }

  val coverBitmap by
    produceState<ImageBitmap?>(initialValue = null, card.coverImagePath) {
      val path = card.coverImagePath
      value =
        if (path.isNullOrBlank()) {
          null
        } else {
          val bmp: Bitmap? = CardCoverImageHelper.loadBitmap(path)
          bmp?.asImageBitmap()
        }
    }

  val borderColor =
    when {
      isActive -> extendedColors.signalActive
      isFocused -> MaterialTheme.colorScheme.primary
      else -> extendedColors.border
    }

  Column(modifier = Modifier.fillMaxWidth()) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .height(CARD_FRONT_HEIGHT)
          .background(baseCardColor)
          .border(if (isActive || isFocused) 1.5.dp else 1.dp, borderColor)
          .clipToBounds()
          .clickable(onClick = onCardClick)
          .testTag("wallet_card_${card.id}"),
    ) {
      val loadedCover = coverBitmap
      if (loadedCover != null) {
        Image(
          bitmap = loadedCover,
          contentDescription = card.label,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(74.dp)
              .align(Alignment.TopCenter)
              .background(Color.Black.copy(alpha = 0.56f)),
        )
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(62.dp)
              .align(Alignment.BottomCenter)
              .background(Color.Black.copy(alpha = 0.64f)),
        )
      } else {
        WalletDefaultCardPattern(
          accentColor =
            if (isActive) extendedColors.signalActive
            else MaterialTheme.colorScheme.primary,
          modifier = Modifier.fillMaxSize(),
        )
      }

      Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Box(
              modifier =
                Modifier.background(Color.Black.copy(alpha = 0.42f))
                  .border(1.dp, Color.White.copy(alpha = 0.28f))
                  .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
              Text(
                text = recordTag,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
              )
            }
            Text(
              text = card.label,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (isActive) {
              Box(
                modifier =
                  Modifier.background(extendedColors.signalActiveContainer)
                    .border(1.dp, extendedColors.signalActive)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
              ) {
                Text(
                  text = stringResource(R.string.emulation_active_label),
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = extendedColors.signalActive,
                )
              }
            }
            Icon(
              imageVector = Icons.Outlined.Nfc,
              contentDescription = null,
              tint =
                if (isActive) extendedColors.signalActive
                else Color.White.copy(alpha = 0.85f),
              modifier = Modifier.size(20.dp),
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Bottom,
        ) {
          Column(
            modifier = Modifier.weight(1f).padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text(
              text = card.summary,
              style = MonoBodyStyle,
              color = Color.White.copy(alpha = 0.94f),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = if (card.isReadOnly) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
              contentDescription = null,
              tint = Color.White.copy(alpha = 0.72f),
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "${card.ndefByteSize} B",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.78f),
            )
          }
        }
      }
    }

    AnimatedVisibility(visible = isFocused) {
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onPickPhoto,
          shape = MaterialTheme.shapes.small,
          border = BorderStroke(1.dp, extendedColors.border),
          contentPadding = PaddingValues(horizontal = 10.dp),
          modifier = Modifier.height(48.dp).testTag("wallet_photo_button_${card.id}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.AddPhotoAlternate,
            contentDescription = stringResource(R.string.cd_wallet_cover_photo),
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text =
              stringResource(
                if (card.coverImagePath != null) R.string.wallet_photo_change
                else R.string.wallet_photo_set
              ),
            style = MaterialTheme.typography.labelMedium,
          )
        }

        if (card.coverImagePath != null) {
          IconButton(
            onClick = onRemovePhoto,
            modifier =
              Modifier.minimumInteractiveComponentSize()
                .border(1.dp, extendedColors.border)
                .size(48.dp)
                .testTag("wallet_clear_photo_${card.id}"),
          ) {
            Icon(
              imageVector = Icons.Outlined.HideImage,
              contentDescription = stringResource(R.string.cd_wallet_remove_photo),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        OutlinedButton(
          onClick = onCardClick,
          shape = MaterialTheme.shapes.small,
          colors =
            ButtonDefaults.outlinedButtonColors(
              containerColor =
                if (isActive) extendedColors.signalActiveContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
          border =
            BorderStroke(
              1.dp,
              if (isActive) extendedColors.signalActive else extendedColors.border,
            ),
          contentPadding = PaddingValues(horizontal = 10.dp),
          modifier = Modifier.weight(1f).height(48.dp).testTag("wallet_use_card_${card.id}"),
        ) {
          Text(
            text =
              if (isActive) stringResource(R.string.emulation_active_label)
              else stringResource(R.string.wallet_use_card),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color =
              if (isActive) extendedColors.signalActive
              else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
          )
        }

        IconButton(
          onClick = onDuplicate,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("copy_saved_link_${card.id}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = stringResource(R.string.cd_copy_saved_link),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }

        IconButton(
          onClick = onDelete,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("delete_saved_link_${card.id}"),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete_saved_link),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun WalletDefaultCardPattern(
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val lineColor = Color.White.copy(alpha = 0.08f)
    // Subtle physical card chip frame on lower-left center
    val chipWidth = 34.dp.toPx()
    val chipHeight = 24.dp.toPx()
    val chipLeft = 14.dp.toPx()
    val chipTop = size.height * 0.44f
    drawRect(
      color = Color.White.copy(alpha = 0.16f),
      topLeft = Offset(chipLeft, chipTop),
      size = Size(chipWidth, chipHeight),
      style = Stroke(width = 1.dp.toPx()),
    )
    drawLine(
      color = lineColor,
      start = Offset(chipLeft + chipWidth * 0.35f, chipTop),
      end = Offset(chipLeft + chipWidth * 0.35f, chipTop + chipHeight),
      strokeWidth = 1.dp.toPx(),
    )
    drawLine(
      color = lineColor,
      start = Offset(chipLeft, chipTop + chipHeight * 0.5f),
      end = Offset(chipLeft + chipWidth, chipTop + chipHeight * 0.5f),
      strokeWidth = 1.dp.toPx(),
    )

    // Subtle geometric accent bar at right edge
    drawRect(
      color = accentColor.copy(alpha = 0.28f),
      topLeft = Offset(size.width - 6.dp.toPx(), 0f),
      size = Size(6.dp.toPx(), size.height),
    )

    // Crisp horizontal security hairline across card
    drawLine(
      color = lineColor,
      start = Offset(0f, size.height * 0.35f),
      end = Offset(size.width, size.height * 0.35f),
      strokeWidth = 1.dp.toPx(),
      cap = StrokeCap.Butt,
    )
  }
}

private fun walletCardTone(id: Long, type: NdefRecordType?): Color {
  val palette =
    when (type) {
      NdefRecordType.WIFI -> Color(0xFF1A2829)
      NdefRecordType.CONTACT -> Color(0xFF212432)
      NdefRecordType.GEO -> Color(0xFF1F2A22)
      NdefRecordType.APP -> Color(0xFF28222B)
      else ->
        when ((id % 4L).toInt()) {
          0 -> Color(0xFF1A222E)
          1 -> Color(0xFF23252C)
          2 -> Color(0xFF1D2824)
          else -> Color(0xFF26212A)
        }
    }
  return palette
}
