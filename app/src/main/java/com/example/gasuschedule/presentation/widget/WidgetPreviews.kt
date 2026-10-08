package com.example.gasuschedule.presentation.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager

/**
 * Публикует сгенерированное превью виджета (Android 15+). Система ограничивает частоту вызова,
 * поэтому делаем это один раз на версию приложения.
 */
object WidgetPreviews {
    private const val PREFS = "widget"
    private const val KEY_VERSION = "preview_version"

    suspend fun publishOnce(context: Context, appVersion: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_VERSION, -1) == appVersion) return
        val result = runCatching {
            val manager = GlanceAppWidgetManager(context)
            listOf(NextLessonWidgetReceiver::class, CountdownWidgetReceiver::class)
                .map { manager.setWidgetPreviews(it) }
                .firstOrNull { it != GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }
                ?: GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
        }.getOrNull()
        // При RATE_LIMITED попробуем на следующем запуске.
        if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
            prefs.edit().putInt(KEY_VERSION, appVersion).apply()
        }
    }
}
