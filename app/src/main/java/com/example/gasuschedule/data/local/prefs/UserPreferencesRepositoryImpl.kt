package com.example.gasuschedule.data.local.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
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

    private companion object {
        val GROUP_NAME = stringPreferencesKey("group_name")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }
}
