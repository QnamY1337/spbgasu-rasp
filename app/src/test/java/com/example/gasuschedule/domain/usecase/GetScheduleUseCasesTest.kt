package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.testutil.FakePreferences
import com.example.gasuschedule.testutil.FakeScheduleRepository
import com.example.gasuschedule.testutil.clockAt
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import com.example.gasuschedule.testutil.schedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetScheduleUseCasesTest {

    private val repo = FakeScheduleRepository()
    private val prefs = FakePreferences()

    private suspend fun seed() = repo.replaceSnapshot(
        schedule(
            listOf(5, 6),
            lesson(d(28), 1, "История России"),
            lesson(d(28), 3, "Философия"),
            lesson(d(30), 2),
            lesson(d(6, 10), 1),
        ),
        emptyList(),
    )

    @Test
    fun `сегодня - только пары этого дня`() = runTest {
        seed()
        val today = GetTodayScheduleUseCase(repo, prefs, clockAt(d(28))).invoke().first()
        assertEquals(listOf("История России", "Философия"), today.map { it.subject })
    }

    @Test
    fun `без выбранной группы - пусто`() = runTest {
        seed()
        val today = GetTodayScheduleUseCase(repo, FakePreferences(null), clockAt(d(28))).invoke().first()
        assertTrue(today.isEmpty())
    }

    @Test
    fun `неделя - 7 дней с понедельника, номер и чётность с сайта`() = runTest {
        seed()
        val week = GetWeekScheduleUseCase(repo, prefs).invoke(d(1, 10)).first()
        assertEquals((0L..6L).map { d(28).plusDays(it) }, week.days.keys.toList())
        assertEquals(5, week.week?.number)
        assertEquals(2, week.days.getValue(d(28)).size)
        assertEquals(1, week.days.getValue(d(30)).size)
        assertTrue(week.days.getValue(d(29)).isEmpty())
        assertTrue(week.days.values.flatten().none { it.date == d(6, 10) })
    }

    @Test
    fun `неопубликованная неделя - week = null, дни пустые`() = runTest {
        seed()
        val week = GetWeekScheduleUseCase(repo, prefs).invoke(d(20, 10)).first()
        assertNull(week.week)
        assertEquals(7, week.days.size)
        assertTrue(week.days.values.all { it.isEmpty() })
    }
}
