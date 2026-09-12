package com.studylens

import android.app.Application
import android.util.Log
import com.studylens.input.InputProviderImpl
import com.studylens.input.capture.CameraCapture
import com.studylens.input.data.AppDatabase
import com.studylens.input.focus.NotificationCollector
import com.studylens.input.focus.UsageCollector
import com.studylens.input.network.NetworkHealthChecker
import com.studylens.input.ocr.TextExtractor
import com.studylens.input.vitals.DeviceVitalsMonitor

class StudyLensApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var networkHealthChecker: NetworkHealthChecker
        private set

    lateinit var deviceVitalsMonitor: DeviceVitalsMonitor
        private set

    lateinit var inputProvider: InputProviderImpl
        private set

    lateinit var usageCollector: UsageCollector
        private set

    lateinit var notificationCollector: NotificationCollector
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "Initializing StudyLensApp services...")

        database = AppDatabase.getDatabase(this)
        Log.d(TAG, "AppDatabase initialized.")

        networkHealthChecker = NetworkHealthChecker(this)
        Log.d(TAG, "NetworkHealthChecker initialized.")

        deviceVitalsMonitor = DeviceVitalsMonitor(this)
        Log.d(TAG, "DeviceVitalsMonitor initialized.")

        val cameraCapture = CameraCapture(this)
        val textExtractor = TextExtractor()

        inputProvider = InputProviderImpl(
            context = this,
            cameraCapture = cameraCapture,
            textExtractor = textExtractor,
            networkHealthChecker = networkHealthChecker,
            database = database
        )
        Log.d(TAG, "InputProviderImpl initialized.")

        usageCollector = UsageCollector(this, database)
        notificationCollector = NotificationCollector(this, database)
        Log.d(TAG, "Focus collectors (UsageCollector & NotificationCollector) initialized.")
    }

    companion object {
        private const val TAG = "StudyLensApp"

        lateinit var instance: StudyLensApp
            private set
    }
}

