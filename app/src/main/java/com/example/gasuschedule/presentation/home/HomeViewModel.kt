package com.example.gasuschedule.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gasuschedule.domain.model.DayWeather
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.HomeworkGroup
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.HomeworkPlanning
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.ScheduleWeek
import com.example.gasuschedule.domain.model.SubjectFilter
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.repository.HomeworkRepository
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.repository.WeatherRepository
import com.example.gasuschedule.domain.usecase.EstimateLeaveTimeUseCase
import com.example.gasuschedule.domain.usecase.LeaveEstimate
import com.example.gasuschedule.domain.usecase.SyncResult
import com.example.gasuschedule.domain.usecase.SyncScheduleUseCase
import com.example.gasuschedule.presentation.common.errorMessage
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

/** День в полоске недели в шапке главной. */
data class HomeWeekDay(val date: LocalDate, val lessons: Int)

/** Дорога к ближайшей первой паре дня. */
data class Commute(val lesson: Lesson, val leave: LeaveEstimate)

data class HomeUiState(
    val loaded: Boolean = false,
    val group: String? = null,
    val now: LocalDateTime = LocalDateTime.MIN,
    val week: ScheduleWeek? = null,
    /**
     * День, чьи пары показаны: сегодня, а через 20 минут после последней пары
     * (или если сегодня пар нет) — ближайший учебный день.
     */
    val shownDate: LocalDate? = null,
    /** Сегодня пары были, но уже закончились. */
    val todayFinished: Boolean = false,
    val lessons: List<Lesson> = emptyList(),
    val commute: Commute? = null,
    val home: HomeLocation? = null,
    val mode: TravelMode = TravelMode.TRANSIT,
    /** Невыполненные задания со сроком сегодня/завтра или просроченные. */
    val urgentHomework: List<HomeworkItem> = emptyList(),
    /** Задания по парам, на которых их сдавать (id пары -> задания). */
    val homework: Map<String, List<HomeworkItem>> = emptyMap(),
    /** Погода на сегодня; null — не загрузилась (блок тогда не показываем). */
    val weather: DayWeather? = null,
    val refreshing: Boolean = false,
    /** Пн–Сб недели для полоски в шапке (в воскресенье — следующая неделя) и число пар в каждый день. */
    val weekDays: List<HomeWeekDay> = emptyList(),
    /** Отключённые в настройках предметы — их пары серые и не учитываются в дороге. */
    val subjectFilter: SubjectFilter = SubjectFilter(),
) {
    val today: LocalDate get() = now.toLocalDate()
}

/** Главная: пары на сегодня, дорога к первой паре, (позже) погода. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val preferences: UserPreferencesRepository,
    private val sync: SyncScheduleUseCase,
    private val clock: Clock,
    homework: HomeworkRepository,
    private val weatherRepository: WeatherRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Текущее время раз в 30 секунд — "СЕЙЧАС" и дорога переключаются без перезахода. */
    private val now: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now(clock))
            delay(30_000)
        }
    }

    private val data = preferences.groupName.filterNotNull().flatMapLatest { group ->
        val today = LocalDate.now(clock)
        // С понедельника — чтобы в полоске недели были и прошедшие дни.
        combine(
            repository.observeLessons(group, weekMonday(today), today.plusDays(LOOKAHEAD_DAYS)),
            repository.observeWeeks(group),
        ) { lessons, weeks -> Triple(group, lessons, weeks) }
    }

    private val road = combine(preferences.home, preferences.travelMode, preferences.leaveBufferMinutes, ::Triple)

    val state: StateFlow<HomeUiState> = combine(
        data,
        road,
        now,
        combine(refreshing, weatherRepository.weather, preferences.subjectFilter, ::Triple),
        homework.observeAll(),
    ) { (group, lessons, weeks), (home, mode, buffer), now, (refreshing, weather, filter), tasks ->
        val byDate = lessons.groupBy { it.date }
        val shown = shownDate(byDate, now)
        HomeUiState(
            todayFinished = todayFinished(byDate, now),
            loaded = true,
            group = group,
            now = now,
            week = weeks.firstOrNull { now.toLocalDate() in it.startDate..it.endDate },
            shownDate = shown,
            lessons = shown?.let { byDate[it] }.orEmpty(),
            commute = nextFirstLesson(filter.enabled(lessons), now)?.let {
                Commute(it, EstimateLeaveTimeUseCase.estimate(it, home, mode, buffer))
            },
            home = home,
            mode = mode,
            refreshing = refreshing,
            weather = weather,
            homework = HomeworkPlanning.byLesson(tasks, lessons),
            weekDays = (0L..5L).map { weekMonday(now.toLocalDate()).plusDays(it) }
                .map { HomeWeekDay(it, byDate[it].orEmpty().size) },
            urgentHomework = tasks.filter { HomeworkPlanning.group(it, now.toLocalDate()) == HomeworkGroup.URGENT }
                .sortedWith(compareBy(nullsLast()) { it.dueDate }),
            subjectFilter = filter,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Погода: при открытии и при смене адреса дома (прогноз берётся для дома).
        viewModelScope.launch { preferences.home.collect { weatherRepository.refreshIfStale() } }
        // Главная — стартовый экран: если данные устарели, обновляем при открытии.
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

    companion object {
        private const val LOOKAHEAD_DAYS = 14L
        private val STALE_AFTER = Duration.ofHours(3)

        /** Через сколько после конца последней пары главная переключается на следующий день. */
        val SWITCH_AFTER_LAST_LESSON: Duration = Duration.ofMinutes(20)

        /** Понедельник недели для полоски в шапке; в воскресенье — уже следующей недели. */
        fun weekMonday(today: LocalDate): LocalDate =
            (if (today.dayOfWeek == DayOfWeek.SUNDAY) today.plusDays(1) else today)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** Сегодняшние пары были, но закончились (с запасом [SWITCH_AFTER_LAST_LESSON]). */
        fun todayFinished(byDate: Map<LocalDate, List<Lesson>>, now: LocalDateTime): Boolean {
            val lastEnd = byDate[now.toLocalDate()].orEmpty().maxOfOrNull { it.endTime } ?: return false
            return !now.isBefore(now.toLocalDate().atTime(lastEnd).plus(SWITCH_AFTER_LAST_LESSON))
        }

        /**
         * Сегодня, пока пары идут и ещё 20 минут после последней; потом — ближайший следующий
         * день с парами. Если сегодня пар нет — тоже ближайший день (или null).
         */
        fun shownDate(byDate: Map<LocalDate, List<Lesson>>, now: LocalDateTime): LocalDate? {
            val today = now.toLocalDate()
            if (byDate[today].orEmpty().isNotEmpty() && !todayFinished(byDate, now)) return today
            return byDate.keys.filter { it.isAfter(today) }.minOrNull()
        }

        /**
         * Первая пара ближайшего учебного дня, к которой ещё предстоит выходить из дома:
         * сегодняшняя, если она не началась, иначе — первая пара следующего дня с парами.
         */
        fun nextFirstLesson(lessons: List<Lesson>, now: LocalDateTime): Lesson? =
            lessons.groupBy { it.date }
                .filterKeys { !it.isBefore(now.toLocalDate()) }
                .toSortedMap()
                .values
                .map { day -> day.minWith(compareBy({ it.lessonNumber }, { it.id })) }
                .firstOrNull { it.date.atTime(it.startTime).isAfter(now) }
    }
}
