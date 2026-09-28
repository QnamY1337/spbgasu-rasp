package com.example.gasuschedule.data.remote

import com.example.gasuschedule.data.remote.dto.BitrixAjaxResponse
import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.domain.model.SemesterSchedule
import com.example.gasuschedule.domain.model.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ScheduleHtmlParserTest {

    companion object {
        private const val GROUP = "3-ТТП-26"
        private lateinit var schedule: SemesterSchedule

        @BeforeClass
        @JvmStatic
        fun parseFixture() {
            val html = (BitrixAjaxResponse.parse(Fixtures.raspJson) as BitrixAjaxResponse.Success).html
            schedule = ScheduleHtmlParser().parse(html, GROUP)
        }
    }

    @Test
    fun `весь семестр - 8 недель с чередованием числитель-знаменатель`() {
        assertEquals((1..8).toList(), schedule.weeks.map { it.number })
        schedule.weeks.forEach { w ->
            val expected = if (w.number % 2 == 1) WeekParity.NUMERATOR else WeekParity.DENOMINATOR
            assertEquals("неделя ${w.number}", expected, w.parity)
            assertEquals(DayOfWeek.MONDAY, w.startDate.dayOfWeek)
            assertEquals(w.startDate.plusDays(6), w.endDate)
        }
        val w5 = schedule.weeks.single { it.number == 5 }
        assertEquals(LocalDate.of(2026, 9, 28), w5.startDate)
        assertEquals(LocalDate.of(2026, 10, 4), w5.endDate)
    }

    @Test
    fun `всего 108 пар, у каждой уникальный id и дата внутри своей недели`() {
        assertEquals(108, schedule.lessons.size)
        assertEquals(schedule.lessons.size, schedule.lessons.map { it.id }.toSet().size)
        val weeks = schedule.weeks.associateBy { it.number }
        schedule.lessons.forEach { l ->
            val w = weeks.getValue(l.weekNumber)
            assertTrue(l.id, l.date in w.startDate..w.endDate)
            assertEquals(l.id, w.parity, l.weekParity)
            assertEquals(l.id, l.date.dayOfWeek, l.dayOfWeek)
            assertEquals(l.id, GROUP, l.groupName)
            assertTrue(l.id, l.subject.isNotBlank() && l.room.isNotBlank() && l.teacher.isNotBlank())
        }
    }

    @Test
    fun `номер пары однозначно задаёт время`() {
        val bells = mapOf(
            1 to "09:00-10:30", 2 to "10:45-12:15", 3 to "12:30-14:00",
            4 to "15:00-16:30", 5 to "16:45-18:15", 6 to "18:30-20:00",
        )
        schedule.lessons.forEach { l ->
            assertEquals(l.id, bells.getValue(l.lessonNumber), "${l.startTime}-${l.endTime}")
        }
    }

    /** Эталон выписан вручную из сырого HTML недели 5 (28.09–04.10.2026). */
    @Test
    fun `неделя 5 совпадает с сайтом целиком`() {
        val expected = listOf(
            "28.09 1 История России|LECTURE|Актовый зал/Г|Гурьев Е.П.",
            "28.09 2 История России|LECTURE|Актовый зал/Г|Гурьев Е.П.",
            "28.09 3 Философия|PRACTICE|712/С|Семенюк А.П.",
            "29.09 2 Высшая математика|PRACTICE|409/С|Священко В.А.",
            "29.09 3 Экономическая грамотность в условиях цифровой трансформации|PRACTICE|718/С|Веронская М.В.",
            "29.09 4 Общий курс транспорта|PRACTICE|406*/К|Давыдова В.А.",
            "30.09 2 Высшая математика|LECTURE|220/Г|Михайлов А.Е.",
            "30.09 3 Физика|LECTURE|349/А|Кирк Я.Г.",
            "30.09 6 Дополнительные курсы каф. СФЭиЭ (дисциплина - Физика)|PRACTICE|318/Г|Рогожина Т.С.",
            "01.10 3 Философия|LECTURE|Актовый зал/Г|Семенюк А.П.",
            "01.10 4 История России|PRACTICE|609/С|Лапина И.Ю.",
            "01.10 5 Основы российской государственности|PRACTICE|711/С|Конькова Д.А.",
            "02.10 1 Начертательная геометрия|PRACTICE|421(3)/Г, 444/Г|Гурьева Ю.А., Ржавцев А.А.",
            "02.10 2 Начертательная геометрия|LECTURE|349/А|Ковалева О.Н.",
        )
        val actual = schedule.lessons
            .filter { it.weekNumber == 5 }
            .sortedWith(compareBy({ it.date }, { it.lessonNumber }))
            .map {
                "%02d.%02d %d %s|%s|%s|%s".format(
                    it.date.dayOfMonth, it.date.monthValue, it.lessonNumber,
                    it.subject, it.type, it.room, it.teacher,
                )
            }
        assertEquals(expected.joinToString("\n"), actual.joinToString("\n"))
    }

    @Test
    fun `одна пара разобрана во всех полях`() {
        val l = schedule.lessons.single { it.date == LocalDate.of(2026, 9, 1) && it.lessonNumber == 3 }
        assertEquals("3-ТТП-26|2026-09-01|3", l.id)
        assertEquals("Экономическая грамотность в условиях цифровой трансформации", l.subject)
        assertEquals(LessonType.LECTURE, l.type)
        assertEquals("207/5", l.room)
        assertEquals("5", l.building)
        assertEquals("Куцевский В.В.", l.teacher)
        assertEquals(LocalTime.of(12, 30), l.startTime)
        assertEquals(LocalTime.of(14, 0), l.endTime)
        assertEquals(DayOfWeek.TUESDAY, l.dayOfWeek)
        assertEquals(1, l.weekNumber)
        assertEquals(WeekParity.NUMERATOR, l.weekParity)
        assertNull(l.note)
    }

    @Test
    fun `подгруппы - несколько аудиторий и преподавателей через br и запятую`() {
        val byBr = schedule.lessons.first { it.rooms == listOf("421(1)/Г", "421(2)/Г") }
        assertEquals(listOf("Гурьева Ю.А.", "Якубенко О.В."), byBr.teachers)
        assertEquals("Г", byBr.building)

        val byComma = schedule.lessons.first { "Дронов В.М." in it.teachers }
        assertEquals(listOf("Кирк Я.Г.", "Дронов В.М."), byComma.teachers)
    }

    @Test
    fun `все типы занятий распознаны, лаба тоже`() {
        assertTrue(schedule.lessons.none { it.type == LessonType.OTHER })
        assertTrue(schedule.lessons.any { it.type == LessonType.LAB && it.subject == "Физика" })
        assertTrue(schedule.lessons.none { it.subject.endsWith(".)") })
    }

    @Test
    fun `коды корпусов`() {
        assertEquals(
            setOf("5", "Г", "Б", "А", "К", "С"),
            schedule.lessons.mapNotNull { it.building }.toSet(),
        )
        assertEquals("К", ScheduleHtmlParser.buildingCode("406*/К"))
        assertEquals("Г", ScheduleHtmlParser.buildingCode("Актовый зал/Г"))
        assertNull(ScheduleHtmlParser.buildingCode("Спортзал"))
    }

    @Test
    fun `несуществующая группа - пустое расписание, не ошибка`() {
        val html = (BitrixAjaxResponse.parse(Fixtures.notFoundJson) as BitrixAjaxResponse.Success).html
        val empty = ScheduleHtmlParser().parse(html, "nonexistent")
        assertTrue(empty.isEmpty)
        assertEquals("каркас недель приходит и без пар", 8, empty.weeks.size)
    }

    @Test(expected = ScheduleParseException::class)
    fun `чужая разметка - понятная ошибка разбора`() {
        ScheduleHtmlParser().parse("<div>Сайт на техобслуживании</div>", GROUP)
    }

    @Test(expected = ScheduleParseException::class)
    fun `изменённый заголовок недели - ошибка разбора, а не мусор`() {
        val html = """<div class="owl-carousel"><div class="item" data-hash="week_1">
            <div class="time">Week 1</div></div></div>"""
        ScheduleHtmlParser().parse(html, GROUP)
    }
}
