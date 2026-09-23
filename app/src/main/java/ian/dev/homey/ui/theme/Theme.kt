package ian.dev.homey.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalHomeyColors = staticCompositionLocalOf { LightHomeyColors }

object Homey {
    val colors: HomeyColors
        @Composable @ReadOnlyComposable get() = LocalHomeyColors.current
}

@Composable
fun HomeyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val c = if (darkTheme) DarkHomeyColors else LightHomeyColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.green, onPrimary = c.onGreen, primaryContainer = c.greenSoft,
            secondary = c.coral, onSecondary = c.onCoral,
            background = c.background, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.surfaceMuted, onSurfaceVariant = c.muted,
            outline = c.line, error = c.urgent
        )
    } else {
        lightColorScheme(
            primary = c.green, onPrimary = c.onGreen, primaryContainer = c.greenSoft,
            secondary = c.coral, onSecondary = c.onCoral,
            background = c.background, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, surfaceVariant = c.surfaceMuted, onSurfaceVariant = c.muted,
            outline = c.line, error = c.urgent
        )
    }
    CompositionLocalProvider(LocalHomeyColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
