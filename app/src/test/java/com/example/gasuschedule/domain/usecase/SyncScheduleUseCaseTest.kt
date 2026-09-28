package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.ChangeType
import com.example.gasuschedule.domain.model.ScheduleNetworkException
import com.example.gasuschedule.domain.model.ScheduleParseException
import com.example.gasuschedule.domain.model.SemesterSchedule
import com.example.gasuschedule.testutil.FakePreferences
import com.example.gasuschedule.testutil.FakeScheduleRepository
import com.example.gasuschedule.testutil.GROUP
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

class SyncScheduleUseCaseTest {

    private val repo = FakeScheduleRepository()
    private val prefs = FakePreferences()
    private val clock = clockAt(d(28))
    private val sync = SyncScheduleUseCase(repo, prefs, ScheduleDiffer(), clock)

    private val weeks = listOf(5, 6)
    private val base = schedule(weeks, lesson(d(29), 3), lesson(d(30), 1, "Философия"))

    @Test
    fun `первая синхронизация сохраняет снепшот без замен`() = runTest {
        repo.remote = { base }
        val r = sync()
        assertEquals(SyncResult.Success(2, emptyList()), r)
        assertEquals(base, repo.snapshot(GROUP))
        assertEquals(clock.instant(), prefs.lastSyncAt.value)
    }

    @Test
    fun `вторая синхронизация находит и сохраняет замену`() = runTest {
        repo.remote = { base }
        sync()
        repo.remote = { schedule(weeks, lesson(d(29), 3, rooms = listOf("812/С")), lesson(d(30), 1, "Философия")) }

        val r = sync() as SyncResult.Success
        assertEquals(1, r.changes.size)
        assertEquals(ChangeType.ROOM, r.changes.single().type)
        assertEquals(r.changes, repo.changes.value)
        assertEquals("812/С", repo.snapshot(GROUP).lessons.first().room)
    }

    @Test
    fun `повторная синхронизация без изменений не дублирует замены`() = runTest {
        repo.remote = { base }
        sync()
        repo.remote = { schedule(weeks, lesson(d(29), 3, rooms = listOf("812/С")), lesson(d(30), 1, "Философия")) }
        sync()
        val again = sync() as SyncResult.Success
        assertTrue(again.changes.isEmpty())
        assertEquals(1, repo.changes.value.size)
    }

    @Test
    fun `сетевая ошибка - снепшот не трогаем`() = runTest {
        repo.remote = { base }
        sync()
        repo.remote = { throw ScheduleNetworkException("Нет связи с сайтом расписания") }
        val r = sync()
        assertEquals(SyncResult.Failure(SyncResult.Reason.NETWORK, "Нет связи с сайтом расписания"), r)
        assertEquals(base, repo.snapshot(GROUP))
    }

    @Test
    fun `ошибка разбора`() = runTest {
        repo.remote = { throw ScheduleParseException("Не распознан заголовок недели") }
        assertEquals(SyncResult.Reason.PARSE, (sync() as SyncResult.Failure).reason)
    }

    @Test
    fun `сайт внезапно отдал пустое расписание - не затираем старое и не шлём сотню отмен`() = runTest {
        repo.remote = { base }
        sync()
        repo.remote = { SemesterSchedule(GROUP, emptyList(), emptyList()) }
        assertEquals(SyncResult.Reason.SUSPICIOUS_EMPTY, (sync() as SyncResult.Failure).reason)
        assertEquals(base, repo.snapshot(GROUP))
        assertTrue(repo.changes.value.isEmpty())
    }

    @Test
    fun `группы нет на сайте`() = runTest {
        repo.remote = { schedule(weeks) }
        assertSame(SyncResult.GroupNotFound, sync())
        assertNull(prefs.lastSyncAt.value)
    }

    @Test
    fun `группа не выбрана - в сеть не ходим`() = runTest {
        val noGroup = SyncScheduleUseCase(repo, FakePreferences(group = null), ScheduleDiffer(), clock)
        assertSame(SyncResult.NoGroup, noGroup())
        assertEquals(0, repo.fetchCount)
    }

    @Test
    fun `явно переданная группа важнее сохранённой`() = runTest {
        repo.remote = { base.copy(groupName = "1-А-26") }
        sync("1-А-26")
        assertEquals(2, repo.snapshot("1-А-26").lessons.size)
        assertTrue(repo.snapshot(GROUP).lessons.isEmpty())
    }
}
