package com.studylens.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylens.shared.FocusInsight
import com.studylens.shared.StudySession

@Composable
fun FocusScreen(
    session: StudySession?,
    focusInsight: FocusInsight?,
    modifier: Modifier = Modifier
) {
    val durationMins = (session?.durationMs ?: (6 * 60 * 1000L)) / 60000L
    val notifs = session?.notificationCount ?: 3

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FE))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top App Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Focus Insights",
                color = Color(0xFF1E1B4B),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Green Trending Chart Card (Image 1 Screen 8)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF0FDF4),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFBBF7D0), Color(0xFF86EFAC))),
                        width = 1.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Circular Green Trending Icon
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFFDCFCE7), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            TrendingUpVector(tint = Color(0xFF16A34A))
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "You stayed on this problem for $durationMins minutes before switching apps.",
                                color = Color(0xFF1E1B4B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Sessions without a switch tend to finish about 2x faster.",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // Card 2: Pink/Coral Notification Bell Card (Image 1 Screen 8)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFFF1F2),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFFECDD3), Color(0xFFFDA4AF))),
                        width = 1.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Circular Pink Bell Icon
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFFFFE4E6), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            NotificationBellVector(tint = Color(0xFFE11D48))
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "You received $notifs notifications during your study session.",
                                color = Color(0xFF1E1B4B),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Fewer interruptions usually lead to better focus.",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            // Card 3: Blue On-Device Privacy/Usage Card (Image 1 Screen 8)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFEFF6FF),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFBFDBFE), Color(0xFF93C5FD))),
                        width = 1.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Square Blue Info Icon
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFDBEAFE), shape = RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            DeviceInsightVector(tint = Color(0xFF2563EB))
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = "These insights are generated on your device using your app usage during study sessions.",
                            color = Color(0xFF475569),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrendingUpVector(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeW = 2.2.dp.toPx()
        val path = Path().apply {
            moveTo(2.dp.toPx(), 14.dp.toPx())
            lineTo(7.dp.toPx(), 9.dp.toPx())
            lineTo(11.dp.toPx(), 13.dp.toPx())
            lineTo(18.dp.toPx(), 5.dp.toPx())
        }
        drawPath(path, color = tint, style = Stroke(width = strokeW, cap = StrokeCap.Round))

        // Arrow head
        drawLine(tint, Offset(13.dp.toPx(), 5.dp.toPx()), Offset(18.dp.toPx(), 5.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(tint, Offset(18.dp.toPx(), 5.dp.toPx()), Offset(18.dp.toPx(), 10.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

@Composable
fun NotificationBellVector(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeW = 2.dp.toPx()
        val cx = size.width / 2f

        val bellPath = Path().apply {
            moveTo(cx, 3.dp.toPx())
            cubicTo(cx - 5.dp.toPx(), 3.dp.toPx(), cx - 6.dp.toPx(), 9.dp.toPx(), cx - 7.dp.toPx(), 13.dp.toPx())
            lineTo(cx + 7.dp.toPx(), 13.dp.toPx())
            cubicTo(cx + 6.dp.toPx(), 9.dp.toPx(), cx + 5.dp.toPx(), 3.dp.toPx(), cx, 3.dp.toPx())
            close()
        }
        drawPath(bellPath, color = tint, style = Stroke(width = strokeW))

        // Clapper
        drawCircle(color = tint, radius = 1.5.dp.toPx(), center = Offset(cx, 16.dp.toPx()))
    }
}

@Composable
fun DeviceInsightVector(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val strokeW = 1.8.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
            size = Size(14.dp.toPx(), 14.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = strokeW)
        )
        drawLine(tint, Offset(5.dp.toPx(), 6.dp.toPx()), Offset(13.dp.toPx(), 6.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(tint, Offset(5.dp.toPx(), 10.dp.toPx()), Offset(10.dp.toPx(), 10.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}
