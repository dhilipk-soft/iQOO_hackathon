package com.studylens.input.vitals

import android.content.Context
import android.os.Debug
import com.studylens.shared.InferenceStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DeviceVitalsMonitor(private val context: Context) {
    private val _vitals = MutableStateFlow(
        InferenceStats(
            tokensPerSecond = 0.0,
            latencyMs = 0L,
            ramUsedMb = getRamUsage(),
            thermalStatus = "NORMAL"
        )
    )
    val vitals: StateFlow<InferenceStats> = _vitals

    fun updateInferenceStats(tokensPerSecond: Double, latencyMs: Long) {
        _vitals.value = InferenceStats(
            tokensPerSecond = tokensPerSecond,
            latencyMs = latencyMs,
            ramUsedMb = getRamUsage(),
            thermalStatus = "NORMAL"
        )
    }

    private fun getRamUsage(): Long {
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)
        return memoryInfo.totalPss / 1024L
    }
}
