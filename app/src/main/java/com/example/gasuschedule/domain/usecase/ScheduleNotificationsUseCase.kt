package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.model.HomeworkPlanning
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.model.LessonReminder
import com.example.gasuschedule.domain.model.subjectWithType
import com.example.gasuschedule.domain.repository.HomeworkRepository
import com.example.gasuschedule.domain.repository.ReminderScheduler
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Пересчитывает напоминания о парах и ставит их будильниками.
 *
 * Планируем сегодня и завтра: ежедневный запуск в 00:05 может опоздать (Doze, выключенный телефон),
 * а запас в сутки гарантирует, что утренние пары не останутся без напоминаний.
 * Ставятся только напоминания с моментом срабатывания в будущем — повторный пересчёт
 * (после каждой синхронизации) не присылает уже показанные уведомления заново.
 */
class ScheduleNotificationsUseCase @Inject constructor(
    private val repository: ScheduleRepository,
    private val preferences: UserPreferencesRepository,
    private val scheduler: ReminderScheduler,
    private val clock: Clock,
    private val homework: HomeworkRepository,
) {
    suspend operator fun invoke(): List<LessonReminder> {
        val group = preferences.groupName.first()
        val reminders = if (group == null) {
            emptyList()
        } else {
            val today = LocalDate.now(clock)
            // На послезавтра — только для дедлайнов заданий (напоминание бывает за сутки).
            val window = repository.observeLessons(group, today, today.plusDays(2)).first()
            val lessons = window.filter { !it.date.isAfter(today.plusDays(1)) }
            val homeworkReminders = buildHomeworkReminders(
                homework.observeAll().first(),
                window,
                preferences.homeworkReminderHours.first(),
                clock,
            )
            val lessonReminders =
                if (preferences.remindersEnabled.first()) buildReminders(lessons, preferences.reminderMinutes.first(), clock)
                else emptyList()
            val leaveReminders =
                if (preferences.leaveRemindersEnabled.first()) {
                    buildLeaveReminders(
                        lessons,
                        preferences.home.first(),
                        preferences.travelMode.first(),
                        preferences.leaveBufferMinutes.first(),
                        clock,
                    )
                } else emptyList()
            (lessonReminders + leaveReminders + homeworkReminders).sortedBy { it.triggerAt }
        }
        scheduler.replaceAll(reminders)
        return reminders
    }

    companion object {
        /** Напоминание о невыполненном задании за [hoursBefore] часов до дедлайна ([HomeworkPlanning.deadline]). */
        fun buildHomeworkReminders(
            items: List<HomeworkItem>,
            lessons: List<Lesson>,
            hoursBefore: Int,
            clock: Clock,
        ): List<LessonReminder> {
            if (hoursBefore <= 0) return emptyList()
            val now = Instant.now(clock)
            return items.filter { !it.isDone }.mapNotNull { item ->
                val deadline = HomeworkPlanning.deadline(item, lessons) ?: return@mapNotNull null
                val trigger = deadline.minusHours(hoursBefore.toLong()).atZone(clock.zone).toInstant()
                if (!trigger.isAfter(now)) return@mapNotNull null
                val day = if (deadline.toLocalDate() == trigger.atZone(clock.zone).toLocalDate()) "сегодня" else "завтра"
                LessonReminder(
                    key = "hw|${item.id}",
                    lessonId = item.lessonId.orEmpty(),
                    triggerAt = trigger,
                    title = "Сдать $day к ${deadline.toLocalTime()} · ${item.subject}",
                    text = item.description,
                )
            }
        }

        /** "Пора выходить" — к первой паре каждого дня, если указан дом и известен корпус. */
        fun buildLeaveReminders(
            lessons: List<Lesson>,
            home: HomeLocation?,
            mode: TravelMode,
            bufferMinutes: Int,
            clock: Clock,
        ): List<LessonReminder> {
            if (home == null) return emptyList()
            val now = Instant.now(clock)
            return lessons.groupBy { it.date }.values.mapNotNull { day ->
                val first = day.minWith(compareBy({ it.lessonNumber }, { it.id }))
                val estimate = EstimateLeaveTimeUseCase.estimate(first, home, mode, bufferMinutes)
                    as? LeaveEstimate.Estimated ?: return@mapNotNull null
                val route = estimate.route
                val trigger = first.date.atTime(route.recommendedLeaveTime).atZone(clock.zone).toInstant()
                if (!trigger.isAfter(now)) return@mapNotNull null
                val how = if (mode == TravelMode.WALKING) "пешком" else "на транспорте"
                LessonReminder(
                    key = "leave|${first.groupName}|${first.date}",
                    lessonId = first.id,
                    triggerAt = trigger,
                    title = "Пора выходить · ${first.subject} в ${first.startTime}",
                    text = "Дорога ≈${route.travelMinutes} мин $how · ${route.building.name}, ${route.building.address}",
                )
            }
        }

        fun buildReminders(lessons: List<Lesson>, minutesBefore: Int, clock: Clock): List<LessonReminder> {
            val now = Instant.now(clock)
            return lessons
                .groupBy { it.date to it.lessonNumber }
                .values
                .mapNotNull { slot ->
                    val first = slot.minBy { it.id }
                    val start = first.date.atTime(first.startTime).atZone(clock.zone).toInstant()
                    val trigger = start.minusSeconds(minutesBefore * 60L)
                    if (!trigger.isAfter(now)) return@mapNotNull null
                    LessonReminder(
                        key = "${first.groupName}|${first.date}|${first.lessonNumber}",
                        lessonId = first.id,
                        triggerAt = trigger,
                        title = title(slot, minutesBefore),
                        text = text(slot),
                    )
                }
                .sortedBy { it.triggerAt }
        }

        /** "Через 15 мин · История России (л.)". */
        private fun title(slot: List<Lesson>, minutes: Int): String {
            val subjects = slot.map { it.subjectWithType }.distinct().joinToString(" / ")
            return "Через $minutes мин · $subjects"
        }

        /** "09:00–10:30 · Актовый зал/Г · Гурьев Е.П." (у подгрупп — все аудитории и преподаватели). */
        private fun text(slot: List<Lesson>): String {
            val first = slot.first()
            val rooms = slot.flatMap { it.rooms }.distinct().joinToString(", ")
            val teachers = slot.flatMap { it.teachers }.distinct().joinToString(", ")
            return listOf("${first.startTime}–${first.endTime}", rooms, teachers)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }
    }
}
