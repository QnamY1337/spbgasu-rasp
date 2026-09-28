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
     * Когда задание нужно сдать: начало первой пары по этому предмету в день сдачи
     * (задают обычно "к следующей паре"), иначе 09:00 того дня. null — срока нет.
     */
    fun deadline(item: HomeworkItem, lessons: List<Lesson>): LocalDateTime? {
        val due = item.dueDate ?: return null
        val lessonStart = lessons
            .filter { it.date == due && sameSubject(it.subject, item.subject) }
            .minOfOrNull { it.startTime }
        return due.atTime(lessonStart ?: DEFAULT_DEADLINE_TIME)
    }

    /** Дата следующей пары по предмету после [after] — срок "к следующей паре". */
    fun nextLessonDate(subject: String, after: LocalDate, lessons: List<Lesson>): LocalDate? =
        lessons.filter { sameSubject(it.subject, subject) && it.date.isAfter(after) }.minOfOrNull { it.date }

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
