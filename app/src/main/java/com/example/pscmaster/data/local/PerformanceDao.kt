package com.example.pscmaster.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.pscmaster.data.entity.UserPerformance
import kotlinx.coroutines.flow.Flow

@Dao
interface PerformanceDao {
    @Insert
    suspend fun insertPerformance(performance: UserPerformance)

    @Query("SELECT * FROM user_performance ORDER BY timestamp DESC")
    fun getAllPerformance(): Flow<List<UserPerformance>>


    @Query("""
        SELECT up.* 
        FROM user_performance up 
        INNER JOIN (
            SELECT questionId, MAX(timestamp) as maxTs 
            FROM user_performance 
            GROUP BY questionId
        ) latest ON up.questionId = latest.questionId AND up.timestamp = latest.maxTs 
        WHERE up.isCorrect = 0
    """)
    suspend fun getWrongAnswers(): List<UserPerformance>

    @Query("""
        SELECT up.subject, COUNT(up.id) as count 
        FROM user_performance up 
        INNER JOIN (
            SELECT questionId, MAX(timestamp) as maxTs 
            FROM user_performance 
            GROUP BY questionId
        ) latest ON up.questionId = latest.questionId AND up.timestamp = latest.maxTs 
        WHERE up.isCorrect = 0 
        GROUP BY up.subject 
        ORDER BY count DESC
    """)
    fun getWeakSubjects(): Flow<List<SubjectCount>>

    @Query("SELECT COUNT(CASE WHEN isCorrect = 1 THEN 1 END) as correct, COUNT(*) as total FROM user_performance WHERE timestamp >= :since")
    fun getWeeklyStats(since: Long): Flow<WeeklyStats>
}

data class WeeklyStats(
    val correct: Int,
    val total: Int
)

data class SubjectCount(
    val subject: String,
    val count: Int
)
