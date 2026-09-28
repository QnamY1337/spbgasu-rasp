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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** Сырые данные прогноза на сегодня. */
data class OpenMeteoForecast(
    val date: LocalDate,
    val tempC: Int,
    val weatherCode: Int,
    val isDay: Boolean,
    val tempMin: Int,
    val tempMax: Int,
    val precipitationChance: Int?,
)

/**
 * Open-Meteo — бесплатный прогноз без ключа (для некоммерческого использования).
 * OpenWeatherMap и Яндекс.Погода требуют ключ, у Яндекса API ещё и платный.
 */
class OpenMeteoClient(
    private val client: OkHttpClient,
    baseUrl: String,
) {
    @Inject constructor(client: OkHttpClient) : this(client, DEFAULT_BASE_URL)

    private val base: HttpUrl = baseUrl.toHttpUrl()

    suspend fun forecast(point: GeoPoint): OpenMeteoForecast = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegments("v1/forecast")
            .addQueryParameter("latitude", point.latitude.toString())
            .addQueryParameter("longitude", point.longitude.toString())
            .addQueryParameter("current", "temperature_2m,weather_code,is_day")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            .addQueryParameter("timezone", "Europe/Moscow")
            .addQueryParameter("forecast_days", "1")
            .build()
        val body = try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw ScheduleNetworkException("HTTP ${resp.code} от сервиса погоды", problem = NetworkProblem.ofHttp(resp.code))
                resp.body.string()
            }
        } catch (e: IOException) {
            throw ScheduleNetworkException("Нет связи с сервисом погоды", e, NetworkProblem.of(e))
        }
        parse(body)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.open-meteo.com/"

        fun parse(body: String): OpenMeteoForecast {
            val root = Json.parseToJsonElement(body).jsonObject
            val current = root["current"] as? JsonObject ?: throw ScheduleNetworkException("Нет current в ответе погоды")
            val daily = root["daily"] as? JsonObject ?: throw ScheduleNetworkException("Нет daily в ответе погоды")
            fun first(key: String) = (daily[key] as? JsonArray)?.firstOrNull()?.jsonPrimitive
            val date = first("time")?.contentOrNull?.let(LocalDate::parse)
                ?: throw ScheduleNetworkException("Нет даты в ответе погоды")
            val temp = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull
                ?: throw ScheduleNetworkException("Нет температуры в ответе погоды")
            return OpenMeteoForecast(
                date = date,
                tempC = temp.roundToInt(),
                weatherCode = current["weather_code"]?.jsonPrimitive?.intOrNull ?: first("weather_code")?.intOrNull ?: -1,
                isDay = current["is_day"]?.jsonPrimitive?.intOrNull != 0,
                tempMin = first("temperature_2m_min")?.doubleOrNull?.roundToInt() ?: temp.roundToInt(),
                tempMax = first("temperature_2m_max")?.doubleOrNull?.roundToInt() ?: temp.roundToInt(),
                precipitationChance = first("precipitation_probability_max")?.intOrNull,
            )
        }
    }
}
