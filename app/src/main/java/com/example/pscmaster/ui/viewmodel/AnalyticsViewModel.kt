package com.example.pscmaster.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pscmaster.api.AiService
import com.example.pscmaster.data.repository.PSCRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: PSCRepository,
    private val aiService: AiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState = _uiState.asStateFlow()

    fun generateInsights(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val weakSubjects = repository.getWeakSubjects().first()
                val dataHash = weakSubjects.hashCode()

                if (!forceRefresh) {
                    val cachedResult = repository.getCachedInsights(dataHash)
                    if (cachedResult != null) {
                        _uiState.value = _uiState.value.copy(
                            insights = cachedResult.insights,
                            provider = cachedResult.provider,
                            isLoading = false
                        )
                        return@launch
                    }
                }

                val result = aiService.analyzePerformance(weakSubjects)
                
                // Only cache valid generations
                if (result.provider != "Error" && result.provider != "System") {
                    repository.saveCachedInsights(dataHash, result)
                }

                _uiState.value = _uiState.value.copy(
                    insights = result.insights,
                    provider = result.provider,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    insights = "Error: ${e.message}",
                    provider = "Error",
                    isLoading = false
                )
            }
        }
    }
}

data class AnalyticsUiState(
    val insights: String = "",
    val provider: String = "",
    val isLoading: Boolean = false
)
