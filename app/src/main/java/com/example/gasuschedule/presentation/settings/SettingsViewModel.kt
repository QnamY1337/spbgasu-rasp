package com.example.gasuschedule.presentation.settings

import com.example.gasuschedule.presentation.common.networkMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.ScheduleNetworkException
import com.example.gasuschedule.domain.model.ThemeMode
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.repository.AddressSearch
import com.example.gasuschedule.domain.repository.ReminderReplanTrigger
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.repository.WidgetUpdater
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Раздел "Дорога до вуза". */
data class RoadSettings(
    val home: HomeLocation? = null,
    val mode: TravelMode = TravelMode.TRANSIT,
    val bufferMinutes: Int = UserPreferencesRepository.DEFAULT_LEAVE_BUFFER_MINUTES,
    val leaveReminders: Boolean = true,
)

data class SettingsUiState(
    val group: String? = null,
    val remindersEnabled: Boolean = true,
    val reminderMinutes: Int = UserPreferencesRepository.DEFAULT_REMINDER_MINUTES,
    val changeNotificationsEnabled: Boolean = true,
    /** 0 — не напоминать о дедлайнах заданий. */
    val homeworkReminderHours: Int = UserPreferencesRepository.DEFAULT_HOMEWORK_REMINDER_HOURS,
    val lastSyncAt: Instant? = null,
    val refreshing: Boolean = false,
    val road: RoadSettings = RoadSettings(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

private data class NotificationSettings(
    val reminders: Boolean,
    val minutes: Int,
    val changes: Boolean,
    val homeworkHours: Int,
)

/** Диалог выбора адреса дома. */
data class AddressSearchState(
    val results: List<HomeLocation> = emptyList(),
    val searching: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val sync: SyncScheduleUseCase,
    private val reminders: ReminderReplanTrigger,
    private val widgets: WidgetUpdater,
    private val backgroundSync: BackgroundSync,
    private val addressSearch: AddressSearch,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val road = combine(
        preferences.home,
        preferences.travelMode,
        preferences.leaveBufferMinutes,
        preferences.leaveRemindersEnabled,
        ::RoadSettings,
    )

    val state: StateFlow<SettingsUiState> = combine(
        combine(preferences.groupName, preferences.lastSyncAt, refreshing, ::Triple),
        combine(
            preferences.remindersEnabled,
            preferences.reminderMinutes,
            preferences.changeNotificationsEnabled,
            preferences.homeworkReminderHours,
            ::NotificationSettings,
        ),
        road,
        preferences.themeMode,
    ) { (group, lastSync, refreshing), (reminders, minutes, changes, homeworkHours), road, theme ->
        SettingsUiState(
            group = group,
            remindersEnabled = reminders,
            reminderMinutes = minutes,
            changeNotificationsEnabled = changes,
            homeworkReminderHours = homeworkHours,
            lastSyncAt = lastSync,
            refreshing = refreshing,
            road = road,
            themeMode = theme,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _address = MutableStateFlow(AddressSearchState())
    val address: StateFlow<AddressSearchState> = _address

    /** Только в debug-сборке: фоновая сверка с уведомлением о заменах прямо сейчас. */
    fun runBackgroundSyncNow() {
        backgroundSync.runNowForDebug()
        viewModelScope.launch { _messages.send("Фоновая сверка запущена") }
    }

    fun setHomeworkReminderHours(hours: Int) = viewModelScope.launch {
        preferences.setHomeworkReminderHours(hours)
        reminders.requestReplan()
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }

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

    // --- Дорога до вуза: всё влияет на "Пора выходить" и плашку виджета ---

    private fun roadChanged() {
        reminders.requestReplan()
        widgets.requestUpdate()
    }

    fun setTravelMode(mode: TravelMode) = viewModelScope.launch {
        preferences.setTravelMode(mode)
        roadChanged()
    }

    fun setLeaveBuffer(minutes: Int) = viewModelScope.launch {
        preferences.setLeaveBufferMinutes(minutes)
        roadChanged()
    }

    fun setLeaveReminders(enabled: Boolean) = viewModelScope.launch {
        preferences.setLeaveRemindersEnabled(enabled)
        roadChanged()
    }

    fun setHome(home: HomeLocation?) = viewModelScope.launch {
        preferences.setHome(home)
        _address.value = AddressSearchState()
        roadChanged()
    }

    fun searchAddress(query: String) {
        if (query.isBlank()) return
        _address.value = AddressSearchState(searching = true)
        viewModelScope.launch {
            _address.value = try {
                val found = addressSearch.search(query.trim())
                AddressSearchState(
                    results = found,
                    error = if (found.isEmpty()) "Ничего не нашлось. Попробуйте «улица, дом»." else null,
                )
            } catch (e: ScheduleNetworkException) {
                AddressSearchState(error = networkMessage(e.problem, "сервис поиска адресов"))
            }
        }
    }

    /** Дом по геолокации: подпись — адрес точки, а если не определился — координаты. */
    fun setHomeFromLocation(point: GeoPoint) {
        _address.update { it.copy(searching = true, error = null) }
        viewModelScope.launch {
            val label = addressSearch.describe(point)
                ?: "Моё местоположение (%.4f, %.4f)".format(java.util.Locale.US, point.latitude, point.longitude)
            setHome(HomeLocation(point, label))
        }
    }

    fun onLocationFailed(message: String) {
        _address.update { it.copy(searching = false, error = message) }
    }

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
