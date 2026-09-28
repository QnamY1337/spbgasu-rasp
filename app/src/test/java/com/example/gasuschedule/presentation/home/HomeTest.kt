package com.example.gasuschedule.presentation.home

import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.usecase.LeaveEstimate
import com.example.gasuschedule.domain.usecase.ScheduleDiffer
import com.example.gasuschedule.domain.usecase.SyncScheduleUseCase
import com.example.gasuschedule.testutil.FakeHomeworkRepository
import com.example.gasuschedule.testutil.FakePreferences
import com.example.gasuschedule.testutil.FakeReplanTrigger
import com.example.gasuschedule.testutil.FakeScheduleRepository
import com.example.gasuschedule.testutil.FakeWidgetUpdater
import com.example.gasuschedule.testutil.MainDispatcherRule
import com.example.gasuschedule.testutil.clockAt
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import com.example.gasuschedule.testutil.schedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

class HomeTest {

    @get:Rule val main = MainDispatcherRule()

    private val mon = listOf(lesson(d(28), 1), lesson(d(28), 2), lesson(d(28), 3))
    private val tue = listOf(lesson(d(29), 2, rooms = listOf("409/С")), lesson(d(29), 3))
    private val all = mon + tue

    @Test
    fun `показываемый день - сегодня или ближайший учебный`() {
        val byDate = all.groupBy { it.date }
        assertEquals(d(28), HomeViewModel.shownDate(byDate, d(28).atTime(8, 0)))
        assertEquals("воскресенье без пар", d(28), HomeViewModel.shownDate(byDate, d(27).atTime(12, 0)))
        assertEquals(d(29), HomeViewModel.shownDate(tue.groupBy { it.date }, d(28).atTime(8, 0)))
        assertNull(HomeViewModel.shownDate(emptyMap(), d(28).atTime(8, 0)))
    }

    @Test
    fun `через 20 минут после последней пары - следующий день`() {
        val byDate = all.groupBy { it.date } // последняя пара понедельника — 3-я, до 14:00
        assertEquals("пара идёт", d(28), HomeViewModel.shownDate(byDate, d(28).atTime(13, 0)))
        assertEquals("10 минут после", d(28), HomeViewModel.shownDate(byDate, d(28).atTime(14, 10)))
        assertEquals("19:59 после", d(28), HomeViewModel.shownDate(byDate, d(28).atTime(14, 19, 59)))
        assertEquals("ровно 20 минут", d(29), HomeViewModel.shownDate(byDate, d(28).atTime(14, 20)))
        assertEquals(d(29), HomeViewModel.shownDate(byDate, d(28).atTime(21, 0)))

        assertTrue(HomeViewModel.todayFinished(byDate, d(28).atTime(14, 20)))
        assertTrue(!HomeViewModel.todayFinished(byDate, d(28).atTime(14, 19)))
        assertTrue("сегодня пар не было — не «закончились»", !HomeViewModel.todayFinished(byDate, d(27).atTime(20, 0)))
        // После последней пары недели показывать нечего.
        assertNull(HomeViewModel.shownDate(byDate, d(29).atTime(14, 20)))
    }

    @Test
    fun `дорога - к сегодняшней первой паре, а после её начала - к завтрашней`() {
        assertEquals(mon[0], HomeViewModel.nextFirstLesson(all, d(28).atTime(7, 0)))
        // 1 пара уже идёт — дорога нужна к первой паре вторника (2 пара), а не к 2 паре сегодня.
        assertEquals(tue[0], HomeViewModel.nextFirstLesson(all, d(28).atTime(9, 30)))
        assertEquals(tue[0], HomeViewModel.nextFirstLesson(all, d(28).atTime(20, 0)))
        assertNull(HomeViewModel.nextFirstLesson(all, d(29).atTime(11, 0)))
    }

    @Test
    fun `подсказка под временем выхода`() {
        val leave = d(28).atTime(8, 0)
        assertEquals("через 25 мин", leaveHint(d(28).atTime(7, 35), leave))
        assertEquals("через 1 ч 20 мин", leaveHint(d(28).atTime(6, 40), leave))
        assertEquals("через 2 ч", leaveHint(d(28).atTime(6, 0), leave))
        assertEquals("Пора выходить!", leaveHint(d(28).atTime(8, 5), leave))
        assertNull("вечером накануне — без подсказки", leaveHint(d(27).atTime(20, 0), leave))
        assertEquals("ЗАВТРА", dayLabel(d(29), d(28)))
        assertEquals("СР 30.09", dayLabel(d(30), d(28)))
    }

    @Test
    fun `состояние главной - пары сегодня и дорога к первой`() = runTest {
        val repo = FakeScheduleRepository()
        repo.replaceSnapshot(schedule(listOf(5), *all.toTypedArray()), emptyList())
        val prefs = FakePreferences().apply {
            lastSyncAt.value = clockAt(d(28), 7).instant()
            home.value = HomeLocation(GeoPoint(60.0514, 30.3325), "пр. Просвещения")
        }
        val clock = clockAt(d(28), 7)
        val vm = HomeViewModel(
            repo, prefs,
            SyncScheduleUseCase(repo, prefs, ScheduleDiffer(), clock, FakeReplanTrigger(), FakeWidgetUpdater()),
            clock,
            FakeHomeworkRepository(),
        )
        val state = vm.state.first { it.loaded }
        assertEquals(d(28), state.shownDate)
        assertEquals(3, state.lessons.size)
        assertEquals(5, state.week?.number)
        val leave = state.commute!!.leave as LeaveEstimate.Estimated
        assertEquals(mon[0], state.commute!!.lesson)
        assertEquals(LocalTime.of(7, 35), leave.route.recommendedLeaveTime)
        assertTrue(repo.fetchCount == 0)
    }
}
