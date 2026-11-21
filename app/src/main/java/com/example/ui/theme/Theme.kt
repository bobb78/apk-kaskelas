package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalKasIsDark = compositionLocalOf { false }

@Composable
fun kasIncomeColor(): Color = if (LocalKasIsDark.current) IncomeGreenDark else IncomeGreen

@Composable
fun kasIncomeContainer(): Color = if (LocalKasIsDark.current) IncomeGreenContainerDark else IncomeGreenContainer

@Composable
fun kasExpenseColor(): Color = if (LocalKasIsDark.current) ExpenseRedDark else ExpenseRed

@Composable
fun kasExpenseContainer(): Color = if (LocalKasIsDark.current) ExpenseRedContainerDark else ExpenseRedContainer

@Composable
fun kasGoldColor(): Color = if (LocalKasIsDark.current) AccentGoldDark else AccentGold

@Composable
fun kasGoldContainer(): Color = if (LocalKasIsDark.current) AccentGoldContainerDark else AccentGoldContainer

private val DarkColorScheme =
  darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = Color(0xFF003258),
    primaryContainer = BlueContainerDark,
    onPrimaryContainer = OnBlueContainerDark,
    secondary = BlueSecondaryDark,
    onSecondary = Color(0xFF00344F),
    secondaryContainer = Color(0xFF0D2B45),
    onSecondaryContainer = GeoSoftBlueContainer,
    background = NeutralDarkBg,
    surface = NeutralDarkSurface,
    surfaceVariant = NeutralDarkCard,
    surfaceContainerLow = Color(0xFF171B21),
    surfaceContainer = Color(0xFF1D2229),
    surfaceContainerHigh = Color(0xFF262C35),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF43474E),
    outlineVariant = Color(0xFF2C333D),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = GeoBrandBlue,
    onPrimary = Color.White,
    primaryContainer = GeoSoftBlueContainer,
    onPrimaryContainer = GeoNavy,
    secondary = BlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = BlueIce,
    onSecondaryContainer = GeoNavy,
    background = GeoBackground,
    surface = GeoSurface,
    surfaceVariant = GeoNeutralContainer,
    surfaceContainerLow = Color(0xFFF3F6FC),
    surfaceContainer = GeoNavBg,
    surfaceContainerHigh = Color(0xFFE6ECF7),
    onBackground = GeoOnSurface,
    onSurface = GeoOnSurface,
    onSurfaceVariant = GeoSubtitle,
    outline = GeoBorder,
    outlineVariant = Color(0xFFE3E8F0),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable Android 12 dynamic color by default so our custom blue identity shines through
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
      }
    }
  }

  CompositionLocalProvider(LocalKasIsDark provides darkTheme) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}

