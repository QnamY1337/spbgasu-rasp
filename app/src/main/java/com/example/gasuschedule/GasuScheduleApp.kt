package com.example.gasuschedule

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.gasuschedule.work.ReminderNotifications
import com.example.gasuschedule.work.ReminderWork
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GasuScheduleApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminderWork: ReminderWork

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
        reminderWork.ensureDailyPlanning()
        // Будильники могли пропасть (force stop, первая установка) — пересчитываем при каждом запуске.
        reminderWork.requestReplan()
    }
}
