package com.studylens.ui.vitals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylens.shared.InferenceStats

@Composable
fun DeviceVitalsStrip(
    stats: InferenceStats,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E2C))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isOnline) "🟢 ONLINE" else "🔴 OFFLINE",
            color = if (isOnline) Color(0xFF4CAF50) else Color(0xFFFF5252),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "RAM: ${stats.ramUsedMb}MB",
            color = Color.White,
            fontSize = 12.sp
        )
        Text(
            text = "Latency: ${stats.latencyMs}ms",
            color = Color.White,
            fontSize = 12.sp
        )
        Text(
            text = "Speed: ${stats.tokensPerSecond} t/s",
            color = Color.White,
            fontSize = 12.sp
        )
    }
}
