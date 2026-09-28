package com.example.gasuschedule.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface UserPreferencesRepository {
    /** Выбранная группа; null — онбординг ещё не пройден. */
    val groupName: Flow<String?>
    suspend fun setGroupName(name: String)

    val lastSyncAt: Flow<Instant?>
    suspend fun setLastSyncAt(at: Instant)

    val remindersEnabled: Flow<Boolean>
    suspend fun setRemindersEnabled(enabled: Boolean)

    /** За сколько минут до начала пары напоминать. */
    val reminderMinutes: Flow<Int>
    suspend fun setReminderMinutes(minutes: Int)

    companion object {
        const val DEFAULT_REMINDER_MINUTES = 15
        val REMINDER_MINUTES_OPTIONS = listOf(5, 10, 15, 30, 60)
    }
}
