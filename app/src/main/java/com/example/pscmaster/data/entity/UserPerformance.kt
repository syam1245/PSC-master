package com.example.pscmaster.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_performance")
data class UserPerformance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long = 0,
    val isCorrect: Boolean = false,
    val subject: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
