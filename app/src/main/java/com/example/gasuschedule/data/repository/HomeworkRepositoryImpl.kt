package com.example.gasuschedule.data.repository

import com.example.gasuschedule.data.local.dao.HomeworkDao
import com.example.gasuschedule.data.local.toDomain
import com.example.gasuschedule.data.local.toEntity
import com.example.gasuschedule.domain.model.HomeworkItem
import com.example.gasuschedule.domain.repository.HomeworkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class HomeworkRepositoryImpl @Inject constructor(
    private val dao: HomeworkDao,
) : HomeworkRepository {
    override fun observeAll(): Flow<List<HomeworkItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeForLesson(lessonId: String): Flow<List<HomeworkItem>> =
        dao.observeForLesson(lessonId).map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: String): HomeworkItem? = dao.get(id)?.toDomain()
    override suspend fun upsert(item: HomeworkItem) = dao.upsert(item.toEntity())
    override suspend fun setDone(id: String, done: Boolean) = dao.setDone(id, done)
    override suspend fun delete(id: String) = dao.delete(id)
}
