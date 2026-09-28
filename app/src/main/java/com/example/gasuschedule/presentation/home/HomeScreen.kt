package com.example.gasuschedule.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.usecase.LeaveEstimate
import com.example.gasuschedule.presentation.common.StatusBarIcons
import com.example.gasuschedule.presentation.common.YandexMaps
import com.example.gasuschedule.presentation.homework.dueText
import com.example.gasuschedule.presentation.lessondetail.LessonDetailSheet
import com.example.gasuschedule.presentation.schedule.CenteredDivider
import com.example.gasuschedule.presentation.schedule.LessonCard
import com.example.gasuschedule.presentation.schedule.LessonTiming
import com.example.gasuschedule.presentation.schedule.LocalLessonClick
import com.example.gasuschedule.presentation.schedule.dayTitle
import com.example.gasuschedule.presentation.schedule.lessonTimings
import com.example.gasuschedule.presentation.schedule.lessonsCount
import com.example.gasuschedule.presentation.schedule.shortDate
import com.example.gasuschedule.presentation.schedule.shortDayName
import com.example.gasuschedule.presentation.schedule.weekPill
import com.example.gasuschedule.presentation.theme.GasuTheme
import com.example.gasuschedule.presentation.theme.MonoStyles
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
fun HomeRoute(
    onOpenSettings: () -> Unit,
    onOpenHomework: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    StatusBarIcons(onBrickHeader = true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    CompositionLocalProvider(LocalLessonClick provides viewModel::openLesson) {
        HomeScreen(state, snackbar, onRefresh = { viewModel.refresh() }, onOpenSettings = onOpenSettings, onOpenHomework = onOpenHomework)
    }
    detail?.let { LessonDetailSheet(it, onDismiss = { viewModel.openLesson(null) }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHomework: () -> Unit = {},
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val listState = rememberLazyListState()
        val collapse = remember { HeaderCollapse() }
        val scrollConnection = remember(listState, collapse) { collapse.connection { listState.canScrollBackward } }
        // Шапка — над списком (внутри прокрутки не срабатывал отступ под статус-бар),
        // и сворачивается вместе с прокруткой списка.
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .nestedScroll(scrollConnection),
        ) {
            Header(state, collapse)
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (state.loaded) HomeContent(state, onOpenSettings, onOpenHomework, listState)
            }
        }
    }
}

/**
 * Сворачивание шапки при прокрутке. [offsetPx] — на сколько шапка уже свёрнута:
 * 0 — раскрыта, [maxPx] — надписи полностью скрыты, остаётся только кирпичная полоса под статус-баром.
 */
@Stable
class HeaderCollapse {
    var maxPx by mutableFloatStateOf(0f)
    var offsetPx by mutableFloatStateOf(0f)
    val fraction: Float get() = if (maxPx <= 0f) 0f else (offsetPx / maxPx).coerceIn(0f, 1f)

    /** Сдвиг на dy (как в прокрутке: dy < 0 — листаем вниз). Возвращает съеденную часть dy. */
    fun consume(dy: Float): Float {
        val old = offsetPx
        offsetPx = (offsetPx - dy).coerceIn(0f, maxPx)
        return old - offsetPx
    }

    /**
     * Листаем вниз — сначала сворачивается шапка, потом едет список.
     * Листаем вверх — шапка раскрывается, только когда список уже в самом начале;
     * остаток жеста достаётся pull-to-refresh.
     */
    fun connection(listCanScrollBack: () -> Boolean) = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            val consumed = when {
                dy < 0 -> consume(dy)
                dy > 0 && !listCanScrollBack() -> consume(dy)
                else -> 0f
            }
            return Offset(0f, consumed)
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onOpenSettings: () -> Unit,
    onOpenHomework: () -> Unit,
    listState: LazyListState,
) {
    val timings = remember(state.lessons, state.now) { lessonTimings(state.lessons, state.now) }
    val side = Modifier.padding(horizontal = 20.dp)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { WeatherSlot(side) }
        state.commute?.let { commute ->
            item { CommuteCard(commute, state, onOpenSettings, side) }
        }
        if (state.urgentHomework.isNotEmpty()) {
            item { UrgentHomeworkCard(state, onOpenHomework, side) }
        }
        item { DayTitle(state, side.padding(top = 8.dp)) }
        items(state.lessons, key = { it.id }) { lesson ->
            LessonCard(lesson, timings[lesson.id] ?: LessonTiming.UPCOMING, state.now, side)
        }
        if (state.lessons.isNotEmpty()) {
            item { CenteredDivider("Пар больше нет", side.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun Header(state: HomeUiState, collapse: HeaderCollapse) {
    val colors = GasuTheme.colors
    // Кирпичная полоса под статус-баром остаётся всегда; содержимое под ней сжимается и тает.
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.header)
            .statusBarsPadding()
            .clipToBounds(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    collapse.maxPx = placeable.height.toFloat()
                    val offset = collapse.offsetPx.roundToInt()
                    layout(placeable.width, (placeable.height - offset).coerceAtLeast(0)) {
                        placeable.place(0, -offset)
                    }
                }
                .graphicsLayer { alpha = 1f - collapse.fraction }
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 22.dp),
        ) {
            HeaderContent(state)
        }
    }
}

