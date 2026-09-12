package com.studylens.input.vitals

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.studylens.shared.InferenceStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceVitalsMonitor(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null

    @Volatile
    private var lastTokensPerSecond: Double = 0.0

    @Volatile
    private var lastLatencyMs: Long = 0L

    private val _vitals = MutableStateFlow(
        InferenceStats(
            tokensPerSecond = 0.0,
            latencyMs = 0L,
            ramUsedMb = getRamUsedMb(),
            thermalStatus = getThermalStatus()
        )
    )
    val vitals: StateFlow<InferenceStats> = _vitals.asStateFlow()

    init {
        startMonitoring()
    }

    fun startMonitoring() {
        if (pollingJob?.isActive == true) return
        Log.d(TAG, "Starting periodic device vitals monitoring loop (1500ms polling)...")
        pollingJob = scope.launch {
            while (isActive) {
                val ram = getRamUsedMb()
                val thermal = getThermalStatus()
                _vitals.value = InferenceStats(
                    tokensPerSecond = lastTokensPerSecond,
                    latencyMs = lastLatencyMs,
                    ramUsedMb = ram,
                    thermalStatus = thermal
                )
                delay(1500)
            }
        }
    }

    fun stopMonitoring() {
        Log.d(TAG, "Stopping device vitals monitoring loop.")
        pollingJob?.cancel()
        pollingJob = null
    }

    fun recordInference(tokensPerSecond: Double, latencyMs: Long) {
        lastTokensPerSecond = tokensPerSecond
        lastLatencyMs = latencyMs
        val ram = getRamUsedMb()
        val thermal = getThermalStatus()
        Log.i(TAG, "Inference recorded: $tokensPerSecond tok/s, latency=${latencyMs}ms, RAM=${ram}MB, Thermal=$thermal")
        _vitals.value = _vitals.value.copy(
            tokensPerSecond = tokensPerSecond,
            latencyMs = latencyMs,
            ramUsedMb = ram,
            thermalStatus = thermal
        )
    }

    fun getRamUsedMb(): Long {
        return try {
            val info = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(info)
            (info.totalMem - info.availMem) / (1024 * 1024)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching memory info", e)
            0L
        }
    }

    fun getThermalStatus(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                when (powerManager.currentThermalStatus) {
                    PowerManager.THERMAL_STATUS_NONE -> "NONE"
                    PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                    PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                    PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                    PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                    PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                    PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                    else -> "UNKNOWN"
                }
            } else {
                "NORMAL"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching thermal status", e)
            "UNKNOWN"
        }
    }

    companion object {
        private const val TAG = "DeviceVitalsMonitor"
    }
}

