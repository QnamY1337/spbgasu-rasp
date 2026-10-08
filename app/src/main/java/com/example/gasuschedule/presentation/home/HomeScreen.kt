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
import java.time.LocalTime
import androidx.compose.ui.graphics.PathEffect
import com.example.gasuschedule.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import com.example.gasuschedule.domain.model.WeekParity
import com.example.gasuschedule.domain.model.ScheduleWeek
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
    onOpenDay: (LocalDate) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    StatusBarIcons(onBrickHeader = true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    CompositionLocalProvider(LocalLessonMenu provides { lesson, expanded, dismiss -> LessonHomeworkMenu(lesson, expanded, dismiss) }) {
        HomeScreen(state, snackbar, onRefresh = { viewModel.refresh() }, onOpenSettings = onOpenSettings, onOpenHomework = onOpenHomework, onOpenDay = onOpenDay)
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
    onOpenDay: (LocalDate) -> Unit = {},
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
            Header(state, collapse, onOpenDay)
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
    val timings = remember(state.lessons, state.now, state.subjectFilter) {
        lessonTimings(state.lessons, state.now, state.subjectFilter::isDisabled)
    }
    val side = Modifier.padding(horizontal = 20.dp)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.weather != null || state.commute != null) {
            item { WeatherRoadStrip(state, onOpenSettings, side) }
        }
        if (state.urgentHomework.isNotEmpty()) {
            item { UrgentHomeworkCard(state, onOpenHomework, side) }
        }
        item { DayTitle(state, side.padding(top = 8.dp)) }
        items(state.lessons, key = { it.id }) { lesson ->
            val index = state.lessons.indexOf(lesson)
            val disabled = state.subjectFilter.isDisabled(lesson)
            TimelineRow(
                lesson = lesson,
                disabled = disabled,
                timing = timings[lesson.id] ?: LessonTiming.UPCOMING,
                first = index == 0,
                last = index == state.lessons.lastIndex,
                modifier = side,
            ) { LessonCard(lesson, timings[lesson.id] ?: LessonTiming.UPCOMING, state.now, it, homework = state.homework[lesson.id].orEmpty(), showTime = false, disabled = disabled) }
            breakAfter(state.lessons, index)?.let { gap ->
                BreakRow(gap, state.now.takeIf { state.shownDate == state.today }?.toLocalTime(), side.padding(top = 12.dp))
            }
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
    disabled: Boolean,
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    card: @Composable (Modifier) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val faint = GasuTheme.colors.textFaint
    val past = disabled || timing == LessonTiming.PAST
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

/** Длинный промежуток между парами: с 30 минут — обед, больше полутора часов — окно. */
internal data class LessonBreak(val from: LocalTime, val to: LocalTime) {
    val minutes: Long get() = Duration.between(from, to).toMinutes()
    val title: String get() = if (minutes > 90) "Окно" else "Обеденный перерыв"
}

/** Перерыв после пары [index], если до следующей не меньше 30 минут; обычные перемены не показываем. */
internal fun breakAfter(lessons: List<Lesson>, index: Int): LessonBreak? {
    val current = lessons.getOrNull(index) ?: return null
    val next = lessons.getOrNull(index + 1) ?: return null
    if (next.startTime == current.startTime) return null // подгруппы в одном слоте
    val gap = LessonBreak(current.endTime, next.startTime)
    return gap.takeIf { it.minutes >= 30 }
}

/** "1 ч", "40 мин", "1 ч 20 мин". */
internal fun breakLength(minutes: Long): String = when {
    minutes < 60 -> "$minutes мин"
    minutes % 60 == 0L -> "${minutes / 60} ч"
    else -> "${minutes / 60} ч ${minutes % 60} мин"
}

/** Строка таймлайна между парами: рельса без точки и карточка перерыва. Идёт сейчас — выделена. */
@Composable
private fun BreakRow(gap: LessonBreak, now: LocalTime?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val active = now != null && !now.isBefore(gap.from) && now.isBefore(gap.to)
    Row(modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(52.dp))
        Box(
            Modifier
                .width(24.dp)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(
                        scheme.outlineVariant,
                        Offset(size.width / 2, 0f),
                        Offset(size.width / 2, size.height),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    )
                },
        )
        Surface(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
            color = if (active) scheme.primaryContainer else scheme.background,
            border = BorderStroke(1.dp, if (active) scheme.primary else scheme.outlineVariant),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                // Название не переносим по слогам: на крупном шрифте лучше многоточие.
                Text(
                    gap.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${gap.from}–${gap.to} · ${breakLength(gap.minutes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun Header(state: HomeUiState, collapse: HeaderCollapse, onOpenDay: (LocalDate) -> Unit) {
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
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
        ) {
            HeaderContent(state, onOpenDay)
        }
    }
}

/**
 * Шапка: что сейчас с парами одной строкой, справа неделя; ниже — Пн–Сб с точками по числу пар.
 * Тап по дню открывает его в "Расписании".
 */
@Composable
private fun HeaderContent(state: HomeUiState, onOpenDay: (LocalDate) -> Unit) {
    val colors = GasuTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                headerTitle(state),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
                color = colors.onHeader,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            state.week?.let {
                Spacer(Modifier.width(12.dp))
                Text(shortWeek(it), style = MonoStyles.label, color = colors.onHeaderMuted, maxLines = 1)
            }
        }
        if (state.weekDays.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.weekDays.forEach { day ->
                    WeekDayChip(
                        day = day,
                        today = day.date == state.today,
                        next = day.date == state.shownDate && day.date != state.today,
                        past = day.date.isBefore(state.today),
                        onClick = { onOpenDay(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDayChip(day: HomeWeekDay, today: Boolean, next: Boolean, past: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = GasuTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val fg = when {
        today -> colors.header
        past -> colors.onHeader.copy(alpha = 0.6f)
        else -> colors.onHeader
    }
    Column(
        modifier
            .clip(shape)
            .background(if (today) colors.onHeader else colors.onHeader.copy(alpha = 0.1f))
            .then(if (next) Modifier.border(1.5.dp, colors.onHeader.copy(alpha = 0.7f), shape) else Modifier)
            .clickable(onClickLabel = "Открыть день", onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(shortDayName(day.date), style = MonoStyles.caption, color = fg)
        Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = fg)
        Row(Modifier.padding(top = 3.dp).height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(day.lessons.coerceAtMost(5)) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (today) MaterialTheme.colorScheme.primary else fg.copy(alpha = 0.55f)),
                )
            }
        }
    }
}

/** "ЗН · НЕД. 6". */
private fun shortWeek(week: ScheduleWeek): String = when (week.parity) {
    WeekParity.NUMERATOR -> "ЧС · "
    WeekParity.DENOMINATOR -> "ЗН · "
    WeekParity.EVERY -> ""
} + "НЕД. ${week.number}"

/**
 * "Сегодня 4 пары · идёт 2-я", "Сегодня 4 пары, первая в 09:00", "На сегодня всё · дальше сб в 12:30".
 */
internal fun headerTitle(state: HomeUiState): String {
    val date = state.shownDate ?: return "В ближайшие две недели пар нет"
    val lessons = state.lessons
    val first = lessons.firstOrNull() ?: return "Пар нет"
    if (date == state.today) {
        val count = lessonsCount(lessons.size)
        val time = state.now.toLocalTime()
        val current = lessons.firstOrNull { !time.isBefore(it.startTime) && time.isBefore(it.endTime) }
        val upcoming = lessons.firstOrNull { time.isBefore(it.startTime) }
        return when {
            current != null -> "Сегодня $count · идёт ${current.lessonNumber}-я"
            upcoming == first -> "Сегодня $count, первая в ${first.startTime}"
            upcoming != null -> "Сегодня $count · дальше ${upcoming.lessonNumber}-я в ${upcoming.startTime}"
            else -> "Сегодня $count"
        }
    }
    val day = if (date == state.today.plusDays(1)) "завтра" else shortDayName(date).lowercase()
    val prefix = if (state.todayFinished) "На сегодня всё" else "Сегодня пар нет"
    return "$prefix · дальше $day в ${first.startTime}"
}

/** "+11°", "−3°", "0°" — со знаком, как в прогнозах. */
internal fun signedTemp(t: Int): String = when {
    t > 0 -> "+$t°"
    t < 0 -> "−${-t}°"
    else -> "0°"
}

/** Что показать в полосе "погода + дорога". */
internal data class RoadLine(val main: String, val sub: String?, val go: Boolean = false)

/**
 * Дорога одной строкой: "Выйти в 11:40" + "сб к 12:30 · 35 мин"; когда пора — "Пора выходить".
 * Без дорожки — погода словами.
 */
internal fun roadLine(state: HomeUiState): RoadLine? {
    val commute = state.commute
    if (commute == null) {
        val w = state.weather ?: return null
        val rain = w.precipitationChance?.takeIf { it >= 10 }?.let { "осадки до $it%" }
        return RoadLine(w.condition, rain)
    }
    val l = commute.lesson
    val day = when (l.date) {
        state.today -> ""
        state.today.plusDays(1) -> "завтра "
        else -> shortDayName(l.date).lowercase() + " "
    }
    return when (val leave = commute.leave) {
        LeaveEstimate.NoHome -> RoadLine("Укажите дом", "посчитаем время выхода")
        is LeaveEstimate.UnknownBuilding -> RoadLine("Корпус не найден", "${day}к ${l.startTime} · ${l.room}")
        is LeaveEstimate.Estimated -> {
            val r = leave.route
            val sub = "${day}к ${l.startTime} · ${r.travelMinutes} мин"
            val leaveAt = l.date.atTime(r.recommendedLeaveTime)
            if (!state.now.isBefore(leaveAt)) RoadLine("Пора выходить", sub, go = true)
            else RoadLine("Выйти в ${r.recommendedLeaveTime}", sub)
        }
    }
}

/**
 * Погода и дорога к первой паре одной полосой 56 dp: слева значок и температура (и "зонт"),
 * за чертой время выхода, справа кнопка маршрута (или "+" — указать дом).
 */
@Composable
private fun WeatherRoadStrip(state: HomeUiState, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val weather = state.weather
    val line = roadLine(state) ?: return
    val commute = state.commute
    val route = (commute?.leave as? LeaveEstimate.Estimated)?.route
    Surface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium,
        color = scheme.surface,
        border = BorderStroke(1.dp, if (line.go) scheme.primary else scheme.outlineVariant),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
            weather?.let { w ->
                Row(Modifier.padding(start = 14.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(w.icon, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(signedTemp(w.tempC), style = MonoStyles.time.copy(fontSize = 16.sp), color = scheme.onSurface)
                    if (w.umbrella) {
                        Spacer(Modifier.width(6.dp))
                        Text("зонт", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = scheme.primary)
                    }
                }
                if (commute != null) {
                    Box(Modifier.width(1.dp).height(32.dp).background(scheme.outlineVariant))
                }
            }
            // Две строки внутри 56 dp: в одну строку пояснение обрезалось до "сб к…".
            Column(Modifier.weight(1f).padding(start = if (weather == null || commute != null) 12.dp else 0.dp, end = 8.dp)) {
                Text(
                    line.main,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        line.go -> scheme.primary
                        commute?.leave is LeaveEstimate.Estimated -> scheme.onSurface
                        commute == null -> scheme.onSurface
                        else -> scheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                line.sub?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                route != null -> StripButton(
                    accent = line.go,
                    label = "Маршрут в Яндекс.Картах",
                    onClick = { YandexMaps.openRoute(context, state.home?.point, route.building.location, route.mode) },
                ) { tint -> Icon(painterResource(R.drawable.ic_route), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
                commute?.leave == LeaveEstimate.NoHome -> StripButton(
                    accent = false,
                    label = "Указать адрес дома",
                    onClick = onOpenSettings,
                ) { tint -> Icon(Icons.Default.Add, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

@Composable
private fun StripButton(accent: Boolean, label: String, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (accent) scheme.primary else scheme.primaryContainer,
        modifier = Modifier.size(40.dp).semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon(if (accent) scheme.onPrimary else scheme.onPrimaryContainer)
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
