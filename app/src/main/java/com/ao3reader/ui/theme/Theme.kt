package com.ao3reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.ao3reader.data.prefs.AppSettings
import com.ao3reader.data.prefs.ThemeMode

@Composable
fun isAppInDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun Ao3Theme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(settings)
    val context = LocalContext.current
    val accent = Color(settings.accent.argb)

    var scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = lerp(accent, Color.White, 0.35f),
            onPrimary = Color(0xFF1A1A1A),
            primaryContainer = lerp(accent, Color.Black, 0.45f),
            onPrimaryContainer = lerp(accent, Color.White, 0.8f),
            secondary = lerp(accent, Color.White, 0.5f),
            secondaryContainer = lerp(accent, Color(0xFF2A2A2A), 0.75f),
            onSecondaryContainer = Color(0xFFEDE0E0),
            background = Color(0xFF121212),
            surface = Color(0xFF121212),
            surfaceContainer = Color(0xFF1C1B1B),
            surfaceContainerLow = Color(0xFF181717),
            surfaceContainerHigh = Color(0xFF232121),
            surfaceContainerHighest = Color(0xFF2C2A2A),
        )
        else -> lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = lerp(accent, Color.White, 0.8f),
            onPrimaryContainer = lerp(accent, Color.Black, 0.6f),
            secondary = lerp(accent, Color.Gray, 0.4f),
            secondaryContainer = lerp(accent, Color.White, 0.88f),
        )
    }
    if (dark && settings.amoledBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF101010),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
