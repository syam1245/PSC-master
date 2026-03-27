package com.example.pscmaster.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pscmaster.data.entity.QuestionWithMetadata
import com.example.pscmaster.data.repository.PSCRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MistakeNotebookViewModel @Inject constructor(
    private val repository: PSCRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MistakeNotebookUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadMistakes()
    }

    fun loadMistakes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val mistakes = repository.getQuestionsWithMistakes()
                _uiState.value = _uiState.value.copy(
                    mistakes = mistakes,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
}

data class MistakeNotebookUiState(
    val mistakes: List<QuestionWithMetadata> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
