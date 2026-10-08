package com.example.gasuschedule.presentation.widget

import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.usecase.LeaveEstimate
import com.example.gasuschedule.domain.usecase.NextLesson
import com.example.gasuschedule.presentation.common.YandexMaps
import com.example.gasuschedule.presentation.home.leaveHint
import com.example.gasuschedule.presentation.schedule.shortDate
import com.example.gasuschedule.presentation.schedule.shortDayName
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

/**
 * Виджет "Дорога" (4×1): подпись сверху, крупная строка, пояснение и кнопка маршрута.
 * К первой паре дня с указанным домом — "Выйти в 08:10"; в остальное время — ближайшая пара.
 *
 * @param routeQuery параметры маршрута для Яндекс.Карт; null — кнопки маршрута нет.
 */
data class WidgetModel(
    val label: String,
    val title: String,
    val subtitle: String?,
    val routeQuery: String? = null,
)

private val RU = Locale.forLanguageTag("ru")

/** "ИДЁТ СЕЙЧАС", "БЛИЖАЙШАЯ ПАРА", "ЗАВТРА", "СР 30.09". */
private fun whenLabel(next: NextLesson.Found, now: LocalDateTime): String {
    val date = next.lesson.date
    val today = now.toLocalDate()
    return when {
        next.ongoing -> "ИДЁТ СЕЙЧАС"
        date == today -> "БЛИЖАЙШАЯ ПАРА"
        date == today.plusDays(1) -> "ЗАВТРА"
        else -> "${shortDayName(date)} ${shortDate(date)}"
    }
}

private fun NextLesson.Found.slot(): List<Lesson> = listOf(lesson) + parallel
private fun NextLesson.Found.subjects() = slot().map { it.subject }.distinct().joinToString(" / ")
private fun NextLesson.Found.rooms() = slot().flatMap { it.rooms }.distinct().joinToString(", ")
private fun NextLesson.Found.teachers() = slot().flatMap { it.teachers }.distinct().joinToString(", ")

/**
 * @param leave время выхода к паре (если указан дом) — берётся только для первой пары дня.
 * @param home точка дома — начало маршрута в Картах.
 */
fun widgetModel(
    next: NextLesson,
    now: LocalDateTime,
    leave: LeaveEstimate? = null,
    home: GeoPoint? = null,
): WidgetModel = when (next) {
    NextLesson.NoGroup -> WidgetModel("РАСПИСАНИЕ СПБГАСУ", "Выберите группу", "Откройте приложение, чтобы начать")

    NextLesson.NoneSoon -> WidgetModel("БЛИЖАЙШАЯ ПАРА", "Пар нет", "В ближайшие две недели занятий нет")

    is NextLesson.Found -> {
        val l = next.lesson
        val route = (leave as? LeaveEstimate.Estimated)?.route?.takeIf { !next.ongoing && next.firstOfDay }
        if (route != null) {
            val day = when (l.date) {
                now.toLocalDate() -> ""
                now.toLocalDate().plusDays(1) -> "ЗАВТРА "
                else -> "${shortDayName(l.date)} ${shortDate(l.date)} "
            }
            val how = if (route.mode == TravelMode.WALKING) "пешком" else "на транспорте"
            val hint = leaveHint(now, l.date.atTime(route.recommendedLeaveTime))?.lowercase(RU)
            WidgetModel(
                label = listOf("${day}К ${l.startTime}", next.subjects().uppercase(RU), next.rooms())
                    .filter { it.isNotBlank() }.joinToString(" · "),
                title = "Выйти в ${route.recommendedLeaveTime}",
                subtitle = listOfNotNull("≈${route.travelMinutes} мин $how", hint).joinToString(" · "),
                routeQuery = YandexMaps.routeQuery(home, route.building.location, route.mode),
            )
        } else {
            val corner = if (next.ongoing) "до ${l.endTime}" else l.startTime.toString()
            WidgetModel(
                label = "${whenLabel(next, now)} · ${corner.uppercase(RU)}",
                title = next.subjects(),
                subtitle = listOf(next.rooms(), next.teachers()).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null },
            )
        }
    }
}

/**
 * Виджет "Обратный отсчёт" (2×2).
 * @param caption подпись сверху ("ЧЕРЕЗ", "ИДЁТ · ДО 10:30", "ЗАВТРА")
 * @param value крупное значение ("25", "2:10", "09:00"); null — не показываем
 * @param unit единица рядом со значением ("мин", "ч")
 * @param progress доля прошедшей пары, если она идёт
 */
data class CountdownModel(
    val caption: String,
    val value: String?,
    val unit: String?,
    val title: String,
    val subtitle: String?,
    val progress: Float? = null,
)

fun countdownModel(next: NextLesson, now: LocalDateTime): CountdownModel = when (next) {
    NextLesson.NoGroup -> CountdownModel("РАСПИСАНИЕ", null, null, "Выберите группу", "Откройте приложение")
    NextLesson.NoneSoon -> CountdownModel("БЛИЖАЙШАЯ ПАРА", null, null, "Пар нет", "Две недели свободны")
    is NextLesson.Found -> {
        val l = next.lesson
        val start = l.date.atTime(l.startTime)
        val end = l.date.atTime(l.endTime)
        val rooms = next.rooms()
        if (next.ongoing) {
            val total = Duration.between(start, end).toMinutes().coerceAtLeast(1)
            val left = Duration.between(now, end).toMinutes().coerceIn(0, total)
            CountdownModel(
                caption = "ИДЁТ · ДО ${l.endTime}",
                value = null,
                unit = null,
                title = next.subjects(),
                subtitle = listOf(rooms, "ещё $left мин").filter { it.isNotBlank() }.joinToString(" · "),
                progress = (total - left).toFloat() / total,
            )
        } else {
            val minutes = Duration.between(now, start).toMinutes().coerceAtLeast(0)
            val subtitle = listOf(rooms, l.startTime.toString()).filter { it.isNotBlank() }.joinToString(" · ")
            when {
                minutes < 60 -> CountdownModel("ЧЕРЕЗ", minutes.toString(), "мин", next.subjects(), subtitle)
                minutes < 6 * 60 -> CountdownModel(
                    "ЧЕРЕЗ",
                    "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}",
                    "ч",
                    next.subjects(),
                    subtitle,
                )
                else -> CountdownModel(
                    caption = if (l.date == now.toLocalDate()) "СЕГОДНЯ" else whenLabel(next, now),
                    value = l.startTime.toString(),
                    unit = null,
                    title = next.subjects(),
                    subtitle = rooms.ifBlank { null },
                )
            }
        }
    }
}

/**
 * Когда перерисовать отсчёт: каждую минуту, пока пара идёт или до неё меньше 6 часов;
 * иначе — когда до неё останется 6 часов (и не позже границы [NextLesson.Found.validUntil]).
 */
fun countdownRefreshAt(next: NextLesson.Found, now: LocalDateTime): LocalDateTime {
    val start = next.lesson.date.atTime(next.lesson.startTime)
    val sixHoursBefore = start.minusHours(6)
    val at = if (next.ongoing || !now.isBefore(sixHoursBefore)) now.plusMinutes(1).withSecond(0).withNano(0) else sixHoursBefore
    return minOf(at, next.validUntil)
}
