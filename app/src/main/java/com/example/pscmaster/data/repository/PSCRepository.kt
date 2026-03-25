package com.example.pscmaster.data.repository

import android.net.Uri
import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.data.entity.UserPerformance
import com.example.pscmaster.api.AiResult
import com.example.pscmaster.data.local.SubjectCount
import kotlinx.coroutines.flow.Flow
import java.io.File

interface PSCRepository {
    // Question related
    suspend fun addQuestion(question: Question): Boolean
    suspend fun updateQuestion(question: Question)
    fun getAllQuestions(): Flow<List<Question>>
    suspend fun deleteQuestion(question: Question)
    suspend fun deleteQuestions(questions: List<Question>)
    fun getAllSubjects(): Flow<List<String>>
    fun getQuestionCount(): Flow<Int>
    suspend fun getQuestionsForPractice(subjects: List<String>, shuffle: Boolean): List<Question>
    suspend fun getRevisionQuestions(): List<Question>
    
    // Performance and SRS
    suspend fun savePerformance(performance: UserPerformance)
    suspend fun updateSrs(questionId: Long, isCorrect: Boolean)
    fun getAllPerformance(): Flow<List<UserPerformance>>
    suspend fun getWrongAnswers(): List<UserPerformance>
    fun getWeakSubjects(): Flow<List<SubjectCount>>
    suspend fun getCachedInsights(hash: Int): AiResult?
    suspend fun saveCachedInsights(hash: Int, result: AiResult)
    
    // Data Management
    suspend fun importQuestionsFromCsv(uri: Uri): Int
    suspend fun renameSubject(oldName: String, newName: String)
    fun getDatabaseFile(): File
    fun getStorageInfo(): StorageInfo
    suspend fun exportDatabaseToCache(): File?
    suspend fun importDatabaseFromUri(uri: Uri)
    fun closeDatabase()

    // Firebase Sync
    suspend fun syncToFirebase(): Result<Unit>
    fun startRealtimeSync()
    // Network
    fun isNetworkAvailable(): Boolean
}

data class StorageInfo(
    val path: String,
    val sizeBytes: Long,
    val availableSpaceBytes: Long
)
