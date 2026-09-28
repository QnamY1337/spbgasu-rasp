package com.example.gasuschedule.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Периодическая фоновая синхронизация расписания (см. [BackgroundSyncPolicy]). */
@Singleton
class BackgroundSync @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** KEEP — повторный вызов при каждом запуске приложения не сбивает расписание запусков. */
    fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            BackgroundSyncPolicy.PERIOD.toMinutes(), TimeUnit.MINUTES,
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Отладка: фоновая сверка прямо сейчас, в обход ограничения по времени. */
    fun runNowForDebug() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_FORCE to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DEBUG_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val NAME = "schedule-sync"
        private const val DEBUG_NAME = "schedule-sync-debug"
    }
}
