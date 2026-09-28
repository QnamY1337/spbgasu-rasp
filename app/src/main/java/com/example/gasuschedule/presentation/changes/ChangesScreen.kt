package com.example.gasuschedule.presentation.changes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gasuschedule.domain.model.ChangeType
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.domain.model.ScheduleTime
import com.example.gasuschedule.presentation.common.StatusBarIcons
import com.example.gasuschedule.presentation.theme.GasuTheme
import com.example.gasuschedule.presentation.theme.MonoStyles
import com.example.gasuschedule.presentation.theme.PlexMono
import com.example.gasuschedule.work.ChangeNotifications
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ChangesRoute(viewModel: ChangesViewModel = hiltViewModel()) {
    StatusBarIcons(onBrickHeader = false)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Замены просмотрены — уведомление о них больше не нужно.
    LaunchedEffect(Unit) { ChangeNotifications.cancel(context) }
    ChangesScreen(state, today = LocalDate.now(ScheduleTime.ZONE))
}

@Composable
fun ChangesScreen(state: ChangesUiState, today: LocalDate) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Замены", style = MaterialTheme.typography.headlineMedium)
            state.lastSyncAt?.let {
                Text(
                    "Последняя сверка с сайтом — ${relativeTime(it, today)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (state.loaded && state.isEmpty) {
            item { EmptyChanges() }
        }
        days(state.upcoming, dimmed = false)
        if (state.past.isNotEmpty()) {
            item {
                Text(
                    "ПРОШЕДШИЕ",
                    style = MonoStyles.label,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 20.dp),
                )
            }
            days(state.past, dimmed = true)
        }
    }
}

private fun LazyListScope.days(days: List<ChangeDay>, dimmed: Boolean) {
    for (day in days) {
        item(key = "day-${day.date}-$dimmed") {
            Text(
                DAY_HEADER.format(day.date).uppercase(RU),
                style = MonoStyles.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 12.dp),
            )
        }
        items(day.cards, key = { it.key }) { card ->
            ChangeCardView(card, Modifier.alpha(if (dimmed) 0.6f else 1f))
        }
    }
}

@Composable
private fun ChangeCardView(card: ChangeCard, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val faint = GasuTheme.colors.textFaint
    val cancelled = card.changes.all { it.type == ChangeType.CANCELLED }
    val accent = scheme.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (cancelled) scheme.surface.copy(alpha = 0.6f) else scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            Modifier
                .then(
                    if (card.isNew) Modifier.drawBehind {
                        drawRect(accent, size = Size(4.dp.toPx(), size.height))
                    } else Modifier,
                )
                .padding(start = if (card.isNew) 20.dp else 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    card.subject,
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                    color = if (cancelled) scheme.onSurfaceVariant else scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text("${card.lessonNumber} пара", style = MonoStyles.label, color = scheme.onSurfaceVariant)
            }
            card.changes.forEach { change ->
                Spacer(Modifier.height(6.dp))
                Text(changeLine(change, accent, faint, scheme.onSurfaceVariant), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** "709/С → 712/С — сменилась аудитория": старое зачёркнуто, новое акцентом, аудитории моноширинным. */
private fun changeLine(
    change: ScheduleChange,
    accent: Color,
    faint: Color,
    muted: Color,
): AnnotatedString = buildAnnotatedString {
    val mono = change.type == ChangeType.ROOM
    when (change.type) {
        ChangeType.CANCELLED -> withStyle(SpanStyle(color = accent.copy(alpha = 0.8f))) { append(change.type.label) }
        ChangeType.ADDED -> {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(change.type.label) }
            change.newValue?.let { withStyle(SpanStyle(color = muted)) { append(": $it") } }
        }
        else -> {
            withStyle(
                SpanStyle(
                    color = faint,
                    textDecoration = TextDecoration.LineThrough,
                    fontFamily = if (mono) PlexMono else null,
                ),
            ) { append(change.oldValue.orEmpty()) }
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("  →  ") }
            withStyle(
                SpanStyle(color = accent, fontWeight = FontWeight.SemiBold, fontFamily = if (mono) PlexMono else null),
            ) { append(change.newValue.orEmpty()) }
            withStyle(SpanStyle(color = muted)) { append("  — ${change.type.label}") }
        }
    }
}

@Composable
private fun EmptyChanges() {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Замен пока не было", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            "Приложение сверяет расписание с сайтом каждые 2–4 часа и сообщит, если аудитория, преподаватель или пара поменяются.",
            style = MaterialTheme.typography.bodyMedium,
            color = GasuTheme.colors.textFaint,
            textAlign = TextAlign.Center,
        )
    }
}

private val RU = Locale.forLanguageTag("ru")
private val DAY_HEADER = DateTimeFormatter.ofPattern("d MMMM, EEEE", RU)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", RU)
private val DATE_TIME = DateTimeFormatter.ofPattern("d MMMM в HH:mm", RU)

/** "сегодня в 07:12", "вчера в 21:40", "26 сентября в 10:05". */
fun relativeTime(at: Instant, today: LocalDate): String {
    val local = at.atZone(ScheduleTime.ZONE)
    return when (local.toLocalDate()) {
        today -> "сегодня в ${TIME.format(local)}"
        today.minusDays(1) -> "вчера в ${TIME.format(local)}"
        else -> DATE_TIME.format(local)
    }
}
