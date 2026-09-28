package com.example.gasuschedule.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.gasuschedule.domain.repository.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Когда перерисовывать виджет:
 * - сразу после синхронизации ([requestUpdate]);
 * - точно на границе пары ([scheduleRefreshAt]) — чтобы "ИДЁТ СЕЙЧАС" сменилось следующей парой;
 * - раз в 30 минут силами системы (updatePeriodMillis) — подстраховка.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetUpdater {

    override fun requestUpdate() = enqueue(NOW, Duration.ZERO)

    fun scheduleRefreshAt(at: LocalDateTime, now: LocalDateTime) =
        enqueue(AT_BOUNDARY, refreshDelay(at, now))

    private fun enqueue(name: String, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        // Две разные очереди: немедленное обновление не отменяет запланированное на границу пары.
        private const val NOW = "widget-refresh-now"
        private const val AT_BOUNDARY = "widget-refresh-boundary"

        /** С запасом 30 секунд, чтобы точно оказаться после границы, и не чаще раза в минуту. */
        fun refreshDelay(at: LocalDateTime, now: LocalDateTime): Duration =
            maxOf(Duration.between(now, at).plusSeconds(30), Duration.ofMinutes(1))
    }
}

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        NextLessonWidget().updateAll(applicationContext)
        return Result.success()
    }
}
