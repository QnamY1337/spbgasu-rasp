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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.example.gasuschedule.domain.model.Lesson
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
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
import com.example.gasuschedule.domain.model.DayWeather
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.model.WeatherPlace
import com.example.gasuschedule.domain.usecase.LeaveEstimate
import com.example.gasuschedule.presentation.common.StatusBarIcons
import com.example.gasuschedule.presentation.common.YandexMaps
import com.example.gasuschedule.presentation.homework.dueText
import com.example.gasuschedule.presentation.homework.LessonHomeworkMenu
import com.example.gasuschedule.presentation.schedule.CenteredDivider
import com.example.gasuschedule.presentation.schedule.LessonCard
import com.example.gasuschedule.presentation.schedule.LessonTiming
import com.example.gasuschedule.presentation.schedule.LocalLessonMenu
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
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    CompositionLocalProvider(LocalLessonMenu provides { lesson, expanded, dismiss -> LessonHomeworkMenu(lesson, expanded, dismiss) }) {
        HomeScreen(state, snackbar, onRefresh = { viewModel.refresh() }, onOpenSettings = onOpenSettings, onOpenHomework = onOpenHomework)
    }
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
        state.weather?.let { weather -> item { WeatherCard(weather, side) } }
        state.commute?.let { commute ->
            item { CommuteCard(commute, state, onOpenSettings, side) }
        }
        if (state.urgentHomework.isNotEmpty()) {
            item { UrgentHomeworkCard(state, onOpenHomework, side) }
        }
        item { DayTitle(state, side.padding(top = 8.dp)) }
        items(state.lessons, key = { it.id }) { lesson ->
            val index = state.lessons.indexOf(lesson)
            TimelineRow(
                lesson = lesson,
                timing = timings[lesson.id] ?: LessonTiming.UPCOMING,
                first = index == 0,
                last = index == state.lessons.lastIndex,
                modifier = side,
            ) { LessonCard(lesson, timings[lesson.id] ?: LessonTiming.UPCOMING, state.now, it, homework = state.homework[lesson.id].orEmpty(), showTime = false) }
        }
        if (state.lessons.isNotEmpty()) {
            item { CenteredDivider("Пар больше нет", side.padding(top = 8.dp)) }
        }
    }
}

/** Строка таймлайна: время слева, рельса с точкой, карточка пары справа. */
@Composable
private fun TimelineRow(
    lesson: Lesson,
    timing: LessonTiming,
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    card: @Composable (Modifier) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val faint = GasuTheme.colors.textFaint
    val past = timing == LessonTiming.PAST
    val highlighted = timing == LessonTiming.CURRENT || timing == LessonTiming.NEXT
    val dotY = if (highlighted) 24.dp else 14.dp
    Row(modifier.height(IntrinsicSize.Min)) {
        Column(Modifier.width(52.dp).padding(top = dotY - 9.dp), horizontalAlignment = Alignment.End) {
            Text(lesson.startTime.toString(), style = MonoStyles.time, color = if (past) faint else scheme.onSurface, maxLines = 1, softWrap = false)
            Text(lesson.endTime.toString(), style = MonoStyles.timeSecondary, color = if (past) faint else scheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
        Box(
            Modifier
                .width(24.dp)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val y = dotY.toPx()
                    val line = scheme.outlineVariant
                    drawLine(line, Offset(x, if (first) y else 0f), Offset(x, if (last) y else size.height), strokeWidth = 2.dp.toPx())
                    if (highlighted) {
                        drawCircle(scheme.primary, radius = 6.dp.toPx(), center = Offset(x, y))
                    } else {
                        drawCircle(scheme.surface, radius = 6.dp.toPx(), center = Offset(x, y))
                        drawCircle(if (past) line else scheme.outline, radius = 6.dp.toPx(), center = Offset(x, y), style = Stroke(2.dp.toPx()))
                    }
                },
        )
        card(Modifier.weight(1f))
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
        Text(dayTitle(state.today).uppercase(), style = MonoStyles.label, color = colors.onHeaderMuted)
        Spacer(Modifier.height(4.dp))
        Text(
            headline(state),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
            color = colors.onHeader,
        )
        val chips = buildList {
            if (state.shownDate == state.today) state.lessons.lastOrNull()?.let { add("Заканчиваем в ${it.endTime}") }
        }
        if (chips.isNotEmpty() || state.group != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                state.group?.let { HeaderChip(it, mono = true) }
                chips.forEach { HeaderChip(it) }
            }
        }
    }
}

