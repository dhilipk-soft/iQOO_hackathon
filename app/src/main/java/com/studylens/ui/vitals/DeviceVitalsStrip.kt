package com.studylens.ui.vitals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
            .background(Color(0xFF0F172A)) // Deep slate background for high contrast
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Online / Offline Indicator Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(
                    if (isOnline) Color(0x2210B981) else Color(0x22EF4444),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isOnline) "🟢 ONLINE" else "📴 OFFLINE",
                color = if (isOnline) Color(0xFF34D399) else Color(0xFFF87171),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // On-Device AI Telemetry Items
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VitalBadge(
                label = "RAM",
                value = "${stats.ramUsedMb}MB",
                color = Color(0xFFE2E8F0)
            )

            VitalBadge(
                label = "SPEED",
                value = if (stats.tokensPerSecond > 0) "${stats.tokensPerSecond} t/s" else "IDLE",
                color = Color(0xFF38BDF8)
            )

            VitalBadge(
                label = "LATENCY",
                value = if (stats.latencyMs > 0) "${stats.latencyMs}ms" else "--",
                color = Color(0xFFA78BFA)
            )

            VitalBadge(
                label = "THERMAL",
                value = stats.thermalStatus.ifBlank { "NORMAL" },
                color = if (stats.thermalStatus.equals("NORMAL", ignoreCase = true)) Color(0xFF4ADE80) else Color(0xFFFBBF24)
            )
        }
    }
}

@Composable
private fun VitalBadge(
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "$label:",
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
