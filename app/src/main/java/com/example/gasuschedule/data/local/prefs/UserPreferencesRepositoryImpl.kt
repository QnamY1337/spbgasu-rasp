package com.example.gasuschedule.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository.Companion.DEFAULT_REMINDER_MINUTES
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val groupName: Flow<String?> =
        dataStore.data.map { it[GROUP_NAME] }.distinctUntilChanged()

    override suspend fun setGroupName(name: String) {
        dataStore.edit { it[GROUP_NAME] = name }
    }

    override val lastSyncAt: Flow<Instant?> =
        dataStore.data.map { p -> p[LAST_SYNC_AT]?.let(Instant::ofEpochMilli) }.distinctUntilChanged()

    override suspend fun setLastSyncAt(at: Instant) {
        dataStore.edit { it[LAST_SYNC_AT] = at.toEpochMilli() }
    }

    override val remindersEnabled: Flow<Boolean> =
        dataStore.data.map { it[REMINDERS_ENABLED] ?: true }.distinctUntilChanged()

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[REMINDERS_ENABLED] = enabled }
    }

    override val reminderMinutes: Flow<Int> =
        dataStore.data.map { it[REMINDER_MINUTES] ?: DEFAULT_REMINDER_MINUTES }.distinctUntilChanged()

    override suspend fun setReminderMinutes(minutes: Int) {
        dataStore.edit { it[REMINDER_MINUTES] = minutes }
    }

    override val changeNotificationsEnabled: Flow<Boolean> =
        dataStore.data.map { it[CHANGE_NOTIFICATIONS] ?: true }.distinctUntilChanged()

    override suspend fun setChangeNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[CHANGE_NOTIFICATIONS] = enabled }
    }

    private companion object {
        val CHANGE_NOTIFICATIONS = booleanPreferencesKey("change_notifications")
        val GROUP_NAME = stringPreferencesKey("group_name")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
    }
}
