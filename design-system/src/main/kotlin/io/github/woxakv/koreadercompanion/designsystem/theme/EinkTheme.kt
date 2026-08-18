package io.github.woxakv.koreadercompanion.designsystem.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val EinkColorScheme = lightColorScheme(
    primary = EinkColors.OnBackground,
    onPrimary = EinkColors.Background,
    background = EinkColors.Background,
    onBackground = EinkColors.OnBackground,
    surface = EinkColors.Surface,
    onSurface = EinkColors.OnSurface,
    outline = EinkColors.Outline,
)

/**
 * The single, hardcoded v1 theme: strict black & white, no animations
 * downstream, ripple disabled. A future theme picker extends this module
 * rather than this composable itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EinkTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        MaterialTheme(
            colorScheme = EinkColorScheme,
            typography = EinkTypography,
            content = content,
        )
    }
}
