package com.studylens.input.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class NetworkHealthChecker(private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "NetworkCallback: Network available, probing connectivity...")
            probeAndUpdate()
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "NetworkCallback: Network lost -> setting isOnline = false")
            _isOnline.value = false
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (!hasInternet) {
                Log.d(TAG, "NetworkCallback: NET_CAPABILITY_INTERNET missing -> setting isOnline = false")
                _isOnline.value = false
            } else {
                probeAndUpdate()
            }
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.d(TAG, "Registered NetworkCallback for active network tracking.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register NetworkCallback", e)
        }
        probeAndUpdate()
    }

    fun triggerRefresh() {
        Log.d(TAG, "Manual network probe trigger requested.")
        probeAndUpdate()
    }

    private fun probeAndUpdate() {
        scope.launch {
            val reallyOnline = isReallyOnline()
            if (_isOnline.value != reallyOnline) {
                Log.i(TAG, "Network status changed: isOnline = $reallyOnline")
            }
            _isOnline.value = reallyOnline
        }
    }

    /**
     * Active 2-tier check:
     * 1. Cheap check: Active network capabilities.
     * 2. Real probe: Fast HTTP HEAD/GET to generate_204 with 1500ms timeout to bypass captive portals and dead signals.
     */
    suspend fun isReallyOnline(): Boolean = withContext(Dispatchers.IO) {
        try {
            val activeNetwork = connectivityManager.activeNetwork ?: run {
                Log.d(TAG, "isReallyOnline: No active network found.")
                return@withContext false
            }
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: run {
                Log.d(TAG, "isReallyOnline: No network capabilities found.")
                return@withContext false
            }
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                Log.d(TAG, "isReallyOnline: NET_CAPABILITY_INTERNET false.")
                return@withContext false
            }

            // Real probe
            val url = URL("https://www.google.com/generate_204")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                instanceFollowRedirects = false
                useCaches = false
                requestMethod = "GET"
            }
            try {
                val code = conn.responseCode
                val success = code == 204
                Log.d(TAG, "isReallyOnline: HTTP probe response code = $code, online = $success")
                success
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.d(TAG, "isReallyOnline: Probe exception: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "NetworkHealthChecker"
    }
}

