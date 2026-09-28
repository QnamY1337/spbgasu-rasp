package com.example.gasuschedule.presentation.lessondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.gasuschedule.domain.model.Building
import com.example.gasuschedule.domain.model.Buildings
import com.example.gasuschedule.domain.usecase.EstimateLeaveTimeUseCase
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.model.shortLabel
import com.example.gasuschedule.domain.usecase.LeaveEstimate
import com.example.gasuschedule.presentation.common.YandexMaps
import com.example.gasuschedule.presentation.schedule.shortDate
import com.example.gasuschedule.presentation.schedule.shortDayName
import com.example.gasuschedule.presentation.theme.MonoStyles

/** Всё, что нужно карточке пары. */
data class LessonDetail(
    val lesson: Lesson,
    val building: Building?,
    val leave: LeaveEstimate,
    /** Первая пара дня — к ней выходят из дома. */
    val firstOfDay: Boolean,
    val home: HomeLocation?,
    val mode: TravelMode,
) {
    companion object {
        /** [dayLessons] — пары того же дня (чтобы понять, первая ли это пара). */
        fun of(lesson: Lesson, dayLessons: List<Lesson>, home: HomeLocation?, mode: TravelMode, bufferMinutes: Int) =
            LessonDetail(
                lesson = lesson,
                building = Buildings.byCode(lesson.building),
                leave = EstimateLeaveTimeUseCase.estimate(lesson, home, mode, bufferMinutes),
                firstOfDay = EstimateLeaveTimeUseCase.isFirstOfDay(lesson, dayLessons),
                home = home,
                mode = mode,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailSheet(detail: LessonDetail, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        LessonDetailContent(detail)
    }
}

@Composable
fun LessonDetailContent(detail: LessonDetail) {
    val l = detail.lesson
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
    ) {
        Text(
            buildAnnotatedString {
                append(l.subject)
                if (l.type != LessonType.OTHER) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = scheme.onSurfaceVariant)) {
                        append(" (${l.type.shortLabel})")
                    }
                }
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${shortDayName(l.date)} ${shortDate(l.date)} · ${l.lessonNumber} пара · ${l.startTime}–${l.endTime}",
            style = MonoStyles.label,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        InfoRow("Аудитория", l.rooms.joinToString(", ").ifEmpty { "—" })
        InfoRow("Преподаватель", l.teachers.joinToString(", ").ifEmpty { "—" })
        InfoRow(
            "Корпус",
            detail.building?.let { "${it.name}\n${it.address}" }
                ?: "«${l.building ?: "?"}» — нет в справочнике корпусов",
        )
        l.note?.let { InfoRow("Примечание", it) }

        Spacer(Modifier.height(16.dp))
        RoadBlock(detail)

        detail.building?.let { building ->
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { YandexMaps.openRoute(context, detail.home?.point, building.location, detail.mode) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text("Маршрут в Яндекс.Картах") }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RoadBlock(detail: LessonDetail) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.medium, color = scheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when (val leave = detail.leave) {
                LeaveEstimate.NoHome -> Text(
                    "Укажите адрес дома в настройках — посчитаем, во сколько выходить к первой паре.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                )
                is LeaveEstimate.UnknownBuilding -> Text(
                    "Маршрут недоступен: корпус не найден в справочнике.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                )
                is LeaveEstimate.Estimated -> {
                    val r = leave.route
                    val how = if (r.mode == TravelMode.WALKING) "пешком" else "на транспорте"
                    if (detail.firstOfDay) {
                        Text("Выйти в ${r.recommendedLeaveTime}", style = MaterialTheme.typography.headlineMedium, color = scheme.onPrimaryContainer)
                        Text(
                            "Дорога ≈${r.travelMinutes} мин $how + запас",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onPrimaryContainer,
                        )
                    } else {
                        Text(
                            "Из дома ≈${r.travelMinutes} мин $how. Это не первая пара дня — время выхода считается к первой.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Оценка по расстоянию. Точное время — в Яндекс.Картах.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}
