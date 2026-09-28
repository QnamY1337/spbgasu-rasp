package com.example.gasuschedule.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.LessonType
import java.time.Instant
import java.time.LocalDate

/**
 * Без внешнего ключа на lessons: снепшот расписания пересоздаётся при каждой синхронизации,
 * а задания должны переживать это. lessonId стабилен (группа|дата|номер пары).
 */
@Entity(tableName = "homework", indices = [Index("lessonId"), Index("dueDate")])
data class HomeworkEntity(
    @PrimaryKey val id: String,
    val lessonId: String?,
    val subject: String,
    val description: String,
    val dueDate: LocalDate?,
    val isDone: Boolean,
    val createdAt: Instant,
    /** Добавлено в версии БД 2 (автомиграция: nullable-колонка). */
    val lessonType: LessonType? = null,
)

fun HomeworkEntity.toDomain() = HomeworkItem(id, lessonId, subject, description, dueDate, isDone, createdAt, lessonType)

fun HomeworkItem.toEntity() = HomeworkEntity(id, lessonId, subject, description, dueDate, isDone, createdAt, lessonType)
