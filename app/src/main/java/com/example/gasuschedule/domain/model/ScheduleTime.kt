package com.example.gasuschedule.domain.model

import java.time.ZoneId

object ScheduleTime {
    /**
     * Пары в СПбГАСУ идут по московскому времени, какой бы часовой пояс ни стоял на телефоне
     * (поездка, неверная настройка). Все "сегодня", "сейчас" и моменты напоминаний считаются в нём.
     */
    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
}
