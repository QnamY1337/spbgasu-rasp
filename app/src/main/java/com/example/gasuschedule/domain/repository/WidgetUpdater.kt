package com.example.gasuschedule.domain.repository

/** Просьба перерисовать виджеты на главном экране — расписание в базе поменялось. */
interface WidgetUpdater {
    fun requestUpdate()
}
