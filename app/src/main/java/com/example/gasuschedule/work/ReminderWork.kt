package com.example.gasuschedule.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.gasuschedule.domain.repository.ReminderReplanTrigger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Очередь WorkManager для пересчёта напоминаний. */
@Singleton
class ReminderWork @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ReminderReplanTrigger {

    private val workManager get() = WorkManager.getInstance(context)

    override fun requestReplan() {
        workManager.enqueueUniqueWork(
            REPLAN_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderPlanningWorker>().build(),
        )
    }

    /** Ежедневный пересчёт около 00:05. KEEP — чтобы каждый запуск приложения не сдвигал расписание. */
    fun ensureDailyPlanning() {
        val request = PeriodicWorkRequestBuilder<ReminderPlanningWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNextRun(LocalDateTime.now(clock)).toMinutes(), TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val REPLAN_NOW = "reminders-replan"
        const val DAILY = "reminders-daily"
        private val DAILY_AT: LocalTime = LocalTime.of(0, 5)

        fun delayUntilNextRun(now: LocalDateTime): Duration {
            val todayRun = now.toLocalDate().atTime(DAILY_AT)
            val next = if (now.isBefore(todayRun)) todayRun else todayRun.plusDays(1)
            return Duration.between(now, next)
        }
    }
}
