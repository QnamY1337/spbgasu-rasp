package com.example.gasuschedule.di

import com.example.gasuschedule.data.remote.BitrixScheduleSource
import com.example.gasuschedule.data.remote.NominatimAddressSearch
import com.example.gasuschedule.data.remote.ScheduleRemoteSource
import com.example.gasuschedule.domain.repository.AddressSearch
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    fun provideAddressSearch(client: OkHttpClient): AddressSearch = NominatimAddressSearch(client)

    /** Singleton: источник держит сессию сайта (cookie + CSRF-токен) и кэш списка групп. */
    @Provides
    @Singleton
    fun provideScheduleRemoteSource(client: OkHttpClient): ScheduleRemoteSource =
        BitrixScheduleSource(client = client)
}
