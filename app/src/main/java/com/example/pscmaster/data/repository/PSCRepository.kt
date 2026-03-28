package com.example.pscmaster.data.repository

import android.net.Uri
import com.example.pscmaster.data.entity.*
import com.example.pscmaster.data.local.SubjectCount
import com.example.pscmaster.api.AiResult
import kotlinx.coroutines.flow.Flow
import java.io.File

interface PSCRepository {
    // Question related
    suspend fun addQuestion(question: Question): Boolean
    suspend fun updateQuestion(question: Question)
    fun getAllQuestions(): Flow<List<Question>>
    suspend fun getAllQuestionsWithMetadata(): List<QuestionWithMetadata>
    suspend fun deleteQuestion(question: Question)
    suspend fun deleteQuestions(questions: List<Question>)
    fun getAllSubjects(): Flow<List<String>>
    fun getQuestionCount(): Flow<Int>
    suspend fun getQuestionsForPractice(subjects: List<String>, shuffle: Boolean): List<Question>
    suspend fun getRevisionQuestions(): List<Question>
    suspend fun getQuestionsByIds(ids: List<Long>): List<Question>
    
    // Performance and SRS
    suspend fun savePerformance(performance: UserPerformance)
    suspend fun updateSrs(questionId: Long, isCorrect: Boolean)
    suspend fun cleanupOldPerformanceLogs(thresholdTimestamp: Long)
    fun getAllPerformance(): Flow<List<UserPerformance>>
    suspend fun getWrongAnswers(): List<UserPerformance>
    fun getWeakSubjects(): Flow<List<SubjectCount>>
    fun getWeeklyProgress(): Flow<com.example.pscmaster.data.local.WeeklyStats>
    suspend fun calculateStudyStreak(): Int
    suspend fun getCachedInsights(hash: Int): AiResult?
    suspend fun saveCachedInsights(hash: Int, result: AiResult)
    suspend fun getQuestionsWithMistakes(): List<QuestionWithMetadata>
    
    // Quiz Sessions & Adaptive Engine
    suspend fun startQuizSession(subject: String?): QuizSession
    suspend fun finishQuizSession(sessionId: String, performances: List<UserPerformance>)
    suspend fun generateAdaptiveQuiz(size: Int, subject: String?, newQuestionRatio: Float = 0.2f): List<Question>
    fun observeBadgeState(questionId: Long): Flow<Int?>
    
    // Data Management
    suspend fun importQuestionsFromCsv(uri: Uri): Int
    suspend fun renameSubject(oldName: String, newName: String)
    fun getDatabaseFile(): File
    suspend fun exportDatabaseToCache(): File?
    suspend fun importDatabaseFromUri(uri: Uri)
    fun closeDatabase()
    fun getStorageInfo(): Pair<String, String>

    // Firebase Sync
    suspend fun syncToFirebase(): Result<Unit>
    fun startRealtimeSync()
    
    // Network
    fun isNetworkAvailable(): Boolean
}
