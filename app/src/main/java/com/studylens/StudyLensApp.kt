package com.studylens

import android.app.Application
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

        database = AppDatabase.getDatabase(this)
        networkHealthChecker = NetworkHealthChecker(this)
        deviceVitalsMonitor = DeviceVitalsMonitor(this)

        val cameraCapture = CameraCapture(this)
        val textExtractor = TextExtractor()

        inputProvider = InputProviderImpl(
            context = this,
            cameraCapture = cameraCapture,
            textExtractor = textExtractor,
            networkHealthChecker = networkHealthChecker,
            database = database
        )

        usageCollector = UsageCollector(this, database)
        notificationCollector = NotificationCollector(this, database)
    }

    companion object {
        lateinit var instance: StudyLensApp
            private set
    }
}
