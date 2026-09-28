package com.example.gasuschedule.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.gasuschedule.data.local.LessonEntity
import com.example.gasuschedule.data.local.ScheduleChangeEntity
import com.example.gasuschedule.data.local.WeekEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
abstract class ScheduleDao {

    @Query(
        """SELECT * FROM lessons WHERE groupName = :group AND date BETWEEN :from AND :to
           ORDER BY date, lessonNumber, id"""
    )
    abstract fun observeLessons(group: String, from: LocalDate, to: LocalDate): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE groupName = :group ORDER BY date, lessonNumber, id")
    abstract suspend fun getLessons(group: String): List<LessonEntity>

    @Query("SELECT * FROM lessons WHERE id = :id")
    abstract suspend fun getLesson(id: String): LessonEntity?

    @Query("SELECT * FROM weeks WHERE groupName = :group ORDER BY number")
    abstract fun observeWeeks(group: String): Flow<List<WeekEntity>>

    @Query("SELECT * FROM weeks WHERE groupName = :group ORDER BY number")
    abstract suspend fun getWeeks(group: String): List<WeekEntity>

    @Query("DELETE FROM lessons WHERE groupName = :group")
    protected abstract suspend fun deleteLessons(group: String)

    @Query("DELETE FROM weeks WHERE groupName = :group")
    protected abstract suspend fun deleteWeeks(group: String)

    @Insert
    protected abstract suspend fun insertLessons(lessons: List<LessonEntity>)

    @Insert
    protected abstract suspend fun insertWeeks(weeks: List<WeekEntity>)

    @Insert
    protected abstract suspend fun insertChanges(changes: List<ScheduleChangeEntity>)

    /** Снепшот группы и найденные замены сохраняются вместе или не сохраняются вовсе. */
    @Transaction
    open suspend fun replaceSnapshot(
        group: String,
        lessons: List<LessonEntity>,
        weeks: List<WeekEntity>,
        changes: List<ScheduleChangeEntity>,
    ) {
        deleteLessons(group)
        deleteWeeks(group)
        insertLessons(lessons)
        insertWeeks(weeks)
        insertChanges(changes)
    }

    @Query(
        """SELECT * FROM schedule_changes WHERE groupName = :group
           ORDER BY detectedAt DESC, date, lessonNumber, id"""
    )
    abstract fun observeChanges(group: String): Flow<List<ScheduleChangeEntity>>

    @Query("SELECT COUNT(*) FROM schedule_changes WHERE groupName = :group AND seen = 0")
    abstract fun observeUnseenCount(group: String): Flow<Int>

    @Query("UPDATE schedule_changes SET seen = 1 WHERE groupName = :group AND seen = 0")
    abstract suspend fun markSeen(group: String)
}
