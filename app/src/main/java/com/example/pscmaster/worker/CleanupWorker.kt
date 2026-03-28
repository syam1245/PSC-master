package com.example.pscmaster.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pscmaster.data.repository.PSCRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PSCRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("CleanupWorker", "Starting log cleanup")
            // Cleanup logs older than 30 days
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            repository.cleanupOldPerformanceLogs(thirtyDaysAgo)
            Log.d("CleanupWorker", "Log cleanup completed")
            Result.success()
        } catch (e: Exception) {
            Log.e("CleanupWorker", "Error in log cleanup", e)
            Result.failure()
        }
    }
}
