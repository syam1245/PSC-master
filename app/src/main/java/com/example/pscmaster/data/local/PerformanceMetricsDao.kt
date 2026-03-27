package com.example.pscmaster.data.local

import androidx.room.*
import com.example.pscmaster.data.entity.UserPerformanceMetrics
import kotlinx.coroutines.flow.Flow

@Dao
interface PerformanceMetricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetrics(metrics: UserPerformanceMetrics)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkUpsertMetrics(metrics: List<UserPerformanceMetrics>)

    @Query("SELECT * FROM user_performance_metrics WHERE questionId = :questionId")
    suspend fun getMetricsForQuestion(questionId: Long): UserPerformanceMetrics?

    @Query("SELECT * FROM user_performance_metrics WHERE questionId = :questionId")
    fun observeMetricsForQuestion(questionId: Long): Flow<UserPerformanceMetrics?>

    @Query("SELECT * FROM user_performance_metrics")
    suspend fun getAllMetrics(): List<UserPerformanceMetrics>
    
    @Query("DELETE FROM user_performance_metrics")
    suspend fun clearAllMetrics()
}
