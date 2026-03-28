package com.example.pscmaster

import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.data.entity.UserPerformanceMetrics
import com.example.pscmaster.data.local.QuestionDao
import com.example.pscmaster.data.repository.PSCRepositoryImpl
import com.example.pscmaster.data.local.AppDatabase
import com.example.pscmaster.data.local.PerformanceDao
import com.example.pscmaster.data.local.SessionDao
import com.example.pscmaster.data.local.PerformanceMetricsDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SrsLogicTest {

    private lateinit var repository: PSCRepositoryImpl
    private val questionDao = mockk<QuestionDao>(relaxed = true)
    private val performanceDao = mockk<PerformanceDao>(relaxed = true)
    private val sessionDao = mockk<SessionDao>(relaxed = true)
    private val metricsDao = mockk<PerformanceMetricsDao>(relaxed = true)
    private val database = mockk<AppDatabase>(relaxed = true)
    private val firestore = mockk<FirebaseFirestore>(relaxed = true)
    private val auth = mockk<FirebaseAuth>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(System::class)
        every { System.currentTimeMillis() } returns 1000000L
        
        repository = PSCRepositoryImpl(
            database,
            questionDao,
            performanceDao,
            sessionDao,
            metricsDao,
            firestore,
            auth,
            context
        )
    }

    @Test
    fun `test correct answer increases srs interval`() = runTest {
        val initialQuestion = Question(id = 1, questionText = "Test")
        val initialMetrics = UserPerformanceMetrics(questionId = 1, intervalIndex = 0)
        
        coEvery { questionDao.getQuestionById(1) } returns initialQuestion
        coEvery { metricsDao.getMetricsForQuestion(1) } returns initialMetrics
        
        val updatedMetricsSlot = slot<UserPerformanceMetrics>()
        coEvery { metricsDao.upsertMetrics(capture(updatedMetricsSlot)) } returns Unit
        
        repository.updateSrs(1, true)
        
        assertEquals(1, updatedMetricsSlot.captured.intervalIndex)
        assertTrue(updatedMetricsSlot.captured.nextReviewTimestamp > 1000000L)
    }

    @Test
    fun `test wrong answer resets srs interval`() = runTest {
        val initialQuestion = Question(id = 1, questionText = "Test")
        val initialMetrics = UserPerformanceMetrics(questionId = 1, intervalIndex = 3, consecutiveCorrect = 3)
        
        coEvery { questionDao.getQuestionById(1) } returns initialQuestion
        coEvery { metricsDao.getMetricsForQuestion(1) } returns initialMetrics
        
        val updatedMetricsSlot = slot<UserPerformanceMetrics>()
        coEvery { metricsDao.upsertMetrics(capture(updatedMetricsSlot)) } returns Unit
        
        repository.updateSrs(1, false)
        
        assertEquals(0, updatedMetricsSlot.captured.intervalIndex)
        assertEquals(1000000L + 86400000L, updatedMetricsSlot.captured.nextReviewTimestamp)
    }
}
