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

    @Query("SELECT * FROM user_performance")
    suspend fun getAllPerformanceList(): List<UserPerformance>

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
}

data class SubjectCount(
    val subject: String,
    val count: Int
)
