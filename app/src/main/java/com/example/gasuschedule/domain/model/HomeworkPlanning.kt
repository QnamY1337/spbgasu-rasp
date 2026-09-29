package com.example.gasuschedule.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Группы списка заданий. */
enum class HomeworkGroup(val title: String) {
    URGENT("Горят"),
    THIS_WEEK("На этой неделе"),
    LATER("Позже"),
    NO_DEADLINE("Без срока"),
    DONE("Выполненные"),
}

object HomeworkPlanning {
    /** Если в день сдачи нет пары по предмету — считаем дедлайном утро. */
    val DEFAULT_DEADLINE_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * Пара, на которой сдаётся задание. Если его добавили на конкретную пару и срок — её день,
     * это она. Иначе — первая пара по предмету в день сдачи, причём нужного типа
     * (задание к практике сдаётся на практике, даже если утром лекция). null — срока нет
     * или в тот день пар по предмету нет.
     */
    fun dueLesson(item: HomeworkItem, lessons: List<Lesson>): Lesson? {
        val due = item.dueDate ?: return null
        lessons.firstOrNull { it.id == item.lessonId && it.date == due }?.let { return it }
        val sameDay = lessons.filter { it.date == due && sameSubject(it.subject, item.subject) }
        return (sameDay.filter { it.type == item.lessonType }.ifEmpty { sameDay }).minByOrNull { it.startTime }
    }

    /** Когда задание нужно сдать: начало пары из [dueLesson], иначе 09:00 дня сдачи. null — срока нет. */
    fun deadline(item: HomeworkItem, lessons: List<Lesson>): LocalDateTime? {
        val due = item.dueDate ?: return null
        return due.atTime(dueLesson(item, lessons)?.startTime ?: DEFAULT_DEADLINE_TIME)
    }

    /** Задания по парам, на которых их сдавать: id пары -> задания (для строки "ДЗ" в карточке). */
    fun byLesson(items: List<HomeworkItem>, lessons: List<Lesson>): Map<String, List<HomeworkItem>> =
        items.mapNotNull { item -> dueLesson(item, lessons)?.let { it.id to item } }
            .groupBy({ it.first }, { it.second })

    /**
     * Дата следующей пары по предмету после [after] — срок "к следующей паре".
     * С [type] — следующая пара того же типа (практика к практике, лаба к лабе);
     * если такой впереди нет — любая следующая пара по предмету.
     */
    fun nextLessonDate(subject: String, after: LocalDate, lessons: List<Lesson>, type: LessonType? = null): LocalDate? {
        val next = lessons.filter { sameSubject(it.subject, subject) && it.date.isAfter(after) }
        val sameType = if (type == null || type == LessonType.OTHER) emptyList() else next.filter { it.type == type }
        return sameType.ifEmpty { next }.minOfOrNull { it.date }
    }

    /** Типы занятий предмета в расписании — для выбора "к какой паре" в редакторе. */
    fun typesOf(subject: String, lessons: List<Lesson>): List<LessonType> =
        lessons.filter { sameSubject(it.subject, subject) && it.type != LessonType.OTHER }
            .map { it.type }.distinct().sortedBy { it.ordinal }

    fun group(item: HomeworkItem, today: LocalDate): HomeworkGroup {
        val due = item.dueDate
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return when {
            item.isDone -> HomeworkGroup.DONE
            due == null -> HomeworkGroup.NO_DEADLINE
            !due.isAfter(today.plusDays(1)) -> HomeworkGroup.URGENT // просрочено, сегодня, завтра
            !due.isAfter(sunday) -> HomeworkGroup.THIS_WEEK
            else -> HomeworkGroup.LATER
        }
    }

    /** Список по группам в порядке [HomeworkGroup]; внутри — по сроку, потом по времени создания. */
    fun grouped(items: List<HomeworkItem>, today: LocalDate): Map<HomeworkGroup, List<HomeworkItem>> =
        items.groupBy { group(it, today) }
            .mapValues { (group, list) ->
                if (group == HomeworkGroup.DONE) list.sortedByDescending { it.createdAt }
                else list.sortedWith(compareBy<HomeworkItem, LocalDate?>(nullsLast()) { it.dueDate }.thenBy { it.createdAt })
            }
            .toSortedMap(compareBy { it.ordinal })

    /** Невыполненные с дедлайном сегодня/завтра или просроченные — для бейджа и главной. */
    fun urgentCount(items: List<HomeworkItem>, today: LocalDate): Int =
        items.count { group(it, today) == HomeworkGroup.URGENT }

    /** "просрочено", "сегодня", "завтра" или "до СР 07.10" ([dayName] форматирует дату). */
    fun dueLabel(due: LocalDate, today: LocalDate, dayName: (LocalDate) -> String): String =
        when (ChronoUnit.DAYS.between(today, due)) {
            in Long.MIN_VALUE..-1 -> "просрочено"
            0L -> "сегодня"
            1L -> "завтра"
            else -> "до ${dayName(due)}"
        }

    /** Предмет на сайте и в задании может отличаться регистром и пробелами. */
    fun sameSubject(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)
}
