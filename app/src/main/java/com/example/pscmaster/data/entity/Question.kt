package com.example.pscmaster.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "questions",
    indices = [
        Index(value = ["questionText"], unique = true),
        Index(value = ["subject"]),
        Index(value = ["nextReviewTimestamp"])
    ]
)
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String = "",
    val questionText: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val nextReviewTimestamp: Long = 0,
    val intervalIndex: Int = 0
)
