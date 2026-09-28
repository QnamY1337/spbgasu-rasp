package com.example.gasuschedule.domain.repository

import com.example.gasuschedule.domain.model.HomeworkItem
import kotlinx.coroutines.flow.Flow

interface HomeworkRepository {
    fun observeAll(): Flow<List<HomeworkItem>>
    fun observeForLesson(lessonId: String): Flow<List<HomeworkItem>>
    suspend fun get(id: String): HomeworkItem?
    suspend fun upsert(item: HomeworkItem)
    suspend fun setDone(id: String, done: Boolean)
    suspend fun delete(id: String)
}
