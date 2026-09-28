package com.example.gasuschedule.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class WeekParity { NUMERATOR, DENOMINATOR, EVERY }

enum class LessonType { LECTURE, PRACTICE, LAB, OTHER }

data class Lesson(
    /** Стабильный id: группа + дата + номер пары (+ индекс, если в слоте несколько занятий). */
    val id: String,
    val subject: String,
    val type: LessonType,
    /** Все преподаватели через запятую — для отображения. */
    val teacher: String,
    /** Все аудитории через запятую — для отображения. */
    val room: String,
    /** Код корпуса первой аудитории: "712/С" -> "С". */
    val building: String?,
    val lessonNumber: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val dayOfWeek: DayOfWeek,
    val date: LocalDate,
    val weekNumber: Int,
    val weekParity: WeekParity,
    val groupName: String,
    /** Подгруппы: у одной пары бывает несколько аудиторий и преподавателей. */
    val rooms: List<String> = listOf(room),
    val teachers: List<String> = listOf(teacher),
    /** Текст из блока .ibfo_para (ссылки/коды Teams и т.п.), если есть. */
    val note: String? = null,
)

data class ScheduleWeek(
    val number: Int,
    val parity: WeekParity,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

/** Всё расписание группы на семестр — результат одного запроса к сайту. */
data class SemesterSchedule(
    val groupName: String,
    val weeks: List<ScheduleWeek>,
    val lessons: List<Lesson>,
) {
    /** Сайт и для несуществующей группы отдаёт каркас недель, только без пар. */
    val isEmpty: Boolean get() = lessons.isEmpty()
}
