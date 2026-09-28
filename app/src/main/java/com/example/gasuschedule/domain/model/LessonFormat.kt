package com.example.gasuschedule.domain.model

val LessonType.shortLabel: String
    get() = when (this) {
        LessonType.LECTURE -> "л."
        LessonType.PRACTICE -> "пр."
        LessonType.LAB -> "лаб."
        LessonType.OTHER -> ""
    }

/** "Физика (лаб.)" — как на сайте. */
val Lesson.subjectWithType: String
    get() = if (type == LessonType.OTHER) subject else "$subject (${type.shortLabel})"
