package com.example.gasuschedule.presentation.common

import com.example.gasuschedule.domain.model.NetworkProblem
import com.example.gasuschedule.domain.usecase.SyncResult

/** Понятный пользователю текст для неуспешной синхронизации; null — показывать нечего. */
fun SyncResult.errorMessage(): String? = when (this) {
    is SyncResult.Success, SyncResult.NoGroup -> null
    SyncResult.GroupNotFound -> "На сайте нет пар для этой группы. Проверь название."
    is SyncResult.Failure -> when (reason) {
        SyncResult.Reason.NETWORK -> networkMessage(problem, "сайт расписания")
        SyncResult.Reason.PARSE -> "Сайт расписания изменился, и приложение пока не может его прочитать."
        SyncResult.Reason.SUSPICIOUS_EMPTY -> "Сайт вернул пустое расписание — показываем сохранённое."
    }
}

/** "Нет интернета", "не отвечает", "временно недоступен" — [service] в именительном падеже. */
fun networkMessage(problem: NetworkProblem?, service: String): String = when (problem) {
    NetworkProblem.OFFLINE -> "Нет подключения к интернету."
    NetworkProblem.TIMEOUT -> "${service.replaceFirstChar { it.uppercase() }} не отвечает. Попробуй позже."
    NetworkProblem.SERVER -> "${service.replaceFirstChar { it.uppercase() }} временно недоступен. Попробуй позже."
    NetworkProblem.REJECTED, null -> "${service.replaceFirstChar { it.uppercase() }} отклонил запрос. Попробуй позже."
}
