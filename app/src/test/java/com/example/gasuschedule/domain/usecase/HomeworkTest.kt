package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.HomeworkGroup
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.HomeworkPlanning
import com.example.gasuschedule.presentation.homework.dueText
import com.example.gasuschedule.testutil.FakeHomeworkRepository
import com.example.gasuschedule.testutil.FakeReplanTrigger
import com.example.gasuschedule.testutil.MOSCOW
import com.example.gasuschedule.testutil.clockAt
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

class HomeworkTest {

    private val created = Instant.parse("2026-09-28T07:00:00Z")
    private fun hw(id: String, due: LocalDate?, subject: String = "Физика", done: Boolean = false, at: Instant = created) =
        HomeworkItem(id, null, subject, "задание $id", due, done, at)

    // Физика: 30.09 3-я пара (12:30), 07.10 1-я пара (09:00); Философия 01.10.
    private val lessons = listOf(
        lesson(d(30), 3, "Физика"),
        lesson(d(1, 10), 3, "Философия"),
        lesson(d(7, 10), 1, "Физика"),
    )

    @Test
    fun `срок - начало пары по предмету в день сдачи, иначе 9 утра`() {
        assertEquals(d(30).atTime(12, 30), HomeworkPlanning.deadline(hw("a", d(30)), lessons))
        assertEquals("регистр не важен", d(30).atTime(12, 30), HomeworkPlanning.deadline(hw("a", d(30), " физика "), lessons))
        assertEquals(d(29).atTime(9, 0), HomeworkPlanning.deadline(hw("b", d(29)), lessons))
        assertNull(HomeworkPlanning.deadline(hw("c", null), lessons))
    }

    @Test
    fun `следующая пара по предмету`() {
        assertEquals(d(30), HomeworkPlanning.nextLessonDate("Физика", d(28), lessons))
        assertEquals(d(7, 10), HomeworkPlanning.nextLessonDate("Физика", d(30), lessons))
        assertNull(HomeworkPlanning.nextLessonDate("Химия", d(28), lessons))
    }

    @Test
    fun `группы - горят, эта неделя, позже, без срока, выполненные`() {
        val today = d(28) // понедельник; воскресенье — 04.10
        val items = listOf(
            hw("late", d(25)), hw("today", d(28)), hw("tomorrow", d(29)),
            hw("week", d(4, 10)), hw("later", d(5, 10)), hw("none", null), hw("done", d(29), done = true),
        )
        val groups = HomeworkPlanning.grouped(items, today)
        assertEquals(HomeworkGroup.entries.toList(), groups.keys.toList())
        assertEquals(listOf("late", "today", "tomorrow"), groups.getValue(HomeworkGroup.URGENT).map { it.id })
        assertEquals(listOf("week"), groups.getValue(HomeworkGroup.THIS_WEEK).map { it.id })
        assertEquals(listOf("later"), groups.getValue(HomeworkGroup.LATER).map { it.id })
        assertEquals(3, HomeworkPlanning.urgentCount(items, today))
    }

    @Test
    fun `подписи срока`() {
        assertEquals("просрочено", dueText(d(27), d(28)))
        assertEquals("сегодня", dueText(d(28), d(28)))
        assertEquals("завтра", dueText(d(29), d(28)))
        assertEquals("до СР 30.09", dueText(d(30), d(28)))
    }

    @Test
    fun `напоминание о дедлайне - за N часов, только невыполненные и только в будущем`() {
        val clock = clockAt(d(29), 18) // вторник 18:00
        val items = listOf(
            hw("physics", d(30)), // дедлайн 30.09 12:30 -> за 12 ч = 00:30 30.09
            hw("done", d(30), done = true),
            hw("past", d(29)), // дедлайн 29.09 09:00 — уже прошёл
            hw("nodue", null),
        )
        val reminders = ScheduleNotificationsUseCase.buildHomeworkReminders(items, lessons, 12, clock)
        assertEquals(1, reminders.size)
        with(reminders.single()) {
            assertEquals("hw|physics", key)
            assertEquals(ZonedDateTime.of(2026, 9, 30, 0, 30, 0, 0, MOSCOW).toInstant(), triggerAt)
            assertEquals("Сдать сегодня к 12:30 · Физика", title)
            assertEquals("задание physics", text)
        }
        // За сутки: в 18:00 вторника момент "вторник 12:30" уже прошёл — напоминания нет,
        // а утром вторника оно ещё впереди.
        assertTrue(ScheduleNotificationsUseCase.buildHomeworkReminders(items, lessons, 24, clock).isEmpty())
        val dayBefore = ScheduleNotificationsUseCase.buildHomeworkReminders(items, lessons, 24, clockAt(d(29), 8)).single()
        assertEquals("Сдать завтра к 12:30 · Физика", dayBefore.title)
        assertTrue(ScheduleNotificationsUseCase.buildHomeworkReminders(items, lessons, 0, clock).isEmpty())
    }

    @Test
    fun `сохранение - новое, правка сохраняет статус и дату создания, всё пересчитывает будильники`() = runTest {
        val repo = FakeHomeworkRepository()
        val replan = FakeReplanTrigger()
        val manage = ManageHomeworkUseCase(repo, replan, clockAt(d(28), 10))

        manage.save(null, "lesson-1", " Физика ", " Лаба 3 ", d(30))
        val created = repo.items.value.single()
        assertEquals("Физика", created.subject)
        assertEquals("Лаба 3", created.description)
        assertEquals("lesson-1", created.lessonId)

        manage.setDone(created.id, true)
        manage.save(created.id, null, "Физика", "Лаба 3 и 4", d(7, 10))
        with(repo.items.value.single()) {
            assertEquals("Лаба 3 и 4", description)
            assertEquals(d(7, 10), dueDate)
            assertTrue(isDone)
            assertEquals(created.createdAt, createdAt)
            assertEquals("lesson-1", lessonId)
        }
        manage.delete(created.id)
        assertTrue(repo.items.value.isEmpty())
        assertEquals(4, replan.requests)
    }
}
