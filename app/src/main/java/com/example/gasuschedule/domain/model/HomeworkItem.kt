package com.example.gasuschedule.domain.model

import java.time.Instant
import java.time.LocalDate

data class HomeworkItem(
    val id: String,
    /** null — задание по предмету в целом, без привязки к конкретной паре. */
    val lessonId: String?,
    val subject: String,
    val description: String,
    val dueDate: LocalDate?,
    val isDone: Boolean,
    val createdAt: Instant,
    /**
     * К какому типу занятия задание: задали на практике — сдавать на практике.
     * null — тип не важен (любая следующая пара по предмету).
     */
    val lessonType: LessonType? = null,
)
