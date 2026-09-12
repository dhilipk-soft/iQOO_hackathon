package com.studylens

import android.app.Application

class StudyLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize global components, Room Database, WorkManager, etc.
    }
}
