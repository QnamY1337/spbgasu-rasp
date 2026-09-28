package com.example.gasuschedule.data.remote

import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.ScheduleNetworkException
import com.example.gasuschedule.domain.repository.AddressSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

/**
 * Геокодер OpenStreetMap (Nominatim) — бесплатный, без ключа. Используется только когда
 * пользователь сам ищет адрес дома, поэтому правилам сервиса (не чаще 1 запроса в секунду,
 * осмысленный User-Agent) соответствует.
 */
class NominatimAddressSearch(
    private val client: OkHttpClient,
    baseUrl: String,
) : AddressSearch {

    @Inject constructor(client: OkHttpClient) : this(client, DEFAULT_BASE_URL)

    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Сначала ищем строго в Петербурге и пригородах: без этого "Московский проспект 100"
     * находится в Воронеже. Если там ничего нет — по всей России.
     */
    override suspend fun search(query: String): List<HomeLocation> =
        searchOnce(query, bounded = true).ifEmpty { searchOnce(query, bounded = false) }

    private suspend fun searchOnce(query: String, bounded: Boolean): List<HomeLocation> {
        val url = base.newBuilder().addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("limit", "6")
            .addQueryParameter("countrycodes", "ru")
            .addQueryParameter("viewbox", SPB_VIEWBOX)
            .addQueryParameter("bounded", if (bounded) "1" else "0")
            .addQueryParameter("accept-language", "ru")
            .build()
        val results = json.parseToJsonElement(get(url)) as? JsonArray ?: return emptyList()
        return results.mapNotNull { (it as? JsonObject)?.toHome() }.distinctBy { it.label }
    }

    override suspend fun describe(point: GeoPoint): String? {
        val url = base.newBuilder().addPathSegment("reverse")
            .addQueryParameter("lat", point.latitude.toString())
            .addQueryParameter("lon", point.longitude.toString())
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("zoom", "18")
            .addQueryParameter("accept-language", "ru")
            .build()
        return runCatching { (json.parseToJsonElement(get(url)) as? JsonObject)?.toHome()?.label }.getOrNull()
    }

    private suspend fun get(url: HttpUrl): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw ScheduleNetworkException("HTTP ${resp.code} от геокодера")
                resp.body.string()
            }
        } catch (e: IOException) {
            throw ScheduleNetworkException("Нет связи с сервисом поиска адресов", e)
        }
    }

    private fun JsonObject.toHome(): HomeLocation? {
        val lat = str("lat")?.toDoubleOrNull() ?: return null
        val lon = str("lon")?.toDoubleOrNull() ?: return null
        return HomeLocation(GeoPoint(lat, lon), shortLabel(this))
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    companion object {
        const val DEFAULT_BASE_URL = "https://nominatim.openstreetmap.org/"
        /** Петербург с пригородами (Пушкин, Красное Село, Всеволожск, Кронштадт): lon1,lat1,lon2,lat2. */
        private const val SPB_VIEWBOX = "29.4,60.3,31.0,59.6"
        // Только ASCII: OkHttp отклоняет заголовки с кириллицей.
        private const val USER_AGENT = "GasuSchedule/0.1 (Android; SPbGASU student timetable)"

        /** "Невский проспект, 28, Санкт-Петербург" вместо длинного display_name. */
        fun shortLabel(obj: JsonObject): String {
            val address = obj["address"] as? JsonObject
            fun a(key: String) = address?.get(key)?.jsonPrimitive?.contentOrNull
            val street = a("road") ?: a("pedestrian") ?: a("footway") ?: a("neighbourhood")
            val house = a("house_number")
            val place = a("city") ?: a("town") ?: a("village") ?: a("hamlet") ?: a("suburb")
            val label = listOfNotNull(
                street?.let { if (house != null) "$it, $house" else it },
                place,
            ).joinToString(", ")
            return label.ifEmpty {
                obj["display_name"]?.jsonPrimitive?.contentOrNull.orEmpty().split(", ").take(3).joinToString(", ")
            }
        }
    }
}
