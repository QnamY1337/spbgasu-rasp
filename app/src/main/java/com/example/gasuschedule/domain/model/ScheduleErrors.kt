package com.example.gasuschedule.domain.model

/** Сайт недоступен, HTTP-ошибка или отказ по CSRF. */
class ScheduleNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Разметка сайта не похожа на ожидаемую — вероятно, её поменяли. */
class ScheduleParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
