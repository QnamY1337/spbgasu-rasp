package com.example.gasuschedule

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.gasuschedule.presentation.widget.WidgetPreviews
import com.example.gasuschedule.work.BackgroundSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.gasuschedule.work.ChangeNotifications
import com.example.gasuschedule.work.ReminderNotifications
import com.example.gasuschedule.work.ReminderWork
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GasuScheduleApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminderWork: ReminderWork
    @Inject lateinit var backgroundSync: BackgroundSync

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
        ChangeNotifications.createChannel(this)
        backgroundSync.ensureScheduled()
        reminderWork.ensureDailyPlanning()
        // Будильники могли пропасть (force stop, первая установка) — пересчитываем при каждом запуске.
        reminderWork.requestReplan()
        appScope.launch { WidgetPreviews.publishOnce(this@GasuScheduleApp, BuildConfig.VERSION_CODE) }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
