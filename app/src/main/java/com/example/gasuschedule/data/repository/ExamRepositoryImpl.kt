package com.example.gasuschedule.data.repository

import com.example.gasuschedule.data.local.dao.ExamDao
import com.example.gasuschedule.data.local.toDomain
import com.example.gasuschedule.data.local.toEntity
import com.example.gasuschedule.domain.model.ExamEntry
import com.example.gasuschedule.domain.repository.ExamRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class ExamRepositoryImpl @Inject constructor(
    private val dao: ExamDao,
) : ExamRepository {
    override fun observeUpcoming(from: LocalDate): Flow<List<ExamEntry>> =
        dao.observeUpcoming(from).map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(exam: ExamEntry) = dao.upsert(exam.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}
