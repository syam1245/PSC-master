package com.example.pscmaster.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.data.entity.UserPerformance
import com.example.pscmaster.data.repository.PSCRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

import com.example.pscmaster.domain.usecase.GenerateAdaptiveQuizUseCase

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val repository: PSCRepository,
    private val aiService: com.example.pscmaster.api.AiService,
    private val generateAdaptiveQuizUseCase: GenerateAdaptiveQuizUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState = _uiState.asStateFlow()

    private val _configState = MutableStateFlow(PracticeConfig())
    val configState = _configState.asStateFlow()

    private var originalQuestions: List<Question> = emptyList()
    private val sessionPerformances = mutableListOf<UserPerformance>()

    init {
        viewModelScope.launch {
            repository.getAllSubjects().collect { subjects ->
                _configState.value = _configState.value.copy(availableSubjects = subjects)
            }
        }
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
        _configState.value = _configState.value.copy(isRevisionMode = enabled)
    }

    fun onToggleAiVariation(enabled: Boolean) {
        _configState.value = _configState.value.copy(isAiVariationEnabled = enabled)
    }

    fun onToggleAdaptiveMode(enabled: Boolean) {
        _configState.value = _configState.value.copy(isAdaptiveMode = enabled)
    }

    fun updateCurrentPage(index: Int) {
        _uiState.value = _uiState.value.copy(currentQuestionIndex = index)
        // Optimization: Prefetch AI variation for the next question
        if (_configState.value.isAiVariationEnabled) {
            prefetchAiVariation(index + 1)
        }
    }

    private fun prefetchAiVariation(index: Int) {
        val questions = _uiState.value.questions
        if (index >= 0 && index < questions.size) {
            generateAiVariation(index)
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

                val correctAnswerText = question.options[question.correctIndex]
                val result = aiService.generateVariation(question.questionText, correctAnswerText)

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
            val displayQuestions = baseQuestions.map { shuffleQuestionOptions(it) }

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

            // Auto-generate first variation if enabled
            if (_configState.value.isAiVariationEnabled && displayQuestions.isNotEmpty()) {
                generateAiVariation(0)
            }
        }
    }

    private fun shuffleQuestionOptions(question: Question): Question {
        val optionsWithMetadata = question.options.mapIndexed { index, text ->
            text to (index == question.correctIndex)
        }
        val shuffledMetadata = optionsWithMetadata.shuffled()
        
        return question.copy(
            options = shuffledMetadata.map { it.first },
            correctIndex = shuffledMetadata.indexOfFirst { it.second }
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
    }

    fun onSkipQuestion(questionIndex: Int) {
        val state = _uiState.value
        if (state.answeredIndices.containsKey(questionIndex)) return
        
        val newSkipped = state.skippedIndices.toMutableSet()
        newSkipped.add(questionIndex)
        
        _uiState.value = state.copy(
            skippedIndices = newSkipped
        )
    }

    fun onFinishQuiz() {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            if (sessionId != null) {
                repository.finishQuizSession(sessionId, sessionPerformances)
            }
            _uiState.value = _uiState.value.copy(isQuizFinished = true)
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
    val availableSubjects: List<String> = emptyList()
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
