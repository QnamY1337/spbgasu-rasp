package com.example.gasuschedule.data.remote

import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.ScheduleNetworkException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NominatimAddressSearchTest {

    private lateinit var server: MockWebServer
    private lateinit var search: NominatimAddressSearch

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        search = NominatimAddressSearch(OkHttpClient(), server.url("/").toString())
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String) = MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun `поиск - короткие подписи, запрос с приоритетом Петербурга`() = runTest {
        server.enqueue(
            json(
                """[
                  {"lat":"59.9149515","lon":"30.3166988","display_name":"4, 2-я Красноармейская улица, округ Измайловское, Санкт-Петербург, Россия",
                   "address":{"house_number":"4","road":"2-я Красноармейская улица","city":"Санкт-Петербург"}},
                  {"lat":"59.93","lon":"30.36","display_name":"Невский проспект, Санкт-Петербург, Россия",
                   "address":{"road":"Невский проспект","city":"Санкт-Петербург"}}
                ]""",
            ),
        )
        val found = search.search("Красноармейская 4")
        assertEquals(
            listOf("2-я Красноармейская улица, 4, Санкт-Петербург", "Невский проспект, Санкт-Петербург"),
            found.map { it.label },
        )
        assertEquals(GeoPoint(59.9149515, 30.3166988), found.first().point)

        val request = server.takeRequest()
        assertEquals("/search", request.url.encodedPath)
        assertEquals("Красноармейская 4", request.url.queryParameter("q"))
        assertEquals("ru", request.url.queryParameter("countrycodes"))
        assertEquals("сначала только Петербург", "1", request.url.queryParameter("bounded"))
        assertTrue(request.headers["User-Agent"]!!.startsWith("GasuSchedule/"))
    }

    @Test
    fun `в Петербурге не нашлось - ищем по всей России`() = runTest {
        server.enqueue(json("[]"))
        server.enqueue(json("""[{"lat":"51.7","lon":"39.2","address":{"road":"Московский проспект","house_number":"100","city":"Воронеж"}}]"""))
        assertEquals(listOf("Московский проспект, 100, Воронеж"), search.search("Московский 100").map { it.label })
        assertEquals("1", server.takeRequest().url.queryParameter("bounded"))
        assertEquals("0", server.takeRequest().url.queryParameter("bounded"))
    }

    @Test
    fun `адрес точки геолокации`() = runTest {
        server.enqueue(
            json("""{"lat":"59.91","lon":"30.31","address":{"road":"улица Егорова","house_number":"5/8","city":"Санкт-Петербург"}}"""),
        )
        assertEquals("улица Егорова, 5/8, Санкт-Петербург", search.describe(GeoPoint(59.91, 30.31)))
        assertEquals("/reverse", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `ошибка сервиса при описании точки - null, без падения`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        assertNull(search.describe(GeoPoint(59.91, 30.31)))
    }

    @Test(expected = ScheduleNetworkException::class)
    fun `ошибка сервиса при поиске - понятная ошибка`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        search.search("Невский")
    }
}
