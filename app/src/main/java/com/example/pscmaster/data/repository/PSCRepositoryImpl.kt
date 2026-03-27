package com.example.pscmaster.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.example.pscmaster.api.AiResult
import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.data.entity.UserPerformance
import com.example.pscmaster.data.local.AppDatabase
import com.example.pscmaster.data.local.PerformanceDao
import com.example.pscmaster.data.local.QuestionDao
import com.example.pscmaster.data.local.SessionDao
import com.example.pscmaster.data.local.PerformanceMetricsDao
import com.example.pscmaster.data.local.SubjectCount
import com.example.pscmaster.data.entity.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class PSCRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val questionDao: QuestionDao,
    private val performanceDao: PerformanceDao,
    private val sessionDao: SessionDao,
    private val metricsDao: PerformanceMetricsDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : PSCRepository {

    private val dbName = "psc_master_db"
    private val secureRandom = SecureRandom()
    private val prefs = context.getSharedPreferences("ai_cache_prefs", Context.MODE_PRIVATE)
    private val syncPrefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncListenerRegistration: ListenerRegistration? = null

    private val srsIntervals = listOf(1L, 3L, 7L, 14L, 30L, 60L, 120L)

    override suspend fun addQuestion(question: Question): Boolean {
        val id = questionDao.insertQuestion(question)
        if (id != -1L) {
            sessionDao.upsertBadgeState(QuestionBadgeState(questionId = id, state = QuestionBadgeState.STATE_UNSEEN))
            pushQuestionToFirebase(question.copy(id = id))
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

    override suspend fun getAllQuestionsWithMetadata(): List<QuestionWithMetadata> = withContext(Dispatchers.IO) {
        questionDao.getAllQuestionsWithMetadata()
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
    
    override suspend fun startQuizSession(subject: String?): QuizSession = withContext(Dispatchers.IO) {
        val session = QuizSession(subjectFilter = subject)
        sessionDao.insertSession(session)
        session
    }

    override fun observeBadgeState(questionId: Long): Flow<Int?> {
        return sessionDao.observeBadgeState(questionId)
    }

    override suspend fun finishQuizSession(sessionId: String, performances: List<UserPerformance>) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val session = sessionDao.getSessionById(sessionId) ?: return@withTransaction
            
            // 1. Mark session as completed
            sessionDao.updateSession(session.copy(
                isCompleted = true,
                endTime = System.currentTimeMillis()
            ))

            // 2. Update Badge States to REMOVED (3) for questions in this session
            val badgeStates = performances.map { 
                QuestionBadgeState(questionId = it.questionId, state = QuestionBadgeState.STATE_BADGE_REMOVED) 
            }
            sessionDao.bulkUpsertBadgeState(badgeStates)

            // 3. Update Performance Metrics
            performances.forEach { perf ->
                val currentMetrics = metricsDao.getMetricsForQuestion(perf.questionId)
                    ?: UserPerformanceMetrics(questionId = perf.questionId)
                
                val totalAttempts = currentMetrics.totalAttempts + 1
                val correctAttempts = if (perf.isCorrect) currentMetrics.correctAttempts + 1 else currentMetrics.correctAttempts
                val consecutiveCorrect = if (perf.isCorrect) currentMetrics.consecutiveCorrect + 1 else 0
                
                metricsDao.upsertMetrics(currentMetrics.copy(
                    totalAttempts = totalAttempts,
                    correctAttempts = correctAttempts,
                    lastAttemptTimestamp = System.currentTimeMillis(),
                    consecutiveCorrect = consecutiveCorrect
                ))

                // Also update the legacy SRS system
                updateSrs(perf.questionId, perf.isCorrect)
                
                // Save to legacy performance table
                performanceDao.insertPerformance(perf)
            }
        }
    }

    override suspend fun generateAdaptiveQuiz(size: Int, subject: String?, newQuestionRatio: Float): List<Question> = withContext(Dispatchers.IO) {
        val newCount = (size * newQuestionRatio).toInt()
        val repeatCount = size - newCount

        val candidates = if (subject == null) {
            questionDao.getNewCandidateQuestions(newCount)
        } else {
            questionDao.getQuestionsWithMetadataBySubject(subject)
                .filter { (it.badgeState?.state ?: 0) < 3 }
                .shuffled()
                .take(newCount)
        }

        val newOnes = candidates.map { it.question }
        
        val repeats = if (subject == null) {
            questionDao.getAllQuestionsList()
                .filter { q -> !newOnes.any { it.id == q.id } }
                .shuffled()
                .take(repeatCount)
        } else {
            questionDao.getQuestionsBySubjects(listOf(subject))
                .filter { q -> !newOnes.any { it.id == q.id } }
                .shuffled()
                .take(repeatCount)
        }

        (newOnes + repeats).shuffled()
    }

    override suspend fun importQuestionsFromCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val questionsToInsert = mutableListOf<Question>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                csvReader().readAll(inputStream).forEachIndexed { index, row ->
                    if (index == 0 && row.any { it.contains("subject", ignoreCase = true) }) {
                        return@forEachIndexed
                    }
                    
                    if (row.size >= 7) {
                        questionsToInsert.add(
                            Question(
                                subject = row[0].trim(),
                                questionText = row[1].trim(),
                                options = listOf(row[2].trim(), row[3].trim(), row[4].trim(), row[5].trim()),
                                correctIndex = (row[6].trim().toIntOrNull() ?: 0).coerceIn(0, 3),
                                explanation = if (row.size >= 8) row[7].trim() else ""
                            )
                        )
                    }
                }
            }

            if (questionsToInsert.isNotEmpty()) {
                val results = questionDao.bulkInsert(questionsToInsert)
                val addedCount = results.count { it != -1L }
                
                val newBadgeStates = results.filter { it != -1L }.map { id ->
                    QuestionBadgeState(questionId = id, state = QuestionBadgeState.STATE_UNSEEN)
                }
                sessionDao.bulkUpsertBadgeState(newBadgeStates)

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
        repositoryScope.coroutineContext.cancelChildren()
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
        // Implement logic or use existing Dao method
        val questions = questionDao.getScheduledRevisionQuestions(System.currentTimeMillis()).toMutableList()
        // If not enough revision questions, add some new ones (simplified logic)
        return interleaveBySubject(questions.shuffled(secureRandom))
    }

    private fun interleaveBySubject(questions: List<Question>): List<Question> {
        if (questions.size <= 1) return questions
        val grouped = questions.groupBy { it.subject }
            .values
            .map { java.util.LinkedList(it) }
        
        val result = ArrayList<Question>(questions.size)
        var hasMore = true
        while (hasMore) {
            hasMore = false
            for (group in grouped) {
                if (group.isNotEmpty()) {
                    result.add(group.removeFirst())
                    hasMore = true
                }
            }
        }
        return result
    }

    override fun getDatabaseFile(): File {
        return context.getDatabasePath(dbName)
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
            delay(200) 
            val dbFile = getDatabaseFile()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            syncPrefs.edit().putLong("last_sync_timestamp", 0L).apply()
        } catch (e: Exception) {
            Log.e("PSCRepository", "Database import failed", e)
        }
    }

    override fun getStorageInfo(): Pair<String, String> {
        val dbFile = getDatabaseFile()
        val dbSize = if (dbFile.exists()) {
            val size = dbFile.length()
            if (size < 1024 * 1024) "${size / 1024} KB"
            else String.format(Locale.US, "%.2f MB", size.toDouble() / (1024 * 1024))
        } else "0 KB"

        val freeSpace = context.filesDir.usableSpace
        val freeSpaceStr = if (freeSpace < 1024 * 1024 * 1024) "${freeSpace / (1024 * 1024)} MB"
        else String.format(Locale.US, "%.2f GB", freeSpace.toDouble() / (1024 * 1024 * 1024))

        return Pair(dbSize, freeSpaceStr)
    }

    private fun String.toStableId(): String {
        return MessageDigest.getInstance("MD5")
            .digest(this.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    override suspend fun syncToFirebase(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.currentUser?.uid ?: return@withContext Result.failure(Exception("User not logged in"))

            withTimeout(60000L) {
                val lastSync = syncPrefs.getLong("last_sync_timestamp", 0L)
                val localQuestions = questionDao.getAllQuestionsList().filter { it.timestamp > lastSync }
                
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

                val remoteQuery = firestore.collection("shared_questions")
                    .whereGreaterThan("timestamp", lastSync)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                
                val remoteQuestions = remoteQuery.get().await()
                remoteQuestions.documents.forEach { doc ->
                    val question = doc.toObject(Question::class.java)
                    if (question != null) {
                        val existing = questionDao.getQuestionByText(question.questionText)
                        if (existing == null) {
                            questionDao.insertQuestion(question.copy(id = 0))
                        } else if (question.timestamp > existing.timestamp) {
                            questionDao.updateQuestion(question.copy(id = existing.id))
                        }
                    }
                }
                
                syncPrefs.edit().putLong("last_sync_timestamp", System.currentTimeMillis()).apply()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Sync failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun startRealtimeSync() {
        syncListenerRegistration?.remove()
        
        val lastSync = syncPrefs.getLong("last_sync_timestamp", 0L)
        syncListenerRegistration = firestore.collection("shared_questions")
            .whereGreaterThan("timestamp", lastSync)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("FirebaseSync", "Listen failed.", e)
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { dc ->
                    repositoryScope.launch {
                        try {
                            val question = dc.document.toObject(Question::class.java) ?: return@launch
                            when (dc.type) {
                                com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                                    questionDao.insertQuestion(question.copy(id = 0))
                                }
                                com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                                    val local = questionDao.getQuestionByText(question.questionText)
                                    if (local != null) {
                                        if (question.timestamp > local.timestamp) {
                                            questionDao.updateQuestion(local.copy(
                                                options = question.options,
                                                correctIndex = question.correctIndex,
                                                explanation = question.explanation,
                                                subject = question.subject,
                                                timestamp = question.timestamp
                                            ))
                                        }
                                    } else {
                                        questionDao.insertQuestion(question.copy(id = 0))
                                    }
                                }
                                com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                                    val local = questionDao.getQuestionByText(question.questionText)
                                    if (local != null) questionDao.deleteQuestion(local)
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
