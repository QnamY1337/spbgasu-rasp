package com.example.gasuschedule.domain.repository

import com.example.gasuschedule.domain.model.LessonReminder

/** Системные будильники для напоминаний (AlarmManager). */
interface ReminderScheduler {
    /** Заменяет все поставленные напоминания на [reminders]; лишние отменяет. */
    suspend fun replaceAll(reminders: List<LessonReminder>)
}

/** Просьба пересчитать напоминания в фоне (WorkManager) — после синхронизации, смены настроек и т.п. */
interface ReminderReplanTrigger {
    fun requestReplan()
}
