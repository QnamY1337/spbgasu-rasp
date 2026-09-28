package com.example.gasuschedule.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.gasuschedule.domain.model.ExamEntry
import com.example.gasuschedule.domain.model.ExamType
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "exams", indices = [Index("date")])
data class ExamEntity(
    @PrimaryKey val id: String,
    val subject: String,
    val type: ExamType,
    val date: LocalDate,
    val time: LocalTime?,
    val room: String?,
    val teacher: String?,
    val notes: String?,
)

fun ExamEntity.toDomain() = ExamEntry(id, subject, type, date, time, room, teacher, notes)

fun ExamEntry.toEntity() = ExamEntity(id, subject, type, date, time, room, teacher, notes)
