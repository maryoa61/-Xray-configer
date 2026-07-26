package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryBlue,
    primaryContainer = PrimaryBlueContainer,
    onPrimaryContainer = OnPrimaryBlueContainer,
    secondary = HighDensityAccentBlue,
    background = HighDensityBackground,
    surface = HighDensitySurface,
    surfaceVariant = HighDensitySurfaceVariant,
    onBackground = HighDensityOnSurface,
    onSurface = HighDensityOnSurface,
    onSurfaceVariant = HighDensityOnSurfaceVariant,
    outline = HighDensityOutline
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = HighDensityAccentBlue,
    primaryContainer = PrimaryBlue,
    onPrimaryContainer = HighDensitySurface,
    secondary = PrimaryBlueContainer,
    background = DarkCodeBackground,
    surface = Color(0xFF252526),
    surfaceVariant = Color(0xFF2D2D30),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = HighDensityOutline,
    outline = HighDensityOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
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

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
