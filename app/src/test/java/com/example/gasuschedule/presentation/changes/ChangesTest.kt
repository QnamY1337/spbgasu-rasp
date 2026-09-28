package com.example.gasuschedule.presentation.changes

import com.example.gasuschedule.domain.model.ChangeType
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.testutil.GROUP
import com.example.gasuschedule.testutil.MOSCOW
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.work.BackgroundSyncPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

class ChangesTest {

    private val morning = Instant.parse("2026-09-28T04:12:00Z") // 07:12 МСК
    private val evening = Instant.parse("2026-09-28T15:00:00Z")

    private var nextId = 1L
    private fun change(
        date: LocalDate, number: Int, type: ChangeType, old: String?, new: String?,
        subject: String = "Философия", at: Instant = morning, seen: Boolean = false,
    ) = ScheduleChange(
        id = nextId++, groupName = GROUP, lessonId = "$GROUP|$date|$number", date = date,
        lessonNumber = number, subject = subject, type = type, oldValue = old, newValue = new,
        detectedAt = at, seen = seen,
    )

    @Test
    fun `тексты замен`() {
        assertEquals("Философия: ауд. 709/С → 712/С", change(d(28), 3, ChangeType.ROOM, "709/С", "712/С").summary())
        assertEquals(
            "Экономическая грамотность: Веронская М.В. → Кузнецова О.И.",
            change(d(26), 3, ChangeType.TEACHER, "Веронская М.В.", "Кузнецова О.И.", "Экономическая грамотность").summary(),
        )
        assertEquals("Высшая математика — пара отменена",
            change(d(26), 2, ChangeType.CANCELLED, "Высшая математика (л.)", null, "Высшая математика").summary())
        assertEquals("добавлена пара: Физика (лаб.)", change(d(30), 5, ChangeType.ADDED, null, "Физика (лаб.)", "Физика").summary())
        assertEquals("ВТ 29.09, 3 пара", change(d(29), 3, ChangeType.ROOM, "a", "b").slotLabel())
    }

    @Test
    fun `уведомление - одна замена и много замен`() {
        val one = changeNotificationContent(listOf(change(d(29), 3, ChangeType.ROOM, "718/С", "812/С")))
        assertEquals("Изменение в расписании", one.title)
        assertEquals(listOf("ВТ 29.09, 3 пара · Философия: ауд. 718/С → 812/С"), one.lines)

        val many = changeNotificationContent((1..7).map { change(d(30), (it % 6) + 1, ChangeType.ROOM, "1", "2") })
        assertEquals("Изменения в расписании: 7", many.title)
        assertEquals(5, many.lines.size)
        assertEquals(2, many.overflow)
    }

    @Test
    fun `лента - предстоящие по возрастанию, прошедшие от свежих, одна карточка на пару и сверку`() {
        val room = change(d(29), 3, ChangeType.ROOM, "718/С", "812/С")
        val teacher = change(d(29), 3, ChangeType.TEACHER, "Веронская М.В.", "Кузнецова О.И.")
        val later = change(d(29), 3, ChangeType.ROOM, "812/С", "718/С", at = evening, seen = true)
        val oct = change(d(2, 10), 1, ChangeType.CANCELLED, "Физика (л.)", null, "Физика", seen = true)
        val past1 = change(d(26), 2, ChangeType.CANCELLED, "Высшая математика (л.)", null, seen = true)
        val past2 = change(d(24), 1, ChangeType.ROOM, "1", "2", seen = true)

        val (upcoming, past) = ChangesViewModel.buildChangeDays(
            listOf(oct, past2, room, later, teacher, past1),
            newIds = setOf(room.id, teacher.id),
            today = d(28),
        )
        assertEquals(listOf(d(29), d(2, 10)), upcoming.map { it.date })
        assertEquals(listOf(d(26), d(24)), past.map { it.date })

        val day29 = upcoming.first().cards
        assertEquals("свежая сверка сверху", listOf(evening, morning), day29.map { it.detectedAt })
        assertEquals(listOf(ChangeType.ROOM, ChangeType.TEACHER), day29[1].changes.map { it.type })
        assertTrue(day29[1].isNew)
        assertFalse(day29[0].isNew)
    }

    @Test
    fun `политика фоновой синхронизации - днём раз в 2 часа, ночью раз в 8`() {
        fun at(h: Int) = ZonedDateTime.of(2026, 9, 28, h, 0, 0, 0, MOSCOW)
        fun hoursAgo(now: ZonedDateTime, h: Long) = now.minusHours(h).toInstant()

        assertTrue(BackgroundSyncPolicy.shouldSync(at(12), null))
        assertTrue(BackgroundSyncPolicy.shouldSync(at(12), hoursAgo(at(12), 3)))
        assertFalse("недавно обновили вручную", BackgroundSyncPolicy.shouldSync(at(12), hoursAgo(at(12), 1)))
        assertFalse("ночью 3 часа — рано", BackgroundSyncPolicy.shouldSync(at(2), hoursAgo(at(2), 3)))
        assertTrue(BackgroundSyncPolicy.shouldSync(at(2), hoursAgo(at(2), 9)))
        assertTrue("в 7 утра уже день", BackgroundSyncPolicy.shouldSync(at(7), hoursAgo(at(7), 3)))
    }
}
