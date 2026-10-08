package com.example.gasuschedule.presentation.schedule

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/**
 * Просьба открыть в "Расписании" конкретный день — например, тапом по полоске недели на главной.
 * Вкладка "Расписание" забирает дату и сбрасывает её.
 */
object ScheduleDayRequest {
    private val _date = MutableStateFlow<LocalDate?>(null)
    val date: StateFlow<LocalDate?> = _date

    fun open(date: LocalDate) {
        _date.value = date
    }

    fun consume() {
        _date.value = null
    }
}
