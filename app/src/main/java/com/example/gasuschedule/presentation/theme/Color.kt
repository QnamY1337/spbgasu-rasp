package com.example.gasuschedule.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Официальная палитра СПбГАСУ — значения из макета "официальные цвета".
private val Brick = Color(0xFF96331A)        // шапка
private val Brand = Color(0xFFC14524)        // акцент, кнопки, выделение
private val BrandTint = Color(0xFFF6DED6)    // фон текущей пары / выбранной группы
private val BrandInk = Color(0xFF8C4530)     // текст на BrandTint
private val Paper = Color(0xFFF3F3F4)        // фон экранов
private val Ink = Color(0xFF242424)
private val InkMuted = Color(0xFF84807D)
private val InkFaint = Color(0xFFABA6A1)     // прошедшие пары
private val Line = Color(0xFFE1DFDD)
private val LineStrong = Color(0xFFCFCBC7)

val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandTint,
    onPrimaryContainer = BrandInk,
    secondary = Brick,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainer = Color.White,
    outline = LineStrong,
    outlineVariant = Line,
    error = Color(0xFFB3261E),
)

// Тёмная тема в макете не нарисована — те же оттенки, приглушённые под тёмный фон.
val DarkColors = darkColorScheme(
    primary = Color(0xFFE8795A),
    onPrimary = Color(0xFF3A0F04),
    primaryContainer = Color(0xFF4A2419),
    onPrimaryContainer = Color(0xFFF6C3B3),
    secondary = Color(0xFF6E2413),
    onSecondary = Color.White,
    background = Color(0xFF141313),
    onBackground = Color(0xFFECEAE8),
    surface = Color(0xFF1E1C1B),
    onSurface = Color(0xFFECEAE8),
    surfaceVariant = Color(0xFF141313),
    onSurfaceVariant = Color(0xFFA7A19C),
    surfaceContainerLowest = Color(0xFF1E1C1B),
    surfaceContainer = Color(0xFF1E1C1B),
    outline = Color(0xFF4A4644),
    outlineVariant = Color(0xFF34312F),
    error = Color(0xFFF2B8B5),
)

/** Цвета макета, которых нет в Material-схеме. */
@Immutable
data class GasuColors(
    val header: Color,
    val onHeader: Color,
    val onHeaderMuted: Color,
    val textFaint: Color,
)

val LightGasuColors = GasuColors(
    header = Brick,
    onHeader = Color.White,
    onHeaderMuted = Color.White.copy(alpha = 0.72f),
    textFaint = InkFaint,
)

val DarkGasuColors = GasuColors(
    header = Color(0xFF5E2012),
    onHeader = Color(0xFFF4ECE9),
    onHeaderMuted = Color(0xFFF4ECE9).copy(alpha = 0.66f),
    textFaint = Color(0xFF6E6965),
)

val LocalGasuColors = staticCompositionLocalOf { LightGasuColors }
