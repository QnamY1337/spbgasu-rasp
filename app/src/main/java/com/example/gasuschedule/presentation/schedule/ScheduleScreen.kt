package com.example.gasuschedule.presentation.schedule

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.presentation.common.StatusBarIcons
import com.example.gasuschedule.presentation.settings.ReminderPermissions
import com.example.gasuschedule.presentation.theme.GasuTheme
import com.example.gasuschedule.presentation.theme.MonoStyles
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

private const val TAB_TODAY = 0
private const val TAB_WEEK = 1

@Composable
fun ScheduleRoute(onChangeGroup: () -> Unit, viewModel: ScheduleViewModel = hiltViewModel()) {
    StatusBarIcons(onBrickHeader = true)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    AskNotificationsOnce()

    ScheduleScreen(
        state = state,
        snackbar = snackbar,
        onRefresh = { viewModel.refresh() },
        onSelectDate = viewModel::selectDate,
        onShowWeek = viewModel::showWeek,
        onShowWeekOf = viewModel::showWeekOf,
        onChangeGroup = onChangeGroup,
    )
}

/**
 * Напоминания — главная фича, поэтому разрешение на уведомления (Android 13+) спрашиваем
 * один раз при первом открытии расписания. Дальше — только из настроек.
 */
@Composable
private fun AskNotificationsOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        val permissions = ReminderPermissions.read(context)
        if (!permissions.notificationsAllowed && !permissions.notificationsPermissionAsked) {
            ReminderPermissions.markNotificationsAsked(context)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onShowWeek: (Long) -> Unit,
    onShowWeekOf: (LocalDate) -> Unit,
    onChangeGroup: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_TODAY) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val pillWeek = if (tab == TAB_TODAY) state.weekOf(state.selectedDate) else state.week?.week
            Header(
                group = state.group.orEmpty(),
                pill = pillWeek?.let(::weekPill),
                onGroupClick = onChangeGroup,
            )
            Tabs(
                selected = tab,
                onSelect = {
                    if (it == TAB_WEEK) onShowWeekOf(state.selectedDate)
                    tab = it
                },
            )
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f),
            ) {
                when {
                    !state.loaded -> Unit
                    state.weeks.isEmpty() && state.lessonsByDate.isEmpty() -> EmptySchedule(state.isRefreshing, onRefresh)
                    tab == TAB_TODAY -> TodayTab(state, onSelectDate)
                    else -> WeekTab(
                        state = state,
                        onShowWeek = onShowWeek,
                        onOpenDay = { onSelectDate(it); tab = TAB_TODAY },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(group: String, pill: String?, onGroupClick: () -> Unit) {
    val colors = GasuTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.header)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "СПБГАСУ",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onHeaderMuted,
                modifier = Modifier.weight(1f),
            )
            if (pill != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.onHeader.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, colors.onHeader.copy(alpha = 0.3f)),
                ) {
                    Text(
                        pill,
                        style = MonoStyles.label,
                        color = colors.onHeader,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Text(
            group,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onHeader,
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable(onClickLabel = "Сменить группу", onClick = onGroupClick),
        )
    }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf("Сегодня", "Неделя").forEachIndexed { i, title ->
                val active = i == selected
                Column(
                    Modifier
                        .clickable { onSelect(i) }
                        .padding(top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) scheme.onBackground else scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .width(if (active) 60.dp else 0.dp)
                            .height(3.dp)
                            .background(scheme.primary),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
    }
}

@Composable
private fun TodayTab(state: ScheduleUiState, onSelectDate: (LocalDate) -> Unit) {
    val days = state.days
    if (days.isEmpty()) return
    val scope = rememberCoroutineScope()
    // Диапазон дней меняется после первой синхронизации — тогда пересоздаём пейджер на выбранной дате.
    val pagerState = key(days.first(), days.last()) {
        rememberPagerState(initialPage = days.indexOf(state.selectedDate).coerceAtLeast(0)) { days.size }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onSelectDate(days[it]) }
    }
    LaunchedEffect(state.selectedDate, pagerState) {
        val target = days.indexOf(state.selectedDate)
        if (target >= 0 && target != pagerState.currentPage) pagerState.scrollToPage(target)
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
        val date = days[page]
        DayPage(
            date = date,
            lessons = state.lessonsByDate[date].orEmpty(),
            state = state,
            onBackToToday = if (date != state.today) {
                { scope.launch { pagerState.animateScrollToPage(days.indexOf(state.today)) } }
            } else null,
        )
    }
}

@Composable
private fun DayPage(date: LocalDate, lessons: List<Lesson>, state: ScheduleUiState, onBackToToday: (() -> Unit)?) {
    val timings = remember(lessons, state.now) { lessonTimings(lessons, state.now) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayTitle(date), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (lessons.isNotEmpty()) Text(lessonsCount(lessons.size), style = MonoStyles.label, color = muted)
            }
            if (onBackToToday != null) {
                TextButton(onClick = onBackToToday, contentPadding = PaddingValues(0.dp)) {
                    Text("← К сегодня")
                }
            }
        }
        if (lessons.isEmpty()) {
            item { NoLessons() }
        } else {
            items(lessons, key = { it.id }) { lesson ->
                LessonCard(lesson, timings[lesson.id] ?: LessonTiming.UPCOMING, state.now)
            }
            item { CenteredDivider("Пар больше нет", Modifier.padding(top = 12.dp)) }
        }
    }
}

@Composable
private fun NoLessons() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Пар нет", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("Можно отдохнуть", style = MaterialTheme.typography.bodyMedium, color = GasuTheme.colors.textFaint)
    }
}

@Composable
private fun WeekTab(state: ScheduleUiState, onShowWeek: (Long) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val week = state.week ?: return
    val scheme = MaterialTheme.colorScheme
    val monday = week.days.keys.first()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShowWeek(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущая неделя")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        week.week?.let { "Неделя ${it.number} · ${parityName(it.parity)}" } ?: "Неделя не опубликована",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${shortDate(monday)} – ${shortDate(monday.plusDays(6))}",
                        style = MonoStyles.label,
                        color = scheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onShowWeek(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующая неделя")
                }
            }
        }
        for ((date, lessons) in week.days) {
            if (lessons.isEmpty() && date.dayOfWeek == DayOfWeek.SUNDAY) continue
            item(key = date.toString()) {
                WeekDay(date, lessons, isToday = date == state.today, state = state, onOpen = { onOpenDay(date) })
            }
        }
    }
}

@Composable
private fun WeekDay(date: LocalDate, lessons: List<Lesson>, isToday: Boolean, state: ScheduleUiState, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val timings = remember(lessons, state.now) { lessonTimings(lessons, state.now) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Открыть день", onClick = onOpen)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${shortDayName(date)} · ${shortDate(date)}",
                style = MonoStyles.label.copy(fontWeight = FontWeight.SemiBold),
                color = if (isToday) scheme.primary else scheme.onBackground,
            )
            if (isToday) {
                Text("  сегодня", style = MaterialTheme.typography.bodySmall, color = scheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (lessons.isEmpty()) "пар нет" else lessonsCount(lessons.size),
                style = MonoStyles.label,
                color = scheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lessons.forEach { LessonCard(it, timings[it.id] ?: LessonTiming.UPCOMING, state.now) }
        }
    }
}

@Composable
private fun EmptySchedule(refreshing: Boolean, onRefresh: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Расписание ещё не загружено", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(12.dp))
        Button(onClick = onRefresh, enabled = !refreshing, shape = MaterialTheme.shapes.medium) {
            Text(if (refreshing) "Загружаем…" else "Загрузить")
        }
    }
}
