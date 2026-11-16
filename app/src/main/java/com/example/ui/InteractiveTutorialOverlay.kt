package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalTagExtendedColors
import kotlin.math.hypot
import kotlin.math.max

enum class TutorialTarget {
  NFC_STATUS,
  MAKE_TAG,
  COPY_TAG,
  ACTIVATE_TAG,
  SHORTCUTS,
}

private data class TutorialStepSpec(
  val target: TutorialTarget,
  val titleRes: Int,
  val bodyRes: Int,
)

private val TUTORIAL_STEPS =
  listOf(
    TutorialStepSpec(
      target = TutorialTarget.NFC_STATUS,
      titleRes = R.string.tutorial_step1_title,
      bodyRes = R.string.tutorial_step1_body,
    ),
    TutorialStepSpec(
      target = TutorialTarget.MAKE_TAG,
      titleRes = R.string.tutorial_step2_title,
      bodyRes = R.string.tutorial_step2_body,
    ),
    TutorialStepSpec(
      target = TutorialTarget.COPY_TAG,
      titleRes = R.string.tutorial_step3_title,
      bodyRes = R.string.tutorial_step3_body,
    ),
    TutorialStepSpec(
      target = TutorialTarget.ACTIVATE_TAG,
      titleRes = R.string.tutorial_step4_title,
      bodyRes = R.string.tutorial_step4_body,
    ),
    TutorialStepSpec(
      target = TutorialTarget.SHORTCUTS,
      titleRes = R.string.tutorial_step5_title,
      bodyRes = R.string.tutorial_step5_body,
    ),
  )

@Composable
fun InteractiveTutorialOverlay(
  stepIndex: Int,
  targetBounds: Map<TutorialTarget, Rect>,
  onNext: () -> Unit,
  onPrev: () -> Unit,
  onSkip: () -> Unit,
) {
  BackHandler { onSkip() }

  val safeIndex = stepIndex.coerceIn(0, TUTORIAL_STEPS.lastIndex)
  val step = TUTORIAL_STEPS[safeIndex]
  val isLastStep = safeIndex == TUTORIAL_STEPS.lastIndex
  val extendedColors = LocalTagExtendedColors.current
  val primaryColor = MaterialTheme.colorScheme.primary
  val density = LocalDensity.current

  val infiniteTransition = rememberInfiniteTransition(label = "tutorial_pulse")
  val pulseProgress by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1400, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "pulse_ring",
    )

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize().testTag("tutorial_overlay"),
  ) {
    val rootWidthPx = with(density) { maxWidth.toPx() }
    val rootHeightPx = with(density) { maxHeight.toPx() }
    val defaultRect =
      Rect(
        left = rootWidthPx * 0.25f,
        top = rootHeightPx * 0.25f,
        right = rootWidthPx * 0.75f,
        bottom = rootHeightPx * 0.40f,
      )
    val rawTarget = targetBounds[step.target] ?: defaultRect

    val animatedTarget by
      animateRectAsState(
        targetValue = rawTarget,
        animationSpec =
          spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
          ),
        label = "spotlight_rect",
      )

    val center = animatedTarget.center
    val paddingPx = with(density) { 10.dp.toPx() }
    val circleRadius =
      max(
        with(density) { 36.dp.toPx() },
        (max(animatedTarget.width, animatedTarget.height) * 0.5f + paddingPx)
          .coerceAtMost(rootWidthPx * 0.46f),
      )

    Canvas(
      modifier =
        Modifier.fillMaxSize()
          .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
          .pointerInput(safeIndex, center, circleRadius) {
            detectTapGestures { tapOffset ->
              val dist = hypot(tapOffset.x - center.x, tapOffset.y - center.y)
              if (dist <= circleRadius) {
                onNext()
              }
            }
          },
    ) {
      drawRect(color = Color.Black.copy(alpha = 0.72f))

      drawCircle(
        color = Color.Transparent,
        radius = circleRadius,
        center = center,
        blendMode = BlendMode.Clear,
      )

      drawCircle(
        color = primaryColor,
        radius = circleRadius,
        center = center,
        style = Stroke(width = 2.5.dp.toPx()),
      )

      val expandingRadius = circleRadius + (22.dp.toPx() * pulseProgress)
      val ringAlpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.75f
      drawCircle(
        color = primaryColor.copy(alpha = ringAlpha),
        radius = expandingRadius,
        center = center,
        style = Stroke(width = 2.dp.toPx()),
      )
    }

    val placeAbove = center.y > rootHeightPx * 0.52f
    Box(
      modifier =
        Modifier.fillMaxSize()
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 16.dp, vertical = 14.dp),
      contentAlignment = if (placeAbove) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .widthIn(max = 520.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, extendedColors.border)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            repeat(TUTORIAL_STEPS.size) { index ->
              val active = index == safeIndex
              Box(
                modifier =
                  Modifier.height(6.dp)
                    .width(if (active) 20.dp else 8.dp)
                    .background(
                      if (active) MaterialTheme.colorScheme.primary
                      else extendedColors.border
                    )
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "${safeIndex + 1} / ${TUTORIAL_STEPS.size}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          TextButton(
            onClick = onSkip,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("tutorial_skip_button"),
          ) {
            Text(text = stringResource(R.string.tutorial_skip))
          }
        }

        AnimatedContent(
          targetState = step,
          transitionSpec = {
            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(140))
          },
          label = "tutorial_step_content",
        ) { currentStep ->
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                  modifier =
                    Modifier.size(20.dp).graphicsLayer {
                      val scale = 0.94f + (0.12f * (1f - pulseProgress))
                      scaleX = scale
                      scaleY = scale
                    },
                )
              }
              Text(
                text = stringResource(currentStep.titleRes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }

            Text(
              text = stringResource(currentStep.bodyRes),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Text(
          text = stringResource(R.string.tutorial_tap_circle_hint),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (safeIndex > 0) {
            OutlinedButton(
              onClick = onPrev,
              shape = MaterialTheme.shapes.small,
              border = BorderStroke(1.dp, extendedColors.border),
              modifier =
                Modifier.weight(0.38f)
                  .height(48.dp)
                  .testTag("tutorial_back_button"),
            ) {
              Text(text = stringResource(R.string.tutorial_back))
            }
          }

          Button(
            onClick = onNext,
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier =
              Modifier.weight(if (safeIndex > 0) 0.62f else 1f)
                .height(48.dp)
                .testTag(if (isLastStep) "tutorial_done_button" else "tutorial_next_button"),
          ) {
            Text(
              text =
                stringResource(
                  if (isLastStep) R.string.tutorial_finish else R.string.tutorial_next
                ),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
          }
        }
      }
    }
  }
}
