package com.example.gasuschedule.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.gasuschedule.presentation.onboarding.OnboardingRoute
import com.example.gasuschedule.presentation.schedule.ScheduleRoute
import kotlinx.serialization.Serializable

/** Выбор группы. [changing] — пользователь меняет уже выбранную группу, можно вернуться назад. */
@Serializable
data class OnboardingDestination(val changing: Boolean = false)

@Serializable
data object ScheduleDestination

@Composable
fun AppNavHost(hasGroup: Boolean) {
    val nav = rememberNavController()
    NavHost(
        navController = nav,
        startDestination = if (hasGroup) ScheduleDestination else OnboardingDestination(),
    ) {
        composable<OnboardingDestination> { entry ->
            val changing = entry.toRoute<OnboardingDestination>().changing
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
    }
}
