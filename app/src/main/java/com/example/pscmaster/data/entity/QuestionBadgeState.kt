package com.example.pscmaster.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "question_badge_state",
    foreignKeys = [
        ForeignKey(
            entity = Question::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["state"])]
)
data class QuestionBadgeState(
    @PrimaryKey val questionId: Long,
    val state: Int = STATE_UNSEEN,
    val lastSessionId: String? = null
) {
    companion object {
        const val STATE_UNSEEN = 0
        const val STATE_SEEN_IN_SESSION = 1
        const val STATE_COMPLETED = 2
        const val STATE_BADGE_REMOVED = 3
    }
}
