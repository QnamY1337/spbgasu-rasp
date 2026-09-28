package com.example.gasuschedule.presentation.widget

import com.example.gasuschedule.domain.usecase.NextLesson
import com.example.gasuschedule.presentation.schedule.shortDate
import com.example.gasuschedule.presentation.schedule.shortDayName
import java.time.LocalDateTime

/** Тексты виджета по макету: подпись сверху, время справа, предмет, аудитория/преподаватель, плашка. */
data class WidgetModel(
    val label: String,
    val corner: String?,
    val title: String,
    val subtitle: String?,
    val chip: String?,
)

fun widgetModel(next: NextLesson, now: LocalDateTime): WidgetModel = when (next) {
    NextLesson.NoGroup -> WidgetModel(
        label = "РАСПИСАНИЕ СПБГАСУ",
        corner = null,
        title = "Выберите группу",
        subtitle = "Откройте приложение, чтобы начать",
        chip = null,
    )

    NextLesson.NoneSoon -> WidgetModel(
        label = "БЛИЖАЙШАЯ ПАРА",
        corner = null,
        title = "Пар нет",
        subtitle = "В ближайшие две недели занятий нет",
        chip = null,
    )

    is NextLesson.Found -> {
        val l = next.lesson
        val slot = listOf(l) + next.parallel
        val today = now.toLocalDate()
        val label = when {
            next.ongoing -> "ИДЁТ СЕЙЧАС"
            l.date == today -> "БЛИЖАЙШАЯ ПАРА"
            l.date == today.plusDays(1) -> "ЗАВТРА"
            else -> "${shortDayName(l.date)} ${shortDate(l.date)}"
        }
        WidgetModel(
            label = label,
            corner = if (next.ongoing) "до ${l.endTime}" else l.startTime.toString(),
            title = slot.map { it.subject }.distinct().joinToString(" / "),
            subtitle = listOf(
                slot.flatMap { it.rooms }.distinct().joinToString(", "),
                slot.flatMap { it.teachers }.distinct().joinToString(", "),
            ).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null },
            // В фазе 7 здесь появится "Выйти в 10:11".
            chip = "${l.lessonNumber} пара · ${l.startTime}–${l.endTime}",
        )
    }
}
