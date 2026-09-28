package com.example.gasuschedule.work

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Будильники AlarmManager не переживают перезагрузку и обновление приложения, а смена времени
 * или часового пояса делает их неверными — во всех этих случаях пересчитываем заново.
 */
@AndroidEntryPoint
class SystemEventsReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderWork: ReminderWork

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        reminderWork.ensureDailyPlanning()
        reminderWork.requestReplan()
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
