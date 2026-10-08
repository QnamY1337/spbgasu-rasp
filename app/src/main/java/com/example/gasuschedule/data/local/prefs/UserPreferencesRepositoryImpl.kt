package com.example.gasuschedule.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.ThemeMode
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.repository.UserPreferencesRepository.Companion.DEFAULT_LEAVE_BUFFER_MINUTES
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

    override val home: Flow<HomeLocation?> = dataStore.data.map { p ->
        val lat = p[HOME_LAT]
        val lon = p[HOME_LON]
        if (lat == null || lon == null) null else HomeLocation(GeoPoint(lat, lon), p[HOME_LABEL].orEmpty())
    }.distinctUntilChanged()

    override suspend fun setHome(home: HomeLocation?) {
        dataStore.edit {
            if (home == null) {
                it.remove(HOME_LAT); it.remove(HOME_LON); it.remove(HOME_LABEL)
            } else {
                it[HOME_LAT] = home.point.latitude
                it[HOME_LON] = home.point.longitude
                it[HOME_LABEL] = home.label
            }
        }
    }

    override val travelMode: Flow<TravelMode> = dataStore.data.map { p ->
        p[TRAVEL_MODE]?.let { runCatching { TravelMode.valueOf(it) }.getOrNull() } ?: TravelMode.TRANSIT
    }.distinctUntilChanged()

    override suspend fun setTravelMode(mode: TravelMode) {
        dataStore.edit { it[TRAVEL_MODE] = mode.name }
    }

    override val leaveBufferMinutes: Flow<Int> =
        dataStore.data.map { it[LEAVE_BUFFER] ?: DEFAULT_LEAVE_BUFFER_MINUTES }.distinctUntilChanged()

    override suspend fun setLeaveBufferMinutes(minutes: Int) {
        dataStore.edit { it[LEAVE_BUFFER] = minutes }
    }

    override val leaveRemindersEnabled: Flow<Boolean> =
        dataStore.data.map { it[LEAVE_REMINDERS] ?: true }.distinctUntilChanged()

    override suspend fun setLeaveRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[LEAVE_REMINDERS] = enabled }
    }

    override val homeworkReminderHours: Flow<Int> =
        dataStore.data.map { it[HOMEWORK_REMINDER_HOURS] ?: UserPreferencesRepository.DEFAULT_HOMEWORK_REMINDER_HOURS }
            .distinctUntilChanged()

    override suspend fun setHomeworkReminderHours(hours: Int) {
        dataStore.edit { it[HOMEWORK_REMINDER_HOURS] = hours }
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { p ->
        p[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    }.distinctUntilChanged()

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HOMEWORK_REMINDER_HOURS = intPreferencesKey("homework_reminder_hours")
        val HOME_LAT = doublePreferencesKey("home_lat")
        val HOME_LON = doublePreferencesKey("home_lon")
        val HOME_LABEL = stringPreferencesKey("home_label")
        val TRAVEL_MODE = stringPreferencesKey("travel_mode")
        val LEAVE_BUFFER = intPreferencesKey("leave_buffer_minutes")
        val LEAVE_REMINDERS = booleanPreferencesKey("leave_reminders")
        val CHANGE_NOTIFICATIONS = booleanPreferencesKey("change_notifications")
        val GROUP_NAME = stringPreferencesKey("group_name")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
    }
}
