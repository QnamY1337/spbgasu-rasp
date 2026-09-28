package com.example.gasuschedule.domain.model

import java.time.LocalTime
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class TravelMode { WALKING, TRANSIT }

data class HomeLocation(val point: GeoPoint, val label: String)

data class RouteEstimate(
    val building: Building,
    val mode: TravelMode,
    val travelMinutes: Int,
    val recommendedLeaveTime: LocalTime,
)

/**
 * Оценка времени в пути по расстоянию — без платных API маршрутизации.
 * Точное время пользователь видит в Яндекс.Картах по кнопке «Маршрут».
 *
 * - Пешком: дороги длиннее прямой примерно в 1,3 раза, скорость 4,5 км/ч.
 * - Транспорт: 10 минут на дорогу до остановки и ожидание + путь (×1,4 к прямой) со средней
 *   скоростью 20 км/ч (метро с пересадками и наземный транспорт Петербурга).
 *   Если пешком быстрее (короткое расстояние) — берём пешком.
 * Результат округляется вверх до 5 минут: лучше выйти чуть раньше.
 */
object TravelTime {
    private const val WALK_DETOUR = 1.3
    private const val WALK_KMH = 4.5
    private const val TRANSIT_OVERHEAD_MIN = 10.0
    private const val TRANSIT_DETOUR = 1.4
    private const val TRANSIT_KMH = 20.0

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }

    fun minutes(from: GeoPoint, to: GeoPoint, mode: TravelMode): Int {
        val km = distanceKm(from, to)
        val walk = km * WALK_DETOUR / WALK_KMH * 60
        val raw = when (mode) {
            TravelMode.WALKING -> walk
            TravelMode.TRANSIT -> minOf(walk, TRANSIT_OVERHEAD_MIN + km * TRANSIT_DETOUR / TRANSIT_KMH * 60)
        }
        return (ceil(raw / 5.0) * 5).toInt().coerceAtLeast(5)
    }
}
