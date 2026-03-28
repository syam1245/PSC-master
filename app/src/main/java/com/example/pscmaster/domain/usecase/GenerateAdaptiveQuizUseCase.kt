package com.example.pscmaster.domain.usecase

import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.data.entity.QuestionBadgeState
import com.example.pscmaster.data.entity.QuestionWithMetadata
import com.example.pscmaster.data.local.QuestionDao
import com.example.pscmaster.data.repository.PSCRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.exp

class GenerateAdaptiveQuizUseCase @Inject constructor(
    private val questionDao: QuestionDao
) {
    suspend operator fun invoke(
        size: Int,
        subject: String?,
        newQuestionRatio: Float = 0.2f // Deprecated functionally, handled natively via priorities
    ): List<Question> = withContext(Dispatchers.Default) {
        val currentTime = System.currentTimeMillis()
        val questions = questionDao.fetchAndMarkAdaptiveQuestions(size, subject, currentTime)
        
        // Final shuffle to decouple any strict rank predictability to the user
        questions.shuffled()
    }
}
