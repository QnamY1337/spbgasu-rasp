package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.NetworkProblem
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.domain.model.ScheduleNetworkException
import com.example.gasuschedule.domain.model.ScheduleParseException
import com.example.gasuschedule.domain.repository.ReminderReplanTrigger
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.repository.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncResult {
    data class Success(val lessonCount: Int, val changes: List<ScheduleChange>) : SyncResult

    /** Группа ещё не выбрана (онбординг не пройден). */
    data object NoGroup : SyncResult

    /** У группы нет ни одной пары — скорее всего, опечатка в названии. */
    data object GroupNotFound : SyncResult

    /** [problem] — уточнение для [Reason.NETWORK]: нет интернета, таймаут, сервер лежит. */
    data class Failure(val reason: Reason, val message: String, val problem: NetworkProblem? = null) : SyncResult

    enum class Reason {
        NETWORK,
        PARSE,

        /** Сайт вдруг отдал пустое расписание, хотя раньше пары были — снепшот не трогаем. */
        SUSPICIOUS_EMPTY,
    }
}

/** Тянет расписание с сайта, находит замены относительно сохранённого снепшота и сохраняет всё разом. */
@Singleton
class SyncScheduleUseCase @Inject constructor(
    private val repository: ScheduleRepository,
    private val preferences: UserPreferencesRepository,
    private val differ: ScheduleDiffer,
    private val clock: Clock,
    private val reminders: ReminderReplanTrigger,
    private val widgets: WidgetUpdater,
) {
    // Синхронизацию могут одновременно запустить воркер и пользователь — диффить надо по очереди.
    private val mutex = Mutex()

    suspend operator fun invoke(group: String? = null): SyncResult = mutex.withLock {
        val groupName = group ?: preferences.groupName.first() ?: return SyncResult.NoGroup

        val fresh = try {
            repository.fetchRemote(groupName)
        } catch (e: ScheduleNetworkException) {
            return SyncResult.Failure(SyncResult.Reason.NETWORK, e.message.orEmpty(), e.problem)
        } catch (e: ScheduleParseException) {
            return SyncResult.Failure(SyncResult.Reason.PARSE, e.message.orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Неожиданный ответ сайта (битый JSON, NPE в разборе) не должен ронять приложение.
            return SyncResult.Failure(SyncResult.Reason.PARSE, e.toString())
        }

        val old = repository.getSnapshot(groupName)
        if (fresh.isEmpty) {
            return if (old.isEmpty) SyncResult.GroupNotFound
            else SyncResult.Failure(SyncResult.Reason.SUSPICIOUS_EMPTY, "Сайт вернул пустое расписание")
        }

        val now = clock.instant()
        val changes = differ.diff(old, fresh, LocalDate.now(clock), now)
        repository.replaceSnapshot(fresh, changes)
        preferences.setLastSyncAt(now)
        // Пары могли поменяться — будильники напоминаний и виджет обновляются в фоне.
        reminders.requestReplan()
        widgets.requestUpdate()
        SyncResult.Success(fresh.lessons.size, changes)
    }
}
