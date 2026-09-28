package com.example.gasuschedule.domain.repository

import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.HomeLocation

/** Поиск адреса дома и подпись для точки геолокации. */
interface AddressSearch {
    /** Бросает ScheduleNetworkException, если нет сети. */
    suspend fun search(query: String): List<HomeLocation>

    /** Короткий адрес точки ("2-я Красноармейская ул., 4"); null — не удалось определить. */
    suspend fun describe(point: GeoPoint): String?
}
