package com.example.pscmaster.api

import com.example.pscmaster.data.local.SubjectCount

data class AiResult(
    val insights: String,
    val provider: String
)

interface AiService {
    suspend fun analyzePerformance(weakSubjects: List<SubjectCount>): AiResult
    suspend fun generateVariation(
        question: String,
        options: List<String>,
        correctIndex: Int,
        subject: String = "General",
        difficulty: String = "medium"
    ): AiResult
    suspend fun generateNewQuestions(subject: String, count: Int): AiResult
}
