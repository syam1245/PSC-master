package com.example.pscmaster

import android.app.Application
import com.example.pscmaster.data.repository.PSCRepository
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.example.pscmaster.worker.CleanupWorker
import com.example.pscmaster.worker.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class PSCApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var repository: PSCRepository

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        repository.startRealtimeSync()
        scheduleBackgroundWorkers()
    }

    private fun scheduleBackgroundWorkers() {
        val workManager = WorkManager.getInstance(this)

        // 1. Periodic Sync (Every 6 hours)
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "PeriodicSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        // 2. Periodic Cleanup (Every 24 hours)
        val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            "PeriodicCleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }
}
