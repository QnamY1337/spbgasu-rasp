package com.example.gasuschedule.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gasuschedule.domain.usecase.ScheduleNotificationsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/** Пересчитывает будильники напоминаний: раз в сутки и по запросу (синк, настройки, перезагрузка). */
@HiltWorker
class ReminderPlanningWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduleNotifications: ScheduleNotificationsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        scheduleNotifications()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
