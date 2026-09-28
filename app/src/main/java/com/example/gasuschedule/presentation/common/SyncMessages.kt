package com.example.gasuschedule.presentation.common

import com.example.gasuschedule.domain.usecase.SyncResult

/** Понятный пользователю текст для неуспешной синхронизации; null — показывать нечего. */
fun SyncResult.errorMessage(): String? = when (this) {
    is SyncResult.Success, SyncResult.NoGroup -> null
    SyncResult.GroupNotFound -> "На сайте нет пар для этой группы. Проверь название."
    is SyncResult.Failure -> when (reason) {
        SyncResult.Reason.NETWORK -> "Не удалось загрузить расписание: нет связи с сайтом СПбГАСУ."
        SyncResult.Reason.PARSE -> "Сайт расписания изменился, и приложение пока не может его прочитать."
        SyncResult.Reason.SUSPICIOUS_EMPTY -> "Сайт вернул пустое расписание — показываем сохранённое."
    }
}
