package com.example.gasuschedule.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.gasuschedule.domain.model.ChangeType
import com.example.gasuschedule.domain.model.ScheduleChange
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "schedule_changes", indices = [Index("groupName", "detectedAt")])
data class ScheduleChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupName: String,
    val lessonId: String,
    val date: LocalDate,
    val lessonNumber: Int,
    val subject: String,
    val type: ChangeType,
    val oldValue: String?,
    val newValue: String?,
    val detectedAt: Instant,
    val seen: Boolean,
)

fun ScheduleChangeEntity.toDomain() = ScheduleChange(
    id, groupName, lessonId, date, lessonNumber, subject, type, oldValue, newValue, detectedAt, seen,
)

fun ScheduleChange.toEntity() = ScheduleChangeEntity(
    id, groupName, lessonId, date, lessonNumber, subject, type, oldValue, newValue, detectedAt, seen,
)
