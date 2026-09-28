package com.example.gasuschedule.presentation.common

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Цвет иконок статус-бара для экрана: под кирпичной шапкой — светлые,
 * на экранах без шапки (настройки) — тёмные в светлой теме.
 */
@Composable
fun StatusBarIcons(onBrickHeader: Boolean) {
    val view = LocalView.current
    val darkIcons = !onBrickHeader && !isSystemInDarkTheme()
    if (view.isInEditMode) return
    DisposableEffect(darkIcons) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
        }
        onDispose { }
    }
}
