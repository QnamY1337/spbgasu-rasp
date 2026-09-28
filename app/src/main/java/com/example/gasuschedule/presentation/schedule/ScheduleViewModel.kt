package com.example.gasuschedule.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasuschedule.domain.model.Buildings
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.ScheduleWeek
import com.example.gasuschedule.domain.model.WeekSchedule
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.usecase.EstimateLeaveTimeUseCase
import com.example.gasuschedule.domain.usecase.GetWeekScheduleUseCase
import com.example.gasuschedule.domain.usecase.SyncResult
import com.example.gasuschedule.domain.usecase.SyncScheduleUseCase
import com.example.gasuschedule.presentation.common.errorMessage
import com.example.gasuschedule.presentation.lessondetail.LessonDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class ScheduleUiState(
    val group: String? = null,
    /** Все дни, между которыми можно листать на вкладке "Сегодня" (семестр + сегодня). */
    val days: List<LocalDate> = emptyList(),
    val lessonsByDate: Map<LocalDate, List<Lesson>> = emptyMap(),
    val weeks: List<ScheduleWeek> = emptyList(),
    val selectedDate: LocalDate = LocalDate.MIN,
    val week: WeekSchedule? = null,
    val now: LocalDateTime = LocalDateTime.MIN,
    val isRefreshing: Boolean = false,
    val loaded: Boolean = false,
) {
    val today: LocalDate get() = now.toLocalDate()
    fun weekOf(date: LocalDate): ScheduleWeek? = weeks.firstOrNull { date in it.startDate..it.endDate }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val preferences: UserPreferencesRepository,
    private val sync: SyncScheduleUseCase,
    private val getWeek: GetWeekScheduleUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now(clock))
    /** Понедельник недели, открытой на вкладке "Неделя". */
    private val selectedWeekStart = MutableStateFlow(mondayOf(LocalDate.now(clock)))
    private val refreshing = MutableStateFlow(false)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Текущее время, раз в 30 секунд — чтобы "СЕЙЧАС" переезжало на следующую пару без перезахода. */
    private val now: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(30_000)
        }
    }

    private val semester = preferences.groupName.filterNotNull().flatMapLatest { group ->
        combine(
            repository.observeWeeks(group),
            repository.observeLessons(group, RANGE_START, RANGE_END),
        ) { weeks, lessons -> Triple(group, weeks, lessons.groupBy { it.date }) }
    }

    private val week = selectedWeekStart.flatMapLatest { getWeek(it) }

    val state: StateFlow<ScheduleUiState> = combine(
        semester, week, selectedDate, now, refreshing,
    ) { (group, weeks, byDate), week, selected, now, refreshing ->
        ScheduleUiState(
            group = group,
            days = dayRange(weeks, now.toLocalDate()),
            lessonsByDate = byDate,
            weeks = weeks,
            selectedDate = selected,
            week = week,
            now = now,
            isRefreshing = refreshing,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    private val openedLesson = MutableStateFlow<Lesson?>(null)

    /** Карточка пары (по тапу): корпус, дорога из дома, маршрут. */
    val detail: StateFlow<LessonDetail?> = openedLesson.flatMapLatest { lesson ->
        if (lesson == null) return@flatMapLatest flowOf(null)
        combine(preferences.home, preferences.travelMode, preferences.leaveBufferMinutes) { home, mode, buffer ->
            LessonDetail(
                lesson = lesson,
                building = Buildings.byCode(lesson.building),
                leave = EstimateLeaveTimeUseCase.estimate(lesson, home, mode, buffer),
                firstOfDay = EstimateLeaveTimeUseCase.isFirstOfDay(lesson, state.value.lessonsByDate[lesson.date].orEmpty()),
                home = home,
                mode = mode,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openLesson(lesson: Lesson?) {
        openedLesson.value = lesson
    }

    init {
        // Обновляем при открытии, если данные устарели (фоновая синхронизация может не успеть).
        viewModelScope.launch {
            val last = preferences.lastSyncAt.first()
            if (last == null || Duration.between(last, clock.instant()) > STALE_AFTER) refresh(quiet = true)
        }
    }

    fun refresh(quiet: Boolean = false) {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            val result = sync()
            refreshing.value = false
            when {
                result is SyncResult.Success && !quiet -> _messages.send(
                    if (result.changes.isEmpty()) "Расписание актуально"
                    else "Обновлено: изменений — ${result.changes.size}"
                )
                result !is SyncResult.Success -> result.errorMessage()?.let { _messages.send(it) }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun showWeek(offset: Long) {
        selectedWeekStart.value = selectedWeekStart.value.plusWeeks(offset)
    }

    fun showWeekOf(date: LocalDate) {
        selectedWeekStart.value = mondayOf(date)
    }

    companion object {
        private val RANGE_START = LocalDate.of(2000, 1, 1)
        private val RANGE_END = LocalDate.of(2100, 1, 1)
        private val STALE_AFTER = Duration.ofHours(3)

        fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** От начала первой опубликованной недели до конца последней; сегодня входит всегда. */
        fun dayRange(weeks: List<ScheduleWeek>, today: LocalDate): List<LocalDate> {
            val start = minOf(weeks.minOfOrNull { it.startDate } ?: today, today)
            val end = maxOf(weeks.maxOfOrNull { it.endDate } ?: today, today)
            return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        }
    }
}
