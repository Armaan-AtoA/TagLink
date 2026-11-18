package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class TagExtendedColors(
  val border: Color,
  val signalActive: Color,
  val signalActiveContainer: Color,
)

val LocalTagExtendedColors =
  staticCompositionLocalOf {
    TagExtendedColors(
      border = LightBorder,
      signalActive = LightSignalGreen,
      signalActiveContainer = LightSignalGreenContainer,
    )
  }

private val LightColors =
  lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = LightError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightError,
  )

private val DarkColors =
  darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = DarkError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkError,
  )

private val SharpShapes =
  Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(2.dp),
    extraLarge = RoundedCornerShape(2.dp),
  )

@Composable
fun TagLinkTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColors else LightColors
  val extendedColors =
    if (darkTheme) {
      TagExtendedColors(
        border = DarkBorder,
        signalActive = DarkSignalGreen,
        signalActiveContainer = DarkSignalGreenContainer,
      )
    } else {
      TagExtendedColors(
        border = LightBorder,
        signalActive = LightSignalGreen,
        signalActiveContainer = LightSignalGreenContainer,
      )
    }

  CompositionLocalProvider(LocalTagExtendedColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = SharpShapes,
      content = content,
    )
  }
}
