package com.example.gasuschedule.domain.repository

import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.domain.model.ScheduleWeek
import com.example.gasuschedule.domain.model.SemesterSchedule
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ScheduleRepository {
    fun observeLessons(group: String, from: LocalDate, to: LocalDate): Flow<List<Lesson>>
    fun observeWeeks(group: String): Flow<List<ScheduleWeek>>
    suspend fun getLesson(id: String): Lesson?

    /** Свежее расписание с сайта. Бросает ScheduleNetworkException / ScheduleParseException. */
    suspend fun fetchRemote(group: String): SemesterSchedule

    /** Сохранённый снепшот группы (пустой, если синхронизаций ещё не было). */
    suspend fun getSnapshot(group: String): SemesterSchedule

    /** Атомарно заменяет снепшот группы и дописывает найденные замены. */
    suspend fun replaceSnapshot(schedule: SemesterSchedule, changes: List<ScheduleChange>)

    fun observeChanges(group: String): Flow<List<ScheduleChange>>
    fun observeUnseenChangesCount(group: String): Flow<Int>
    suspend fun markChangesSeen(group: String)

    suspend fun fetchGroups(): List<String>
}
