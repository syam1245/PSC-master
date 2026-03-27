package com.example.pscmaster.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class QuestionWithMetadata(
    @Embedded val question: Question,
    @Relation(
        parentColumn = "id",
        entityColumn = "questionId"
    )
    val badgeState: QuestionBadgeState?,
    @Relation(
        parentColumn = "id",
        entityColumn = "questionId"
    )
    val metrics: UserPerformanceMetrics?
)
