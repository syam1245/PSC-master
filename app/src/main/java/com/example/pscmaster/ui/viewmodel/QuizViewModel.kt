package com.example.pscmaster.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.data.entity.UserPerformance
import com.example.pscmaster.data.repository.PSCRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.example.pscmaster.domain.usecase.GenerateAdaptiveQuizUseCase

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val repository: PSCRepository,
    private val aiService: com.example.pscmaster.api.AiService,
    private val generateAdaptiveQuizUseCase: GenerateAdaptiveQuizUseCase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState = _uiState.asStateFlow()

    private val _configState = MutableStateFlow(PracticeConfig())
    val configState = _configState.asStateFlow()

    private var originalQuestions: List<Question> = emptyList()
    private val sessionPerformances = mutableListOf<UserPerformance>()

    private val prefs = context.getSharedPreferences("active_quiz_session", android.content.Context.MODE_PRIVATE)

    init {
        viewModelScope.launch {
            repository.getAllSubjects().collect { subjects ->
                val hasSession = prefs.getString("sessionId", null) != null
                _configState.value = _configState.value.copy(availableSubjects = subjects, hasActiveSession = hasSession)
            }
        }
    }

    private fun persistSessionState() {
        val state = _uiState.value
        if (state.sessionId == null || state.isQuizFinished) {
            prefs.edit().clear().apply()
            return
        }
        val qIds = state.questions.joinToString(",") { it.id.toString() }
        val answered = state.answeredIndices.entries.joinToString(",") { "${it.key}:${it.value}" }
        val skipped = state.skippedIndices.joinToString(",")
        prefs.edit()
            .putString("sessionId", state.sessionId)
            .putString("qIds", qIds)
            .putString("answered", answered)
            .putString("skipped", skipped)
            .putInt("score", state.score)
            .putInt("currentIndex", state.currentQuestionIndex)
            .apply()
    }

    fun onToggleShuffle(enabled: Boolean) {
        _configState.value = _configState.value.copy(isShuffleEnabled = enabled)
    }

    fun onToggleSubject(subject: String) {
        val current = _configState.value.selectedSubjects.toMutableSet()
        if (current.contains(subject)) current.remove(subject) else current.add(subject)
        _configState.value = _configState.value.copy(selectedSubjects = current.toList())
    }

    fun onToggleRevision(enabled: Boolean) {
        _configState.value = _configState.value.copy(
            isRevisionMode = enabled,
            isAdaptiveMode = if (enabled) false else _configState.value.isAdaptiveMode
        )
    }

    fun onToggleAiVariation(enabled: Boolean) {
        _configState.value = _configState.value.copy(isAiVariationEnabled = enabled)
    }

    fun onToggleAdaptiveMode(enabled: Boolean) {
        _configState.value = _configState.value.copy(
            isAdaptiveMode = enabled,
            isRevisionMode = if (enabled) false else _configState.value.isRevisionMode
        )
    }

    fun updateCurrentPage(index: Int) {
        if (_uiState.value.currentQuestionIndex != index) {
            _uiState.value = _uiState.value.copy(currentQuestionIndex = index)
            persistSessionState()
        }
    }

    fun generateAiVariation(questionIndex: Int) {
        val currentQuestions = _uiState.value.questions
        val question = currentQuestions.getOrNull(questionIndex) ?: return
        
        if (_uiState.value.aiVariations.containsKey(questionIndex)) return
        if (_uiState.value.loadingVariations.contains(questionIndex)) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    loadingVariations = _uiState.value.loadingVariations + questionIndex
                )

                val result = aiService.generateVariation(
                    question = question.questionText,
                    options = question.options,
                    correctIndex = question.correctIndex,
                    subject = question.subject
                )

                if (result.provider != "Error") {
                    _uiState.value = _uiState.value.copy(
                        aiVariations = _uiState.value.aiVariations + (questionIndex to result.insights)
                    )
                } else {
                    // Fail silently or store error message
                    _uiState.value = _uiState.value.copy(
                        aiVariations = _uiState.value.aiVariations + (questionIndex to "Could not generate variation: ${result.insights}")
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    aiVariations = _uiState.value.aiVariations + (questionIndex to "Error: ${e.localizedMessage}")
                )
            } finally {
                _uiState.value = _uiState.value.copy(
                    loadingVariations = _uiState.value.loadingVariations - questionIndex
                )
            }
        }
    }

    fun startPractice() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, isConfiguring = false)
            
            val baseQuestions = if (_configState.value.isAdaptiveMode) {
                generateAdaptiveQuizUseCase(
                    size = 10,
                    subject = if (_configState.value.selectedSubjects.size == 1) _configState.value.selectedSubjects.first() else null
                )
            } else if (_configState.value.isRevisionMode) {
                repository.getRevisionQuestions()
            } else {
                repository.getQuestionsForPractice(
                    _configState.value.selectedSubjects,
                    _configState.value.isShuffleEnabled
                )
            }

            val session = repository.startQuizSession(
                if (_configState.value.selectedSubjects.size == 1) _configState.value.selectedSubjects.first() else null
            )

            originalQuestions = baseQuestions
            sessionPerformances.clear()
            
            val displayQuestions = baseQuestions.map { 
                shuffleQuestionOptions(it, session.sessionId.hashCode().toLong()) 
            }

            _uiState.value = _uiState.value.copy(
                questions = displayQuestions,
                sessionId = session.sessionId,
                isLoading = false,
                answeredIndices = mutableMapOf(),
                skippedIndices = emptySet(),
                score = 0,
                isQuizFinished = false,
                currentQuestionIndex = 0
            )
            persistSessionState()

            // Auto-generation on start is disabled to save AI tokens.
            // User can manually click the 'Variation' button on any question page.
        }
    }

    private fun shuffleQuestionOptions(question: Question, seed: Long? = null): Question {
        val optionsWithMetadata = question.options.mapIndexed { index, text ->
            text to (index == question.correctIndex)
        }.toMutableList()
        
        if (seed != null) {
            optionsWithMetadata.shuffle(java.util.Random(seed + question.id))
        } else {
            optionsWithMetadata.shuffle()
        }
        
        return question.copy(
            options = optionsWithMetadata.map { it.first },
            correctIndex = optionsWithMetadata.indexOfFirst { it.second }
        )
    }

    fun onAnswerSelected(questionIndex: Int, optionIndex: Int) {
        val state = _uiState.value
        val displayQuestion = state.questions.getOrNull(questionIndex) ?: return
        if (state.answeredIndices.containsKey(questionIndex)) return

        val isCorrect = optionIndex == displayQuestion.correctIndex
        val originalQuestion = originalQuestions.getOrNull(questionIndex)
        
        viewModelScope.launch {
            if (originalQuestion != null) {
                sessionPerformances.add(
                    UserPerformance(
                        questionId = originalQuestion.id,
                        isCorrect = isCorrect,
                        subject = originalQuestion.subject
                    )
                )
            }
        }

        val newAnswered = state.answeredIndices.toMutableMap()
        newAnswered[questionIndex] = optionIndex
        
        // Remove from skipped if it was there
        val newSkipped = state.skippedIndices.toMutableSet()
        newSkipped.remove(questionIndex)
        
        _uiState.value = state.copy(
            answeredIndices = newAnswered,
            skippedIndices = newSkipped,
            score = if (isCorrect) state.score + 1 else state.score
        )
        persistSessionState()
    }

    fun onSkipQuestion(questionIndex: Int) {
        val state = _uiState.value
        if (state.answeredIndices.containsKey(questionIndex)) return
        
        val newSkipped = state.skippedIndices.toMutableSet()
        newSkipped.add(questionIndex)
        
        _uiState.value = state.copy(
            skippedIndices = newSkipped
        )
        persistSessionState()
    }

    fun onFinishQuiz() {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            if (sessionId != null) {
                repository.finishQuizSession(sessionId, sessionPerformances)
            }
            _uiState.value = _uiState.value.copy(isQuizFinished = true)
            persistSessionState() // Standard clear-up
        }
    }

    fun resetToConfig() {
        // User is intentionally closing the session
        prefs.edit().clear().apply()
        
        _uiState.value = QuizUiState(
            isConfiguring = true
        )
        // Refresh available subjects on reset and update config state
        viewModelScope.launch {
            val subjects = repository.getAllSubjects().first()
            _configState.value = _configState.value.copy(
                availableSubjects = subjects,
                hasActiveSession = false
            )
        }
    }

    fun updateQuestion(updatedQuestion: Question) {
        viewModelScope.launch {
            repository.updateQuestion(updatedQuestion)
            // Update UI list
            val newQuestions = _uiState.value.questions.map { if (it.id == updatedQuestion.id) updatedQuestion else it }
            _uiState.value = _uiState.value.copy(questions = newQuestions)
            
            // Also update the original cached version for correct SRS syncing later
            originalQuestions = originalQuestions.map { if (it.id == updatedQuestion.id) updatedQuestion else it }
        }
    }

    fun resumeSession() {
        val sessionId = prefs.getString("sessionId", null) ?: return
        val qIdsStr = prefs.getString("qIds", "") ?: ""
        val answeredStr = prefs.getString("answered", "") ?: ""
        val skippedStr = prefs.getString("skipped", "") ?: ""
        val score = prefs.getInt("score", 0)
        val currentIndex = prefs.getInt("currentIndex", 0)

        if (qIdsStr.isEmpty()) return

        val ids = qIdsStr.split(",").mapNotNull { it.toLongOrNull() }
        
        viewModelScope.launch {
            _uiState.value = QuizUiState(isLoading = true, isConfiguring = false)
            val questions = repository.getQuestionsByIds(ids)
            
            originalQuestions = ids.mapNotNull { id -> questions.find { it.id == id } }
            
            val orderedDisplay = originalQuestions.map { 
                shuffleQuestionOptions(it, sessionId.hashCode().toLong()) 
            }

            val answeredMap = mutableMapOf<Int, Int>()
            if (answeredStr.isNotEmpty()) {
                answeredStr.split(",").forEach { pair ->
                    val parts = pair.split(":")
                    if (parts.size == 2) {
                        parts[0].toIntOrNull()?.let { k ->
                            parts[1].toIntOrNull()?.let { v -> answeredMap[k] = v }
                        }
                    }
                }
            }

            val skippedSet = skippedStr.split(",").mapNotNull { it.toIntOrNull() }.toSet()

            _uiState.value = QuizUiState(
                questions = orderedDisplay,
                sessionId = sessionId,
                isLoading = false,
                answeredIndices = answeredMap,
                skippedIndices = skippedSet,
                score = score,
                isQuizFinished = false,
                currentQuestionIndex = currentIndex,
                isConfiguring = false
            )
        }
    }

    fun observeBadgeState(questionId: Long): Flow<Int?> {
        return repository.observeBadgeState(questionId)
    }
}

data class PracticeConfig(
    val isShuffleEnabled: Boolean = true,
    val isRevisionMode: Boolean = false,
    val isAdaptiveMode: Boolean = true, // Default to true now
    val isAiVariationEnabled: Boolean = false,
    val selectedSubjects: List<String> = emptyList(),
    val availableSubjects: List<String> = emptyList(),
    val hasActiveSession: Boolean = false
)

data class QuizUiState(
    val questions: List<Question> = emptyList(),
    val sessionId: String? = null,
    val currentQuestionIndex: Int = 0,
    val answeredIndices: Map<Int, Int> = emptyMap(),
    val skippedIndices: Set<Int> = emptySet(),
    val aiVariations: Map<Int, String> = emptyMap(),
    val loadingVariations: Set<Int> = emptySet(),
    val score: Int = 0,
    val isLoading: Boolean = false,
    val isQuizFinished: Boolean = false,
    val isConfiguring: Boolean = true
)
