package com.example.pscmaster.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.data.repository.PSCRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.pscmaster.api.AiService
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class InputViewModel @Inject constructor(
    private val repository: PSCRepository,
    private val aiService: AiService,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(InputUiState())
    val uiState = _uiState.asStateFlow()

    private val gson = Gson()

    init {
        // Lightweight count for HomeScreen stats
        viewModelScope.launch {
            repository.getQuestionCount().collectLatest { count ->
                _uiState.value = _uiState.value.copy(totalQuestionsCount = count)
            }
        }
        // Full question list for ManageQuestionsScreen
        viewModelScope.launch {
            repository.getAllQuestions().collectLatest { questions ->
                _uiState.value = _uiState.value.copy(recentQuestions = questions)
            }
        }
        viewModelScope.launch {
            repository.getAllSubjects().collectLatest { subjects ->
                _uiState.value = _uiState.value.copy(availableSubjects = subjects)
            }
        }
        updateStorageInfo()
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        val user = auth.currentUser
        _uiState.value = _uiState.value.copy(
            isLoggedIn = user != null,
            userEmail = user?.email ?: user?.uid ?: "Guest"
        )
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSyncing = true)
                auth.signInAnonymously().await()
                checkAuthStatus()
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    infoMessage = "Logged in successfully!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    errorMessage = "Login failed: ${e.message}"
                )
            }
        }
    }

    fun syncToFirebase() {
        if (!repository.isNetworkAvailable()) {
            _uiState.value = _uiState.value.copy(errorMessage = "No internet connection. Please check your network and try again.")
            return
        }

        if (auth.currentUser == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please login first")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            repository.syncToFirebase()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        infoMessage = "Sync completed successfully!"
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        errorMessage = "Sync failed: ${e.message}"
                    )
                }
        }
    }

    fun updateStorageInfo() {
        val info = repository.getStorageInfo()
        _uiState.value = _uiState.value.copy(
            dbPath = info.path,
            dbSize = formatFileSize(info.sizeBytes),
            dbSizeBytes = info.sizeBytes,
            availableSpace = formatFileSize(info.availableSpaceBytes)
        )
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun updateQuestionText(text: String) {
        _uiState.value = _uiState.value.copy(questionText = text)
    }

    fun updateOption(index: Int, text: String) {
        val newOptions = _uiState.value.options.toMutableList()
        if (index < newOptions.size) {
            newOptions[index] = text
            _uiState.value = _uiState.value.copy(options = newOptions)
        }
    }

    fun updateCorrectIndex(index: Int) {
        _uiState.value = _uiState.value.copy(correctIndex = index)
    }

    fun updateSubject(subject: String) {
        _uiState.value = _uiState.value.copy(subject = subject)
    }

    fun saveQuestion() {
        val state = _uiState.value
        if (state.questionText.isBlank() || state.options.any { it.isBlank() } || state.subject.isBlank()) {
            return
        }

        viewModelScope.launch {
            val question = Question(
                subject = state.subject,
                questionText = state.questionText.trim(),
                options = state.options.map { it.trim() },
                correctIndex = state.correctIndex
            )
            val success = repository.addQuestion(question)
            if (success) {
                _uiState.value = _uiState.value.copy(
                    questionText = "",
                    options = listOf("", "", "", ""),
                    correctIndex = 0,
                    infoMessage = "Question Saved!"
                )
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Question already exists!")
            }
            updateStorageInfo()
        }
    }

    fun deleteQuestion(question: Question) {
        viewModelScope.launch {
            repository.deleteQuestion(question)
            updateStorageInfo()
        }
    }

    fun deleteQuestions(questions: List<Question>) {
        if (questions.isEmpty()) return
        viewModelScope.launch {
            repository.deleteQuestions(questions)
            _uiState.value = _uiState.value.copy(infoMessage = "Deleted ${questions.size} questions")
            updateStorageInfo()
        }
    }



    fun onExportRequested(context: Context, onUriReady: (Uri) -> Unit) {
        viewModelScope.launch {
            try {
                val backupFile = repository.exportDatabaseToCache()
                if (backupFile != null) {
                    val uri = FileProvider.getUriForFile(
                        context, 
                        "${context.packageName}.fileprovider", 
                        backupFile
                    )
                    onUriReady(uri)
                    _uiState.value = _uiState.value.copy(infoMessage = "Database exported successfully!")
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "Export failed: Could not create backup file.")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Export failed: ${e.message}")
            }
        }
    }

    fun importDatabase(uri: Uri) {
        viewModelScope.launch {
            repository.importDatabaseFromUri(uri)
            _uiState.value = _uiState.value.copy(
                infoMessage = "Database imported! Restarting app...",
                needsRestart = true
            )
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            val count = repository.importQuestionsFromCsv(uri)
            _uiState.value = _uiState.value.copy(infoMessage = "Imported $count new questions")
            updateStorageInfo()
        }
    }

    fun renameSubject(oldName: String, newName: String) {
        viewModelScope.launch {
            repository.renameSubject(oldName, newName)
        }
    }

    fun generateAiQuestions() {
        val subject = _uiState.value.subject
        if (subject.isBlank()) return

        if (!repository.isNetworkAvailable()) {
            _uiState.value = _uiState.value.copy(errorMessage = "No internet connection. AI generation requires an active network.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, errorMessage = null)
            
            try {
                val result = aiService.generateNewQuestions(subject, 5)
                
                if (result.provider != "Error") {
                    val rawJson = result.insights
                    val jsonStart = rawJson.indexOf('[')
                    val jsonEnd = rawJson.lastIndexOf(']')
                    
                    if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                        val json = rawJson.substring(jsonStart, jsonEnd + 1)
                        val type = object : TypeToken<List<GeneratedQuestion>>() {}.type
                        val generated: List<GeneratedQuestion> = gson.fromJson(json, type)
                        
                        if (generated.isNullOrEmpty()) {
                            _uiState.value = _uiState.value.copy(errorMessage = "AI returned no questions.")
                        } else {
                            _uiState.value = _uiState.value.copy(
                                generatedQuestions = generated,
                                showGenerationDialog = true
                            )
                        }
                    } else {
                        Log.e("InputViewModel", "Invalid JSON from AI: $rawJson")
                        _uiState.value = _uiState.value.copy(errorMessage = "AI response was not in a recognized format.")
                    }
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = result.insights)
                }
            } catch (e: Exception) {
                Log.e("InputViewModel", "Generation failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "An unexpected error occurred: ${e.localizedMessage}")
            } finally {
                _uiState.value = _uiState.value.copy(isGenerating = false)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearInfo() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    fun saveSelectedQuestions(indices: Set<Int>) {
        val questions = _uiState.value.generatedQuestions
        val subject = _uiState.value.subject
        
        viewModelScope.launch {
            var addedCount = 0
            indices.forEach { index ->
                if (index in questions.indices) {
                    val gen = questions[index]
                    val safeOptions = gen.options.map { it.trim() }
                    val success = repository.addQuestion(
                        Question(
                            subject = subject,
                            questionText = gen.question.trim(),
                            options = safeOptions,
                            correctIndex = gen.correctIndex.coerceIn(0, (safeOptions.size - 1).coerceAtLeast(0))
                        )
                    )
                    if (success) addedCount++
                }
            }
            
            val skipped = indices.size - addedCount
            val message = if (skipped > 0) {
                "$addedCount new questions added ($skipped skipped as duplicates)"
            } else {
                "$addedCount questions added!"
            }
            
            _uiState.value = _uiState.value.copy(infoMessage = message)
            discardGeneratedQuestions()
            updateStorageInfo()
        }
    }

    fun discardGeneratedQuestions() {
        _uiState.value = _uiState.value.copy(
            generatedQuestions = emptyList(),
            showGenerationDialog = false
        )
    }
}

data class GeneratedQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int
)

data class InputUiState(
    val questionText: String = "",
    val options: List<String> = listOf("", "", "", ""),
    val correctIndex: Int = 0,
    val subject: String = "",
    val availableSubjects: List<String> = emptyList(),
    val recentQuestions: List<Question> = emptyList(),
    val dbPath: String = "",
    val dbSize: String = "",
    val dbSizeBytes: Long = 0L,
    val availableSpace: String = "",
    val totalQuestionsCount: Int = 0,
    val isGenerating: Boolean = false,
    val generatedQuestions: List<GeneratedQuestion> = emptyList(),
    val showGenerationDialog: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val isSyncing: Boolean = false,
    val userEmail: String = "",
    val needsRestart: Boolean = false
)
