package com.example.pscmaster.api

import android.util.Log
import com.example.pscmaster.BuildConfig
import com.example.pscmaster.data.local.SubjectCount
import com.google.ai.client.generativeai.GenerativeModel
import javax.inject.Inject

class AiServiceImpl @Inject constructor(
    private val groqApi: GroqApi
) : AiService {

    private val geminiModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY.trim()
        )
    }

    override suspend fun analyzePerformance(weakSubjects: List<SubjectCount>): AiResult {
        if (weakSubjects.isEmpty()) return AiResult("No performance data yet. Take a quiz to start receiving AI insights!", "System")

        val subjectsInfo = weakSubjects.joinToString { "${it.subject} (${it.count} errors)" }
        val prompt = """
            You are a specialized study assistant for the Kerala PSC (Public Service Commission) exams.
            Based on the user's recent practice quiz results, here are the subjects where they made mistakes:
            $subjectsInfo
            
            Please provide:
            1. A brief analysis of their current weak areas.
            2. Specific topics within these subjects that frequently appear in Kerala PSC exams.
            3. A short, encouraging study tip.
            
            Keep the response concise and professional. Respond ONLY in English. No other languages are permitted.
        """.trimIndent()

        val errors = mutableListOf<String>()

        // 1. Try Gemini
        if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "null") {
            try {
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    geminiModel.generateContent(prompt)
                }
                val text = response.text
                if (!text.isNullOrBlank()) return AiResult(text, "Gemini 1.5 Flash")
            } catch (e: Exception) {
                Log.e("AiService", "Gemini failed: ${e.message}")
                errors.add("Gemini: ${e.localizedMessage}")
            }
        } else {
            errors.add("Gemini API key is missing or invalid.")
        }

        // 2. Fallback to Groq
        if (BuildConfig.GROQ_API_KEY.isNotBlank() && BuildConfig.GROQ_API_KEY != "null") {
            try {
                val request = GroqRequest(
                    messages = listOf(
                        GroqMessage(role = "system", content = "You are a Kerala PSC study assistant. Respond ONLY in English."),
                        GroqMessage(role = "user", content = prompt)
                    )
                )
                val response = groqApi.getChatCompletion("Bearer ${BuildConfig.GROQ_API_KEY}", request)
                val result = response.choices.firstOrNull()?.message?.content
                if (!result.isNullOrBlank()) return AiResult(result, "Groq Llama 3.1")
            } catch (e: Exception) {
                Log.e("AiService", "Groq failed: ${e.message}")
                errors.add("Groq: ${e.localizedMessage}")
            }
        } else {
            errors.add("Groq API key is missing or invalid.")
        }

        return AiResult("AI Analysis Unavailable.\n\nDebug Info:\n${errors.joinToString("\n")}\n\nPlease check your internet connection and ensure valid API keys are set in local.properties.", "Error")
    }

    override suspend fun generateVariation(question: String, correctAnswer: String): AiResult {
        val prompt = """
            You are a Kerala PSC exam expert. 
            Rephrase or provide a variation of the following question while STRICTLY maintaining its original meaning and ensuring the correct answer ('$correctAnswer') remains exactly the same.
            
            CRITICAL:
            1. DO NOT change the logic in a way that makes another option correct.
            2. The options are fixed and will not change, so the new question MUST lead to '$correctAnswer' as the only correct choice.
            3. Provide the variation ONLY in English.
            
            Original Question: $question
            Correct Answer: $correctAnswer
            
            Format:
            English: [Variation in English]
            
            Keep it concise. Use ONLY English. No other languages are permitted. Do not include any other text or explanations.
        """.trimIndent()

        val errors = mutableListOf<String>()

        // 1. Try Gemini
        if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "null") {
            try {
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    geminiModel.generateContent(prompt)
                }
                val text = response.text
                if (!text.isNullOrBlank()) return AiResult(text, "Gemini 1.5 Flash")
            } catch (e: Exception) {
                Log.e("AiService", "Gemini failed: ${e.message}")
                errors.add("Gemini: ${e.localizedMessage}")
            }
        }

        // 2. Fallback to Groq
        if (BuildConfig.GROQ_API_KEY.isNotBlank() && BuildConfig.GROQ_API_KEY != "null") {
            try {
                val request = GroqRequest(
                    messages = listOf(
                        GroqMessage(role = "system", content = "You are a Kerala PSC question variation generator. Respond ONLY in English."),
                        GroqMessage(role = "user", content = prompt)
                    )
                )
                val response = groqApi.getChatCompletion("Bearer ${BuildConfig.GROQ_API_KEY}", request)
                val result = response.choices.firstOrNull()?.message?.content
                if (!result.isNullOrBlank()) return AiResult(result, "Groq Llama 3.1")
            } catch (e: Exception) {
                Log.e("AiService", "Groq failed: ${e.message}")
                errors.add("Groq: ${e.localizedMessage}")
            }
        }

        return AiResult("Variation unavailable. Please try again later.", "Error")
    }

    override suspend fun generateNewQuestions(subject: String, count: Int): AiResult {
        val prompt = """
            Generate exactly $count multiple-choice questions for the subject: '$subject' according to Kerala PSC syllabus standards.
            
            Output MUST be a valid JSON array matching this format exactly:
            [
              {
                "question": "The question in English",
                "options": ["Option 1", "Option 2", "Option 3", "Option 4"],
                "correctIndex": 0
              }
            ]
            
            Strict Rules:
            - Return ONLY the JSON array. Do not include any text before or after the JSON.
            - Ensure questions are accurate and relevant to Kerala PSC exams.
            - Provide ONLY English for all text. No other languages are permitted.
            - correctIndex must be an integer (0 to 3).
        """.trimIndent()

        val errors = mutableListOf<String>()

        // 1. Try Gemini
        if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "null") {
            try {
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    geminiModel.generateContent(prompt)
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    // Clean JSON if needed (some models wrap in backticks)
                    val cleaned = text.trim().removePrefix("```json").removeSuffix("```").trim()
                    return AiResult(cleaned, "Gemini 1.5 Flash")
                }
            } catch (e: Exception) {
                Log.e("AiService", "Gemini failed: ${e.message}")
                errors.add("Gemini: ${e.localizedMessage}")
            }
        }

        // 2. Fallback to Groq
        if (BuildConfig.GROQ_API_KEY.isNotBlank() && BuildConfig.GROQ_API_KEY != "null") {
            try {
                val request = GroqRequest(
                    messages = listOf(
                        GroqMessage(role = "system", content = "You are a JSON-only Kerala PSC question generator. Use ONLY English for all question and option text."),
                        GroqMessage(role = "user", content = prompt)
                    )
                )
                val response = groqApi.getChatCompletion("Bearer ${BuildConfig.GROQ_API_KEY}", request)
                val result = response.choices.firstOrNull()?.message?.content
                if (!result.isNullOrBlank()) {
                    val cleaned = result.trim().removePrefix("```json").removeSuffix("```").trim()
                    return AiResult(cleaned, "Groq Llama 3.1")
                }
            } catch (e: Exception) {
                Log.e("AiService", "Groq failed: ${e.message}")
                errors.add("Groq: ${e.localizedMessage}")
            }
        }

        return AiResult("Generation failed.", "Error")
    }
}
