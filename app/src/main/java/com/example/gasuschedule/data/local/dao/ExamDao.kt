package com.example.gasuschedule.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.gasuschedule.data.local.ExamEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE date >= :from ORDER BY date, time")
    fun observeUpcoming(from: LocalDate): Flow<List<ExamEntity>>

    @Upsert
    suspend fun upsert(exam: ExamEntity)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun delete(id: String)
}
