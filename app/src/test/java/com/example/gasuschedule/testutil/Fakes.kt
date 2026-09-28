package com.example.gasuschedule.testutil

import com.example.gasuschedule.domain.model.DayWeather
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.LessonReminder
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.domain.model.ScheduleWeek
import com.example.gasuschedule.domain.model.SemesterSchedule
import com.example.gasuschedule.domain.model.StudyGroup
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.repository.HomeworkRepository
import com.example.gasuschedule.domain.repository.ReminderReplanTrigger
import com.example.gasuschedule.domain.repository.ReminderScheduler
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.repository.WeatherRepository
import com.example.gasuschedule.domain.repository.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class FakeScheduleRepository : ScheduleRepository {
    /** Что вернёт следующий fetchRemote; исключение — будет брошено. */
    var remote: () -> SemesterSchedule = { error("remote не задан") }
    var fetchCount = 0
    var groups: () -> List<StudyGroup> = { listOf(StudyGroup(GROUP)) }

    private val snapshots = MutableStateFlow<Map<String, SemesterSchedule>>(emptyMap())
    val changes = MutableStateFlow<List<ScheduleChange>>(emptyList())

    fun snapshot(group: String) = snapshots.value[group] ?: SemesterSchedule(group, emptyList(), emptyList())

    override fun observeLessons(group: String, from: LocalDate, to: LocalDate): Flow<List<Lesson>> =
        snapshots.map { s -> s[group]?.lessons.orEmpty().filter { it.date in from..to } }

    override fun observeWeeks(group: String): Flow<List<ScheduleWeek>> =
        snapshots.map { it[group]?.weeks.orEmpty() }

    override suspend fun getLesson(id: String) = snapshots.value.values.flatMap { it.lessons }.find { it.id == id }

    override suspend fun fetchRemote(group: String): SemesterSchedule {
        fetchCount++
        return remote()
    }

    override suspend fun getSnapshot(group: String) = snapshot(group)

    override suspend fun replaceSnapshot(schedule: SemesterSchedule, changes: List<ScheduleChange>) {
        snapshots.value = snapshots.value + (schedule.groupName to schedule)
        this.changes.value = this.changes.value + changes
    }

    override fun observeChanges(group: String) = changes.map { l -> l.filter { it.groupName == group } }
    override fun observeUnseenChangesCount(group: String) =
        changes.map { l -> l.count { it.groupName == group && !it.seen } }

    override suspend fun markChangesSeen(group: String) {
        changes.value = changes.value.map { if (it.groupName == group) it.copy(seen = true) else it }
    }

    override suspend fun fetchGroups() = groups()
}

class FakePreferences(group: String? = GROUP) : UserPreferencesRepository {
    override val groupName = MutableStateFlow(group)
    override suspend fun setGroupName(name: String) { groupName.value = name }

    override val lastSyncAt = MutableStateFlow<Instant?>(null)
    override suspend fun setLastSyncAt(at: Instant) { lastSyncAt.value = at }

    override val remindersEnabled = MutableStateFlow(true)
    override suspend fun setRemindersEnabled(enabled: Boolean) { remindersEnabled.value = enabled }

    override val reminderMinutes = MutableStateFlow(UserPreferencesRepository.DEFAULT_REMINDER_MINUTES)
    override suspend fun setReminderMinutes(minutes: Int) { reminderMinutes.value = minutes }

    override val changeNotificationsEnabled = MutableStateFlow(true)
    override suspend fun setChangeNotificationsEnabled(enabled: Boolean) { changeNotificationsEnabled.value = enabled }

    override val home = MutableStateFlow<HomeLocation?>(null)
    override suspend fun setHome(home: HomeLocation?) { this.home.value = home }

    override val travelMode = MutableStateFlow(TravelMode.TRANSIT)
    override suspend fun setTravelMode(mode: TravelMode) { travelMode.value = mode }

    override val leaveBufferMinutes = MutableStateFlow(UserPreferencesRepository.DEFAULT_LEAVE_BUFFER_MINUTES)
    override suspend fun setLeaveBufferMinutes(minutes: Int) { leaveBufferMinutes.value = minutes }

    override val homeworkReminderHours = MutableStateFlow(UserPreferencesRepository.DEFAULT_HOMEWORK_REMINDER_HOURS)
    override suspend fun setHomeworkReminderHours(hours: Int) { homeworkReminderHours.value = hours }

    override val leaveRemindersEnabled = MutableStateFlow(true)
    override suspend fun setLeaveRemindersEnabled(enabled: Boolean) { leaveRemindersEnabled.value = enabled }
}

class FakeReminderScheduler : ReminderScheduler {
    var scheduled: List<LessonReminder> = emptyList()
    var calls = 0
    override suspend fun replaceAll(reminders: List<LessonReminder>) {
        scheduled = reminders
        calls++
    }
}

class FakeReplanTrigger : ReminderReplanTrigger {
    var requests = 0
    override fun requestReplan() { requests++ }
}

class FakeWidgetUpdater : WidgetUpdater {
    var requests = 0
    override fun requestUpdate() { requests++ }
}

class FakeHomeworkRepository : HomeworkRepository {
    val items = MutableStateFlow<List<HomeworkItem>>(emptyList())

    override fun observeAll(): Flow<List<HomeworkItem>> = items
    override fun observeForLesson(lessonId: String) = items.map { l -> l.filter { it.lessonId == lessonId } }
    override suspend fun get(id: String) = items.value.find { it.id == id }
    override suspend fun upsert(item: HomeworkItem) {
        items.value = items.value.filterNot { it.id == item.id } + item
    }
    override suspend fun setDone(id: String, done: Boolean) {
        items.value = items.value.map { if (it.id == id) it.copy(isDone = done) else it }
    }
    override suspend fun delete(id: String) {
        items.value = items.value.filterNot { it.id == id }
    }
}

class FakeWeatherRepository : WeatherRepository {
    override val weather = MutableStateFlow<DayWeather?>(null)
    var refreshCalls = 0
    override suspend fun refreshIfStale(): Boolean {
        refreshCalls++
        return true
    }
}
