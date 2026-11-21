package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.preferences.ThemeMode

private val LightColorScheme = lightColorScheme(
  primary = IndigoPrimary,
  onPrimary = Color.White,
  primaryContainer = IndigoContainer,
  onPrimaryContainer = IndigoOnContainer,
  secondary = SkySecondary,
  onSecondary = Color.White,
  secondaryContainer = SkyContainer,
  onSecondaryContainer = SkyOnContainer,
  tertiary = EmeraldTertiary,
  onTertiary = Color.White,
  tertiaryContainer = EmeraldContainer,
  onTertiaryContainer = EmeraldOnContainer,
  error = PriorityUrgentColor,
  onError = Color.White,
  errorContainer = PriorityUrgentContainerLight,
  onErrorContainer = Color(0xFF7F1D1D),
  background = LightBackground,
  onBackground = LightTextPrimary,
  surface = LightSurface,
  onSurface = LightTextPrimary,
  surfaceVariant = LightSurfaceVariant,
  onSurfaceVariant = LightTextSecondary,
  outline = LightOutline,
  outlineVariant = LightOutlineVariant,
)

private val DarkColorScheme = darkColorScheme(
  primary = IndigoLight,
  onPrimary = Color(0xFF0F172A),
  primaryContainer = IndigoDark,
  onPrimaryContainer = Color(0xFFE0E7FF),
  secondary = Color(0xFF38BDF8),
  onSecondary = Color(0xFF082F49),
  secondaryContainer = Color(0xFF0369A1),
  onSecondaryContainer = Color(0xFFE0F2FE),
  tertiary = Color(0xFF34D399),
  onTertiary = Color(0xFF022C22),
  tertiaryContainer = Color(0xFF065F46),
  onTertiaryContainer = Color(0xFFD1FAE5),
  error = Color(0xFFF87171),
  onError = Color(0xFF450A0A),
  errorContainer = PriorityUrgentContainerDark,
  onErrorContainer = Color(0xFFFEE2E2),
  background = DarkBackground,
  onBackground = DarkTextPrimary,
  surface = DarkSurface,
  onSurface = DarkTextPrimary,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = DarkTextSecondary,
  outline = DarkOutline,
  outlineVariant = DarkOutlineVariant,
)

val FloatTaskShapes = Shapes(
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(22.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FloatTaskTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  content: @Composable () -> Unit,
) {
  val isDark = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }

  MaterialTheme(
    colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
    typography = FloatTaskTypography,
    shapes = FloatTaskShapes,
    content = content,
  )
}
