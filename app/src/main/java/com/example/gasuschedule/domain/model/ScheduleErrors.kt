package com.example.gasuschedule.domain.model

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/** Что именно пошло не так с сетью — от этого зависит текст для пользователя. */
enum class NetworkProblem {
    /** Нет интернета: адрес не разрешается, соединение не устанавливается. */
    OFFLINE,

    /** Сервер не ответил вовремя. */
    TIMEOUT,

    /** Сервер ответил 5xx — лежит или перегружен. */
    SERVER,

    /** Сервер ответил, но отказал или прислал не то (4xx, ошибка Bitrix, CSRF). */
    REJECTED;

    companion object {
        fun of(e: IOException): NetworkProblem = when (e) {
            is UnknownHostException, is ConnectException, is NoRouteToHostException -> OFFLINE
            // SocketTimeoutException — подкласс InterruptedIOException, как и таймаут вызова OkHttp.
            is InterruptedIOException -> TIMEOUT
            else -> OFFLINE
        }

        fun ofHttp(code: Int): NetworkProblem = if (code >= 500) SERVER else REJECTED
    }
}

/** Сайт недоступен, HTTP-ошибка или отказ по CSRF. */
class ScheduleNetworkException(
    message: String,
    cause: Throwable? = null,
    val problem: NetworkProblem = NetworkProblem.REJECTED,
) : Exception(message, cause)

/** Разметка сайта не похожа на ожидаемую — вероятно, её поменяли. */
class ScheduleParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
