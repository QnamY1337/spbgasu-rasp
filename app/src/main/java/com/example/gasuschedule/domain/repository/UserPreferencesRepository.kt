package com.example.gasuschedule.domain.repository

import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.ThemeMode
import com.example.gasuschedule.domain.model.TravelMode
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

    /** Уведомлять ли о заменах, найденных фоновой синхронизацией. */
    val changeNotificationsEnabled: Flow<Boolean>
    suspend fun setChangeNotificationsEnabled(enabled: Boolean)

    /** Дом — точка отсчёта для времени выхода; null — не указан. */
    val home: Flow<HomeLocation?>
    suspend fun setHome(home: HomeLocation?)

    val travelMode: Flow<TravelMode>
    suspend fun setTravelMode(mode: TravelMode)

    /** Запас к времени в пути, минут. */
    val leaveBufferMinutes: Flow<Int>
    suspend fun setLeaveBufferMinutes(minutes: Int)

    /** За сколько часов до дедлайна задания напоминать; 0 — не напоминать. */
    val homeworkReminderHours: Flow<Int>
    suspend fun setHomeworkReminderHours(hours: Int)

    /** Уведомлять "Пора выходить" перед первой парой дня. */
    val leaveRemindersEnabled: Flow<Boolean>
    suspend fun setLeaveRemindersEnabled(enabled: Boolean)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    companion object {
        const val DEFAULT_REMINDER_MINUTES = 15
        val REMINDER_MINUTES_OPTIONS = listOf(5, 10, 15, 30, 60)
        const val DEFAULT_LEAVE_BUFFER_MINUTES = 10
        val LEAVE_BUFFER_OPTIONS = listOf(0, 5, 10, 15, 20)
        const val DEFAULT_HOMEWORK_REMINDER_HOURS = 12
        val HOMEWORK_REMINDER_OPTIONS = listOf(0, 2, 6, 12, 24)
    }
}
