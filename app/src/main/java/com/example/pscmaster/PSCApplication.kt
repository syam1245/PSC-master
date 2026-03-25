package com.example.pscmaster

import android.app.Application
import com.example.pscmaster.data.repository.PSCRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PSCApplication : Application() {

    @Inject
    lateinit var repository: PSCRepository

    override fun onCreate() {
        super.onCreate()
        repository.startRealtimeSync()
    }
}
