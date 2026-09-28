package com.example.gasuschedule.domain.model

data class GeoPoint(val latitude: Double, val longitude: Double)

data class Building(
    /** Код после "/" в номере аудитории: "712/С" -> "С". */
    val code: String,
    val name: String,
    val address: String,
    val location: GeoPoint,
)

/**
 * Корпуса СПбГАСУ. Список и адреса сверены с картой кампуса map.spbgasu.ru (сентябрь 2026),
 * коды — со списком аудиторий сайта расписания (window.AUDITORIUMS), координаты — OpenStreetMap.
 * Корпуса Г, С и А — одно здание на 2-й Красноармейской, 4.
 */
object Buildings {
    private val MAIN = GeoPoint(59.9149515, 30.3166988)
    private val TRAINING_BASE = Building(
        "УБ", "Учебная база", "2-й км а/д Красное Село – Пушкин", GeoPoint(59.7344453, 30.1426846),
    )

    val ALL: List<Building> = listOf(
        Building("Г", "Главный корпус", "2-я Красноармейская ул., 4", MAIN),
        Building("С", "Строительный корпус", "2-я Красноармейская ул., 4", MAIN),
        Building("А", "Архитектурный корпус", "2-я Красноармейская ул., 4", MAIN),
        Building("5", "Корпус № 5", "2-я Красноармейская ул., 5", GeoPoint(59.9154710, 30.3155578)),
        Building("Е", "Корпус на ул. Егорова", "ул. Егорова, 5/8", GeoPoint(59.9149583, 30.3136900)),
        Building("К", "Корпус на Курляндской", "Курляндская ул., 2/5", GeoPoint(59.9116970, 30.2920819)),
        Building("Б", "Корпус на Серпуховской", "Серпуховская ул., 10", GeoPoint(59.9163224, 30.3225771)),
        Building("Ф", "Корпус на Фонтанке", "наб. реки Фонтанки, 123", GeoPoint(59.9208251, 30.3099576)),
        TRAINING_BASE,
    )

    private val byCode = ALL.associateBy { it.code } + ("УБ-Б" to TRAINING_BASE)

    /** null — код неизвестен (новый корпус, опечатка на сайте): маршрут недоступен, но не падаем. */
    fun byCode(code: String?): Building? = code?.let { byCode[it.trim()] }
}
