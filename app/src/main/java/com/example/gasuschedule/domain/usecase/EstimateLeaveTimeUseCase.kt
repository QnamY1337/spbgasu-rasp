package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.Buildings
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.RouteEstimate
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.model.TravelTime
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface LeaveEstimate {
    /** Адрес дома не указан в настройках. */
    data object NoHome : LeaveEstimate

    /** Корпус из номера аудитории не найден в справочнике — маршрут недоступен. */
    data class UnknownBuilding(val code: String?) : LeaveEstimate

    data class Estimated(val route: RouteEstimate) : LeaveEstimate
}

/** Во сколько выходить из дома к паре: начало − время в пути − запас. */
class EstimateLeaveTimeUseCase @Inject constructor(
    private val preferences: UserPreferencesRepository,
) {
    suspend operator fun invoke(lesson: Lesson): LeaveEstimate = estimate(
        lesson = lesson,
        home = preferences.home.first(),
        mode = preferences.travelMode.first(),
        bufferMinutes = preferences.leaveBufferMinutes.first(),
    )

    companion object {
        fun estimate(lesson: Lesson, home: HomeLocation?, mode: TravelMode, bufferMinutes: Int): LeaveEstimate {
            if (home == null) return LeaveEstimate.NoHome
            val building = Buildings.byCode(lesson.building) ?: return LeaveEstimate.UnknownBuilding(lesson.building)
            val travel = TravelTime.minutes(home.point, building.location, mode)
            return LeaveEstimate.Estimated(
                RouteEstimate(
                    building = building,
                    mode = mode,
                    travelMinutes = travel,
                    recommendedLeaveTime = lesson.startTime.minusMinutes((travel + bufferMinutes).toLong()),
                ),
            )
        }

        /** Из дома выходят только к первой паре дня — к остальным уже идут из университета. */
        fun isFirstOfDay(lesson: Lesson, dayLessons: List<Lesson>): Boolean =
            dayLessons.filter { it.date == lesson.date }.minOfOrNull { it.lessonNumber } == lesson.lessonNumber
    }
}
