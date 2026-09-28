package com.example.gasuschedule.work

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Когда фоновой синхронизации стоит идти в сеть. WorkManager не умеет менять период в течение
 * суток, поэтому воркер запускается каждые [PERIOD], а решение принимает сам:
 * днём — если с прошлой синхронизации прошло хотя бы [DAY_MIN_GAP] (итого раз в 2–4 часа),
 * ночью (23:00–07:00) — не чаще раза в [NIGHT_MIN_GAP].
 */
object BackgroundSyncPolicy {
    val PERIOD: Duration = Duration.ofHours(3)
    val DAY_MIN_GAP: Duration = Duration.ofHours(2)
    val NIGHT_MIN_GAP: Duration = Duration.ofHours(8)
    private val DAY_HOURS = 7..22

    /** [now] — в поясе расписания (Москва). */
    fun shouldSync(now: ZonedDateTime, lastSync: Instant?): Boolean {
        if (lastSync == null) return true
        val since = Duration.between(lastSync, now.toInstant())
        val gap = if (now.hour in DAY_HOURS) DAY_MIN_GAP else NIGHT_MIN_GAP
        return since >= gap
    }
}
