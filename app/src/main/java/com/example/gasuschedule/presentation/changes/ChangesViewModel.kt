package com.example.gasuschedule.presentation.changes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Карточка ленты: все изменения одной пары, найденные за одну сверку. */
data class ChangeCard(
    val key: String,
    val date: LocalDate,
    val lessonNumber: Int,
    val subject: String,
    val changes: List<ScheduleChange>,
    val detectedAt: Instant,
    /** Новая — не была просмотрена до открытия экрана (полоса слева, как в макете). */
    val isNew: Boolean,
)

data class ChangeDay(val date: LocalDate, val cards: List<ChangeCard>)

data class ChangesUiState(
    val loaded: Boolean = false,
    val lastSyncAt: Instant? = null,
    val upcoming: List<ChangeDay> = emptyList(),
    val past: List<ChangeDay> = emptyList(),
) {
    val isEmpty: Boolean get() = upcoming.isEmpty() && past.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChangesViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {

    /**
     * Какие замены были новыми в момент открытия экрана. В базе они сразу помечаются просмотренными
     * (гаснет бейдж и уведомление), но на экране остаются выделенными до ухода с него.
     */
    private var newIds: Set<Long>? = null

    val state: StateFlow<ChangesUiState> = preferences.groupName.filterNotNull().flatMapLatest { group ->
        combine(repository.observeChanges(group), preferences.lastSyncAt) { changes, lastSync ->
            val highlight = newIds ?: changes.filter { !it.seen }.map { it.id }.toSet().also { newIds = it }
            val (upcoming, past) = buildChangeDays(changes, highlight, LocalDate.now(clock))
            ChangesUiState(loaded = true, lastSyncAt = lastSync, upcoming = upcoming, past = past)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChangesUiState())

    init {
        viewModelScope.launch {
            val group = preferences.groupName.filterNotNull().first()
            state.first { it.loaded }
            repository.markChangesSeen(group)
        }
    }

    companion object {
        /** Предстоящие дни — по возрастанию даты, прошедшие — от недавних к давним. */
        fun buildChangeDays(
            changes: List<ScheduleChange>,
            newIds: Set<Long>,
            today: LocalDate,
        ): Pair<List<ChangeDay>, List<ChangeDay>> {
            val cards = changes
                .groupBy { it.lessonId to it.detectedAt }
                .map { (key, list) ->
                    val first = list.first()
                    ChangeCard(
                        key = "${key.first}@${key.second.toEpochMilli()}",
                        date = first.date,
                        lessonNumber = first.lessonNumber,
                        subject = first.subject,
                        changes = list.sortedBy { it.type.ordinal },
                        detectedAt = key.second,
                        isNew = list.any { it.id in newIds },
                    )
                }
            fun days(list: List<ChangeCard>, dateOrder: Comparator<LocalDate>) = list
                .groupBy { it.date }
                .toSortedMap(dateOrder)
                .map { (date, dayCards) ->
                    ChangeDay(date, dayCards.sortedWith(compareBy<ChangeCard> { it.lessonNumber }.thenByDescending { it.detectedAt }))
                }
            val (upcoming, past) = cards.partition { !it.date.isBefore(today) }
            return days(upcoming, naturalOrder()) to days(past, reverseOrder())
        }
    }
}
