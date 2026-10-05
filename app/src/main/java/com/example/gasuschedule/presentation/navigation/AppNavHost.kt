package com.example.gasuschedule.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.gasuschedule.domain.model.HomeworkPlanning
import com.example.gasuschedule.domain.repository.HomeworkRepository
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.presentation.home.HomeRoute
import com.example.gasuschedule.presentation.homework.HomeworkRoute
import com.example.gasuschedule.presentation.onboarding.OnboardingRoute
import com.example.gasuschedule.presentation.schedule.ScheduleRoute
import com.example.gasuschedule.presentation.settings.SettingsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.reflect.KClass

/** Выбор группы. [changing] — пользователь меняет уже выбранную группу, можно вернуться назад. */
@Serializable
data class OnboardingDestination(val changing: Boolean = false)

@Serializable
data object HomeDestination

@Serializable
data object ScheduleDestination

@Serializable
data object HomeworkDestination

@Serializable
data object SettingsDestination

/** Вкладки нижней панели. "Замены" — вкладка внутри "Расписания", не в нижней панели. */
private enum class Tab(val route: Any, val routeClass: KClass<*>, val title: String) {
    HOME(HomeDestination, HomeDestination::class, "Главная"),
    SCHEDULE(ScheduleDestination, ScheduleDestination::class, "Расписание"),
    HOMEWORK(HomeworkDestination, HomeworkDestination::class, "Задания"),
    SETTINGS(SettingsDestination, SettingsDestination::class, "Настройки"),
}

@Composable
private fun Tab.icon(): Painter = when (this) {
    Tab.HOME -> rememberVectorPainter(Icons.Default.Home)
    Tab.SCHEDULE -> rememberVectorPainter(Icons.Default.DateRange)
    Tab.HOMEWORK -> rememberVectorPainter(Icons.Default.Edit)
    Tab.SETTINGS -> rememberVectorPainter(Icons.Default.Settings)
}

/** Число непросмотренных замен — для бейджа на вкладке. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NavBadgesViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
    repository: ScheduleRepository,
    homework: HomeworkRepository,
    clock: Clock,
) : ViewModel() {
    /** Горящие задания (просрочено, сегодня, завтра) — бейдж на "Заданиях". */
    val urgentHomework: StateFlow<Int> = homework.observeAll()
        .map { HomeworkPlanning.urgentCount(it, LocalDate.now(clock)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val unseenChanges: StateFlow<Int> = preferences.groupName
        .flatMapLatest { group -> if (group == null) flowOf(0) else repository.observeUnseenChangesCount(group) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

/**
 * @param openChangesRequest растёт при каждом тапе по уведомлению о заменах — открываем
 *   "Расписание" на вкладке "Замены".
 */
@Composable
fun AppNavHost(hasGroup: Boolean, openChangesRequest: Int) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = Tab.entries.firstOrNull { tab -> entry?.destination?.hasRoute(tab.routeClass) == true }
    val badges: NavBadgesViewModel = hiltViewModel()
    val unseen by badges.unseenChanges.collectAsStateWithLifecycle()
    val urgentHomework by badges.urgentHomework.collectAsStateWithLifecycle()

    // Запрос обрабатываем, когда вкладки уже на экране (при холодном старте — не сразу), и ровно один раз.
    var handledRequest by rememberSaveable { mutableIntStateOf(0) }
    val tabsShown = currentTab != null
    LaunchedEffect(openChangesRequest, tabsShown) {
        if (openChangesRequest > handledRequest && tabsShown) {
            handledRequest = openChangesRequest
            nav.navigateToTab(Tab.SCHEDULE)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = { if (currentTab != null) BottomBar(currentTab, unseen, urgentHomework, nav) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (hasGroup) HomeDestination else OnboardingDestination(),
            modifier = Modifier.padding(padding).tabSwipe(currentTab) { nav.navigateToTab(it) },
        ) {
            composable<OnboardingDestination> { backStackEntry ->
                val changing = backStackEntry.toRoute<OnboardingDestination>().changing
                OnboardingRoute(
                    onDone = {
                        nav.navigate(HomeDestination) {
                            popUpTo(nav.graph.id) { inclusive = true }
                        }
                    },
                    onBack = if (changing) ({ nav.popBackStack() }) else null,
                )
            }
            composable<HomeDestination> {
                HomeRoute(
                    onOpenSettings = { nav.navigateToTab(Tab.SETTINGS) },
                    onOpenHomework = { nav.navigateToTab(Tab.HOMEWORK) },
                )
            }
            composable<ScheduleDestination> {
                ScheduleRoute(
                    onChangeGroup = { nav.navigate(OnboardingDestination(changing = true)) },
                    unseenChanges = unseen,
                    openChangesRequest = openChangesRequest,
                )
            }
            composable<HomeworkDestination> {
                HomeworkRoute()
            }
            composable<SettingsDestination> {
                SettingsRoute(onChangeGroup = { nav.navigate(OnboardingDestination(changing = true)) })
            }
        }
    }
}

/**
 * Свайп влево/вправо переключает на соседнюю вкладку. Жесты, уже забранные вложенным содержимым
 * (например, пейджер дней в "Расписании"), сюда не доходят.
 */
private fun Modifier.tabSwipe(current: Tab?, onSwitch: (Tab) -> Unit): Modifier =
    if (current == null) this else composed {
        val threshold = with(LocalDensity.current) { 80.dp.toPx() }
        val currentState by rememberUpdatedState(current)
        val onSwitchState by rememberUpdatedState(onSwitch)
        pointerInput(Unit) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    val tabs = Tab.entries
                    val target = when {
                        total <= -threshold -> tabs.getOrNull(currentState.ordinal + 1)
                        total >= threshold -> tabs.getOrNull(currentState.ordinal - 1)
                        else -> null
                    }
                    target?.let(onSwitchState)
                },
                onHorizontalDrag = { _, delta -> total += delta },
            )
        }
    }

private fun NavHostController.navigateToTab(tab: Tab) = navigate(tab.route) {
    // "Главная" — корень стека после онбординга (стартовым мог быть онбординг).
    popUpTo<HomeDestination> { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(current: Tab, unseenChanges: Int, urgentHomework: Int, nav: NavHostController) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = scheme.surface) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { nav.navigateToTab(tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            val count = when (tab) {
                                Tab.SCHEDULE -> unseenChanges
                                Tab.HOMEWORK -> urgentHomework
                                else -> 0
                            }
                            if (count > 0) Badge(containerColor = scheme.primary) { Text(count.toString()) }
                        },
                    ) { Icon(tab.icon(), contentDescription = null) }
                },
                label = { Text(tab.title, style = MaterialTheme.typography.bodySmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = scheme.primary,
                    selectedTextColor = scheme.primary,
                    indicatorColor = scheme.primaryContainer,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant,
                ),
            )
        }
    }
}
