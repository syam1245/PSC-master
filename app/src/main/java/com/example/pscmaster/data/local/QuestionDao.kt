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

    @Query("SELECT * FROM questions WHERE id NOT IN (:excludeIds) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestionsExclude(excludeIds: List<Long>, limit: Int): List<Question>

    @Query("SELECT * FROM questions WHERE subject = :subject AND id NOT IN (:excludeIds) ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomQuestionsExcludeBySubject(subject: String, excludeIds: List<Long>, limit: Int): List<Question>

    @Query("SELECT * FROM questions")
    suspend fun getAllQuestionsList(): List<Question>

    @Query("SELECT * FROM questions ORDER BY timestamp DESC")
    fun getAllQuestions(): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getQuestionsByIds(ids: List<Long>): List<Question>

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

    @Query("""
        SELECT q.* FROM questions q
        JOIN user_performance_metrics m ON q.id = m.questionId
        WHERE m.nextReviewTimestamp > 0 AND m.nextReviewTimestamp <= :currentTime
    """)
    suspend fun getScheduledRevisionQuestions(currentTime: Long): List<Question>

    @Transaction
    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionWithMetadata(id: Long): QuestionWithMetadata?

    @Transaction
    @Query("SELECT * FROM questions ORDER BY timestamp DESC")
    suspend fun getAllQuestionsWithMetadata(): List<QuestionWithMetadata>

    @Transaction
    @Query("""
        SELECT q.* FROM questions q
        LEFT JOIN user_performance_metrics m ON q.id = m.questionId
        WHERE COALESCE(m.isShownInCycle, 0) = 0
          AND (:subject IS NULL OR q.subject = :subject)
        ORDER BY 
          -- Priority 1: Overdue Revisions First (Most overdue items at the very top)
          CASE WHEN COALESCE(m.nextReviewTimestamp, 0) > 0 AND COALESCE(m.nextReviewTimestamp, 0) <= :currentTime THEN 0 ELSE 1 END ASC,
          CASE WHEN COALESCE(m.nextReviewTimestamp, 0) > 0 AND COALESCE(m.nextReviewTimestamp, 0) <= :currentTime THEN m.nextReviewTimestamp ELSE 9223372036854775807 END ASC,
          -- Priority 2: Brand new unseen items
          CASE WHEN COALESCE(m.totalAttempts, 0) = 0 THEN 0 ELSE 1 END ASC,
          -- Priority 3: Weak areas (Accuracy)
          COALESCE(CAST(m.correctAttempts AS REAL) / MAX(m.totalAttempts, 1), 0.5) ASC,
          -- Priority 4: Oldest attempt (Avoid forgetting)
          COALESCE(m.lastAttemptTimestamp, 0) ASC,
          RANDOM()
        LIMIT :limit
    """)
    suspend fun getAdaptiveCycleQuestions(currentTime: Long, subject: String?, limit: Int): List<QuestionWithMetadata>

    @Query("UPDATE user_performance_metrics SET isShownInCycle = 1 WHERE questionId IN (:ids)")
    suspend fun markQuestionsAsShownInCycle(ids: List<Long>)

    @Query("""
        UPDATE user_performance_metrics 
        SET isShownInCycle = 0 
        WHERE questionId IN (
            SELECT id FROM questions WHERE :subject IS NULL OR subject = :subject
        )
    """)
    suspend fun resetCycleCurrentSubject(subject: String?)

    @Query("""
        INSERT INTO user_performance_metrics (questionId, isShownInCycle)
        VALUES (:id, 1)
        ON CONFLICT(questionId) DO UPDATE SET isShownInCycle = 1
    """)
    suspend fun upsertShownInCycle(id: Long)

    @Transaction
    suspend fun fetchAndMarkAdaptiveQuestions(size: Int, subject: String?, currentTime: Long): List<Question> {
        val result = mutableListOf<QuestionWithMetadata>()
        
        var batch = getAdaptiveCycleQuestions(currentTime, subject, size)
        result.addAll(batch)
        
        for (q in batch) { upsertShownInCycle(q.question.id) }
        
        if (result.size < size) {
            resetCycleCurrentSubject(subject)
            for (q in result) { upsertShownInCycle(q.question.id) }
            
            val remainder = size - result.size
            val batch2 = getAdaptiveCycleQuestions(currentTime, subject, remainder)
            result.addAll(batch2)
            
            for (q in batch2) { upsertShownInCycle(q.question.id) }
        }
        
        return result.map { it.question }
    }

    @Transaction
    @Query("SELECT * FROM questions WHERE subject = :subject")
    suspend fun getQuestionsWithMetadataBySubject(subject: String): List<QuestionWithMetadata>

    @Transaction
    @Query("""
        SELECT q.* FROM questions q
        JOIN (
            SELECT questionId, MAX(timestamp) as maxTs
            FROM user_performance
            GROUP BY questionId
        ) latest ON q.id = latest.questionId
        JOIN user_performance up ON up.questionId = latest.questionId AND up.timestamp = latest.maxTs
        WHERE up.isCorrect = 0
        ORDER BY up.timestamp DESC
    """)
    suspend fun getQuestionsWithMistakes(): List<QuestionWithMetadata>
}
