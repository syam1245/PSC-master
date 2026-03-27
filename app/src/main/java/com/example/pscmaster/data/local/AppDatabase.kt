package com.example.pscmaster.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.pscmaster.data.entity.*

@Database(
    entities = [
        Question::class,
        UserPerformance::class,
        QuizSession::class,
        QuestionBadgeState::class,
        UserPerformanceMetrics::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun performanceDao(): PerformanceDao
    abstract fun sessionDao(): SessionDao
    abstract fun metricsDao(): PerformanceMetricsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN nextReviewTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE questions ADD COLUMN intervalIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    DELETE FROM questions 
                    WHERE id NOT IN (
                        SELECT MIN(id) 
                        FROM questions 
                        GROUP BY questionText
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_questions_questionText ON questions (questionText)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_questions_subject ON questions (subject)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_questions_nextReviewTimestamp ON questions (nextReviewTimestamp)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN explanation TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_user_performance_questionId_timestamp ON user_performance (questionId, timestamp)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS quiz_sessions (
                        sessionId TEXT NOT NULL PRIMARY KEY,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        subjectFilter TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS question_badge_state (
                        questionId INTEGER NOT NULL PRIMARY KEY,
                        state INTEGER NOT NULL DEFAULT 0,
                        lastSessionId TEXT,
                        FOREIGN KEY(questionId) REFERENCES questions(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_badge_state ON question_badge_state (state)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_performance_metrics (
                        questionId INTEGER NOT NULL PRIMARY KEY,
                        totalAttempts INTEGER NOT NULL DEFAULT 0,
                        correctAttempts INTEGER NOT NULL DEFAULT 0,
                        lastAttemptTimestamp INTEGER NOT NULL DEFAULT 0,
                        averageTimeSpent INTEGER NOT NULL DEFAULT 0,
                        difficultyFlag INTEGER NOT NULL DEFAULT 0,
                        consecutiveCorrect INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(questionId) REFERENCES questions(id) ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN subjectTag TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_questions_subjectTag ON questions (subjectTag)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Robust cleanup for any variation of "AI POOL (Topic)"
                db.execSQL("""
                    UPDATE questions 
                    SET subjectTag = TRIM(REPLACE(REPLACE(REPLACE(REPLACE(subject, 'AI POOL (', ''), 'AI pool (', ''), 'AI Pool (', ''), ')', '')),
                        subject = 'AI POOL'
                    WHERE subject LIKE 'AI POOL (%)' 
                       OR subject LIKE 'AI pool (%)' 
                       OR subject LIKE 'AI Pool (%)'
                       OR subject LIKE 'ai pool (%)'
                """.trimIndent())
            }
        }
    }
}
