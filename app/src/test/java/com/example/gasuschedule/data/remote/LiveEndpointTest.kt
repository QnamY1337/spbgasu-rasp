package com.example.gasuschedule.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Проверка на живом сайте. По умолчанию пропускается; запуск:
 * ./gradlew :app:testDebugUnitTest -Plive --tests "*LiveEndpointTest*"
 */
class LiveEndpointTest {

    @Before
    fun onlyWhenRequested() = assumeTrue(System.getProperty("live") == "true")

    @Test
    fun `живой эндпоинт отдаёт и разбирает расписание 3-ТТП-26`() = runBlocking {
        val source = BitrixScheduleSource()
        val groups = source.fetchGroups()
        assertTrue("групп: ${groups.size}", groups.size > 100 && "3-ТТП-26" in groups)

        val schedule = source.fetchSchedule("3-ТТП-26")
        println("Недель: ${schedule.weeks.size}, пар: ${schedule.lessons.size}")
        schedule.lessons.take(5).forEach(::println)
        assertTrue(schedule.weeks.isNotEmpty())
        assertTrue(schedule.lessons.isNotEmpty())
    }
}
