package com.example.gasuschedule.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.gasuschedule.domain.model.Lesson
import com.example.gasuschedule.domain.model.LessonType
import com.example.gasuschedule.domain.model.ScheduleWeek
import com.example.gasuschedule.domain.model.WeekParity
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "lessons", indices = [Index("groupName", "date")])
data class LessonEntity(
    @PrimaryKey val id: String,
    val groupName: String,
    val date: LocalDate,
    val lessonNumber: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subject: String,
    val type: LessonType,
    val rooms: List<String>,
    val teachers: List<String>,
    val building: String?,
    val weekNumber: Int,
    val weekParity: WeekParity,
    val note: String?,
)

@Entity(tableName = "weeks", primaryKeys = ["groupName", "number"])
data class WeekEntity(
    val groupName: String,
    val number: Int,
    val parity: WeekParity,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

fun LessonEntity.toDomain() = Lesson(
    id = id,
    subject = subject,
    type = type,
    teacher = teachers.joinToString(", "),
    room = rooms.joinToString(", "),
    building = building,
    lessonNumber = lessonNumber,
    startTime = startTime,
    endTime = endTime,
    dayOfWeek = date.dayOfWeek,
    date = date,
    weekNumber = weekNumber,
    weekParity = weekParity,
    groupName = groupName,
    rooms = rooms,
    teachers = teachers,
    note = note,
)

fun Lesson.toEntity() = LessonEntity(
    id = id,
    groupName = groupName,
    date = date,
    lessonNumber = lessonNumber,
    startTime = startTime,
    endTime = endTime,
    subject = subject,
    type = type,
    rooms = rooms,
    teachers = teachers,
    building = building,
    weekNumber = weekNumber,
    weekParity = weekParity,
    note = note,
)

fun WeekEntity.toDomain() = ScheduleWeek(number, parity, startDate, endDate)

fun ScheduleWeek.toEntity(groupName: String) = WeekEntity(groupName, number, parity, startDate, endDate)
