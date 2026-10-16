package io.github.stub1t.chronicals.ui.theme

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

val Brand = Color(0xFF7462E8)

// Before Android 12 there are no wallpaper colours: the app's own purple on cream, or on near-black.
private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF22145F),
    secondaryContainer = Color(0xFFEDE7F7),
    background = Color(0xFFFBF7F1),
    surface = Color(0xFFFBF7F1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F2EC),
    surfaceContainer = Color(0xFFF2ECE5),
    surfaceContainerHigh = Color(0xFFECE6DF),
    onSurface = Color(0xFF2B2520),
    onSurfaceVariant = Color(0xFF6F645A),
    outlineVariant = Color(0xFFE2DAD0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC7BFFF),
    onPrimary = Color(0xFF2B1D78),
    primaryContainer = Color(0xFF4A3BB0),
    onPrimaryContainer = Color(0xFFE6E0FF),
    background = Color(0xFF18151A),
    surface = Color(0xFF18151A),
    surfaceContainerLowest = Color(0xFF131015),
    surfaceContainerLow = Color(0xFF1F1B22),
    surfaceContainer = Color(0xFF241F27),
    surfaceContainerHigh = Color(0xFF2E2931),
    onSurface = Color(0xFFF5EFF4),
    onSurfaceVariant = Color(0xFFB9ADB8),
    outlineVariant = Color(0xFF3B353E),
)

@Composable
fun ChronicalsTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
