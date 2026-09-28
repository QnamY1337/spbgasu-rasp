package com.example.gasuschedule.testutil

import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.domain.model.ScheduleWeek
import com.example.gasuschedule.domain.model.SemesterSchedule
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class FakeScheduleRepository : ScheduleRepository {
    /** Что вернёт следующий fetchRemote; исключение — будет брошено. */
    var remote: () -> SemesterSchedule = { error("remote не задан") }
    var fetchCount = 0

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

    override suspend fun fetchGroups() = listOf(GROUP)
}

class FakePreferences(group: String? = GROUP) : UserPreferencesRepository {
    override val groupName = MutableStateFlow(group)
    override suspend fun setGroupName(name: String) { groupName.value = name }

    override val lastSyncAt = MutableStateFlow<Instant?>(null)
    override suspend fun setLastSyncAt(at: Instant) { lastSyncAt.value = at }
}
