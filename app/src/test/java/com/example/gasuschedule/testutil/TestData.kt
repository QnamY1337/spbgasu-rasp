package com.example.gasuschedule.testutil

import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.domain.model.ScheduleWeek
import com.example.gasuschedule.domain.model.SemesterSchedule
import com.example.gasuschedule.domain.model.WeekParity
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

const val GROUP = "3-ТТП-26"

fun d(day: Int, month: Int = 9): LocalDate = LocalDate.of(2026, month, day)

/** Недели 4–6 осени 2026: 21.09–27.09, 28.09–04.10, 05.10–11.10. */
fun week(number: Int): ScheduleWeek {
    val start = d(31, 8).plusWeeks((number - 1).toLong())
    return ScheduleWeek(
        number = number,
        parity = if (number % 2 == 1) WeekParity.NUMERATOR else WeekParity.DENOMINATOR,
        startDate = start,
        endDate = start.plusDays(6),
    )
}

private val BELLS = listOf("09:00", "10:45", "12:30", "15:00", "16:45", "18:30").map(LocalTime::parse)

fun lesson(
    date: LocalDate,
    number: Int,
    subject: String = "Физика",
    type: LessonType = LessonType.LECTURE,
    rooms: List<String> = listOf("349/А"),
    teachers: List<String> = listOf("Кирк Я.Г."),
    index: Int = 0,
): Lesson {
    val w = (0..20).map(::week).first { date in it.startDate..it.endDate }
    val base = "$GROUP|$date|$number"
    return Lesson(
        id = if (index == 0) base else "$base#$index",
        subject = subject,
        type = type,
        teacher = teachers.joinToString(", "),
        room = rooms.joinToString(", "),
        building = rooms.firstOrNull()?.substringAfterLast('/'),
        lessonNumber = number,
        startTime = BELLS[number - 1],
        endTime = BELLS[number - 1].plusMinutes(90),
        dayOfWeek = date.dayOfWeek,
        date = date,
        weekNumber = w.number,
        weekParity = w.parity,
        groupName = GROUP,
        rooms = rooms,
        teachers = teachers,
    )
}

fun schedule(weeks: List<Int>, vararg lessons: Lesson) =
    SemesterSchedule(GROUP, weeks.map(::week), lessons.toList())

val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

fun clockAt(date: LocalDate, hour: Int = 10): Clock =
    Clock.fixed(ZonedDateTime.of(date, LocalTime.of(hour, 0), MOSCOW).toInstant(), MOSCOW)
