package com.example.mouse_movil.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val DarkColorScheme = darkColorScheme(
    primary = MseDarkPrimary,
    onPrimary = MseDarkOnPrimary,
    primaryContainer = MseDarkPrimaryContainer,
    onPrimaryContainer = MseDarkOnPrimaryContainer,
    secondary = MseDarkSecondary,
    onSecondary = MseDarkOnSecondary,
    secondaryContainer = MseDarkSecondaryContainer,
    onSecondaryContainer = MseDarkOnSecondaryContainer,
    tertiary = MseDarkTertiary,
    onTertiary = MseDarkOnTertiary,
    background = MseDarkSurface,
    onBackground = MseDarkOnSurface,
    surface = MseDarkSurface,
    onSurface = MseDarkOnSurface,
    surfaceContainerLow = MseDarkSurfaceContainerLow,
    surfaceVariant = MseDarkSurfaceVariant,
    onSurfaceVariant = MseDarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = MseLightPrimary,
    onPrimary = MseLightOnPrimary,
    primaryContainer = MseLightPrimaryContainer,
    onPrimaryContainer = MseLightOnPrimaryContainer,
    secondary = MseLightSecondary,
    onSecondary = MseLightOnSecondary,
    secondaryContainer = MseLightSecondaryContainer,
    onSecondaryContainer = MseLightOnSecondaryContainer,
    tertiary = MseLightTertiary,
    onTertiary = MseLightOnTertiary,
    background = MseLightSurface,
    onBackground = MseLightOnSurface,
    surface = MseLightSurface,
    onSurface = MseLightOnSurface,
    surfaceContainerLow = MseLightSurfaceContainerLow,
    surfaceVariant = MseLightSurfaceVariant,
    onSurfaceVariant = MseLightOnSurfaceVariant
)

@Composable
fun Mouse_movilTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
