package com.example.gasuschedule.domain.model

import java.time.LocalDate

/** Пары одной учебной недели по дням (пн–вс; пустые дни — пустой список). */
data class WeekSchedule(
    /** null, если неделя ещё не опубликована на сайте. */
    val week: ScheduleWeek?,
    val days: Map<LocalDate, List<Lesson>>,
)
