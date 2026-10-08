package com.example.gasuschedule.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
)

private val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Тема на официальных цветах СПбГАСУ. Dynamic color (Material You) намеренно не используется:
 * он подменил бы фирменную палитру цветами обоев.
 */
@Composable
fun GasuTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalGasuColors provides if (darkTheme) DarkGasuColors else LightGasuColors,
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object GasuTheme {
    val colors: GasuColors
        @Composable get() = LocalGasuColors.current

    /** Тёмная ли тема сейчас — с учётом выбора в настройках, а не только системы. */
    val isDark: Boolean
        @Composable get() = LocalDarkTheme.current
}
