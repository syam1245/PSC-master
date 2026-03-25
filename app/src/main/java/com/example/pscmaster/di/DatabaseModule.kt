package com.example.pscmaster.di

import android.content.Context
import androidx.room.Room
import com.example.pscmaster.data.local.AppDatabase
import com.example.pscmaster.data.local.PerformanceDao
import com.example.pscmaster.data.local.QuestionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "psc_master_db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2, 
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4
            )
            .build()
    }

    @Provides
    fun provideQuestionDao(database: AppDatabase): QuestionDao {
        return database.questionDao()
    }

    @Provides
    fun providePerformanceDao(database: AppDatabase): PerformanceDao {
        return database.performanceDao()
    }
}
