package com.example.gasuschedule.domain.model

import java.time.Instant

/**
 * Готовое напоминание о паре. Одно на слот (дата + номер пары): если у подгрупп в одно время
 * разные занятия, они объединяются в одно уведомление.
 */
data class LessonReminder(
    /** Стабильный ключ слота "группа|дата|номер" — по нему будильник заменяется/отменяется. */
    val key: String,
    val lessonId: String,
    val triggerAt: Instant,
    val title: String,
    val text: String,
)
