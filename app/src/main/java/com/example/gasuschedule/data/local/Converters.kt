package com.example.gasuschedule.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Даты храним ISO-строками ("2026-09-28") — они сортируются и сравниваются в SQL как даты
 * (BETWEEN, ORDER BY) и читаются глазами при отладке.
 */
class Converters {
    @TypeConverter fun dateToString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringToDate(s: String?): LocalDate? = s?.let(LocalDate::parse)

    @TypeConverter fun timeToString(t: LocalTime?): String? = t?.toString()
    @TypeConverter fun stringToTime(s: String?): LocalTime? = s?.let(LocalTime::parse)

    @TypeConverter fun instantToMillis(i: Instant?): Long? = i?.toEpochMilli()
    @TypeConverter fun millisToInstant(ms: Long?): Instant? = ms?.let(Instant::ofEpochMilli)

    @TypeConverter fun listToString(list: List<String>?): String? = list?.joinToString("\n")
    @TypeConverter fun stringToList(s: String?): List<String>? =
        s?.let { if (it.isEmpty()) emptyList() else it.split('\n') }
}
