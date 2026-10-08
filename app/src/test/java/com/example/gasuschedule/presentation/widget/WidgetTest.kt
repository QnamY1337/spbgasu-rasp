package com.example.gasuschedule.presentation.widget

import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.domain.usecase.GetNextLessonUseCase
import com.example.gasuschedule.domain.usecase.NextLesson
import com.example.gasuschedule.testutil.FakePreferences
import com.example.gasuschedule.testutil.FakeScheduleRepository
import com.example.gasuschedule.testutil.clockAt
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import com.example.gasuschedule.testutil.schedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

class WidgetTest {

    // Понедельник 28.09: 1, 2, 3 пара; следующая — среда 30.09, 2 пара.
    private val lessons = listOf(
        lesson(d(28), 1, "История России", rooms = listOf("Актовый зал/Г"), teachers = listOf("Гурьев Е.П.")),
        lesson(d(28), 2, "История России", rooms = listOf("Актовый зал/Г"), teachers = listOf("Гурьев Е.П.")),
        lesson(d(28), 3, "Философия", LessonType.PRACTICE, listOf("712/С"), listOf("Семенюк А.П.")),
        lesson(d(30), 2, "Высшая математика", rooms = listOf("220/Г"), teachers = listOf("Михайлов А.Е.")),
    )

    private fun at(date: LocalDate, h: Int, m: Int = 0) = date.atTime(h, m)
    private fun pick(now: LocalDateTime) = GetNextLessonUseCase.pick(lessons, now) as NextLesson.Found

    @Test
    fun `идёт пара - показываем её до конца`() {
        val next = pick(at(d(28), 11, 0))
        assertEquals(2, next.lesson.lessonNumber)
        assertTrue(next.ongoing)
        assertEquals(at(d(28), 12, 15), next.validUntil)

        val model = widgetModel(next, at(d(28), 11, 0))
        assertEquals(WidgetModel("ИДЁТ СЕЙЧАС · ДО 12:15", "История России", "Актовый зал/Г · Гурьев Е.П."), model)
    }

    @Test
    fun `перемена - ближайшая пара сегодня, как в макете`() {
        val next = pick(at(d(28), 12, 20))
        assertEquals(3, next.lesson.lessonNumber)
        assertEquals(false, next.ongoing)
        assertEquals(at(d(28), 12, 30), next.validUntil)
        with(widgetModel(next, at(d(28), 12, 20))) {
            assertEquals("БЛИЖАЙШАЯ ПАРА · 12:30", label)
            assertEquals("Философия", title)
        }
    }

    @Test
    fun `ровно в конец пары она уже не показывается`() {
        assertEquals(3, pick(at(d(28), 12, 15)).lesson.lessonNumber)
    }

    @Test
    fun `вечером - следующий учебный день, через день пропускаем`() {
        with(widgetModel(pick(at(d(28), 20)), at(d(28), 20))) {
            assertEquals("СР 30.09 · 10:45", label)
            assertEquals("Высшая математика", title)
        }
        assertEquals("ЗАВТРА · 10:45", widgetModel(pick(at(d(29), 20)), at(d(29), 20)).label)
    }

    @Test
    fun `подгруппы - все предметы и аудитории`() {
        val slot = listOf(
            lesson(d(2, 10), 1, "Информатика", rooms = listOf("421/Г"), teachers = listOf("Гурьева Ю.А.")),
            lesson(d(2, 10), 1, "Английский язык", rooms = listOf("444/Г"), teachers = listOf("Ржавцев А.А."), index = 1),
        )
        val next = GetNextLessonUseCase.pick(slot, at(d(2, 10), 8)) as NextLesson.Found
        assertEquals(1, next.parallel.size)
        with(widgetModel(next, at(d(2, 10), 8))) {
            assertEquals("Информатика / Английский язык", title)
            assertEquals("421/Г, 444/Г · Гурьева Ю.А., Ржавцев А.А.", subtitle)
        }
    }

    @Test
    fun `пар нет и группа не выбрана`() = runTest {
        assertSame(NextLesson.NoneSoon, GetNextLessonUseCase.pick(lessons, at(d(30), 13)))
        assertEquals("Пар нет", widgetModel(NextLesson.NoneSoon, at(d(30), 13)).title)

        val noGroup = GetNextLessonUseCase(FakeScheduleRepository(), FakePreferences(group = null), clockAt(d(28)))()
        assertSame(NextLesson.NoGroup, noGroup)
        assertNull(widgetModel(noGroup, at(d(28), 9)).routeQuery)
    }

    @Test
    fun `юзкейс читает базу на две недели вперёд`() = runTest {
        val repo = FakeScheduleRepository()
        repo.replaceSnapshot(schedule(listOf(5, 6), *lessons.toTypedArray(), lesson(d(9, 10), 1, "Далеко")), emptyList())
        val next = GetNextLessonUseCase(repo, FakePreferences(), clockAt(d(28), 10))() as NextLesson.Found
        assertEquals("История России", next.lesson.subject)
        assertTrue(next.ongoing)
    }

    @Test
    fun `обновление виджета - сразу после границы, не чаще раза в минуту`() {
        assertEquals(Duration.ofMinutes(15).plusSeconds(30), WidgetRefresher.refreshDelay(at(d(28), 12, 30), at(d(28), 12, 15)))
        assertEquals(Duration.ofMinutes(1), WidgetRefresher.refreshDelay(at(d(28), 12, 30), at(d(28), 12, 30)))
    }

    @Test
    fun `отсчёт - минуты, часы, другой день и идущая пара`() {
        with(countdownModel(pick(at(d(28), 12, 20)), at(d(28), 12, 20))) {
            assertEquals("ЧЕРЕЗ", caption)
            assertEquals("10", value)
            assertEquals("мин", unit)
            assertEquals("712/С · 12:30", subtitle)
        }
        with(countdownModel(pick(at(d(28), 8)), at(d(28), 8))) {
            assertEquals("1:00", value)
            assertEquals("ч", unit)
        }
        with(countdownModel(pick(at(d(28), 20)), at(d(28), 20))) {
            assertEquals("СР 30.09", caption)
            assertEquals("10:45", value)
            assertNull(unit)
        }
        with(countdownModel(pick(at(d(28), 11, 0)), at(d(28), 11, 0))) {
            assertEquals("ИДЁТ · ДО 12:15", caption)
            assertEquals("Актовый зал/Г · ещё 75 мин", subtitle)
            assertEquals(15f / 90f, progress!!, 0.001f)
        }
    }

    @Test
    fun `отсчёт обновляется раз в минуту близко к паре, иначе - за 6 часов до неё`() {
        assertEquals(at(d(28), 11, 1), countdownRefreshAt(pick(at(d(28), 11, 0)), at(d(28), 11, 0)))
        // Вечер понедельника, пара в среду в 10:45 — следующее обновление в среду в 4:45.
        assertEquals(at(d(30), 4, 45), countdownRefreshAt(pick(at(d(28), 20)), at(d(28), 20)))
    }
}
