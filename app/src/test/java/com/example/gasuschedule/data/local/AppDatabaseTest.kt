package com.example.gasuschedule.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.gasuschedule.data.remote.Fixtures
import com.example.gasuschedule.data.remote.ScheduleHtmlParser
import com.example.gasuschedule.data.remote.ScheduleRemoteSource
import com.example.gasuschedule.data.remote.dto.BitrixAjaxResponse
import com.example.gasuschedule.data.repository.ExamRepositoryImpl
import com.example.gasuschedule.data.repository.HomeworkRepositoryImpl
import com.example.gasuschedule.data.repository.ScheduleRepositoryImpl
import com.example.gasuschedule.domain.model.ChangeType
import com.example.gasuschedule.domain.model.ExamEntry
import com.example.gasuschedule.domain.model.ExamType
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.domain.model.SemesterSchedule
import com.example.gasuschedule.testutil.GROUP
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import com.example.gasuschedule.testutil.schedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var schedules: ScheduleRepositoryImpl

    private val noRemote = object : ScheduleRemoteSource {
        override suspend fun fetchSchedule(groupName: String) = error("не используется")
        override suspend fun fetchGroups() = error("не используется")
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        schedules = ScheduleRepositoryImpl(noRemote, db.scheduleDao())
    }

    @After
    fun tearDown() = db.close()

    private fun realSchedule(): SemesterSchedule {
        val html = (BitrixAjaxResponse.parse(Fixtures.raspJson) as BitrixAjaxResponse.Success).html
        return ScheduleHtmlParser().parse(html, GROUP)
    }

    private fun change(type: ChangeType, at: Instant) = ScheduleChange(
        groupName = GROUP, lessonId = "$GROUP|2026-09-29|3", date = d(29), lessonNumber = 3,
        subject = "Физика", type = type, oldValue = "349/А", newValue = "220/Г", detectedAt = at,
    )

    @Test
    fun `реальное расписание проходит через БД без потерь`() = runTest {
        val real = realSchedule()
        schedules.replaceSnapshot(real, emptyList())
        assertEquals(real, schedules.getSnapshot(GROUP))
    }

    @Test
    fun `выборка по диапазону дат и конкретная пара`() = runTest {
        schedules.replaceSnapshot(realSchedule(), emptyList())
        val week5 = schedules.observeLessons(GROUP, d(28), d(4, 10)).first()
        assertEquals(14, week5.size)
        assertEquals(week5.sortedWith(compareBy({ it.date }, { it.lessonNumber })), week5)

        val l = schedules.getLesson("$GROUP|2026-10-02|1")!!
        assertEquals(listOf("421(3)/Г", "444/Г"), l.rooms)
        assertEquals("Гурьева Ю.А., Ржавцев А.А.", l.teacher)
        assertEquals(LocalTime.of(9, 0), l.startTime)
        assertEquals(8, schedules.observeWeeks(GROUP).first().size)
    }

    @Test
    fun `замена снепшота не трогает другие группы и копит историю замен`() = runTest {
        schedules.replaceSnapshot(schedule(listOf(5), lesson(d(29), 3)), emptyList())
        schedules.replaceSnapshot(realSchedule().copy(groupName = "1-А-26", lessons = emptyList()), emptyList())

        val t1 = Instant.parse("2026-09-28T07:00:00Z")
        val t2 = Instant.parse("2026-09-28T11:00:00Z")
        schedules.replaceSnapshot(schedule(listOf(5), lesson(d(29), 3, rooms = listOf("220/Г"))), listOf(change(ChangeType.ROOM, t1)))
        schedules.replaceSnapshot(schedule(listOf(5), lesson(d(29), 3, rooms = listOf("220/Г"))), listOf(change(ChangeType.TEACHER, t2)))

        assertEquals("220/Г", schedules.getSnapshot(GROUP).lessons.single().room)
        assertEquals(8, schedules.getSnapshot("1-А-26").weeks.size)

        val history = schedules.observeChanges(GROUP).first()
        assertEquals(listOf(ChangeType.TEACHER, ChangeType.ROOM), history.map { it.type })
        assertTrue(history.all { it.id > 0 })
        assertEquals(2, schedules.observeUnseenChangesCount(GROUP).first())
        schedules.markChangesSeen(GROUP)
        assertEquals(0, schedules.observeUnseenChangesCount(GROUP).first())
    }

    @Test
    fun `домашние задания - порядок, отметка, удаление, переживают пересинхронизацию`() = runTest {
        val repo = HomeworkRepositoryImpl(db.homeworkDao())
        val created = Instant.parse("2026-09-28T07:00:00Z")
        fun hw(id: String, due: java.time.LocalDate?, lessonId: String? = null) =
            HomeworkItem(id, lessonId, "Физика", "задание $id", due, false, created)

        repo.upsert(hw("later", d(10, 10)))
        repo.upsert(hw("noDue", null))
        repo.upsert(hw("soon", d(30), lessonId = "$GROUP|2026-09-30|3"))
        repo.upsert(hw("done", d(29)))
        repo.setDone("done", true)
        assertEquals(listOf("soon", "later", "noDue", "done"), repo.observeAll().first().map { it.id })

        schedules.replaceSnapshot(realSchedule(), emptyList())
        assertEquals(listOf("soon"), repo.observeForLesson("$GROUP|2026-09-30|3").first().map { it.id })

        repo.upsert(repo.get("soon")!!.copy(description = "Лаба 3"))
        assertEquals("Лаба 3", repo.get("soon")!!.description)
        repo.delete("soon")
        assertNull(repo.get("soon"))
    }

    @Test
    fun `экзамены - только предстоящие, по дате`() = runTest {
        val repo = ExamRepositoryImpl(db.examDao())
        repo.upsert(ExamEntry("e2", "Физика", ExamType.EXAM, d(20, 1).withYear(2027), LocalTime.of(10, 0), "349/А", null, null))
        repo.upsert(ExamEntry("e1", "История России", ExamType.CREDIT, d(28, 12), null, null, "Гурьев Е.П.", null))
        repo.upsert(ExamEntry("old", "Философия", ExamType.CREDIT_WITH_GRADE, d(1, 6), null, null, null, null))
        assertEquals(listOf("e1", "e2"), repo.observeUpcoming(d(28)).first().map { it.id })
        repo.delete("e1")
        assertEquals(listOf("e2"), repo.observeUpcoming(d(28)).first().map { it.id })
    }
}
