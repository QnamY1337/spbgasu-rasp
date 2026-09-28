package com.example.gasuschedule.work

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.gasuschedule.domain.model.ScheduleNetworkException
import com.example.gasuschedule.domain.usecase.ScheduleDiffer
import com.example.gasuschedule.domain.usecase.SyncScheduleUseCase
import com.example.gasuschedule.testutil.FakePreferences
import com.example.gasuschedule.testutil.FakeReplanTrigger
import com.example.gasuschedule.testutil.FakeWeatherRepository
import com.example.gasuschedule.testutil.FakeWidgetUpdater
import com.example.gasuschedule.testutil.FakeScheduleRepository
import com.example.gasuschedule.testutil.clockAt
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import com.example.gasuschedule.testutil.schedule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SyncWorkerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private val repo = FakeScheduleRepository()
    private val prefs = FakePreferences()
    private val clock = clockAt(d(28), hour = 12)
    private val sync = SyncScheduleUseCase(repo, prefs, ScheduleDiffer(), clock, FakeReplanTrigger(), FakeWidgetUpdater())

    private val base = schedule(listOf(5), lesson(d(29), 3, "Экономическая грамотность"))
    private val moved = schedule(listOf(5), lesson(d(29), 3, "Экономическая грамотность", rooms = listOf("812/С")))

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ChangeNotifications.createChannel(context)
    }

    private fun worker(): SyncWorker = TestListenableWorkerBuilder<SyncWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters): ListenableWorker =
                SyncWorker(appContext, params, sync, repo, prefs, clock, FakeWeatherRepository())
        })
        .build()

    @Test
    fun `нашёл замену - уведомление с текстом замены`() = runTest {
        repo.remote = { base }
        sync()
        prefs.lastSyncAt.value = clock.instant().minus(Duration.ofHours(3))
        repo.remote = { moved }

        assertEquals(ListenableWorker.Result.success(), worker().doWork())

        val shown = shadowOf(notifications).allNotifications.single()
        assertEquals(ChangeNotifications.CHANNEL_ID, shown.channelId)
        assertEquals("Изменение в расписании", shadowOf(shown).contentTitle)
        assertEquals("ВТ 29.09, 3 пара · Экономическая грамотность: ауд. 349/А → 812/С", shadowOf(shown).contentText)
    }

    @Test
    fun `уведомления о заменах выключены - замена сохраняется, но без уведомления`() = runTest {
        repo.remote = { base }
        sync()
        prefs.lastSyncAt.value = clock.instant().minus(Duration.ofHours(3))
        prefs.changeNotificationsEnabled.value = false
        repo.remote = { moved }

        worker().doWork()
        assertEquals(1, repo.changes.value.size)
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun `обновляли недавно - в сеть не ходим`() = runTest {
        prefs.lastSyncAt.value = clock.instant().minus(Duration.ofMinutes(30))
        repo.remote = { error("не должно вызываться") }
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(0, repo.fetchCount)
    }

    @Test
    fun `нет сети - повтор`() = runTest {
        repo.remote = { throw ScheduleNetworkException("нет сети") }
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    }
}
