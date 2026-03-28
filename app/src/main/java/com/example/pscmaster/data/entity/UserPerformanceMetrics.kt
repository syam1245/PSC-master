package com.example.pscmaster.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_performance_metrics",
    indices = [
        Index(value = ["nextReviewTimestamp"]),
        Index(value = ["isShownInCycle"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = Question::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class UserPerformanceMetrics(
    @PrimaryKey val questionId: Long,
    val totalAttempts: Int = 0,
    val correctAttempts: Int = 0,
    val lastAttemptTimestamp: Long = 0,
    val averageTimeSpent: Long = 0,
    val difficultyFlag: Int = 0, // 0: Normal, 1: Hard
    val consecutiveCorrect: Int = 0,
    val easeFactor: Double = 2.5,
    val lastIntervalDays: Int = 0,
    val nextReviewTimestamp: Long = 0,
    val intervalIndex: Int = 0,
    val isShownInCycle: Int = 0 
)
