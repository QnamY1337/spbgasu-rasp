package com.example.gasuschedule.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.gasuschedule.data.local.HomeworkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeworkDao {
    // Без дедлайна — в конце списка.
    @Query("SELECT * FROM homework ORDER BY isDone, dueDate IS NULL, dueDate, createdAt")
    fun observeAll(): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework WHERE lessonId = :lessonId ORDER BY createdAt")
    fun observeForLesson(lessonId: String): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework WHERE id = :id")
    suspend fun get(id: String): HomeworkEntity?

    @Upsert
    suspend fun upsert(item: HomeworkEntity)

    @Query("UPDATE homework SET isDone = :done WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean)

    @Query("DELETE FROM homework WHERE id = :id")
    suspend fun delete(id: String)
}
