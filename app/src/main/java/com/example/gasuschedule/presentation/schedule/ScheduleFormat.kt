package com.example.gasuschedule.presentation.schedule

import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.ScheduleWeek
import com.example.gasuschedule.domain.model.WeekParity
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class LessonTiming { PAST, CURRENT, NEXT, UPCOMING }

/**
 * Статус каждой пары относительно [now]. NEXT — ближайшая пара сегодня, если сейчас пары нет
 * (перемена или утро до первой пары): её тоже выделяем, как требует спецификация.
 */
fun lessonTimings(lessons: List<Lesson>, now: LocalDateTime): Map<String, LessonTiming> {
    val today = now.toLocalDate()
    val time = now.toLocalTime()
    val result = lessons.associate { l ->
        l.id to when {
            l.date.isBefore(today) -> LessonTiming.PAST
            l.date.isAfter(today) -> LessonTiming.UPCOMING
            !time.isBefore(l.endTime) -> LessonTiming.PAST
            !time.isBefore(l.startTime) -> LessonTiming.CURRENT
            else -> LessonTiming.UPCOMING
        }
    }.toMutableMap()
    if (LessonTiming.CURRENT !in result.values) {
        lessons.filter { result[it.id] == LessonTiming.UPCOMING && it.date == today }
            .minByOrNull { it.startTime }
            ?.let { result[it.id] = LessonTiming.NEXT }
    }
    return result
}

/** 1 пара, 2 пары, 5 пар, 11 пар, 21 пара. */
fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$n $word"
}

fun lessonsCount(n: Int) = pluralRu(n, "пара", "пары", "пар")

private val RU = Locale.forLanguageTag("ru")
private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)
private val SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM", RU)
private val SHORT_DAY = DateTimeFormatter.ofPattern("EE", RU)

/** "Понедельник, 28 сентября". */
fun dayTitle(date: LocalDate): String = DAY_TITLE.format(date).replaceFirstChar { it.titlecase(RU) }

/** "ПН". */
fun shortDayName(date: LocalDate): String = SHORT_DAY.format(date).uppercase(RU).take(2)

/** "28.09". */
fun shortDate(date: LocalDate): String = SHORT_DATE.format(date)

fun parityName(p: WeekParity): String = when (p) {
    WeekParity.NUMERATOR -> "Числитель"
    WeekParity.DENOMINATOR -> "Знаменатель"
    WeekParity.EVERY -> "Каждую неделю"
}

/** "Числитель · Нед. 5". */
fun weekPill(week: ScheduleWeek): String = "${parityName(week.parity)} · Нед. ${week.number}"

/** "через 25 мин", "через 1 ч 5 мин". */
fun startsIn(now: LocalDateTime, lesson: Lesson): String {
    val minutes = Duration.between(now, lesson.date.atTime(lesson.startTime)).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "сейчас начнётся"
        minutes < 60 -> "через $minutes мин"
        minutes % 60 == 0L -> "через ${minutes / 60} ч"
        else -> "через ${minutes / 60} ч ${minutes % 60} мин"
    }
}
