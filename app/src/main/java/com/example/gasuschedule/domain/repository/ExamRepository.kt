package com.example.gasuschedule.domain.repository

import com.example.gasuschedule.domain.model.ExamEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ExamRepository {
    /** Экзамены начиная с [from], по возрастанию даты. */
    fun observeUpcoming(from: LocalDate): Flow<List<ExamEntry>>
    suspend fun upsert(exam: ExamEntry)
    suspend fun delete(id: String)
}
