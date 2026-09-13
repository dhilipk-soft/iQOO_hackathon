package com.studylens.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    onNavigateToModelPicker: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
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
                text = "Settings",
                color = Color(0xFF1E1B4B),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Settings Items Card (Image 1 Screen 9)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF))),
                width = 1.dp
            ),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingsRowItem(
                    iconBg = Color(0xFFE0E7FF),
                    icon = "👤",
                    title = "Student Profile & Streams",
                    onClick = onNavigateToProfile
                )

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                SettingsRowItem(
                    iconBg = Color(0xFFEDE9FE),
                    icon = "🧠",
                    title = "Choose AI Model",
                    onClick = onNavigateToModelPicker
                )

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                SettingsRowItem(
                    iconBg = Color(0xFFEEF2FF),
                    icon = "ℹ️",
                    title = "About StudyLens",
                    onClick = {}
                )

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                SettingsRowItem(
                    iconBg = Color(0xFFEDE9FE),
                    icon = "🛡️",
                    title = "Privacy",
                    onClick = {}
                )

                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                SettingsRowItem(
                    iconBg = Color(0xFFE0E7FF),
                    icon = "💬",
                    title = "Help & Feedback",
                    onClick = {}
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Brand Footer Section (Image 1 Screen 9)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // [ 📖 ] Bracketed Book Logo
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFEEF2FF), Color(0xFFC7D2FE))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                SettingsBookLogoIcon()
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "StudyLens",
                color = Color(0xFF1E1B4B),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Your Offline AI Study Companion",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "No account. No cloud.\nEvery explanation, answer, and quiz\nis generated on this phone.",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "v1.0.0",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SettingsRowItem(
    iconBg: Color,
    icon: String,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconBg, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = title,
                color = Color(0xFF1E1B4B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SettingsBookLogoIcon() {
    Canvas(modifier = Modifier.size(26.dp)) {
        val strokeW = 2.dp.toPx()
        val color = Color(0xFF4F46E5)
        val cx = size.width / 2f
        val topY = 4.dp.toPx()
        val botY = size.height - 4.dp.toPx()

        // Left page
        val leftPage = Path().apply {
            moveTo(cx, botY)
            cubicTo(cx - 4.dp.toPx(), botY - 2.dp.toPx(), 4.dp.toPx(), botY - 1.dp.toPx(), 3.dp.toPx(), botY - 4.dp.toPx())
            lineTo(3.dp.toPx(), topY + 2.dp.toPx())
            cubicTo(4.dp.toPx(), topY + 5.dp.toPx(), cx - 4.dp.toPx(), topY + 4.dp.toPx(), cx, topY + 6.dp.toPx())
            close()
        }
        drawPath(leftPage, color = color, style = Stroke(width = strokeW))

        // Right page
        val rightPage = Path().apply {
            moveTo(cx, botY)
            cubicTo(cx + 4.dp.toPx(), botY - 2.dp.toPx(), size.width - 4.dp.toPx(), botY - 1.dp.toPx(), size.width - 3.dp.toPx(), botY - 4.dp.toPx())
            lineTo(size.width - 3.dp.toPx(), topY + 2.dp.toPx())
            cubicTo(size.width - 4.dp.toPx(), topY + 5.dp.toPx(), cx + 4.dp.toPx(), topY + 4.dp.toPx(), cx, topY + 6.dp.toPx())
            close()
        }
        drawPath(rightPage, color = color, style = Stroke(width = strokeW))
    }
}
