package com.example.pscmaster.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.data.entity.UserPerformance

@Database(entities = [Question::class, UserPerformance::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun performanceDao(): PerformanceDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE questions ADD COLUMN nextReviewTimestamp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE questions ADD COLUMN intervalIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Delete existing duplicates before creating the unique index to avoid migration failure
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
    }
}
