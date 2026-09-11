package com.fatsar.hermes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val HermesGreen = Color(0xFF4ADE80)
val HermesBlue = Color(0xFF7DD3FC)
val HermesAmber = Color(0xFFFBBF24)
val HermesRed = Color(0xFFFF6B6B)

private val DarkColors = darkColorScheme(
    primary = HermesGreen,
    onPrimary = Color(0xFF05261A),
    primaryContainer = Color(0xFF14351F),
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = HermesBlue,
    onSecondary = Color(0xFF04202E),
    secondaryContainer = Color(0xFF15303F),
    onSecondaryContainer = Color(0xFFCDEBFB),
    tertiary = HermesAmber,
    background = Color(0xFF0F1115),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF151922),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF1E242F),
    onSurfaceVariant = Color(0xFFA9B2C3),
    outline = Color(0xFF39414F),
    outlineVariant = Color(0xFF272E3A),
    error = HermesRed,
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFD7D7),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF10803F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F5D8),
    onPrimaryContainer = Color(0xFF04240F),
    secondary = Color(0xFF0C6A92),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCEBF8),
    onSecondaryContainer = Color(0xFF042230),
    tertiary = Color(0xFF9A6700),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF12161F),
    surface = Color.White,
    onSurface = Color(0xFF12161F),
    surfaceVariant = Color(0xFFE8EBF2),
    onSurfaceVariant = Color(0xFF4A5364),
    outline = Color(0xFFB8C0CE),
    outlineVariant = Color(0xFFDDE2EA),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun HermesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
