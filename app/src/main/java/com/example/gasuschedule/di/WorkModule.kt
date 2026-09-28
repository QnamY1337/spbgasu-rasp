package com.example.gasuschedule.di

import com.example.gasuschedule.domain.repository.ReminderReplanTrigger
import com.example.gasuschedule.domain.repository.ReminderScheduler
import com.example.gasuschedule.domain.repository.WidgetUpdater
import com.example.gasuschedule.presentation.widget.WidgetRefresher
import com.example.gasuschedule.work.AlarmReminderScheduler
import com.example.gasuschedule.work.ReminderWork
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {
    @Binds
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler

    @Binds
    abstract fun bindReminderReplanTrigger(impl: ReminderWork): ReminderReplanTrigger

    @Binds
    abstract fun bindWidgetUpdater(impl: WidgetRefresher): WidgetUpdater
}
