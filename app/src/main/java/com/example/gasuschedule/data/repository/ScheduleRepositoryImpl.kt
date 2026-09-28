package com.example.gasuschedule.data.repository

import com.example.gasuschedule.data.local.dao.ScheduleDao
import com.example.gasuschedule.data.local.toDomain
import com.example.gasuschedule.data.local.toEntity
import com.example.gasuschedule.data.remote.ScheduleRemoteSource
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.domain.model.ScheduleWeek
import com.example.gasuschedule.domain.model.SemesterSchedule
import com.example.gasuschedule.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val remote: ScheduleRemoteSource,
    private val dao: ScheduleDao,
) : ScheduleRepository {

    override fun observeLessons(group: String, from: LocalDate, to: LocalDate): Flow<List<Lesson>> =
        dao.observeLessons(group, from, to).map { list -> list.map { it.toDomain() } }

    override fun observeWeeks(group: String): Flow<List<ScheduleWeek>> =
        dao.observeWeeks(group).map { list -> list.map { it.toDomain() } }

    override suspend fun getLesson(id: String): Lesson? = dao.getLesson(id)?.toDomain()

    override suspend fun fetchRemote(group: String): SemesterSchedule = remote.fetchSchedule(group)

    override suspend fun getSnapshot(group: String): SemesterSchedule = SemesterSchedule(
        groupName = group,
        weeks = dao.getWeeks(group).map { it.toDomain() },
        lessons = dao.getLessons(group).map { it.toDomain() },
    )

    override suspend fun replaceSnapshot(schedule: SemesterSchedule, changes: List<ScheduleChange>) =
        dao.replaceSnapshot(
            group = schedule.groupName,
            lessons = schedule.lessons.map { it.toEntity() },
            weeks = schedule.weeks.map { it.toEntity(schedule.groupName) },
            changes = changes.map { it.toEntity() },
        )

    override fun observeChanges(group: String): Flow<List<ScheduleChange>> =
        dao.observeChanges(group).map { list -> list.map { it.toDomain() } }

    override fun observeUnseenChangesCount(group: String): Flow<Int> = dao.observeUnseenCount(group)

    override suspend fun markChangesSeen(group: String) = dao.markSeen(group)

    override suspend fun fetchGroups(): List<String> = remote.fetchGroups()
}
