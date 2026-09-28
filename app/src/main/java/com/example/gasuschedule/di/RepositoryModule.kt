package com.example.gasuschedule.di

import com.example.gasuschedule.data.local.prefs.UserPreferencesRepositoryImpl
import com.example.gasuschedule.data.repository.ExamRepositoryImpl
import com.example.gasuschedule.data.repository.HomeworkRepositoryImpl
import com.example.gasuschedule.data.repository.ScheduleRepositoryImpl
import com.example.gasuschedule.data.repository.WeatherRepositoryImpl
import com.example.gasuschedule.domain.repository.ExamRepository
import com.example.gasuschedule.domain.repository.HomeworkRepository
import com.example.gasuschedule.domain.repository.ScheduleRepository
import com.example.gasuschedule.domain.repository.UserPreferencesRepository
import com.example.gasuschedule.domain.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds @Singleton
    abstract fun bindHomeworkRepository(impl: HomeworkRepositoryImpl): HomeworkRepository

    @Binds @Singleton
    abstract fun bindExamRepository(impl: ExamRepositoryImpl): ExamRepository

    @Binds @Singleton
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesRepositoryImpl): UserPreferencesRepository
}
