package com.example.gasuschedule.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface UserPreferencesRepository {
    /** Выбранная группа; null — онбординг ещё не пройден. */
    val groupName: Flow<String?>
    suspend fun setGroupName(name: String)

    val lastSyncAt: Flow<Instant?>
    suspend fun setLastSyncAt(at: Instant)
}
