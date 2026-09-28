package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.WeekSchedule
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** Неделя (пн–вс), содержащая [date]: пары по дням + номер и чётность недели с сайта. */
class GetWeekScheduleUseCase @Inject constructor(
    private val repository: ScheduleRepository,
    private val preferences: UserPreferencesRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(date: LocalDate): Flow<WeekSchedule> {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        val emptyDays = (0L..6L).associate { monday.plusDays(it) to emptyList<Lesson>() }

        return preferences.groupName.flatMapLatest { group ->
            if (group == null) return@flatMapLatest flowOf(WeekSchedule(null, emptyDays))
            combine(
                repository.observeLessons(group, monday, sunday),
                repository.observeWeeks(group),
            ) { lessons, weeks ->
                WeekSchedule(
                    week = weeks.firstOrNull { monday in it.startDate..it.endDate },
                    days = emptyDays + lessons.groupBy { it.date },
                )
            }
        }
    }
}
