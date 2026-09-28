package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Что показать в виджете. */
sealed interface NextLesson {
    /** Группа не выбрана — онбординг не пройден. */
    data object NoGroup : NextLesson

    /** В ближайшие [GetNextLessonUseCase.LOOKAHEAD_DAYS] дней пар нет (каникулы, неопубликованные недели). */
    data object NoneSoon : NextLesson

    data class Found(
        val lesson: Lesson,
        /** Пара идёт прямо сейчас. */
        val ongoing: Boolean,
        /** Другие пары в том же слоте (подгруппы). */
        val parallel: List<Lesson> = emptyList(),
        /** Когда показанное устареет: конец текущей пары или начало следующей. */
        val validUntil: LocalDateTime,
        /** Первая пара своего дня — к ней выходят из дома (показываем время выхода). */
        val firstOfDay: Boolean = false,
    ) : NextLesson
}

/** Текущая или ближайшая пара — для виджета. Читает только локальную базу. */
class GetNextLessonUseCase @Inject constructor(
    private val repository: ScheduleRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): NextLesson {
        val group = preferences.groupName.first() ?: return NextLesson.NoGroup
        val today = LocalDate.now(clock)
        val lessons = repository.observeLessons(group, today, today.plusDays(LOOKAHEAD_DAYS)).first()
        return pick(lessons, LocalDateTime.now(clock))
    }

    companion object {
        const val LOOKAHEAD_DAYS = 14L

        fun pick(lessons: List<Lesson>, now: LocalDateTime): NextLesson {
            val slots = lessons
                .filter { it.date.atTime(it.endTime).isAfter(now) }
                .groupBy { it.date to it.lessonNumber }
                .toSortedMap(compareBy({ it.first }, { it.second }))
            val slot = slots.values.firstOrNull() ?: return NextLesson.NoneSoon
            val main = slot.minBy { it.id }
            val start = main.date.atTime(main.startTime)
            val end = main.date.atTime(main.endTime)
            val ongoing = !now.isBefore(start)
            return NextLesson.Found(
                lesson = main,
                ongoing = ongoing,
                parallel = slot.filter { it.id != main.id },
                validUntil = if (ongoing) end else start,
                firstOfDay = EstimateLeaveTimeUseCase.isFirstOfDay(main, lessons),
            )
        }
    }
}
