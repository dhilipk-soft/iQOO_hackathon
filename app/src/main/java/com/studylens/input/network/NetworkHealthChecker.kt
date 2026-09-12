package com.studylens.input.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
            probeAndUpdate()
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (!hasInternet) {
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
        } catch (e: Exception) {
            // Fallback for restricted environments
        }
        probeAndUpdate()
    }

    fun triggerRefresh() {
        probeAndUpdate()
    }

    private fun probeAndUpdate() {
        scope.launch {
            val reallyOnline = isReallyOnline()
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
            val activeNetwork = connectivityManager.activeNetwork ?: return@withContext false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return@withContext false
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
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
                conn.responseCode == 204
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            false
        }
    }
}
