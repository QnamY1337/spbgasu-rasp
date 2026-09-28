package com.example.gasuschedule.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.gasuschedule.presentation.onboarding.OnboardingRoute
import com.example.gasuschedule.presentation.schedule.ScheduleRoute
import com.example.gasuschedule.presentation.settings.SettingsRoute
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** Выбор группы. [changing] — пользователь меняет уже выбранную группу, можно вернуться назад. */
@Serializable
data class OnboardingDestination(val changing: Boolean = false)

@Serializable
data object ScheduleDestination

@Serializable
data object SettingsDestination

/** Вкладки нижней панели (как в макете; "Замены" появятся в фазе 5). */
private enum class Tab(val route: Any, val routeClass: KClass<*>, val title: String, val icon: ImageVector) {
    SCHEDULE(ScheduleDestination, ScheduleDestination::class, "Расписание", Icons.Default.DateRange),
    SETTINGS(SettingsDestination, SettingsDestination::class, "Настройки", Icons.Default.Settings),
}

@Composable
fun AppNavHost(hasGroup: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = Tab.entries.firstOrNull { tab -> entry?.destination?.hasRoute(tab.routeClass) == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = { if (currentTab != null) BottomBar(currentTab, nav) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (hasGroup) ScheduleDestination else OnboardingDestination(),
            modifier = Modifier.padding(padding),
        ) {
            composable<OnboardingDestination> { backStackEntry ->
                val changing = backStackEntry.toRoute<OnboardingDestination>().changing
                OnboardingRoute(
                    onDone = {
                        nav.navigate(ScheduleDestination) {
                            popUpTo(nav.graph.id) { inclusive = true }
                        }
                    },
                    onBack = if (changing) ({ nav.popBackStack() }) else null,
                )
            }
            composable<ScheduleDestination> {
                ScheduleRoute(onChangeGroup = { nav.navigate(OnboardingDestination(changing = true)) })
            }
            composable<SettingsDestination> {
                SettingsRoute(onChangeGroup = { nav.navigate(OnboardingDestination(changing = true)) })
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, nav: NavHostController) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = scheme.surface) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = {
                    nav.navigate(tab.route) {
                        // "Расписание" — корень стека после онбординга (стартовым мог быть онбординг).
                        popUpTo<ScheduleDestination> { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
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
