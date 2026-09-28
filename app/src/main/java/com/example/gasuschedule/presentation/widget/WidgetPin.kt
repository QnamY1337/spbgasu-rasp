package com.example.gasuschedule.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Виджет нельзя "включить" из приложения — только попросить лаунчер закрепить его
 * (Android 8+, если лаунчер это поддерживает). Поэтому в настройках вместо переключателя — кнопка.
 */
object WidgetPin {
    data class State(val installed: Int, val canRequest: Boolean)

    private fun component(context: Context) = ComponentName(context, NextLessonWidgetReceiver::class.java)

    fun state(context: Context): State {
        val manager = AppWidgetManager.getInstance(context)
        return State(
            installed = manager.getAppWidgetIds(component(context)).size,
            canRequest = manager.isRequestPinAppWidgetSupported,
        )
    }

    fun request(context: Context): Boolean =
        AppWidgetManager.getInstance(context).requestPinAppWidget(component(context), null, null)
}
