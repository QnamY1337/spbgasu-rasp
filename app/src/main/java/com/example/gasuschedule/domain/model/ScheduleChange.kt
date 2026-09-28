package com.example.gasuschedule.domain.model

import java.time.Instant
import java.time.LocalDate

enum class ChangeType { SUBJECT, ROOM, TEACHER, CANCELLED, ADDED }

/** Замена: одно изменённое поле одной пары между двумя синхронизациями. */
data class ScheduleChange(
    val id: Long = 0,
    val groupName: String,
    val lessonId: String,
    val date: LocalDate,
    val lessonNumber: Int,
    /** Предмет для подписи в ленте (для CANCELLED — старый, для остальных — новый). */
    val subject: String,
    val type: ChangeType,
    val oldValue: String?,
    val newValue: String?,
    val detectedAt: Instant,
    val seen: Boolean = false,
)
