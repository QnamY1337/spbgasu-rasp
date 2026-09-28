package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.LessonReminder
import com.example.gasuschedule.domain.model.subjectWithType
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
) {
    suspend operator fun invoke(): List<LessonReminder> {
        val group = preferences.groupName.first()
        val reminders = if (group == null || !preferences.remindersEnabled.first()) {
            emptyList()
        } else {
            val today = LocalDate.now(clock)
            val lessons = repository.observeLessons(group, today, today.plusDays(1)).first()
            buildReminders(lessons, preferences.reminderMinutes.first(), clock)
        }
        scheduler.replaceAll(reminders)
        return reminders
    }

    companion object {
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
