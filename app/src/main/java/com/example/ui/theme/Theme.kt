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

private val DarkColorScheme = darkColorScheme(
    primary = VaultBlueLight,
    onPrimary = VaultSlate900,
    primaryContainer = VaultBlueDark,
    onPrimaryContainer = Color.White,
    secondary = VaultCyanAccent,
    onSecondary = VaultSlate900,
    background = VaultSlate900,
    surface = VaultSlate800,
    surfaceVariant = VaultSlate700,
    onBackground = VaultSlate50,
    onSurface = VaultSlate50,
    onSurfaceVariant = VaultSlate100
)

private val LightColorScheme = lightColorScheme(
    primary = VaultBluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = VaultBlueDark,
    secondary = VaultCyanAccent,
    onSecondary = Color.White,
    background = VaultSlate50,
    surface = Color.White,
    surfaceVariant = VaultSlate100,
    onBackground = VaultSlate900,
    onSurface = VaultSlate900,
    onSurfaceVariant = VaultSlate700
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
