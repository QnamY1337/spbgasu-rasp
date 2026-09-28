package com.example.gasuschedule.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.gasuschedule.R
import com.example.gasuschedule.domain.model.ScheduleChange
import com.example.gasuschedule.presentation.MainActivity
import com.example.gasuschedule.presentation.changes.changeNotificationContent

/**
 * Уведомление о заменах. Одно на все непросмотренные замены: новое заменяет прежнее,
 * а не копится стопкой; после просмотра ленты замен — снимается.
 */
object ChangeNotifications {
    const val CHANNEL_ID = "schedule_changes"
    private const val NOTIFICATION_ID = 2001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Изменения в расписании",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Замены аудиторий, преподавателей, отмены пар" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, unseen: List<ScheduleChange>) {
        if (unseen.isEmpty() || !ReminderNotifications.canNotify(context)) return
        val content = changeNotificationContent(unseen)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_CHANGES)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val inbox = NotificationCompat.InboxStyle()
        content.lines.forEach(inbox::addLine)
        if (content.overflow > 0) inbox.setSummaryText("и ещё ${content.overflow}")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.lines.first())
            .setStyle(inbox)
            .setNumber(unseen.size)
            .setColor(ContextCompat.getColor(context, R.color.brand_accent))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Разрешение отозвали — пропускаем, замены всё равно видны в приложении.
        }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}
