package com.example.gasuschedule.work

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.gasuschedule.domain.model.LessonReminder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReminderAndroidTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private fun reminder(key: String, minutesFromNow: Long) = LessonReminder(
        key = key,
        lessonId = key,
        triggerAt = Instant.now().plusSeconds(minutesFromNow * 60),
        title = "Через 15 мин · $key",
        text = "09:00–10:30 · 712/С",
    )

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifications.createChannel(context)
        // В Robolectric, как на Android 14+, точные будильники по умолчанию запрещены.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `будильники ставятся, а при пересчёте лишние снимаются`() = runTest {
        val scheduler = AlarmReminderScheduler(context)
        scheduler.replaceAll(listOf(reminder("g|2026-09-28|1", 30), reminder("g|2026-09-28|2", 120)))
        assertEquals(2, shadowOf(alarms).scheduledAlarms.size)
        assertTrue("точные и работают в Doze", shadowOf(alarms).scheduledAlarms.all { it.windowLengthMs == 0L && it.isAllowWhileIdle })

        // Вторую пару отменили — её будильник должен исчезнуть, первый — остаться (с новым временем).
        val moved = reminder("g|2026-09-28|1", 45)
        scheduler.replaceAll(listOf(moved))
        val left = shadowOf(alarms).scheduledAlarms
        assertEquals(1, left.size)
        assertEquals(moved.triggerAt.toEpochMilli(), left.single().triggerAtTime)

        scheduler.replaceAll(emptyList())
        assertTrue(shadowOf(alarms).scheduledAlarms.isEmpty())
    }

    @Test
    fun `без разрешения на точные будильники - обычный будильник`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        try {
            val r = reminder("g|2026-09-28|1", 30)
            val reminderAt = r.triggerAt.toEpochMilli()
            AlarmReminderScheduler(context).replaceAll(listOf(r))
            val alarm = shadowOf(alarms).scheduledAlarms.single()
            // Не часовое окно setAndAllowWhileIdle, а ±5 минут вокруг нужного момента.
            assertEquals(10 * 60 * 1000L, alarm.windowLengthMs)
            assertEquals(reminderAt - 5 * 60 * 1000L, alarm.triggerAtMs)
        } finally {
            ShadowAlarmManager.setCanScheduleExactAlarms(true)
        }
    }

    @Test
    fun `срабатывание будильника показывает уведомление`() {
        val intent = Intent(context, LessonReminderReceiver::class.java)
            .setAction(LessonReminderReceiver.ACTION)
            .putExtra(LessonReminderReceiver.EXTRA_KEY, "g|2026-09-28|3")
            .putExtra(LessonReminderReceiver.EXTRA_TITLE, "Через 15 мин · Философия (пр.)")
            .putExtra(LessonReminderReceiver.EXTRA_TEXT, "12:30–14:00 · 712/С · Семенюк А.П.")
        LessonReminderReceiver().onReceive(context, intent)

        val shown = shadowOf(notifications).allNotifications.single()
        assertEquals(ReminderNotifications.CHANNEL_ID, shown.channelId)
        assertEquals("Через 15 мин · Философия (пр.)", shadowOf(shown).contentTitle)
        assertEquals("12:30–14:00 · 712/С · Семенюк А.П.", shadowOf(shown).contentText)
    }

    @Test
    fun `без разрешения на уведомления - тихо ничего не показываем`() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReminderNotifications.show(context as Context, "k", "t", "x")
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun `ежедневный пересчёт - в ближайшие 00-05`() {
        assertEquals(Duration.ofMinutes(5), ReminderWork.delayUntilNextRun(LocalDateTime.of(2026, 9, 28, 0, 0)))
        assertEquals(Duration.ofHours(24), ReminderWork.delayUntilNextRun(LocalDateTime.of(2026, 9, 28, 0, 5)))
        assertEquals(Duration.ofMinutes(6 * 60 + 5), ReminderWork.delayUntilNextRun(LocalDateTime.of(2026, 9, 28, 18, 0)))
    }
}
