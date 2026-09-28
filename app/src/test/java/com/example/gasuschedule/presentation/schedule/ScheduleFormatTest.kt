package com.example.gasuschedule.presentation.schedule

import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import com.example.gasuschedule.testutil.week
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleFormatTest {

    // Понедельник 28.09 из макета: 1, 2 и 3 пара.
    private val day = listOf(lesson(d(28), 1), lesson(d(28), 2), lesson(d(28), 3))

    private fun timings(h: Int, m: Int, date: java.time.LocalDate = d(28)) =
        lessonTimings(day, LocalDateTime.of(date, LocalTime.of(h, m))).let { t -> day.map { t.getValue(it.id) } }

    @Test
    fun `во время второй пары - первая прошла, вторая сейчас`() {
        assertEquals(listOf(LessonTiming.PAST, LessonTiming.CURRENT, LessonTiming.UPCOMING), timings(11, 0))
    }

    @Test
    fun `на перемене выделяется ближайшая пара`() {
        assertEquals(listOf(LessonTiming.PAST, LessonTiming.PAST, LessonTiming.NEXT), timings(12, 20))
    }

    @Test
    fun `утром до пар - первая помечена как следующая`() {
        assertEquals(listOf(LessonTiming.NEXT, LessonTiming.UPCOMING, LessonTiming.UPCOMING), timings(7, 30))
    }

    @Test
    fun `граница - в момент окончания пара уже прошла, в момент начала уже идёт`() {
        assertEquals(listOf(LessonTiming.PAST, LessonTiming.CURRENT, LessonTiming.UPCOMING), timings(10, 45))
    }

    @Test
    fun `вечером всё прошло, другие дни не выделяются`() {
        assertEquals(List(3) { LessonTiming.PAST }, timings(21, 0))
        assertEquals(List(3) { LessonTiming.UPCOMING }, timings(12, 0, date = d(27)))
        assertEquals(List(3) { LessonTiming.PAST }, timings(8, 0, date = d(29)))
    }

    @Test
    fun `склонения`() {
        assertEquals(
            listOf("1 пара", "2 пары", "4 пары", "5 пар", "11 пар", "12 пар", "21 пара", "22 пары"),
            listOf(1, 2, 4, 5, 11, 12, 21, 22).map(::lessonsCount),
        )
    }

    @Test
    fun `даты по-русски`() {
        assertEquals("Понедельник, 28 сентября", dayTitle(d(28)))
        assertEquals("Четверг, 1 октября", dayTitle(d(1, 10)))
        assertEquals("ПН", shortDayName(d(28)))
        assertEquals("СБ", shortDayName(d(3, 10)))
        assertEquals("28.09", shortDate(d(28)))
        assertEquals("Числитель · Нед. 5", weekPill(week(5)))
    }

    @Test
    fun `до начала пары`() {
        val l = lesson(d(28), 3) // 12:30
        assertEquals("через 25 мин", startsIn(LocalDateTime.of(2026, 9, 28, 12, 5), l))
        assertEquals("через 2 ч", startsIn(LocalDateTime.of(2026, 9, 28, 10, 30), l))
        assertEquals("через 1 ч 15 мин", startsIn(LocalDateTime.of(2026, 9, 28, 11, 15), l))
    }

    @Test
    fun `диапазон дней - семестр плюс сегодня`() {
        val range = ScheduleViewModel.dayRange(listOf(week(5), week(6)), today = d(28))
        assertEquals(d(28), range.first())
        assertEquals(d(11, 10), range.last())
        assertEquals(14, range.size)
        // Каникулы после опубликованных недель: сегодня всё равно в диапазоне.
        assertEquals(d(20, 10), ScheduleViewModel.dayRange(listOf(week(5)), today = d(20, 10)).last())
        assertEquals(listOf(d(28)), ScheduleViewModel.dayRange(emptyList(), today = d(28)))
    }
}