@Composable
private fun HeaderContent(state: HomeUiState) {
    val colors = GasuTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("СПБГАСУ", style = MaterialTheme.typography.labelMedium, color = colors.onHeaderMuted, modifier = Modifier.weight(1f))
            state.week?.let {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.onHeader.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, colors.onHeader.copy(alpha = 0.3f)),
                ) {
                    Text(weekPill(it), style = MonoStyles.label, color = colors.onHeader, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            dayTitle(state.today),
            // В 2 раза меньше headlineMedium (26 sp) — по просьбе, чтобы шапка была компактнее.
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = colors.onHeader,
        )
        state.group?.let {
            Text(it, style = MonoStyles.label, color = colors.onHeaderMuted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Место под погоду на день (появится в фазе 10). */
@Composable
private fun WeatherSlot(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("☁", style = MaterialTheme.typography.headlineMedium, color = GasuTheme.colors.textFaint)
            Spacer(Modifier.size(14.dp))
            Column {
                Text("Погода — скоро", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
                Text("Здесь появится погода на день", style = MaterialTheme.typography.bodySmall, color = GasuTheme.colors.textFaint)
            }
        }
    }
}

@Composable
private fun CommuteCard(commute: Commute, state: HomeUiState, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val l = commute.lesson
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = scheme.primaryContainer) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "ДОРОГА К 1 ПАРЕ · ${dayLabel(l.date, state.today)}",
                style = MonoStyles.label,
                color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(6.dp))
            when (val leave = commute.leave) {
                LeaveEstimate.NoHome -> {
                    Text(
                        "Укажите адрес дома — посчитаем, во сколько выходить к первой паре.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer,
                    )
                    TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(0.dp)) {
                        Text("Указать адрес", fontWeight = FontWeight.SemiBold)
                    }
                }
                is LeaveEstimate.UnknownBuilding -> Text(
                    "Корпус первой пары не найден в справочнике — маршрут недоступен.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                )
                is LeaveEstimate.Estimated -> {
                    val r = leave.route
                    val leaveAt = l.date.atTime(r.recommendedLeaveTime)
                    Text("Выйти в ${r.recommendedLeaveTime}", style = MaterialTheme.typography.headlineMedium, color = scheme.onPrimaryContainer)
                    leaveHint(state.now, leaveAt)?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, color = scheme.primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    val how = if (r.mode == TravelMode.WALKING) "пешком" else "на транспорте"
                    Text(
                        "Дорога ≈${r.travelMinutes} мин $how · ${r.building.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { YandexMaps.openRoute(context, state.home?.point, r.building.location, r.mode) },
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, scheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.primary),
                    ) { Text("Маршрут в Яндекс.Картах", fontWeight = FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${l.startTime} · ${l.subject} · ${l.room}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

/** "Задания горят": просроченные и со сроком сегодня/завтра. Тап — во вкладку "Задания". */
@Composable
private fun UrgentHomeworkCard(state: HomeUiState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.primary),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "ЗАДАНИЯ ГОРЯТ · ${state.urgentHomework.size}",
                style = MonoStyles.label,
                color = scheme.primary,
            )
            state.urgentHomework.take(3).forEach { item ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.subject,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    item.dueDate?.let {
                        Text(
                            dueText(it, state.today),
                            style = MonoStyles.label,
                            color = if (it.isBefore(state.today)) scheme.error else scheme.primary,
                        )
                    }
                }
            }
            if (state.urgentHomework.size > 3) {
                Text(
                    "и ещё ${state.urgentHomework.size - 3}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DayTitle(state: HomeUiState, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val date = state.shownDate
    Column(modifier) {
        when {
            date == null -> Text("В ближайшие две недели пар нет", style = MaterialTheme.typography.titleLarge, color = muted)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (date) {
                            state.today -> "Сегодня"
                            state.today.plusDays(1) -> "Завтра"
                            else -> dayTitle(date)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(lessonsCount(state.lessons.size), style = MonoStyles.label, color = muted)
                }
                if (date != state.today) {
                    Text(
                        if (state.todayFinished) "Пары на сегодня закончились" else "Сегодня пар нет",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                    )
                }
            }
        }
    }
}

/** "сегодня", "завтра", "СР 30.09". */
internal fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "СЕГОДНЯ"
    today.plusDays(1) -> "ЗАВТРА"
    else -> "${shortDayName(date)} ${shortDate(date)}"
}

/** Подсказка под временем выхода: "через 1 ч 20 мин", "пора выходить!" или null, если ещё далеко. */
internal fun leaveHint(now: LocalDateTime, leaveAt: LocalDateTime): String? {
    val minutes = Duration.between(now, leaveAt).toMinutes()
    return when {
        minutes <= 0 -> "Пора выходить!"
        minutes < 60 -> "через $minutes мин"
        minutes < 6 * 60 -> "через ${minutes / 60} ч ${minutes % 60} мин".replace(" 0 мин", "")
        else -> null
    }
}
