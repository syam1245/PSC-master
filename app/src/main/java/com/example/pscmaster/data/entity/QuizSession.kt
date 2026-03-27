package com.example.pscmaster.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "quiz_sessions")
data class QuizSession(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val isCompleted: Boolean = false,
    val subjectFilter: String? = null
)
