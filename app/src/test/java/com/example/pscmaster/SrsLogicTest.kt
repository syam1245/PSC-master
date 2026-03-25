package com.example.pscmaster

import com.example.pscmaster.data.entity.Question
import com.example.pscmaster.data.local.QuestionDao
import com.example.pscmaster.data.repository.PSCRepositoryImpl
import com.example.pscmaster.data.local.AppDatabase
import com.example.pscmaster.data.local.PerformanceDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Context
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class SrsLogicTest {

    private lateinit var repository: PSCRepositoryImpl
    private val questionDao = mockk<QuestionDao>(relaxed = true)
    private val performanceDao = mockk<PerformanceDao>(relaxed = true)
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
            firestore,
            auth,
            context
        )
    }

    @Test
    fun `test correct answer increases srs interval`() = runTest {
        val initialQuestion = Question(id = 1, intervalIndex = 0, questionText = "Test")
        coEvery { questionDao.getQuestionById(1) } returns initialQuestion
        
        val updatedQuestionSlot = slot<Question>()
        coEvery { questionDao.updateQuestion(capture(updatedQuestionSlot)) } returns Unit
        
        repository.updateSrs(1, true)
        
        assertEquals(1, updatedQuestionSlot.captured.intervalIndex)
        assertTrue(updatedQuestionSlot.captured.nextReviewTimestamp > 1000000L)
    }

    @Test
    fun `test wrong answer resets srs interval`() = runTest {
        val initialQuestion = Question(id = 1, intervalIndex = 3, questionText = "Test")
        coEvery { questionDao.getQuestionById(1) } returns initialQuestion
        
        val updatedQuestionSlot = slot<Question>()
        coEvery { questionDao.updateQuestion(capture(updatedQuestionSlot)) } returns Unit
        
        repository.updateSrs(1, false)
        
        assertEquals(0, updatedQuestionSlot.captured.intervalIndex)
        assertEquals(1000000L + 86400000L, updatedQuestionSlot.captured.nextReviewTimestamp)
    }
}
