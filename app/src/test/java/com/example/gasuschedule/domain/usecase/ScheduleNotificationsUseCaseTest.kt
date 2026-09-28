package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.testutil.FakePreferences
import com.example.gasuschedule.testutil.FakeReminderScheduler
import com.example.gasuschedule.testutil.FakeScheduleRepository
import com.example.gasuschedule.testutil.GROUP
import com.example.gasuschedule.testutil.MOSCOW
import com.example.gasuschedule.testutil.clockAt
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import com.example.gasuschedule.testutil.schedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.ZonedDateTime

class ScheduleNotificationsUseCaseTest {

    private val repo = FakeScheduleRepository()
    private val prefs = FakePreferences()
    private val scheduler = FakeReminderScheduler()

    private fun useCase(hour: Int, day: Int = 28) =
        ScheduleNotificationsUseCase(repo, prefs, scheduler, clockAt(d(day), hour))

    /** Понедельник 28.09 (1, 2, 3 пара), вторник 29.09 (2 пара), среда 30.09 (1 пара). */
    private suspend fun seed() = repo.replaceSnapshot(
        schedule(
            listOf(5),
            lesson(d(28), 1, "История России"),
            lesson(d(28), 2, "История России"),
            lesson(d(28), 3, "Философия", LessonType.PRACTICE, rooms = listOf("712/С"), teachers = listOf("Семенюк А.П.")),
            lesson(d(29), 2, "Высшая математика"),
            lesson(d(30), 1, "Физика"),
        ),
        emptyList(),
    )

    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 9, day, h, m, 0, 0, MOSCOW).toInstant()

    @Test
    fun `сегодня оставшиеся и завтра, за 15 минут, по московскому времени`() = runTest {
        seed()
        val reminders = useCase(hour = 10)() // 10:00, 1 пара уже идёт

        assertEquals(
            listOf(at(28, 10, 30), at(28, 12, 15), at(29, 10, 30)),
            reminders.map { it.triggerAt },
        )
        assertEquals(reminders, scheduler.scheduled)
        with(reminders[1]) {
            assertEquals("$GROUP|2026-09-28|3", key)
            assertEquals("Через 15 мин · Философия (пр.)", title)
            assertEquals("12:30–14:00 · 712/С · Семенюк А.П.", text)
        }
    }

    @Test
    fun `напоминание, время которого прошло, не ставится повторно`() = runTest {
        seed()
        // 10:35 — до 2 пары 10 минут, момент "за 15 минут" уже прошёл.
        val clock = Clock.fixed(at(28, 10, 35), MOSCOW)
        val reminders = ScheduleNotificationsUseCase(repo, prefs, scheduler, clock)()
        assertEquals(listOf(at(28, 12, 15), at(29, 10, 30)), reminders.map { it.triggerAt })
    }

    @Test
    fun `настройка минут`() = runTest {
        seed()
        prefs.reminderMinutes.value = 60
        val first = useCase(hour = 7)().first()
        assertEquals(at(28, 8, 0), first.triggerAt)
        assertTrue(first.title.startsWith("Через 60 мин"))
    }

    @Test
    fun `выключено или группы нет - все будильники снимаются`() = runTest {
        seed()
        prefs.remindersEnabled.value = false
        assertTrue(useCase(hour = 7)().isEmpty())
        assertEquals(1, scheduler.calls)
        assertTrue(scheduler.scheduled.isEmpty())

        val noGroup = FakePreferences(group = null)
        assertTrue(ScheduleNotificationsUseCase(repo, noGroup, scheduler, clockAt(d(28), 7))().isEmpty())
        assertEquals(2, scheduler.calls)
    }

    @Test
    fun `подгруппы в одном слоте - одно уведомление со всеми аудиториями`() = runTest {
        repo.replaceSnapshot(
            schedule(
                listOf(5),
                lesson(d(2, 10), 1, "Начертательная геометрия", LessonType.PRACTICE, listOf("421(3)/Г"), listOf("Гурьева Ю.А.")),
                lesson(d(2, 10), 1, "Начертательная геометрия", LessonType.PRACTICE, listOf("444/Г"), listOf("Ржавцев А.А."), index = 1),
            ),
            emptyList(),
        )
        val reminders = ScheduleNotificationsUseCase(repo, prefs, scheduler, clockAt(d(1, 10), 20))()
        assertEquals(1, reminders.size)
        assertEquals("Через 15 мин · Начертательная геометрия (пр.)", reminders.single().title)
        assertEquals("09:00–10:30 · 421(3)/Г, 444/Г · Гурьева Ю.А., Ржавцев А.А.", reminders.single().text)
    }

    @Test
    fun `послезавтра не планируем - это сделает следующий ежедневный запуск`() = runTest {
        seed()
        val reminders = useCase(hour = 23)()
        assertEquals(listOf(at(29, 10, 30)), reminders.map { it.triggerAt })
    }
}
