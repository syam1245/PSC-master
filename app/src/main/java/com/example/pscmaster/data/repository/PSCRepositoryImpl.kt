package com.example.pscmaster.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.example.pscmaster.api.AiResult
import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.data.entity.UserPerformance
import com.example.pscmaster.data.local.AppDatabase
import com.example.pscmaster.data.local.PerformanceDao
import com.example.pscmaster.data.local.QuestionDao
import com.example.pscmaster.data.local.SubjectCount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class PSCRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val questionDao: QuestionDao,
    private val performanceDao: PerformanceDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : PSCRepository {

    private val dbName = "psc_master_db"
    private val secureRandom = SecureRandom()
    private val prefs = context.getSharedPreferences("ai_cache_prefs", Context.MODE_PRIVATE)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncListenerRegistration: ListenerRegistration? = null

    private val srsIntervals = listOf(1L, 3L, 7L, 14L, 30L, 60L, 120L)

    override suspend fun addQuestion(question: Question): Boolean {
        val id = questionDao.insertQuestion(question)
        if (id != -1L) {
            pushQuestionToFirebase(question)
        }
        return id != -1L
    }

    private fun pushQuestionToFirebase(question: Question) {
        repositoryScope.launch {
            try {
                if (auth.currentUser != null) {
                    val docId = question.questionText.toStableId()
                    firestore.collection("shared_questions").document(docId)
                        .set(question, SetOptions.merge())
                }
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Auto-push failed", e)
            }
        }
    }

    override suspend fun updateQuestion(question: Question) {
        questionDao.updateQuestion(question)
        pushQuestionToFirebase(question)
    }

    override fun getAllQuestions(): Flow<List<Question>> {
        return questionDao.getAllQuestions()
    }

    override suspend fun deleteQuestion(question: Question) {
        questionDao.deleteQuestion(question)
        repositoryScope.launch {
            try {
                val docId = question.questionText.toStableId()
                firestore.collection("shared_questions").document(docId).delete()
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Auto-delete failed", e)
            }
        }
    }

    override suspend fun deleteQuestions(questions: List<Question>) {
        questionDao.deleteQuestions(questions)
        repositoryScope.launch {
            try {
                val batch = firestore.batch()
                questions.forEach { question ->
                    val docId = question.questionText.toStableId()
                    val docRef = firestore.collection("shared_questions").document(docId)
                    batch.delete(docRef)
                }
                batch.commit().await()
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Bulk delete failed", e)
            }
        }
    }

    override fun getAllSubjects(): Flow<List<String>> {
        return questionDao.getAllSubjects()
    }

    override fun getQuestionCount(): Flow<Int> {
        return questionDao.getQuestionCount()
    }

    override suspend fun savePerformance(performance: UserPerformance) {
        performanceDao.insertPerformance(performance)
        updateSrs(performance.questionId, performance.isCorrect)
    }

    override suspend fun updateSrs(questionId: Long, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val question = questionDao.getQuestionById(questionId) ?: return@withContext
        
        val newIntervalIndex: Int
        val nextReviewTimestamp: Long

        if (isCorrect) {
            newIntervalIndex = (question.intervalIndex + 1).coerceAtMost(srsIntervals.size - 1)
            val daysToAdd = srsIntervals[newIntervalIndex]
            nextReviewTimestamp = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(daysToAdd)
        } else {
            newIntervalIndex = 0
            nextReviewTimestamp = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1)
        }

        val updatedQuestion = question.copy(
            intervalIndex = newIntervalIndex,
            nextReviewTimestamp = nextReviewTimestamp
        )
        questionDao.updateQuestion(updatedQuestion)
        pushQuestionToFirebase(updatedQuestion)
    }

    override fun getAllPerformance(): Flow<List<UserPerformance>> {
        return performanceDao.getAllPerformance()
    }

    override suspend fun getWrongAnswers(): List<UserPerformance> {
        return performanceDao.getWrongAnswers()
    }

    override fun getWeakSubjects(): Flow<List<SubjectCount>> {
        return performanceDao.getWeakSubjects()
    }

    override suspend fun getCachedInsights(hash: Int): AiResult? = withContext(Dispatchers.IO) {
        val savedHash = prefs.getInt("cached_hash", 0)
        if (savedHash == hash) {
            val insights = prefs.getString("cached_insights", null)
            val provider = prefs.getString("cached_provider", null)
            if (insights != null && provider != null) {
                return@withContext AiResult(insights, provider)
            }
        }
        null
    }

    override suspend fun saveCachedInsights(hash: Int, result: AiResult) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putInt("cached_hash", hash)
            .putString("cached_insights", result.insights)
            .putString("cached_provider", result.provider)
            .apply()
    }

    override suspend fun importQuestionsFromCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val questionsToInsert = mutableListOf<Question>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream), 8 * 1024).use { reader ->
                    // Skip header if it exists
                    var line = reader.readLine()
                    if (line != null && line.contains("subject", ignoreCase = true)) {
                        line = reader.readLine()
                    }

                    val regex = ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()
                    while (line != null) {
                        if (line.isNotBlank()) {
                            val parts = line.split(regex).map { it.replace("^\"|\"$".toRegex(), "").trim() }
                            
                            if (parts.size >= 7) {
                                questionsToInsert.add(
                                    Question(
                                        subject = parts[0],
                                        questionText = parts[1],
                                        options = listOf(parts[2], parts[3], parts[4], parts[5]),
                                        correctIndex = (parts[6].toIntOrNull() ?: 0).coerceIn(0, 3)
                                    )
                                )
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }

            if (questionsToInsert.isNotEmpty()) {
                val results = questionDao.bulkInsert(questionsToInsert)
                val addedCount = results.count { it != -1L }
                
                // Trigger a full sync in background if any were added
                if (addedCount > 0) {
                    repositoryScope.launch { 
                        syncToFirebase()
                    }
                }
                return@withContext addedCount
            }
        } catch (e: Exception) {
            Log.e("CSVImport", "Bulk import failed: ${e.message}", e)
        }
        0
    }

    override suspend fun renameSubject(oldName: String, newName: String) {
        questionDao.renameSubject(oldName, newName)
    }

    override fun closeDatabase() {
        // Cancel all ongoing coroutines
        repositoryScope.coroutineContext.cancelChildren()
        // Remove Firebase listener
        syncListenerRegistration?.remove()
        syncListenerRegistration = null
        if (database.isOpen) {
            database.close()
        }
    }

    override suspend fun getQuestionsForPractice(subjects: List<String>, shuffle: Boolean): List<Question> {
        val allMatchingQuestions = if (subjects.isEmpty()) {
            questionDao.getAllQuestionsList()
        } else {
            questionDao.getQuestionsBySubjects(subjects)
        }

        if (!shuffle || allMatchingQuestions.isEmpty()) return allMatchingQuestions

        val wrongAnswers = performanceDao.getWrongAnswers()
        val wrongIds = wrongAnswers.map { it.questionId }.toSet()

        val (priorityQuestions, normalQuestions) = allMatchingQuestions.partition { 
            wrongIds.contains(it.id) 
        }

        val shuffledPriority = priorityQuestions.shuffled(secureRandom)
        val shuffledNormal = normalQuestions.shuffled(secureRandom)

        val interleavedPriority = interleaveBySubject(shuffledPriority)
        val interleavedNormal = interleaveBySubject(shuffledNormal)

        return interleavedPriority + interleavedNormal
    }

    override suspend fun getRevisionQuestions(): List<Question> {
        val questions = questionDao.getScheduledRevisionQuestions(System.currentTimeMillis()).toMutableList()
        if (questions.size < 10) {
            val needed = 10 - questions.size
            questions.addAll(questionDao.getNewQuestions(needed))
        }
        return interleaveBySubject(questions.shuffled(secureRandom))
    }

    private fun interleaveBySubject(questions: List<Question>): List<Question> {
        if (questions.size <= 1) return questions
        
        val grouped = questions.groupBy { it.subject }.values.map { it.toMutableList() }
        val result = mutableListOf<Question>()
        
        var hasMore = true
        while (hasMore) {
            hasMore = false
            for (group in grouped) {
                if (group.isNotEmpty()) {
                    result.add(group.removeAt(0))
                    hasMore = true
                }
            }
        }
        return result
    }

    override fun getDatabaseFile(): File {
        return context.getDatabasePath(dbName)
    }

    override fun getStorageInfo(): StorageInfo {
        val dbFile = getDatabaseFile()
        val dbSizeBytes = if (dbFile.exists()) dbFile.length() else 0L
        
        val stat = StatFs(Environment.getDataDirectory().path)
        val availableSpaceBytes = stat.blockSizeLong * stat.availableBlocksLong
        
        return StorageInfo(
            path = dbFile.absolutePath,
            sizeBytes = dbSizeBytes,
            availableSpaceBytes = availableSpaceBytes
        )
    }

    override suspend fun exportDatabaseToCache(): File? = withContext(Dispatchers.IO) {
        val dbFile = getDatabaseFile()
        if (!dbFile.exists()) return@withContext null

        try {
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)")

            val backupFile = File(context.cacheDir, "psc_backup.db")
            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }
            backupFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun importDatabaseFromUri(uri: Uri): Unit = withContext(Dispatchers.IO) {
        try {
            closeDatabase()
            val dbFile = getDatabaseFile()
            // Delete WAL and SHM journal files for a clean import
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            // Note: Room singleton is now closed. The app must be restarted
            // to reinitialize the database connection.
        } catch (e: Exception) {
            Log.e("PSCRepository", "Database import failed", e)
        }
    }

    private fun String.toStableId(): String {
        return MessageDigest.getInstance("MD5")
            .digest(this.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    override suspend fun syncToFirebase(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                return@withContext Result.failure(Exception("User not logged in"))
            }

            withTimeout(60000L) {
                val localQuestions = questionDao.getAllQuestionsList()
                if (localQuestions.isNotEmpty()) {
                    val chunks = localQuestions.chunked(400)
                    chunks.forEach { chunk ->
                        val batch = firestore.batch()
                        chunk.forEach { question ->
                            val docId = question.questionText.toStableId()
                            val docRef = firestore.collection("shared_questions").document(docId)
                            batch.set(docRef, question, SetOptions.merge())
                        }
                        batch.commit().await()
                    }
                }

                val remoteQuestions = firestore.collection("shared_questions").get().await()
                remoteQuestions.documents.forEach { doc ->
                    val question = doc.toObject(Question::class.java)
                    if (question != null) {
                        questionDao.insertQuestion(question.copy(id = 0))
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun startRealtimeSync() {
        // Remove any existing listener before adding a new one
        syncListenerRegistration?.remove()
        syncListenerRegistration = firestore.collection("shared_questions")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("FirebaseSync", "Listen failed.", e)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { dc ->
                    repositoryScope.launch {
                        try {
                            val question = dc.document.toObject(Question::class.java)
                            when (dc.type) {
                                com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                                    if (question != null) {
                                        questionDao.insertQuestion(question.copy(id = 0))
                                    }
                                }
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    if (question != null) {
                                        val local = questionDao.getQuestionByText(question.questionText)
                                        if (local != null) {
                                            questionDao.updateQuestion(local.copy(
                                                options = question.options,
                                                correctIndex = question.correctIndex,
                                                subject = question.subject
                                            ))
                                        } else {
                                            questionDao.insertQuestion(question.copy(id = 0))
                                        }
                                    }
                                }
                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                    if (question != null) {
                                        val local = questionDao.getQuestionByText(question.questionText)
                                        if (local != null) questionDao.deleteQuestion(local)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("FirebaseSync", "Real-time update failed", e)
                        }
                    }
                }
            }
    }
    override fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
}
