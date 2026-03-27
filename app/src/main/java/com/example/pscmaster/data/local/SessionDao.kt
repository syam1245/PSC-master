package com.example.pscmaster.data.local

import androidx.room.*
import com.example.pscmaster.data.entity.QuestionBadgeState
import com.example.pscmaster.data.entity.QuizSession
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: QuizSession)

    @Update
    suspend fun updateSession(session: QuizSession)

    @Query("SELECT * FROM quiz_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): QuizSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBadgeState(state: QuestionBadgeState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun bulkUpsertBadgeState(states: List<QuestionBadgeState>)

    @Query("SELECT * FROM question_badge_state WHERE questionId = :questionId")
    suspend fun getBadgeState(questionId: Long): QuestionBadgeState?

    @Query("SELECT state FROM question_badge_state WHERE questionId = :questionId")
    fun observeBadgeState(questionId: Long): Flow<Int?>

    @Query("UPDATE question_badge_state SET state = :newState WHERE questionId IN (:questionIds)")
    suspend fun updateBadgeStates(questionIds: List<Long>, newState: Int)

    @Query("SELECT * FROM quiz_sessions WHERE isCompleted = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getLatestActiveSession(): QuizSession?
}
