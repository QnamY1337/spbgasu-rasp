package com.example.gasuschedule.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.gasuschedule.data.remote.MetNorwayClient
import com.example.gasuschedule.data.remote.OpenMeteoClient
import com.example.gasuschedule.domain.model.Buildings
import com.example.gasuschedule.domain.model.DayWeather
import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.ScheduleNetworkException
import com.example.gasuschedule.domain.model.TravelTime
import com.example.gasuschedule.domain.model.WeatherCodes
import com.example.gasuschedule.domain.model.WeatherPlace
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.repository.WeatherRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Прогноз хранится в DataStore одной JSON-строкой; живёт 3 часа и только в пределах дня. */
@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val client: OpenMeteoClient,
    private val fallback: MetNorwayClient,
    private val dataStore: DataStore<Preferences>,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) : WeatherRepository {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override val weather: Flow<DayWeather?> = dataStore.data
        .map { p -> p[KEY]?.let(::decode) }
        .map { cached -> cached?.takeIf { it.date == LocalDate.now(clock).toString() }?.toDomain() }
        .distinctUntilChanged()

    override suspend fun refreshIfStale(): Boolean = mutex.withLock {
        val home = preferences.home.first()
        val point = home?.point ?: UNIVERSITY
        val cached = dataStore.data.first()[KEY]?.let(::decode)
        if (cached != null && isFresh(cached, point)) return true
        try {
            // api.open-meteo.com у части провайдеров в России недоступен — тогда MET Norway.
            val f = try {
                client.forecast(point)
            } catch (e: ScheduleNetworkException) {
                fallback.forecast(point)
            }
            val entry = CachedWeather(
                date = f.date.toString(),
                tempC = f.tempC,
                tempMin = f.tempMin,
                tempMax = f.tempMax,
                code = f.weatherCode,
                isDay = f.isDay,
                precipitationChance = f.precipitationChance,
                fetchedAtMillis = clock.millis(),
                latitude = point.latitude,
                longitude = point.longitude,
                place = if (home != null) WeatherPlace.HOME.name else WeatherPlace.UNIVERSITY.name,
            )
            dataStore.edit { it[KEY] = json.encodeToString(CachedWeather.serializer(), entry) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Погода второстепенна: любая ошибка — просто оставляем, что было.
            false
        }
    }

    private fun isFresh(c: CachedWeather, point: GeoPoint): Boolean {
        val age = Duration.between(Instant.ofEpochMilli(c.fetchedAtMillis), clock.instant())
        val samePlace = TravelTime.distanceKm(GeoPoint(c.latitude, c.longitude), point) < SAME_PLACE_KM
        return c.date == LocalDate.now(clock).toString() && age < TTL && samePlace
    }

    private fun decode(s: String): CachedWeather? = runCatching { json.decodeFromString(CachedWeather.serializer(), s) }.getOrNull()

    private fun CachedWeather.toDomain(): DayWeather {
        val (condition, icon) = WeatherCodes.describe(code, isDay)
        return DayWeather(
            date = LocalDate.parse(date),
            tempC = tempC,
            tempMin = tempMin,
            tempMax = tempMax,
            condition = condition,
            icon = icon,
            precipitationChance = precipitationChance,
            fetchedAt = Instant.ofEpochMilli(fetchedAtMillis),
            place = runCatching { WeatherPlace.valueOf(place) }.getOrDefault(WeatherPlace.UNIVERSITY),
        )
    }

    @Serializable
    private data class CachedWeather(
        val date: String,
        val tempC: Int,
        val tempMin: Int,
        val tempMax: Int,
        val code: Int,
        val isDay: Boolean,
        val precipitationChance: Int?,
        val fetchedAtMillis: Long,
        val latitude: Double,
        val longitude: Double,
        val place: String,
    )

    companion object {
        val TTL: Duration = Duration.ofHours(3)
        /** Дом переехал дальше, чем на километр, — прогноз для новой точки. */
        private const val SAME_PLACE_KM = 1.0
        private val KEY = stringPreferencesKey("weather_cache")
        private val UNIVERSITY: GeoPoint = Buildings.byCode("Г")!!.location
    }
}
