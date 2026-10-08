package com.example.gasuschedule.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.gasuschedule.R
import com.example.gasuschedule.presentation.MainActivity

object ReminderNotifications {
    const val CHANNEL_ID = "lesson_reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Напоминания о парах",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "За несколько минут до начала пары" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Можно ли сейчас показывать уведомления (разрешение Android 13+ и не выключены ли они целиком). */
    fun canNotify(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Ключи напоминаний задаёт ScheduleNotificationsUseCase: "hw|…", "leave|…", остальное — пары. */
    private fun iconFor(key: String): Int = when {
        key.startsWith("hw|") -> R.drawable.ic_notif_homework
        key.startsWith("leave|") -> R.drawable.ic_notif_leave
        else -> R.drawable.ic_notification
    }

    fun show(context: Context, key: String, title: String, text: String) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconFor(key))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(ContextCompat.getColor(context, R.color.brand_accent))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
        } catch (e: SecurityException) {
            // Разрешение отозвали прямо сейчас — молча пропускаем.
        }
    }
}
