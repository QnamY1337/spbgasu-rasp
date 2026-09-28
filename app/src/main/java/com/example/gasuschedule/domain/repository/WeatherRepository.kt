package com.example.gasuschedule.domain.repository

import com.example.gasuschedule.domain.model.DayWeather
import kotlinx.coroutines.flow.Flow

/**
 * Погода — второстепенный блок: ошибки сети наружу не выходят, при неудаче остаётся
 * последний сохранённый прогноз (или ничего).
 */
interface WeatherRepository {
    /** Сохранённый прогноз на сегодня; null — нет или он за другой день. */
    val weather: Flow<DayWeather?>

    /** Обновить, если прогноз старше 3 часов, за другой день или для другой точки. true — успешно или свежий. */
    suspend fun refreshIfStale(): Boolean
}
