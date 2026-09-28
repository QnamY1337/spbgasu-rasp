package com.example.gasuschedule.domain.usecase

import com.example.gasuschedule.domain.model.Buildings
import com.example.gasuschedule.domain.model.GeoPoint
import com.example.gasuschedule.domain.model.HomeLocation
import com.example.gasuschedule.domain.model.TravelMode
import com.example.gasuschedule.domain.model.TravelTime
import com.example.gasuschedule.presentation.common.YandexMaps
import com.example.gasuschedule.presentation.widget.widgetModel
import com.example.gasuschedule.testutil.MOSCOW
import com.example.gasuschedule.testutil.clockAt
import com.example.gasuschedule.testutil.d
import com.example.gasuschedule.testutil.lesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZonedDateTime

class RouteTest {

    private val main = Buildings.byCode("Г")!!.location
    /** Условный дом у метро «Проспект Просвещения» — ~13 км от главного корпуса. */
    private val farHome = HomeLocation(GeoPoint(60.0514, 30.3325), "пр. Просвещения")
    /** Дом в 800 м от главного корпуса. */
    private val nearHome = HomeLocation(GeoPoint(59.9190, 30.3050), "Измайловский пр.")

    @Test
    fun `справочник корпусов`() {
        assertEquals("2-я Красноармейская ул., 4", Buildings.byCode("С")!!.address)
        assertEquals(Buildings.byCode("Г")!!.location, Buildings.byCode("А")!!.location)
        assertEquals("Учебная база", Buildings.byCode("УБ-Б")!!.name)
        assertNull(Buildings.byCode("Р"))
        assertNull(Buildings.byCode(null))
    }

    @Test
    fun `оценка времени в пути`() {
        val km = TravelTime.distanceKm(farHome.point, main)
        assertTrue("≈15 км, было $km", km in 14.0..16.0)
        // Транспорт: 10 + 15.2*1.4/20*60 ≈ 74 -> 75; пешком: 15.2*1.3/4.5*60 ≈ 264 -> 265.
        assertEquals(75, TravelTime.minutes(farHome.point, main, TravelMode.TRANSIT))
        assertEquals(265, TravelTime.minutes(farHome.point, main, TravelMode.WALKING))
        // Рядом с вузом транспорт не быстрее ходьбы — берём пешком.
        val walkNear = TravelTime.minutes(nearHome.point, main, TravelMode.WALKING)
        assertEquals(walkNear, TravelTime.minutes(nearHome.point, main, TravelMode.TRANSIT))
        assertEquals(5, TravelTime.minutes(main, main, TravelMode.TRANSIT))
    }

    @Test
    fun `время выхода - начало минус дорога минус запас`() {
        val l = lesson(d(29), 1, rooms = listOf("Актовый зал/Г")) // 09:00
        val e = EstimateLeaveTimeUseCase.estimate(l, farHome, TravelMode.TRANSIT, 10) as LeaveEstimate.Estimated
        assertEquals(75, e.route.travelMinutes)
        assertEquals(LocalTime.of(7, 35), e.route.recommendedLeaveTime)
        assertEquals("Главный корпус", e.route.building.name)

        assertEquals(LeaveEstimate.NoHome, EstimateLeaveTimeUseCase.estimate(l, null, TravelMode.TRANSIT, 10))
        val unknown = lesson(d(29), 1, rooms = listOf("Спортзал"))
        assertTrue(EstimateLeaveTimeUseCase.estimate(unknown, farHome, TravelMode.TRANSIT, 10) is LeaveEstimate.UnknownBuilding)
    }

    @Test
    fun `первая пара дня`() {
        val day = listOf(lesson(d(29), 2), lesson(d(29), 3), lesson(d(30), 1))
        assertTrue(EstimateLeaveTimeUseCase.isFirstOfDay(day[0], day))
        assertFalse(EstimateLeaveTimeUseCase.isFirstOfDay(day[1], day))
        assertTrue(EstimateLeaveTimeUseCase.isFirstOfDay(day[2], day))
    }

    @Test
    fun `пора выходить - только к первой паре дня и только в будущем`() {
        val lessons = listOf(
            lesson(d(28), 1, rooms = listOf("712/С")), // сегодня 09:00 — выход уже прошёл
            lesson(d(29), 2, "Высшая математика", rooms = listOf("409/С")), // завтра 10:45 — первая
            lesson(d(29), 3, rooms = listOf("718/С")),
        )
        val clock = clockAt(d(28), 12)
        val reminders = ScheduleNotificationsUseCase.buildLeaveReminders(lessons, farHome, TravelMode.TRANSIT, 10, clock)
        assertEquals(1, reminders.size)
        with(reminders.single()) {
            assertEquals(ZonedDateTime.of(2026, 9, 29, 9, 20, 0, 0, MOSCOW).toInstant(), triggerAt)
            assertEquals("Пора выходить · Высшая математика в 10:45", title)
            assertEquals("Дорога ≈75 мин на транспорте · Строительный корпус, 2-я Красноармейская ул., 4", text)
            assertTrue(key.startsWith("leave|"))
        }
        assertTrue(ScheduleNotificationsUseCase.buildLeaveReminders(lessons, null, TravelMode.TRANSIT, 10, clock).isEmpty())
    }

    @Test
    fun `виджет - выйти в, только для первой пары и не во время пары`() {
        val first = lesson(d(29), 2, rooms = listOf("409/С"))
        val lessons = listOf(first, lesson(d(29), 3))
        val now = d(28).atTime(20, 0)
        val next = GetNextLessonUseCase.pick(lessons, now) as NextLesson.Found
        assertTrue(next.firstOfDay)
        val leave = EstimateLeaveTimeUseCase.estimate(first, farHome, TravelMode.TRANSIT, 10)
        assertEquals("Выйти в 09:20", widgetModel(next, now, leave).chip)
        assertEquals("2 пара · 10:45–12:15", widgetModel(next, now, LeaveEstimate.NoHome).chip)

        val second = GetNextLessonUseCase.pick(lessons, d(29).atTime(12, 20)) as NextLesson.Found
        assertFalse(second.firstOfDay)
        assertEquals("3 пара · 12:30–14:00", widgetModel(second, d(29).atTime(12, 20), leave).chip)
    }

    @Test
    fun `ссылка на маршрут в Яндекс Картах`() {
        assertEquals(
            "rtext=60.0514,30.3325~59.9149515,30.3166988&rtt=mt",
            YandexMaps.routeQuery(farHome.point, main, TravelMode.TRANSIT),
        )
        assertEquals("rtext=~59.9149515,30.3166988&rtt=pd", YandexMaps.routeQuery(null, main, TravelMode.WALKING))
    }
}
