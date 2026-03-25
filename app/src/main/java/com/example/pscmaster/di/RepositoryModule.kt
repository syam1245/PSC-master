package com.example.pscmaster.di

import com.example.pscmaster.data.repository.PSCRepository
import com.example.pscmaster.data.repository.PSCRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPSCRepository(
        pscRepositoryImpl: PSCRepositoryImpl
    ): PSCRepository
}
