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
            apiKey = BuildConfig.GEMINI_API_KEY.trim(),
            safetySettings = listOf(
                com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HARASSMENT, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.HATE_SPEECH, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.SEXUALLY_EXPLICIT, com.google.ai.client.generativeai.type.BlockThreshold.NONE),
                com.google.ai.client.generativeai.type.SafetySetting(com.google.ai.client.generativeai.type.HarmCategory.DANGEROUS_CONTENT, com.google.ai.client.generativeai.type.BlockThreshold.NONE)
            )
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
            
            OUTPUT FORMAT (Strict JSON):
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
            All questions must belong to the 'AI POOL' category.
            Include a 'subject_tag' from: [Geography, History, Polity, Science, Economics, General Knowledge].
            
            OUTPUT FORMAT (Strict JSON Array):
            [
              {
                "question": "...",
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
            systemPrompt = "Return ONLY valid JSON array. Category must be 'AI POOL'.",
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
                        return AiResult(text.substring(start, end + 1).trim(), "Gemini 1.5 Flash")
                    }
                    // Fallback to raw text if no JSON marks found but prompt was for analysis
                    if (systemPrompt.contains("assistant")) {
                         return AiResult(text.trim(), "Gemini 1.5 Flash")
                    }
                }
            } catch (e: Exception) {
                val eMsg = e.message ?: "Unknown Error"
                Log.e("AiService", "Gemini failed: $eMsg")
                if (eMsg.contains("MissingFieldException")) {
                    errors.add("Gemini: SDK Error (Model/Region issue). Check if Gemini 1.5 is enabled for your API Key.")
                } else {
                    errors.add("Gemini: $eMsg")
                }
            }
        }

        // 2. Fallback to Groq
        if (BuildConfig.GROQ_API_KEY.isNotBlank() && BuildConfig.GROQ_API_KEY != "null") {
            try {
                val request = GroqRequest(
                    model = "llama-3.3-70b-versatile", 
                    messages = listOf(
                        GroqMessage(role = "system", content = systemPrompt),
                        GroqMessage(role = "user", content = prompt)
                    )
                )
                val response = groqApi.getChatCompletion("Bearer ${BuildConfig.GROQ_API_KEY.trim()}", request)
                val result = response.choices.firstOrNull()?.message?.content
                if (!result.isNullOrBlank()) {
                    val arrayStart = result.indexOf("[")
                    val objStart = result.indexOf("{")
                    
                    val start = if (arrayStart != -1 && (objStart == -1 || arrayStart < objStart)) arrayStart else objStart
                    val endChar = if (start == arrayStart) "]" else "}"
                    val end = result.lastIndexOf(endChar)

                    if (start != -1 && end != -1 && end > start) {
                        return AiResult(result.substring(start, end + 1).trim(), "Groq Llama 3.3")
                    }
                    if (systemPrompt.contains("assistant")) {
                         return AiResult(result.trim(), "Groq Llama 3.3")
                    }
                }
            } catch (e: Exception) {
                Log.e("AiService", "Groq failed: ${e.message}")
                errors.add("Groq: ${e.message}")
            }
        }

        val debugInfo = if (errors.isNotEmpty()) "\n\nDebug Info:\n${errors.joinToString("\n")}" else "\n(No API keys configured)"
        return AiResult("$errorLabel$debugInfo", "Error")
    }
}
