package com.example.gasuschedule.presentation.common

import com.example.gasuschedule.domain.model.NetworkProblem
import com.example.gasuschedule.domain.usecase.SyncResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SyncMessagesTest {

    @Test
    fun `вид сетевой ошибки по исключению и коду`() {
        assertEquals(NetworkProblem.OFFLINE, NetworkProblem.of(UnknownHostException("rasp.spbgasu.ru")))
        assertEquals(NetworkProblem.OFFLINE, NetworkProblem.of(ConnectException()))
        assertEquals(NetworkProblem.TIMEOUT, NetworkProblem.of(SocketTimeoutException()))
        assertEquals(NetworkProblem.TIMEOUT, NetworkProblem.of(InterruptedIOException("timeout")))
        assertEquals(NetworkProblem.SERVER, NetworkProblem.ofHttp(502))
        assertEquals(NetworkProblem.REJECTED, NetworkProblem.ofHttp(403))
    }

    @Test
    fun `тексты ошибок синхронизации`() {
        fun net(p: NetworkProblem?) = SyncResult.Failure(SyncResult.Reason.NETWORK, "", p).errorMessage()
        assertEquals("Нет подключения к интернету.", net(NetworkProblem.OFFLINE))
        assertEquals("Сайт расписания не отвечает. Попробуй позже.", net(NetworkProblem.TIMEOUT))
        assertEquals("Сайт расписания временно недоступен. Попробуй позже.", net(NetworkProblem.SERVER))
        assertEquals("Сайт расписания отклонил запрос. Попробуй позже.", net(null))
        assertNull(SyncResult.NoGroup.errorMessage())
        assertEquals(
            "Сервис поиска адресов не отвечает. Попробуй позже.",
            networkMessage(NetworkProblem.TIMEOUT, "сервис поиска адресов"),
        )
    }
}
