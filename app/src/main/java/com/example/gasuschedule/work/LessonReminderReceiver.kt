package com.example.gasuschedule.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Срабатывание будильника: всё для уведомления уже лежит в extras, в базу не ходим. */
class LessonReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        ReminderNotifications.show(
            context,
            key = key,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
        )
    }

    companion object {
        const val ACTION = "com.example.gasuschedule.LESSON_REMINDER"
        const val EXTRA_KEY = "key"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }
}
