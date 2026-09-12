package com.studylens

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.studylens.ui.navigation.NavGraph

class MainActivity : ComponentActivity() {

    private val viewModel: com.studylens.ui.StudyViewModel by lazy {
        com.studylens.ui.StudyViewModel(application)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Called when user presses Home, Middle (recents/overview), or leaves via user interaction
        viewModel.onUserMinimizedApp()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.onAppResumed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request POST_NOTIFICATIONS runtime permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF4F46E5),
                    secondary = Color(0xFF10B981),
                    background = Color(0xFFF8F9FD),
                    surface = Color(0xFFFFFFFF),
                    onPrimary = Color.White,
                    onBackground = Color(0xFF1E293B),
                    onSurface = Color(0xFF1E293B)
                )
            ) {
                Surface {
                    NavGraph(viewModel = viewModel)
                }
            }
        }
    }
}
