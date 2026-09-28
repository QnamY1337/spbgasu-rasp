package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.repository.HomeworkRepository
import com.example.gasuschedule.domain.repository.ReminderReplanTrigger
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/** Изменения заданий. Любое изменение пересчитывает будильники: напоминание о дедлайне. */
class ManageHomeworkUseCase @Inject constructor(
    private val repository: HomeworkRepository,
    private val reminders: ReminderReplanTrigger,
    private val clock: Clock,
) {
    /** Новое задание, если [id] == null; иначе правка существующего (статус и дата создания сохраняются). */
    suspend fun save(
        id: String?,
        lessonId: String?,
        subject: String,
        description: String,
        dueDate: LocalDate?,
    ) {
        val existing = id?.let { repository.get(it) }
        repository.upsert(
            HomeworkItem(
                id = existing?.id ?: UUID.randomUUID().toString(),
                lessonId = existing?.lessonId ?: lessonId,
                subject = subject.trim(),
                description = description.trim(),
                dueDate = dueDate,
                isDone = existing?.isDone ?: false,
                createdAt = existing?.createdAt ?: clock.instant(),
            ),
        )
        reminders.requestReplan()
    }

    suspend fun setDone(id: String, done: Boolean) {
        repository.setDone(id, done)
        reminders.requestReplan()
    }

    suspend fun delete(id: String) {
        repository.delete(id)
        reminders.requestReplan()
    }
}
