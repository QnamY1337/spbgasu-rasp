package com.example.gasuschedule.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.runtime.setValue
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
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.presentation.home.HomeRoute
import com.example.gasuschedule.presentation.onboarding.OnboardingRoute
import com.example.gasuschedule.presentation.schedule.ScheduleRoute
import com.example.gasuschedule.presentation.settings.SettingsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
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
data object SettingsDestination

/** Вкладки нижней панели. "Замены" — вкладка внутри "Расписания", не в нижней панели. */
private enum class Tab(val route: Any, val routeClass: KClass<*>, val title: String) {
    HOME(HomeDestination, HomeDestination::class, "Главная"),
    SCHEDULE(ScheduleDestination, ScheduleDestination::class, "Расписание"),
    SETTINGS(SettingsDestination, SettingsDestination::class, "Настройки"),
}

@Composable
private fun Tab.icon(): Painter = when (this) {
    Tab.HOME -> rememberVectorPainter(Icons.Default.Home)
    Tab.SCHEDULE -> rememberVectorPainter(Icons.Default.DateRange)
    Tab.SETTINGS -> rememberVectorPainter(Icons.Default.Settings)
}

/** Число непросмотренных замен — для бейджа на вкладке. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NavBadgesViewModel @Inject constructor(
    preferences: UserPreferencesRepository,
    repository: ScheduleRepository,
) : ViewModel() {
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
        bottomBar = { if (currentTab != null) BottomBar(currentTab, unseen, nav) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (hasGroup) HomeDestination else OnboardingDestination(),
            modifier = Modifier.padding(padding),
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
                HomeRoute(onOpenSettings = { nav.navigateToTab(Tab.SETTINGS) })
            }
            composable<ScheduleDestination> {
                ScheduleRoute(
                    onChangeGroup = { nav.navigate(OnboardingDestination(changing = true)) },
                    unseenChanges = unseen,
                    openChangesRequest = openChangesRequest,
                )
            }
            composable<SettingsDestination> {
                SettingsRoute(onChangeGroup = { nav.navigate(OnboardingDestination(changing = true)) })
            }
        }
    }
}

private fun NavHostController.navigateToTab(tab: Tab) = navigate(tab.route) {
    // "Главная" — корень стека после онбординга (стартовым мог быть онбординг).
    popUpTo<HomeDestination> { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(current: Tab, unseenChanges: Int, nav: NavHostController) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = scheme.surface) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { nav.navigateToTab(tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab == Tab.SCHEDULE && unseenChanges > 0) {
                                Badge(containerColor = scheme.primary) { Text(unseenChanges.toString()) }
                            }
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
