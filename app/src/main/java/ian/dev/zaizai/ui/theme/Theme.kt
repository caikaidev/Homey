package ian.dev.zaizai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalZaiZaiColors = staticCompositionLocalOf { LightZaiZaiColors }

object ZaiZai {
    val colors: ZaiZaiColors
        @Composable @ReadOnlyComposable get() = LocalZaiZaiColors.current
}

@Composable
fun ZaiZaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val c = if (darkTheme) DarkZaiZaiColors else LightZaiZaiColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.action, onPrimary = c.onAction,
            secondary = c.done, onSecondary = c.surface,
            background = c.background, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, onSurfaceVariant = c.muted,
            outline = c.line
        )
    } else {
        lightColorScheme(
            primary = c.action, onPrimary = c.onAction,
            secondary = c.done, onSecondary = c.surface,
            background = c.background, onBackground = c.ink,
            surface = c.surface, onSurface = c.ink, onSurfaceVariant = c.muted,
            outline = c.line
        )
    }
    CompositionLocalProvider(LocalZaiZaiColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
