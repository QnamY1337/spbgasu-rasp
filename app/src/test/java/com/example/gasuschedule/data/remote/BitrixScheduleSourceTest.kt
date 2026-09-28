package com.example.gasuschedule.data.remote

import com.example.gasuschedule.domain.model.ScheduleNetworkException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

class BitrixScheduleSourceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun source() = BitrixScheduleSource(baseUrl = server.url("/").toString())

    private fun indexResponse() = MockResponse.Builder()
        .body(Fixtures.indexPage)
        .addHeader("Set-Cookie", "PHPSESSID=abc; path=/; HttpOnly")
        .build()

    private fun json(body: String) =
        MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `сначала главная, затем POST с токеном, cookie и form-телом`() = runTest {
        server.enqueue(indexResponse())
        server.enqueue(json(Fixtures.raspJson))

        val schedule = source().fetchSchedule("3-ТТП-26")
        assertEquals(108, schedule.lessons.size)

        val get = server.takeRequest()
        assertEquals("GET", get.method)
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("getRasp", post.url.queryParameter("action"))
        assertEquals("gasu:raspisanie.csv", post.url.queryParameter("c"))
        assertEquals("438f4f3c2e94f4fe97fce25782b4c2de", post.headers["X-Bitrix-Csrf-Token"])
        assertTrue(post.headers["Cookie"].orEmpty().contains("PHPSESSID=abc"))
        val form = URLDecoder.decode(post.body!!.utf8(), "UTF-8")
        assertTrue(form, form.contains("search_params[SEARCH]=3-ТТП-26"))
        assertTrue(form, form.contains("search_params[FILTER]=GROUPS"))
    }

    @Test
    fun `протухший токен - повтор со свежим из invalid_csrf`() = runTest {
        server.enqueue(indexResponse())
        server.enqueue(json("""{"status":"error","errors":[{"code":"invalid_csrf","customData":{"csrf":"fresh123"}}]}"""))
        server.enqueue(json(Fixtures.raspJson))

        val schedule = source().fetchSchedule("3-ТТП-26")
        assertEquals(8, schedule.weeks.size)

        server.takeRequest()
        server.takeRequest()
        assertEquals("fresh123", server.takeRequest().headers["X-Bitrix-Csrf-Token"])
    }

    @Test
    fun `список групп кэшируется после первого запроса`() = runTest {
        server.enqueue(indexResponse())
        val src = source()
        assertEquals(614, src.fetchGroups().size)
        assertEquals(614, src.fetchGroups().size)
        assertEquals(1, server.requestCount)
    }

    @Test(expected = ScheduleNetworkException::class)
    fun `HTTP 500 - понятная сетевая ошибка`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        source().fetchSchedule("3-ТТП-26")
    }
}
