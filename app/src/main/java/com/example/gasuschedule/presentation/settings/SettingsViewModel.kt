package com.example.gasuschedule.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasuschedule.domain.repository.ReminderReplanTrigger
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.usecase.SyncResult
import com.example.gasuschedule.domain.usecase.SyncScheduleUseCase
import com.example.gasuschedule.presentation.common.errorMessage
import com.example.gasuschedule.work.BackgroundSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SettingsUiState(
    val group: String? = null,
    val remindersEnabled: Boolean = true,
    val reminderMinutes: Int = UserPreferencesRepository.DEFAULT_REMINDER_MINUTES,
    val changeNotificationsEnabled: Boolean = true,
    val lastSyncAt: Instant? = null,
    val refreshing: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val sync: SyncScheduleUseCase,
    private val reminders: ReminderReplanTrigger,
    private val backgroundSync: BackgroundSync,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        combine(preferences.groupName, preferences.lastSyncAt, refreshing, ::Triple),
        combine(preferences.remindersEnabled, preferences.reminderMinutes, preferences.changeNotificationsEnabled, ::Triple),
    ) { (group, lastSync, refreshing), (reminders, minutes, changes) ->
        SettingsUiState(
            group = group,
            remindersEnabled = reminders,
            reminderMinutes = minutes,
            changeNotificationsEnabled = changes,
            lastSyncAt = lastSync,
            refreshing = refreshing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Только в debug-сборке: фоновая сверка с уведомлением о заменах прямо сейчас. */
    fun runBackgroundSyncNow() {
        backgroundSync.runNowForDebug()
        viewModelScope.launch { _messages.send("Фоновая сверка запущена") }
    }

    fun setChangeNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setChangeNotificationsEnabled(enabled)
    }

    fun setRemindersEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setRemindersEnabled(enabled)
        reminders.requestReplan()
    }

    fun setReminderMinutes(minutes: Int) = viewModelScope.launch {
        preferences.setReminderMinutes(minutes)
        reminders.requestReplan()
    }

    /** Разрешения поменялись (пользователь вернулся из системных настроек) — будильники ставятся заново. */
    fun onPermissionsChanged() = reminders.requestReplan()

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            val result = sync()
            refreshing.value = false
            _messages.send(
                when (result) {
                    is SyncResult.Success ->
                        if (result.changes.isEmpty()) "Расписание актуально"
                        else "Обновлено: изменений — ${result.changes.size}"
                    else -> result.errorMessage() ?: return@launch
                }
            )
        }
    }
}
