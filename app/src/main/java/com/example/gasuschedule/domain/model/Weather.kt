package com.example.gasuschedule.domain.model

import java.time.Instant
import java.time.LocalDate

/** Погода на день для блока на главной. */
data class DayWeather(
    val date: LocalDate,
    /** Температура сейчас, °C. */
    val tempC: Int,
    val tempMin: Int,
    val tempMax: Int,
    /** "Облачно", "Небольшой дождь"… — по коду погоды на сейчас. */
    val condition: String,
    /** Значок состояния (символ). */
    val icon: String,
    /** Максимальная вероятность осадков за день, %; null — нет данных. */
    val precipitationChance: Int?,
    val fetchedAt: Instant,
    /** Где взят прогноз: у дома или у главного корпуса. */
    val place: WeatherPlace,
) {
    /** Подсказка про зонт — при вероятности осадков от 50%. */
    val umbrella: Boolean get() = (precipitationChance ?: 0) >= UMBRELLA_FROM

    companion object {
        const val UMBRELLA_FROM = 50
    }
}

enum class WeatherPlace { HOME, UNIVERSITY }

/** Коды погоды WMO (их отдаёт Open-Meteo) → описание по-русски и значок. */
object WeatherCodes {
    fun describe(code: Int, isDay: Boolean): Pair<String, String> = when (code) {
        0 -> "Ясно" to if (isDay) "☀" else "☾"
        1 -> "Преимущественно ясно" to if (isDay) "🌤" else "☾"
        2 -> "Переменная облачность" to "⛅"
        3 -> "Пасмурно" to "☁"
        45, 48 -> "Туман" to "🌫"
        51, 53, 55 -> "Морось" to "🌦"
        56, 57 -> "Ледяная морось" to "🌧"
        61 -> "Небольшой дождь" to "🌦"
        63 -> "Дождь" to "🌧"
        65 -> "Сильный дождь" to "🌧"
        66, 67 -> "Ледяной дождь" to "🌧"
        71 -> "Небольшой снег" to "🌨"
        73 -> "Снег" to "🌨"
        75 -> "Сильный снег" to "❄"
        77 -> "Снежная крупа" to "🌨"
        80, 81 -> "Ливень" to "🌧"
        82 -> "Сильный ливень" to "⛈"
        85, 86 -> "Снегопад" to "❄"
        95 -> "Гроза" to "⛈"
        96, 99 -> "Гроза с градом" to "⛈"
        else -> "—" to "☁"
    }
}
