package com.example.pscmaster.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pscmaster.data.entity.*
import com.example.pscmaster.data.repository.PSCRepository
import com.example.pscmaster.data.local.SubjectCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val questionsFlow = repository.getAllQuestions().map { repository.getAllQuestionsWithMetadata() }

    val uiState = combine(
        _uiState,
        repository.getQuestionCount(),
        repository.getAllSubjects(),
        repository.getWeakSubjects(),
        combine(repository.getWeeklyProgress(), questionsFlow) { a, b -> Pair(a, b) }
    ) { state, count, subjects, weak, pair ->
        state.copy(
            totalQuestionsCount = count,
            availableSubjects = (setOf("AI POOL") + subjects.toSet()).toList(), // Safeguard
            weakSubjects = weak,
            mistakesCount = weak.sumOf { it.count },
            weeklyStats = pair.first,
            recentQuestions = pair.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InputUiState()
    )

    private val gson = Gson()

    init {
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

    fun updateExplanation(explanation: String) {
        _uiState.value = _uiState.value.copy(explanation = explanation)
    }

    fun saveQuestion() {
        val state = _uiState.value
        if (state.questionText.isBlank() || state.options.any { it.isBlank() } || state.subject.isBlank()) {
            return
        }

        viewModelScope.launch {
            val question = Question(
                id = state.editingQuestionId ?: 0L,
                subject = state.subject,
                questionText = state.questionText.trim(),
                options = state.options.map { it.trim() },
                correctIndex = state.correctIndex,
                explanation = state.explanation.trim(),
                timestamp = System.currentTimeMillis()
            )
            
            val success = if (state.editingQuestionId != null) {
                repository.updateQuestion(question)
                true
            } else {
                repository.addQuestion(question)
            }

            if (success) {
                _uiState.value = _uiState.value.copy(
                    questionText = "",
                    options = listOf("", "", "", ""),
                    correctIndex = 0,
                    explanation = "",
                    editingQuestionId = null,
                    infoMessage = if (state.editingQuestionId != null) "Question Updated!" else "Question Saved!"
                )
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Question already exists!")
            }
        }
    }

    fun startEditing(item: QuestionWithMetadata) {
        val q = item.question
        _uiState.value = _uiState.value.copy(
            editingQuestionId = q.id,
            questionText = q.questionText,
            options = q.options,
            correctIndex = q.correctIndex,
            explanation = q.explanation,
            subject = q.subject
        )
    }

    fun cancelEditing() {
        _uiState.value = _uiState.value.copy(
            editingQuestionId = null,
            questionText = "",
            options = listOf("", "", "", ""),
            correctIndex = 0,
            explanation = "",
            subject = ""
        )
    }

    fun deleteQuestion(question: Question) {
        viewModelScope.launch {
            repository.deleteQuestion(question)
        }
    }

    fun deleteQuestions(questions: List<Question>) {
        if (questions.isEmpty()) return
        viewModelScope.launch {
            repository.deleteQuestions(questions)
            _uiState.value = _uiState.value.copy(infoMessage = "Deleted ${questions.size} questions")
        }
    }

    fun updateStorageInfo() {
        val info = repository.getStorageInfo()
        _uiState.value = _uiState.value.copy(
            dbSize = info.first,
            availableSpace = info.second
        )
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
        if (indices.isEmpty()) return
        
        val questions = _uiState.value.generatedQuestions
        val originalSubject = _uiState.value.subject
        val aiPoolSubject = "AI POOL (${originalSubject.ifBlank { "Misc" }})"
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSyncing = true) // Show a loader
                var addedCount = 0
                
                indices.forEach { index ->
                    if (index in questions.indices) {
                        val gen = questions[index]
                        val rawOptions = gen.options
                        val sortedOptions = listOf(
                            rawOptions["A"] ?: "",
                            rawOptions["B"] ?: "",
                            rawOptions["C"] ?: "",
                            rawOptions["D"] ?: ""
                        ).map { it.trim() }
                        
                        val mappedCorrectIndex = when(gen.correct_option) {
                            "A" -> 0
                            "B" -> 1
                            "C" -> 2
                            "D" -> 3
                            else -> 0
                        }
                        
                        val q = Question(
                            subject = "AI POOL",
                            subjectTag = gen.subject_tag,
                            questionText = gen.question.trim(),
                            options = sortedOptions,
                            correctIndex = mappedCorrectIndex,
                            explanation = gen.explanation.trim(),
                            timestamp = System.currentTimeMillis()
                        )
                        
                        val success = repository.addQuestion(q)
                        if (success) addedCount++
                    }
                }
                
                val skipped = indices.size - addedCount
                val message = if (skipped > 0) {
                    "$addedCount new questions added ($skipped skipped as duplicates)"
                } else {
                    "$addedCount questions added!"
                }
                
                _uiState.value = _uiState.value.copy(
                    infoMessage = message,
                    isSyncing = false
                )
                discardGeneratedQuestions()
            } catch (e: Exception) {
                Log.e("InputViewModel", "Error saving questions", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to save questions: ${e.localizedMessage}",
                    isSyncing = false
                )
            }
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
    val options: Map<String, String>,
    val correct_option: String,
    val explanation: String = "",
    val subject_tag: String = "General Knowledge"
)

data class InputUiState(
    val questionText: String = "",
    val options: List<String> = listOf("", "", "", ""),
    val correctIndex: Int = 0,
    val explanation: String = "",
    val subject: String = "",
    val availableSubjects: List<String> = emptyList(),
    val weakSubjects: List<SubjectCount> = emptyList(),
    val weeklyStats: com.example.pscmaster.data.local.WeeklyStats = com.example.pscmaster.data.local.WeeklyStats(0, 0),
    val recentQuestions: List<QuestionWithMetadata> = emptyList(),
    val totalQuestionsCount: Int = 0,
    val mistakesCount: Int = 0,
    val isGenerating: Boolean = false,
    val generatedQuestions: List<GeneratedQuestion> = emptyList(),
    val showGenerationDialog: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val isSyncing: Boolean = false,
    val userEmail: String = "",
    val needsRestart: Boolean = false,
    val dbSize: String = "0 KB",
    val availableSpace: String = "0 MB",
    val editingQuestionId: Long? = null
)
