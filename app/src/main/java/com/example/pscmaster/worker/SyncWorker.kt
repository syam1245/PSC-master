package com.example.pscmaster.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pscmaster.data.repository.PSCRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import android.util.Log

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: PSCRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("SyncWorker", "Starting background sync")
            val result = repository.syncToFirebase()
            if (result.isSuccess) {
                Log.d("SyncWorker", "Background sync completed successfully")
                Result.success()
            } else {
                Log.e("SyncWorker", "Background sync failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error in background sync", e)
            Result.failure()
        }
    }
}
