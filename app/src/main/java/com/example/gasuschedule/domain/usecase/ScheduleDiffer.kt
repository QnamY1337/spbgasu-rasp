package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.ChangeType
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.domain.model.SemesterSchedule
import com.example.gasuschedule.domain.model.subjectWithType
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Находит замены между сохранённым и свежим расписанием.
 *
 * Сайт не отдаёт список замен, поэтому замена = расхождение снепшотов в слоте (дата + номер пары).
 * Чтобы не было ложных срабатываний:
 * - первая синхронизация (старого снепшота нет) замен не даёт;
 * - прошедшие дни не сравниваются;
 * - сравниваются только даты недель, присутствующих в обоих снепшотах —
 *   публикация новой недели или исчезновение старой не считаются заменами;
 * - в слоте с несколькими подгруппами занятия сопоставляются сначала целиком, потом по предмету,
 *   потом по порядку — перестановка подгрупп на сайте не даёт замен.
 */
class ScheduleDiffer @Inject constructor() {

    fun diff(
        old: SemesterSchedule,
        new: SemesterSchedule,
        today: LocalDate,
        detectedAt: Instant,
    ): List<ScheduleChange> {
        if (old.lessons.isEmpty()) return emptyList()

        fun SemesterSchedule.covers(date: LocalDate) = weeks.any { date in it.startDate..it.endDate }
        fun comparable(l: Lesson) = !l.date.isBefore(today) && old.covers(l.date) && new.covers(l.date)

        val oldSlots = old.lessons.filter(::comparable).groupBy { it.date to it.lessonNumber }
        val newSlots = new.lessons.filter(::comparable).groupBy { it.date to it.lessonNumber }

        val changes = mutableListOf<ScheduleChange>()
        for (slot in (oldSlots.keys + newSlots.keys).sortedWith(compareBy({ it.first }, { it.second }))) {
            changes += diffSlot(
                oldSlots[slot].orEmpty(), newSlots[slot].orEmpty(), new.groupName, detectedAt,
            )
        }
        return changes
    }

    private fun diffSlot(
        oldList: List<Lesson>,
        newList: List<Lesson>,
        group: String,
        detectedAt: Instant,
    ): List<ScheduleChange> {
        val olds = oldList.toMutableList()
        val news = newList.toMutableList()
        val pairs = mutableListOf<Pair<Lesson, Lesson>>()

        fun pairBy(same: (Lesson, Lesson) -> Boolean) {
            val iter = olds.iterator()
            while (iter.hasNext()) {
                val o = iter.next()
                val n = news.firstOrNull { same(o, it) } ?: continue
                news.remove(n)
                iter.remove()
                pairs += o to n
            }
        }
        pairBy { a, b -> a.subjectWithType == b.subjectWithType && a.room == b.room && a.teacher == b.teacher }
        pairBy { a, b -> a.subjectWithType == b.subjectWithType }
        pairBy { _, _ -> true }

        fun change(l: Lesson, type: ChangeType, old: String?, new: String?) = ScheduleChange(
            groupName = group,
            lessonId = l.id,
            date = l.date,
            lessonNumber = l.lessonNumber,
            subject = l.subject,
            type = type,
            oldValue = old,
            newValue = new,
            detectedAt = detectedAt,
        )

        val result = mutableListOf<ScheduleChange>()
        for ((o, n) in pairs) {
            if (o.subjectWithType != n.subjectWithType) {
                result += change(n, ChangeType.SUBJECT, o.subjectWithType, n.subjectWithType)
            }
            if (o.room != n.room) result += change(n, ChangeType.ROOM, o.room, n.room)
            if (o.teacher != n.teacher) result += change(n, ChangeType.TEACHER, o.teacher, n.teacher)
        }
        olds.forEach { result += change(it, ChangeType.CANCELLED, it.subjectWithType, null) }
        news.forEach { result += change(it, ChangeType.ADDED, null, it.subjectWithType) }
        return result
    }
}
