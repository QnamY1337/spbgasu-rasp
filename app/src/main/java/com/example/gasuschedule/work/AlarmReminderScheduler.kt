package com.example.gasuschedule.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.getSystemService
import com.example.gasuschedule.domain.model.LessonReminder
import com.example.gasuschedule.domain.repository.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ставит напоминания будильниками AlarmManager.
 *
 * Точные будильники (setExactAndAllowWhileIdle) — через USE_EXACT_ALARM они доступны по умолчанию.
 * Если точные всё же запрещены, НЕ используем setAndAllowWhileIdle: система растягивает его окно
 * до часа. Вместо этого — окно ±5 минут вокруг нужного момента (setWindow).
 * Ключи поставленных напоминаний храним, чтобы при пересчёте снимать те, что больше не нужны
 * (пару отменили, выключили напоминания, сменили группу).
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {

    private val alarms: AlarmManager = requireNotNull(context.getSystemService())
    private val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    override suspend fun replaceAll(reminders: List<LessonReminder>) = withContext(Dispatchers.IO) {
        synchronized(this@AlarmReminderScheduler) {
            val newKeys = reminders.map { it.key }.toSet()
            val oldKeys = store.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
            (oldKeys - newKeys).forEach(::cancel)
            reminders.forEach(::schedule)
            store.edit().putStringSet(KEY_SCHEDULED, newKeys).apply()
        }
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    private fun schedule(reminder: LessonReminder) {
        val intent = reminderIntent(reminder.key)
            .putExtra(LessonReminderReceiver.EXTRA_KEY, reminder.key)
            .putExtra(LessonReminderReceiver.EXTRA_TITLE, reminder.title)
            .putExtra(LessonReminderReceiver.EXTRA_TEXT, reminder.text)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = reminder.triggerAt.toEpochMilli()
        try {
            if (canScheduleExact()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                setApproximate(at, pending)
            }
        } catch (e: SecurityException) {
            // Разрешение на точные будильники отозвали между проверкой и вызовом.
            setApproximate(at, pending)
        }
    }

    private fun setApproximate(at: Long, pending: PendingIntent) {
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at - FALLBACK_WINDOW_MS / 2, FALLBACK_WINDOW_MS, pending)
    }

    private fun cancel(key: String) {
        val pending = PendingIntent.getBroadcast(
            context, 0, reminderIntent(key), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(pending)
        pending.cancel()
    }

    /** Уникальность PendingIntent держится на data-URI с ключом слота, а не на requestCode. */
    private fun reminderIntent(key: String) = Intent(context, LessonReminderReceiver::class.java)
        .setAction(LessonReminderReceiver.ACTION)
        .setData(Uri.Builder().scheme("gasu-reminder").opaquePart(key).build())

    private companion object {
        const val STORE = "reminder_alarms"
        const val KEY_SCHEDULED = "scheduled_keys"
        /** Минимальное окно, которое Android 12+ даёт без точных будильников, — 10 минут. */
        const val FALLBACK_WINDOW_MS = 10 * 60 * 1000L
    }
}
