package com.example.gasuschedule.data.remote

import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.NetworkProblem
import com.example.gasuschedule.domain.model.ScheduleNetworkException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Запасной источник погоды — MET Norway (Норвежский метеоинститут), бесплатно и без ключа.
 * Нужен потому, что api.open-meteo.com у части российских провайдеров недоступен.
 * Условия сервиса: осмысленный User-Agent с контактом, координаты не точнее 4 знаков.
 * Вероятности осадков в кратком прогнозе нет — [OpenMeteoForecast.precipitationChance] = null.
 */
class MetNorwayClient(
    private val client: OkHttpClient,
    private val clock: Clock,
    baseUrl: String,
) {
    @Inject constructor(client: OkHttpClient, clock: Clock) : this(client, clock, DEFAULT_BASE_URL)

    private val base: HttpUrl = baseUrl.toHttpUrl()

    suspend fun forecast(point: GeoPoint): OpenMeteoForecast = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegments("weatherapi/locationforecast/2.0/compact")
            .addQueryParameter("lat", "%.4f".format(Locale.US, point.latitude))
            .addQueryParameter("lon", "%.4f".format(Locale.US, point.longitude))
            .build()
        val body = try {
            client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw ScheduleNetworkException("HTTP ${resp.code} от MET Norway", problem = NetworkProblem.ofHttp(resp.code))
                resp.body.string()
            }
        } catch (e: IOException) {
            throw ScheduleNetworkException("Нет связи с MET Norway", e, NetworkProblem.of(e))
        }
        parse(body, clock)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.met.no/"
        private const val USER_AGENT = "GasuSchedule/1.0 github.com/QnamY1337/spbgasu-rasp"

        /**
         * Сейчас — ближайшая к текущему часу точка ряда; мин/макс — по оставшимся часам сегодня
         * (по времени [clock]); состояние — symbol_code на ближайший час, переведённый в код WMO.
         */
        fun parse(body: String, clock: Clock): OpenMeteoForecast {
            val root = Json.parseToJsonElement(body).jsonObject
            val series = ((root["properties"] as? JsonObject)?.get("timeseries") as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
            val points = series.mapNotNull { entry ->
                val time = entry["time"]?.jsonPrimitive?.contentOrNull?.let(Instant::parse) ?: return@mapNotNull null
                val data = entry["data"] as? JsonObject ?: return@mapNotNull null
                val temp = ((data["instant"] as? JsonObject)?.get("details") as? JsonObject)
                    ?.get("air_temperature")?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val symbol = listOf("next_1_hours", "next_6_hours", "next_12_hours").firstNotNullOfOrNull { key ->
                    ((data[key] as? JsonObject)?.get("summary") as? JsonObject)?.get("symbol_code")?.jsonPrimitive?.contentOrNull
                }
                Triple(time, temp, symbol)
            }
            val now = clock.instant()
            // Ряд начинается с текущего часа; берём последнюю точку не позже "сейчас", иначе первую.
            val current = points.lastOrNull { !it.first.isAfter(now) } ?: points.firstOrNull()
                ?: throw ScheduleNetworkException("Пустой прогноз MET Norway")
            val today = LocalDate.now(clock)
            val todayTemps = points.filter { it.first.atZone(clock.zone).toLocalDate() == today }.map { it.second } +
                current.second
            val symbol = current.third ?: points.firstNotNullOfOrNull { it.third }
            return OpenMeteoForecast(
                date = today,
                tempC = current.second.roundToInt(),
                weatherCode = symbol?.let(::wmoCode) ?: -1,
                isDay = symbol?.endsWith("_night") != true,
                tempMin = todayTemps.min().roundToInt(),
                tempMax = todayTemps.max().roundToInt(),
                precipitationChance = null,
            )
        }

        /** symbol_code MET Norway ("lightrainshowers_day") → код WMO для [WeatherCodes]. */
        fun wmoCode(symbol: String): Int {
            val s = symbol.substringBefore('_')
            return when {
                "thunder" in s -> 95
                s == "clearsky" -> 0
                s == "fair" -> 1
                s == "partlycloudy" -> 2
                s == "cloudy" -> 3
                s == "fog" -> 45
                "sleet" in s -> 66
                s.startsWith("heavysnow") -> 75
                s.startsWith("lightsnow") -> 71
                "snow" in s -> 73
                s.startsWith("heavyrain") -> if ("showers" in s) 82 else 65
                s.startsWith("lightrain") -> 61
                s == "rainshowers" -> 80
                s == "rain" -> 63
                else -> -1
            }
        }
    }
}
