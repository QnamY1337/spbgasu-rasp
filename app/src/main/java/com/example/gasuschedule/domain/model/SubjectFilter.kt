package com.example.gasuschedule.domain.model

/** Как показывать предмет: целиком, без лекций или отключён совсем. */
enum class SubjectMode { ON, NO_LECTURES, OFF }

/**
 * Предметы, которые пользователь отключил в настройках. Отключённые пары остаются в расписании
 * серыми, но не считаются ближайшими и не дают напоминаний.
 */
data class SubjectFilter(val modes: Map<String, SubjectMode> = emptyMap()) {
    fun mode(subject: String): SubjectMode = modes[subject] ?: SubjectMode.ON

    fun isDisabled(lesson: Lesson): Boolean = when (mode(lesson.subject)) {
        SubjectMode.ON -> false
        SubjectMode.NO_LECTURES -> lesson.type == LessonType.LECTURE
        SubjectMode.OFF -> true
    }

    fun enabled(lessons: List<Lesson>): List<Lesson> = lessons.filterNot(::isDisabled)

    fun with(subject: String, mode: SubjectMode): SubjectFilter =
        SubjectFilter(if (mode == SubjectMode.ON) modes - subject else modes + (subject to mode))
}
