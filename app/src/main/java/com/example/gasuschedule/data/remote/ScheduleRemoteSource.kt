package com.example.gasuschedule.data.remote

import com.example.gasuschedule.domain.model.SemesterSchedule

/**
 * Источник расписания. Основная реализация — AJAX-эндпоинт Bitrix ([BitrixScheduleSource]);
 * интерфейс позволяет подменить её, например, разбором Excel (getExcel.php) без изменений выше по слоям.
 */
interface ScheduleRemoteSource {
    /** Расписание группы на весь семестр. Пустое ([SemesterSchedule.isEmpty]) — группа не найдена. */
    suspend fun fetchSchedule(groupName: String): SemesterSchedule

    /** Полный список групп для автокомплита. */
    suspend fun fetchGroups(): List<String>
}

