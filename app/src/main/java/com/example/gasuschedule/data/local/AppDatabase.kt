package com.example.gasuschedule.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.gasuschedule.data.local.dao.ExamDao
import com.example.gasuschedule.data.local.dao.HomeworkDao
import com.example.gasuschedule.data.local.dao.ScheduleDao

@Database(
    entities = [
        LessonEntity::class,
        WeekEntity::class,
        ScheduleChangeEntity::class,
        HomeworkEntity::class,
        ExamEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun examDao(): ExamDao

    companion object {
        const val NAME = "gasu_schedule.db"
    }
}
