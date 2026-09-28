package com.example.gasuschedule.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.repository.WeatherRepository
import com.example.gasuschedule.domain.usecase.SyncResult
import com.example.gasuschedule.domain.usecase.SyncScheduleUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZonedDateTime

/** Фоновая синхронизация: тянет расписание, находит замены и уведомляет о них. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncScheduleUseCase,
    private val repository: ScheduleRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
    private val weather: WeatherRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val group = preferences.groupName.first() ?: return Result.success()
        val forced = inputData.getBoolean(KEY_FORCE, false)
        if (!forced && !BackgroundSyncPolicy.shouldSync(ZonedDateTime.now(clock), preferences.lastSyncAt.first())) {
            return Result.success()
        }
        // Погоду обновляем вместе с расписанием; её ошибки ни на что не влияют.
        weather.refreshIfStale()
        return when (val result = sync(group)) {
            is SyncResult.Success -> {
                if (result.changes.isNotEmpty() && preferences.changeNotificationsEnabled.first()) {
                    val unseen = repository.observeChanges(group).first().filter { !it.seen }
                    ChangeNotifications.show(applicationContext, unseen)
                }
                Result.success()
            }
            // Нет сети или сайт лежит — WorkManager повторит с нарастающей паузой.
            is SyncResult.Failure -> when (result.reason) {
                SyncResult.Reason.NETWORK -> if (runAttemptCount < 3) Result.retry() else Result.success()
                // Разметка сменилась или подозрительный ответ — повтор через минуты не поможет.
                else -> Result.success()
            }
            SyncResult.NoGroup, SyncResult.GroupNotFound -> Result.success()
        }
    }

    companion object {
        /** Пропустить проверку [BackgroundSyncPolicy] — для отладочного запуска из настроек. */
        const val KEY_FORCE = "force"
    }
}
