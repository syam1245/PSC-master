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

        return executeAiAction(
            prompt = prompt,
            systemPrompt = "You are a Kerala PSC study assistant. Respond ONLY in English.",
            errorLabel = "AI Analysis Unavailable"
        )
    }

    override suspend fun generateVariation(
        question: String,
        options: List<String>,
        correctIndex: Int,
        subject: String,
        difficulty: String
    ): AiResult {
        val mappedOptions = mapOf(
            "A" to options.getOrElse(0) { "" },
            "B" to options.getOrElse(1) { "" },
            "C" to options.getOrElse(2) { "" },
            "D" to options.getOrElse(3) { "" }
        )
        val correctLetter = when(correctIndex) {
            0 -> "A"
            1 -> "B"
            2 -> "C"
            else -> "D"
        }
        
        val prompt = """
            You are an expert exam question designer specializing in critical thinking and pedagogical question transformation for competitive exam preparation (PSC/UPSC level).
            
            TASK: Transform the following multiple-choice question into a variation that:
            - Tests the SAME knowledge and has the SAME correct answer
            - Keeps OPTIONS identical
            - Rephrases the QUESTION STEM for deeper critical thinking
            - Never changes which option is correct
            
            TRANSFORMATION TECHNIQUES:
            1. REVERSE QUESTIONING
            2. ELIMINATION FRAMING
            3. CONTEXTUAL EMBEDDING
            4. CAUSE->EFFECT flip
            5. ATTRIBUTE QUESTIONING
            6. COMPARATIVE FRAMING
            7. YEAR/EVENT ANCHORING
            8. INFERENCE BASED
            
            INPUT:
            {
              "question": "$question",
              "options": {
                "A": "${mappedOptions["A"]}",
                "B": "${mappedOptions["B"]}",
                "C": "${mappedOptions["C"]}",
                "D": "${mappedOptions["D"]}"
              },
              "correct_option": "$correctLetter",
              "subject": "$subject",
              "difficulty": "$difficulty"
            }
            
            OUTPUT FORMAT (Strict JSON, no extra text):
            {
              "original_question": "$question",
              "varied_question": "<transformed text>",
              "options": { "A": "...", "B": "...", "C": "...", "D": "..." },
              "correct_option": "$correctLetter",
              "transformation_technique": "<name>",
              "reasoning": "<one sentence>"
            }
        """.trimIndent()

        val result = executeAiAction(
            prompt = prompt,
            systemPrompt = "You are an expert question designer. Return ONLY valid JSON.",
            errorLabel = "Variation unavailable"
        )
        
        // Extract varied_question from JSON
        if (result.provider != "Error") {
            try {
                val jsonStart = result.insights.indexOf("{")
                val jsonEnd = result.insights.lastIndexOf("}")
                if (jsonStart != -1 && jsonEnd != -1) {
                    val cleanJson = result.insights.substring(jsonStart, jsonEnd + 1)
                    val jsonObject = com.google.gson.JsonParser.parseString(cleanJson).asJsonObject
                    if (jsonObject.has("varied_question")) {
                        return result.copy(insights = jsonObject.get("varied_question").asString)
                    }
                }
            } catch (e: Exception) {
                Log.e("AiService", "Parsing variation failed: ${e.message}")
            }
        }
        
        return result
    }

    override suspend fun generateNewQuestions(subject: String, count: Int): AiResult {
        val prompt = """
            Your task is to generate $count high-quality multiple-choice questions for the "AI POOL" category.
            
            CATEGORY ASSIGNMENT RULES (CRITICAL):
            - ALL generated questions MUST be assigned to exactly ONE category: "AI POOL"
            - NEVER create subcategories like "AI POOL ($subject)"
            - The category field in your output must ALWAYS be exactly: "AI POOL"
            
            SUBJECT TAG RULES:
            - Every question must include a "subject_tag" from this list ONLY: [Geography, Current Affairs, History, Polity, Science, Economics, Environment, Sports, Art & Culture, General Knowledge]
            - Use "$subject" to guide the topic, but pick the best matching tag from above.
            
            OUTPUT FORMAT (Strict JSON):
            [
              {
                "question": "<text>",
                "options": { "A": "...", "B": "...", "C": "...", "D": "..." },
                "correct_option": "A|B|C|D",
                "explanation": "...",
                "category": "AI POOL",
                "subject_tag": "...",
                "difficulty": "medium"
              }
            ]
        """.trimIndent()

        return executeAiAction(
            prompt = prompt,
            systemPrompt = "You are an expert question generator. Return ONLY valid JSON array. Category must be 'AI POOL'.",
            errorLabel = "Generation failed."
        )
    }

    private suspend fun executeAiAction(
        prompt: String,
        systemPrompt: String,
        errorLabel: String
    ): AiResult {
        val errors = mutableListOf<String>()

        // 1. Try Gemini
        if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "null") {
            try {
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    geminiModel.generateContent(prompt)
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    val arrayStart = text.indexOf("[")
                    val objStart = text.indexOf("{")
                    
                    val start = if (arrayStart != -1 && (objStart == -1 || arrayStart < objStart)) arrayStart else objStart
                    val endChar = if (start == arrayStart) "]" else "}"
                    val end = text.lastIndexOf(endChar)

                    if (start != -1 && end != -1 && end > start) {
                        val cleaned = text.substring(start, end + 1).trim()
                        return AiResult(cleaned, "Gemini 1.5 Flash")
                    }
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
                        GroqMessage(role = "system", content = systemPrompt),
                        GroqMessage(role = "user", content = prompt)
                    )
                )
                val response = groqApi.getChatCompletion("Bearer ${BuildConfig.GROQ_API_KEY}", request)
                val result = response.choices.firstOrNull()?.message?.content
                if (!result.isNullOrBlank()) {
                    val arrayStart = result.indexOf("[")
                    val objStart = result.indexOf("{")
                    
                    val start = if (arrayStart != -1 && (objStart == -1 || arrayStart < objStart)) arrayStart else objStart
                    val endChar = if (start == arrayStart) "]" else "}"
                    val end = result.lastIndexOf(endChar)

                    if (start != -1 && end != -1 && end > start) {
                        val cleaned = result.substring(start, end + 1).trim()
                        return AiResult(cleaned, "Groq Llama 3.1")
                    }
                }
            } catch (e: Exception) {
                Log.e("AiService", "Groq failed: ${e.message}")
                errors.add("Groq: ${e.localizedMessage}")
            }
        }

        val debugInfo = if (errors.isNotEmpty()) "\n\nDebug Info:\n${errors.joinToString("\n")}" else ""
        return AiResult("$errorLabel$debugInfo", "Error")
    }
}
