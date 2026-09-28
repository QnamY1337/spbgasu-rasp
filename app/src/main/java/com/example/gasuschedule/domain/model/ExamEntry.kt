package com.example.gasuschedule.domain.model

import java.time.LocalDate
import java.time.LocalTime

enum class ExamType { EXAM, CREDIT, CREDIT_WITH_GRADE, COURSEWORK }

data class ExamEntry(
    val id: String,
    val subject: String,
    val type: ExamType,
    val date: LocalDate,
    val time: LocalTime?,
    val room: String?,
    val teacher: String?,
    val notes: String?,
)
