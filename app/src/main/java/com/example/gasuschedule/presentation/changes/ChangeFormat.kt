package com.example.gasuschedule.presentation.changes

import com.example.gasuschedule.domain.model.ChangeType
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.presentation.schedule.shortDate
import com.example.gasuschedule.presentation.schedule.shortDayName

/** "сменилась аудитория" — подпись под строкой "было → стало". */
val ChangeType.label: String
    get() = when (this) {
        ChangeType.ROOM -> "сменилась аудитория"
        ChangeType.TEACHER -> "сменился преподаватель"
        ChangeType.SUBJECT -> "другой предмет"
        ChangeType.CANCELLED -> "Пара отменена"
        ChangeType.ADDED -> "Добавлена пара"
    }

/** Одна строка для уведомления: "Философия: ауд. 709/С → 712/С". */
fun ScheduleChange.summary(): String = when (type) {
    ChangeType.ROOM -> "$subject: ауд. $oldValue → $newValue"
    ChangeType.TEACHER -> "$subject: $oldValue → $newValue"
    ChangeType.SUBJECT -> "$oldValue → $newValue"
    ChangeType.CANCELLED -> "$subject — пара отменена"
    ChangeType.ADDED -> "добавлена пара: $newValue"
}

/** "ВТ 29.09, 3 пара". */
fun ScheduleChange.slotLabel(): String = "${shortDayName(date)} ${shortDate(date)}, $lessonNumber пара"

/** Заголовок и строки уведомления о заменах. */
data class ChangeNotificationContent(val title: String, val lines: List<String>, val overflow: Int)

fun changeNotificationContent(changes: List<ScheduleChange>, maxLines: Int = 5): ChangeNotificationContent {
    val sorted = changes.sortedWith(compareBy({ it.date }, { it.lessonNumber }))
    val lines = sorted.map { "${it.slotLabel()} · ${it.summary()}" }
    val title = if (changes.size == 1) "Изменение в расписании" else "Изменения в расписании: ${changes.size}"
    return ChangeNotificationContent(title, lines.take(maxLines), (lines.size - maxLines).coerceAtLeast(0))
}
