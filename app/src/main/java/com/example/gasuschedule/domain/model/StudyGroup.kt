package com.example.gasuschedule.domain.model

/** Группа из каталога сайта. Факультет/уровень/курс известны не для всех групп. */
data class StudyGroup(
    val name: String,
    val faculty: String? = null,
    /** "Бакалавриат", "Магистратура", "Специалитет", ... */
    val level: String? = null,
    /** "1 курс" */
    val course: String? = null,
) {
    /** "Автомобильно-дорожный факультет · 1 курс" или null. */
    val subtitle: String?
        get() = listOfNotNull(faculty, course).joinToString(" · ").ifEmpty { null }
}