@Composable
private fun HeaderChip(text: String, mono: Boolean = false) {
    val colors = GasuTheme.colors
    Surface(shape = RoundedCornerShape(8.dp), color = colors.onHeader.copy(alpha = 0.16f)) {
        Text(
            text,
            style = if (mono) MonoStyles.label.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodySmall,
            color = colors.onHeader,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** "Сегодня 3 пары, первая в 10:00"; в выходной и после пар — про ближайший день. */
private fun headline(state: HomeUiState): String {
    val date = state.shownDate ?: return "В ближайшие две недели пар нет"
    val first = state.lessons.firstOrNull() ?: return "Пар нет"
    val count = lessonsCount(state.lessons.size)
    return when {
        date == state.today -> "Сегодня $count, первая в ${first.startTime}"
        date == state.today.plusDays(1) -> "Завтра $count, первая в ${first.startTime}"
        else -> "${dayTitle(date)}: $count, первая в ${first.startTime}"
    }
}

/** Погода на сегодня: сейчас, днём/ночью, осадки и подсказка про зонт. */
@Composable
private fun WeatherCard(weather: DayWeather, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(weather.icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.size(12.dp))
            Text(
                signedTemp(weather.tempC),
                style = MonoStyles.time.copy(fontSize = 28.sp, lineHeight = 32.sp),
                color = scheme.onSurface,
            )
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(weather.condition, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${signedTemp(weather.tempMin)}…${signedTemp(weather.tempMax)} · " +
                        if (weather.place == WeatherPlace.HOME) "у дома" else "у вуза",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                weather.precipitationChance?.let { chance ->
                    Text(
                        when {
                            weather.umbrella -> "Осадки до $chance% — возьмите зонт"
                            chance < 10 -> "Без осадков"
                            else -> "Осадки до $chance%"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (weather.umbrella) scheme.primary else scheme.onSurfaceVariant,
                        fontWeight = if (weather.umbrella) FontWeight.SemiBold else null,
                    )
                }
            }
        }
    }
}

/** "+11°", "−3°", "0°" — со знаком, как в прогнозах. */
internal fun signedTemp(t: Int): String = when {
    t > 0 -> "+$t°"
    t < 0 -> "−${-t}°"
    else -> "0°"
}

@Composable
private fun CommuteCard(commute: Commute, state: HomeUiState, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val l = commute.lesson
    // Пара сама видна в таймлайне ниже — здесь только дорога к ней.
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = scheme.primaryContainer) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)) {
            Text(
                "ДОРОГА · ${dayLabel(l.date, state.today)} К ${l.startTime}",
                style = MonoStyles.caption.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            when (val leave = commute.leave) {
                LeaveEstimate.NoHome -> {
                    Text(
                        "Укажите адрес дома — посчитаем, во сколько выходить к первой паре.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(0.dp)) {
                        Text("Указать адрес", fontWeight = FontWeight.SemiBold)
                    }
                }
                is LeaveEstimate.UnknownBuilding -> Text(
                    "Корпус первой пары не найден в справочнике — маршрут недоступен.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.padding(end = 8.dp),
                )
                is LeaveEstimate.Estimated -> {
                    val r = leave.route
                    val leaveAt = l.date.atTime(r.recommendedLeaveTime)
                    val how = if (r.mode == TravelMode.WALKING) "пешком" else "на транспорте"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "Выйти в ${r.recommendedLeaveTime}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
                                    color = scheme.onPrimaryContainer,
                                )
                                leaveHint(state.now, leaveAt)?.let {
                                    Text(
                                        "  $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.primary,
                                        modifier = Modifier.padding(bottom = 3.dp),
                                    )
                                }
                            }
                            Text(
                                "≈${r.travelMinutes} мин $how · ${r.building.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                        }
                        TextButton(onClick = { YandexMaps.openRoute(context, state.home?.point, r.building.location, r.mode) }) {
                            Text("Маршрут", fontWeight = FontWeight.SemiBold, color = scheme.primary)
                        }
                    }
                }
            }
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
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
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
