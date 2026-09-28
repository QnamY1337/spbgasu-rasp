package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.data.remote.Fixtures
import com.example.gasuschedule.data.remote.ScheduleHtmlParser
import com.example.gasuschedule.data.remote.dto.BitrixAjaxResponse
import com.example.gasuschedule.domain.model.ChangeType
import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.domain.model.SemesterSchedule
import com.example.gasuschedule.testutil.GROUP
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import com.example.gasuschedule.testutil.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ScheduleDifferTest {

    private val differ = ScheduleDiffer()
    private val now = Instant.parse("2026-09-28T07:00:00Z")
    private val weeks = listOf(4, 5, 6)

    private fun diff(old: SemesterSchedule, new: SemesterSchedule, today: java.time.LocalDate = d(28)) =
        differ.diff(old, new, today, now)

    /** Компактная запись для сравнения: "2026-09-29 3 ROOM 349/А -> 220/Г". */
    private fun List<ScheduleChange>.brief() =
        map { "${it.date} ${it.lessonNumber} ${it.type} ${it.oldValue} -> ${it.newValue}" }

    @Test
    fun `первая синхронизация - замен нет`() {
        val new = schedule(weeks, lesson(d(29), 3))
        assertTrue(diff(schedule(emptyList()), new).isEmpty())
    }

    @Test
    fun `одинаковые снепшоты - замен нет`() {
        val s = schedule(weeks, lesson(d(29), 3), lesson(d(30), 1, "Философия"))
        assertTrue(diff(s, s.copy()).isEmpty())
    }

    @Test
    fun `смена аудитории`() {
        val changes = diff(
            schedule(weeks, lesson(d(29), 3)),
            schedule(weeks, lesson(d(29), 3, rooms = listOf("220/Г"))),
        )
        assertEquals(listOf("2026-09-29 3 ROOM 349/А -> 220/Г"), changes.brief())
        with(changes.single()) {
            assertEquals("$GROUP|2026-09-29|3", lessonId)
            assertEquals("Физика", subject)
            assertEquals(GROUP, groupName)
            assertEquals(now, detectedAt)
        }
    }

    @Test
    fun `смена преподавателя и аудитории сразу - две записи`() {
        val changes = diff(
            schedule(weeks, lesson(d(29), 3)),
            schedule(weeks, lesson(d(29), 3, rooms = listOf("220/Г"), teachers = listOf("Дронов В.М."))),
        )
        assertEquals(
            listOf(
                "2026-09-29 3 ROOM 349/А -> 220/Г",
                "2026-09-29 3 TEACHER Кирк Я.Г. -> Дронов В.М.",
            ),
            changes.brief(),
        )
    }

    @Test
    fun `другой предмет в слоте - замена предмета, остальное тоже сравнивается`() {
        val changes = diff(
            schedule(weeks, lesson(d(30), 2)),
            schedule(weeks, lesson(d(30), 2, "Высшая математика", LessonType.PRACTICE, teachers = listOf("Священко В.А."))),
        )
        assertEquals(
            listOf(
                "2026-09-30 2 SUBJECT Физика (л.) -> Высшая математика (пр.)",
                "2026-09-30 2 TEACHER Кирк Я.Г. -> Священко В.А.",
            ),
            changes.brief(),
        )
        assertEquals("Высшая математика", changes.first().subject)
    }

    @Test
    fun `лекцию заменили практикой - это замена предмета`() {
        val changes = diff(
            schedule(weeks, lesson(d(30), 2)),
            schedule(weeks, lesson(d(30), 2, type = LessonType.PRACTICE)),
        )
        assertEquals(listOf("2026-09-30 2 SUBJECT Физика (л.) -> Физика (пр.)"), changes.brief())
    }

    @Test
    fun `пару отменили и добавили новую`() {
        val changes = diff(
            schedule(weeks, lesson(d(29), 3), lesson(d(30), 1)),
            schedule(weeks, lesson(d(30), 1), lesson(d(2, 10), 5, "Философия")),
        )
        assertEquals(
            listOf(
                "2026-09-29 3 CANCELLED Физика (л.) -> null",
                "2026-10-02 5 ADDED null -> Философия (л.)",
            ),
            changes.brief(),
        )
        assertEquals("Физика", changes.first().subject)
    }

    @Test
    fun `прошедшие дни не сравниваются`() {
        val changes = diff(
            schedule(weeks, lesson(d(29), 3), lesson(d(1, 10), 1)),
            schedule(weeks, lesson(d(29), 3, rooms = listOf("220/Г")), lesson(d(1, 10), 1, rooms = listOf("220/Г"))),
            today = d(30),
        )
        assertEquals(listOf("2026-10-01 1 ROOM 349/А -> 220/Г"), changes.brief())
    }

    @Test
    fun `сегодняшние пары сравниваются`() {
        val changes = diff(
            schedule(weeks, lesson(d(28), 1)),
            schedule(weeks, lesson(d(28), 1, rooms = listOf("220/Г"))),
            today = d(28),
        )
        assertEquals(1, changes.size)
    }

    @Test
    fun `публикация новой недели - не добавленные пары`() {
        val changes = diff(
            schedule(listOf(4, 5), lesson(d(29), 3)),
            schedule(listOf(4, 5, 6), lesson(d(29), 3), lesson(d(6, 10), 1), lesson(d(7, 10), 2)),
        )
        assertTrue(changes.brief().toString(), changes.isEmpty())
    }

    @Test
    fun `неделя пропала из выдачи - не отменённые пары`() {
        val changes = diff(
            schedule(listOf(5, 6), lesson(d(29), 3), lesson(d(6, 10), 1)),
            schedule(listOf(5), lesson(d(29), 3)),
        )
        assertTrue(changes.isEmpty())
    }

    @Test
    fun `подгруппы поменялись местами - замен нет`() {
        val a = { i: Int -> lesson(d(2, 10), 1, "Начертательная геометрия", rooms = listOf("421(1)/Г"), teachers = listOf("Гурьева Ю.А."), index = i) }
        val b = { i: Int -> lesson(d(2, 10), 1, "Начертательная геометрия", rooms = listOf("444/Г"), teachers = listOf("Ржавцев А.А."), index = i) }
        assertTrue(diff(schedule(weeks, a(0), b(1)), schedule(weeks, b(0), a(1))).isEmpty())
    }

    @Test
    fun `у одной подгруппы сменилась аудитория - одна замена у нужной подгруппы`() {
        val old = schedule(
            weeks,
            lesson(d(2, 10), 1, "Информатика", rooms = listOf("421/Г"), teachers = listOf("Гурьева Ю.А.")),
            lesson(d(2, 10), 1, "Английский язык", rooms = listOf("444/Г"), teachers = listOf("Ржавцев А.А."), index = 1),
        )
        val new = schedule(
            weeks,
            lesson(d(2, 10), 1, "Английский язык", rooms = listOf("512/С"), teachers = listOf("Ржавцев А.А.")),
            lesson(d(2, 10), 1, "Информатика", rooms = listOf("421/Г"), teachers = listOf("Гурьева Ю.А."), index = 1),
        )
        val changes = diff(old, new)
        assertEquals(listOf("2026-10-02 1 ROOM 444/Г -> 512/С"), changes.brief())
        assertEquals("Английский язык", changes.single().subject)
    }

    @Test
    fun `реальное расписание - сам с собой без замен, одна правка даёт одну замену`() {
        val html = (BitrixAjaxResponse.parse(Fixtures.raspJson) as BitrixAjaxResponse.Success).html
        val real = ScheduleHtmlParser().parse(html, GROUP)
        assertTrue(diff(real, ScheduleHtmlParser().parse(html, GROUP)).isEmpty())

        // Та самая замена из спецификации: "Экономическая грамотность: ауд. 718/С -> 812/С".
        val target = real.lessons.single { it.date == d(29) && it.lessonNumber == 3 }
        val edited = real.copy(lessons = real.lessons.map {
            if (it.id == target.id) it.copy(room = "812/С", rooms = listOf("812/С")) else it
        })
        val changes = diff(real, edited)
        assertEquals(listOf("2026-09-29 3 ROOM 718/С -> 812/С"), changes.brief())
        assertEquals(ChangeType.ROOM, changes.single().type)
        assertEquals("Экономическая грамотность в условиях цифровой трансформации", changes.single().subject)
    }
}
