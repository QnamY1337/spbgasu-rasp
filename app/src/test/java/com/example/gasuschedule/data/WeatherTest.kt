package com.example.gasuschedule.data

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.gasuschedule.data.remote.Fixtures
import com.example.gasuschedule.data.remote.MetNorwayClient
import com.example.gasuschedule.data.remote.OpenMeteoClient
import com.example.gasuschedule.data.repository.WeatherRepositoryImpl
import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.WeatherCodes
import com.example.gasuschedule.domain.model.WeatherPlace
import com.example.gasuschedule.presentation.home.signedTemp
import com.example.gasuschedule.testutil.FakePreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WeatherTest {

    private lateinit var server: MockWebServer
    /** Запасной источник — MET Norway. */
    private lateinit var met: MockWebServer
    private val moscow = ZoneId.of("Europe/Moscow")
    /** 28.09.2026 10:05 МСК — тот же день, что в фикстуре Open-Meteo (и +4 часа — всё ещё он). */
    private var now = Instant.parse("2026-09-28T07:05:00Z")
    private val clock = object : Clock() {
        override fun getZone() = moscow
        override fun withZone(zone: ZoneId?) = this
        override fun instant() = now
    }

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        met = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.close()
        met.close()
    }

    private fun ok() = MockResponse.Builder().body(Fixtures.text("open_meteo.json")).build()

    private fun repo(prefs: FakePreferences, scope: TestScope): WeatherRepositoryImpl {
        val file = File(ApplicationProvider.getApplicationContext<Application>().filesDir, "w-${System.nanoTime()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { file }
        return WeatherRepositoryImpl(
            OpenMeteoClient(OkHttpClient(), server.url("/").toString()),
            MetNorwayClient(OkHttpClient(), clock, met.url("/").toString()),
            store, prefs, clock,
        )
    }

    @Test
    fun `разбор ответа Open-Meteo`() {
        val f = OpenMeteoClient.parse(Fixtures.text("open_meteo.json"))
        assertEquals("2026-09-28", f.date.toString())
        assertEquals(11, f.tempC)
        assertEquals(0, f.weatherCode)
        assertFalse(f.isDay)
        assertEquals(10, f.tempMin) // 9.6 -> 10
        assertEquals(15, f.tempMax)
        assertEquals(0, f.precipitationChance)
    }

    @Test
    fun `разбор ответа MET Norway - сейчас, мин и макс за сегодня по Москве`() {
        val f = MetNorwayClient.parse(Fixtures.text("met_norway.json"), clock)
        assertEquals("2026-09-28", f.date.toString())
        assertEquals(8, f.tempC)
        assertEquals(61, f.weatherCode) // lightrainshowers -> небольшой дождь
        assertTrue(f.isDay)
        assertEquals("21:00Z — уже 29.09 по Москве, не считаем", 5, f.tempMin)
        assertEquals(10, f.tempMax)
        assertNull(f.precipitationChance)
        assertEquals(0, MetNorwayClient.wmoCode("clearsky_night"))
        assertEquals(95, MetNorwayClient.wmoCode("heavyrainandthunder"))
        assertEquals(66, MetNorwayClient.wmoCode("lightsleetshowers_day"))
        assertEquals(82, MetNorwayClient.wmoCode("heavyrainshowers_night"))
        assertEquals(73, MetNorwayClient.wmoCode("snow"))
    }

    @Test
    fun `Open-Meteo недоступен - прогноз берём у MET Norway`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(FakePreferences().apply { home.value = HomeLocation(GeoPoint(59.87, 30.32), "дом") }, this)
        server.enqueue(MockResponse.Builder().code(503).build())
        met.enqueue(MockResponse.Builder().body(Fixtures.text("met_norway.json")).build())
        assertTrue(repo.refreshIfStale())
        val w = repo.weather.first()!!
        assertEquals(8, w.tempC)
        assertEquals("Небольшой дождь", w.condition)
        val request = met.takeRequest()
        assertEquals("59.8700", request.url.queryParameter("lat"))
        assertTrue(request.headers["User-Agent"]!!.startsWith("GasuSchedule/"))
    }

    @Test
    fun `коды погоды и температура со знаком`() {
        assertEquals("Ясно" to "☀", WeatherCodes.describe(0, isDay = true))
        assertEquals("Ясно" to "☾", WeatherCodes.describe(0, isDay = false))
        assertEquals("Дождь", WeatherCodes.describe(63, true).first)
        assertEquals("Снег", WeatherCodes.describe(73, true).first)
        assertEquals("Гроза с градом", WeatherCodes.describe(99, true).first)
        assertEquals("+11°", signedTemp(11))
        assertEquals("−3°", signedTemp(-3))
        assertEquals("0°", signedTemp(0))
    }

    @Test
    fun `кэш на 3 часа, прогноз для дома, при ошибке сети остаётся прошлый`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = FakePreferences().apply { home.value = HomeLocation(GeoPoint(59.87, 30.32), "Московский пр.") }
        val repo = repo(prefs, this)

        server.enqueue(ok())
        assertTrue(repo.refreshIfStale())
        val w = repo.weather.first()!!
        assertEquals(11, w.tempC)
        assertEquals(WeatherPlace.HOME, w.place)
        assertEquals("59.87", server.takeRequest().url.queryParameter("latitude"))

        // Через час — свежий, в сеть не ходим.
        now = now.plusSeconds(3600)
        assertTrue(repo.refreshIfStale())
        assertEquals(1, server.requestCount)

        // Через 4 часа — устарел; сеть упала — false, но старый прогноз остаётся.
        now = now.plusSeconds(3 * 3600)
        server.enqueue(MockResponse.Builder().code(503).build())
        met.enqueue(MockResponse.Builder().code(503).build())
        assertFalse(repo.refreshIfStale())
        assertEquals(11, repo.weather.first()!!.tempC)
    }

    @Test
    fun `без дома — прогноз у главного корпуса, переезд дома — новый запрос`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = FakePreferences()
        val repo = repo(prefs, this)
        server.enqueue(ok())
        repo.refreshIfStale()
        assertEquals(WeatherPlace.UNIVERSITY, repo.weather.first()!!.place)
        assertTrue(server.takeRequest().url.queryParameter("latitude")!!.startsWith("59.91"))

        prefs.home.value = HomeLocation(GeoPoint(60.05, 30.33), "пр. Просвещения")
        server.enqueue(ok())
        repo.refreshIfStale()
        assertEquals(2, server.requestCount)
        assertEquals(WeatherPlace.HOME, repo.weather.first()!!.place)
    }

    @Test
    fun `прогноз за вчера не показываем`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repo(FakePreferences(), this)
        server.enqueue(ok())
        repo.refreshIfStale()
        now = Instant.parse("2026-09-29T06:00:00Z") // уже 29.09
        assertNull(repo.weather.first())
    }
}
