package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Пары выбранной группы на день (по умолчанию — сегодня), по номеру пары. */
class GetTodayScheduleUseCase @Inject constructor(
    private val repository: ScheduleRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(date: LocalDate = LocalDate.now(clock)): Flow<List<Lesson>> =
        preferences.groupName.flatMapLatest { group ->
            if (group == null) flowOf(emptyList())
            else repository.observeLessons(group, date, date)
        }
}
