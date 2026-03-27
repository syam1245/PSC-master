package com.example.pscmaster.data.local

import androidx.room.*
import com.example.pscmaster.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertQuestion(question: Question): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun bulkInsert(questions: List<Question>): LongArray

    @Update
    suspend fun updateQuestion(question: Question)

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionById(id: Long): Question?

    @Query("SELECT * FROM questions WHERE questionText = :text LIMIT 1")
    suspend fun getQuestionByText(text: String): Question?

    @Query("SELECT * FROM questions ORDER BY RANDOM() LIMIT 10")
    fun getRandomQuestions(): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE subject = :subject ORDER BY RANDOM() LIMIT 10")
    fun getQuestionsBySubject(subject: String): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE subject IN (:subjects)")
    suspend fun getQuestionsBySubjects(subjects: List<String>): List<Question>

    @Query("SELECT * FROM questions")
    suspend fun getAllQuestionsList(): List<Question>

    @Query("SELECT * FROM questions ORDER BY timestamp DESC")
    fun getAllQuestions(): Flow<List<Question>>

    @Delete
    suspend fun deleteQuestion(question: Question)

    @Delete
    suspend fun deleteQuestions(questions: List<Question>)
    
    @Query("SELECT DISTINCT subject FROM questions")
    fun getAllSubjects(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM questions")
    fun getQuestionCount(): Flow<Int>

    @Query("UPDATE questions SET subject = :newName WHERE subject = :oldName")
    suspend fun renameSubject(oldName: String, newName: String)

    @Query("SELECT * FROM questions WHERE nextReviewTimestamp > 0 AND nextReviewTimestamp <= :currentTime")
    suspend fun getScheduledRevisionQuestions(currentTime: Long): List<Question>

    @Transaction
    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionWithMetadata(id: Long): QuestionWithMetadata?

    @Transaction
    @Query("SELECT * FROM questions ORDER BY timestamp DESC")
    suspend fun getAllQuestionsWithMetadata(): List<QuestionWithMetadata>

    @Transaction
    @Query("""
        SELECT * FROM questions 
        WHERE id NOT IN (SELECT questionId FROM question_badge_state WHERE state = 3)
        ORDER BY RANDOM() LIMIT :limit
    """)
    suspend fun getNewCandidateQuestions(limit: Int): List<QuestionWithMetadata>

    @Transaction
    @Query("SELECT * FROM questions WHERE subject = :subject")
    suspend fun getQuestionsWithMetadataBySubject(subject: String): List<QuestionWithMetadata>

    @Transaction
    @Query("""
        SELECT * FROM questions 
        WHERE id IN (SELECT questionId FROM user_performance_metrics WHERE correctAttempts < totalAttempts)
        ORDER BY timestamp DESC
    """)
    suspend fun getQuestionsWithMistakes(): List<QuestionWithMetadata>
}
