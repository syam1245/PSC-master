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
    private val questionDao: QuestionDao,
    private val repository: PSCRepository
) {
    suspend operator fun invoke(
        size: Int,
        subject: String?,
        newQuestionRatio: Float = 0.2f
    ): List<Question> = withContext(Dispatchers.Default) {
        // 1. Fetch Candidates
        val allWithMetadata = if (subject == null) {
            questionDao.getAllQuestionsWithMetadata()
        } else {
            questionDao.getQuestionsWithMetadataBySubject(subject)
        }

        if (allWithMetadata.isEmpty()) return@withContext emptyList()

        // 2. Separate New vs. Known
        val (newCandidates, knownCandidates) = allWithMetadata.partition { 
            (it.badgeState?.state ?: QuestionBadgeState.STATE_UNSEEN) < QuestionBadgeState.STATE_BADGE_REMOVED
        }

        // 3. Adaptive Selection Logic 
        // We want targetNewCount% of new questions, but if we don't have enough known ones, 
        // we take more new ones (and vice versa) to reach the 'size'.
        
        val actualNewCount = (size * newQuestionRatio).toInt().coerceIn(1, newCandidates.size.coerceAtMost(size))
        val selectedNew = newCandidates.shuffled().take(actualNewCount)
        
        val remainingNeeded = size - selectedNew.size
        
        val scoredKnown = knownCandidates.map { 
            val score = calculateScore(it)
            it to score
        }.sortedByDescending { it.second }

        val selectedKnown = scoredKnown.take(remainingNeeded).map { it.first }
        
        // If we STILL need more (e.g., both pools combined are less than 'size' or we have extra new ones)
        val finalPool = mutableListOf<QuestionWithMetadata>()
        finalPool.addAll(selectedNew)
        finalPool.addAll(selectedKnown)
        
        if (finalPool.size < size && newCandidates.size > selectedNew.size) {
            val additionalNew = newCandidates.filterNot { finalPool.contains(it) }.shuffled().take(size - finalPool.size)
            finalPool.addAll(additionalNew)
        }

        // 5. Final Shuffle and Return
        finalPool.shuffled().map { it.question }
    }

    private fun calculateScore(metadata: QuestionWithMetadata): Double {
        val metrics = metadata.metrics ?: return 100.0 // Default high score for no history
        
        val correct = metrics.correctAttempts.toDouble()
        val total = metrics.totalAttempts.toDouble()
        val accuracy = if (total > 0) correct / total else 0.5
        
        // Signals
        val accuracyScore = (1.0 - accuracy) * 40.0 // High error = high priority
        
        val lastAttemptHours = (System.currentTimeMillis() - metrics.lastAttemptTimestamp) / (1000.0 * 60 * 60)
        val recencyScore = ln(lastAttemptHours + 1.0) * 5.0
        
        val fatiguePenalty = if (lastAttemptHours < 1.0) -100.0 else 0.0 // Don't repeat within 1 hour
        
        val hardBonus = if (metrics.difficultyFlag == 1) 20.0 else 0.0
        
        val streakPenalty = metrics.consecutiveCorrect * -10.0
        
        return 100.0 + accuracyScore + recencyScore + fatiguePenalty + hardBonus + streakPenalty
    }
}
