package com.studylens.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ValuePropItem(
    val title: String,
    val iconType: IconType
)

enum class IconType {
    SCAN_BOOK,
    LIGHTBULB,
    CHAT_QUESTION,
    QUIZ_CHECK,
    FOCUS_TARGET
}

@Composable
fun HomeScreen(
    activeProfile: com.studylens.input.data.StudentProfileEntity? = null,
    onOpenProfileDialog: () -> Unit = {},
    onGetStarted: () -> Unit,
    isFocusModeActive: Boolean = false,
    onTriggerIntentToSwitch: () -> Unit = {},
    onTakeBreak: () -> Unit = {},
    onEmergencyExit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val items = listOf(
        ValuePropItem("Scan any textbook page or problem", IconType.SCAN_BOOK),
        ValuePropItem("Get simple explanations", IconType.LIGHTBULB),
        ValuePropItem("Ask follow-up questions", IconType.CHAT_QUESTION),
        ValuePropItem("Take a quick quiz", IconType.QUIZ_CHECK),
        ValuePropItem("Stay focused with insights", IconType.FOCUS_TARGET)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEFF2FE), // Calming soft periwinkle ambient tint
                        Color(0xFFF8F9FD),
                        Color(0xFFFFFFFF)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Focus Guard Active Status Bar (visible during active study focus)
            AnimatedVisibility(visible = isFocusModeActive) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E1B4B),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF22C55E), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🛡️ Focus Guard Active",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                onClick = onTriggerIntentToSwitch,
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF312E81)
                            ) {
                                Text(
                                    "Switch 🧠",
                                    fontSize = 10.sp,
                                    color = Color(0xFFA5B4FC),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                onClick = onTakeBreak,
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF312E81)
                            ) {
                                Text(
                                    "Break ☕",
                                    fontSize = 10.sp,
                                    color = Color(0xFFA5B4FC),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                onClick = onEmergencyExit,
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF450A0A)
                            ) {
                                Text(
                                    "Exit 🚨",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFCA5A5),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Upper Section: Logo + Title + Features
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (activeProfile != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFFE0E7FF), Color(0xFFEDE9FE))),
                            width = 1.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProfileDialog() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4F46E5)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeProfile.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Welcome, ${activeProfile.name}!",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E1B4B)
                                )
                                Text(
                                    text = "${activeProfile.institution} (${activeProfile.stream}) • ${activeProfile.targetExam}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Text(
                                text = "Switch ▾",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hero Logo: Brackets with open book inside
                StudyLensHeroLogo(
                    modifier = Modifier.size(88.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // App Title
                Text(
                    text = "StudyLens",
                    color = Color(0xFF1E1B4B), // Deep Obsidian Indigo
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtitle
                Text(
                    text = "Your Offline AI Study Companion",
                    color = Color(0xFF64748B), // Soft Slate
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Feature List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items.forEach { item ->
                        FeatureRow(item = item)
                    }
                }
            }

            // Lower Section: CTA + Privacy Note
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 8.dp)
            ) {
                // "Get Started →" Button
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5) // Iris Royal Indigo
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 3.dp,
                        pressedElevation = 1.dp
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (activeProfile != null) "Continue to Study Chat" else "Set Up Your Personal AI Tutor",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Trust / Privacy Guarantee Copy
                Text(
                    text = "No account. No cloud.\nEvery explanation, answer, and quiz\nis generated on this phone.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(item: ValuePropItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Box with soothing lavender container
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEEF2FF)), // Soft soothing tinted periwinkle
            contentAlignment = Alignment.Center
        ) {
            FeatureIconCanvas(
                type = item.iconType,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = item.title,
            color = Color(0xFF1E293B), // Deep slate
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun StudyLensHeroLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 3.5.dp.toPx()
        val cornerArm = 20.dp.toPx()
        val radius = 8.dp.toPx()
        val primaryColor = Color(0xFF4F46E5)

        // Subtle glow backdrop
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFEDE9FE), Color(0x00EDE9FE)),
                center = Offset(w / 2, h / 2),
                radius = w * 0.7f
            )
        )

        // 1. Top-Left bracket
        val tlPath = Path().apply {
            moveTo(0f, cornerArm)
            lineTo(0f, radius)
            quadraticBezierTo(0f, 0f, radius, 0f)
            lineTo(cornerArm, 0f)
        }
        drawPath(tlPath, primaryColor, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // 2. Top-Right bracket
        val trPath = Path().apply {
            moveTo(w - cornerArm, 0f)
            lineTo(w - radius, 0f)
            quadraticBezierTo(w, 0f, w, radius)
            lineTo(w, cornerArm)
        }
        drawPath(trPath, primaryColor, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // 3. Bottom-Left bracket
        val blPath = Path().apply {
            moveTo(0f, h - cornerArm)
            lineTo(0f, h - radius)
            quadraticBezierTo(0f, h, radius, h)
            lineTo(cornerArm, h)
        }
        drawPath(blPath, primaryColor, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // 4. Bottom-Right bracket
        val brPath = Path().apply {
            moveTo(w - cornerArm, h)
            lineTo(w - radius, h)
            quadraticBezierTo(w, h, w, h - radius)
            lineTo(w, h - cornerArm)
        }
        drawPath(brPath, primaryColor, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Open Book Graphic inside the brackets
        val centerX = w / 2
        val centerY = h / 2
        val bookW = w * 0.44f
        val bookH = h * 0.36f
        val bookTop = centerY - bookH / 2
        val bookBottom = centerY + bookH / 2

        // Left page
        val leftPage = Path().apply {
            moveTo(centerX, bookBottom)
            quadraticBezierTo(centerX - bookW * 0.4f, bookBottom + 2.dp.toPx(), centerX - bookW, bookBottom - 2.dp.toPx())
            lineTo(centerX - bookW, bookTop)
            quadraticBezierTo(centerX - bookW * 0.4f, bookTop + 4.dp.toPx(), centerX, bookTop + 2.dp.toPx())
            close()
        }
        drawPath(leftPage, primaryColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Right page
        val rightPage = Path().apply {
            moveTo(centerX, bookBottom)
            quadraticBezierTo(centerX + bookW * 0.4f, bookBottom + 2.dp.toPx(), centerX + bookW, bookBottom - 2.dp.toPx())
            lineTo(centerX + bookW, bookTop)
            quadraticBezierTo(centerX + bookW * 0.4f, bookTop + 4.dp.toPx(), centerX, bookTop + 2.dp.toPx())
            close()
        }
        drawPath(rightPage, primaryColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Book spine divider
        drawLine(
            color = primaryColor,
            start = Offset(centerX, bookTop + 2.dp.toPx()),
            end = Offset(centerX, bookBottom),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun FeatureIconCanvas(type: IconType, modifier: Modifier = Modifier) {
    val iconColor = Color(0xFF4F46E5)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()

        when (type) {
            IconType.SCAN_BOOK -> {
                // Book outline
                val left = Path().apply {
                    moveTo(w * 0.5f, h * 0.85f)
                    lineTo(w * 0.15f, h * 0.75f)
                    lineTo(w * 0.15f, h * 0.25f)
                    lineTo(w * 0.5f, h * 0.35f)
                    close()
                }
                val right = Path().apply {
                    moveTo(w * 0.5f, h * 0.85f)
                    lineTo(w * 0.85f, h * 0.75f)
                    lineTo(w * 0.85f, h * 0.25f)
                    lineTo(w * 0.5f, h * 0.35f)
                    close()
                }
                drawPath(left, iconColor, style = Stroke(stroke, cap = StrokeCap.Round))
                drawPath(right, iconColor, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            IconType.LIGHTBULB -> {
                // Shield / Explanation icon
                val shield = Path().apply {
                    moveTo(w * 0.5f, h * 0.15f)
                    lineTo(w * 0.85f, h * 0.3f)
                    quadraticBezierTo(w * 0.85f, h * 0.75f, w * 0.5f, h * 0.9f)
                    quadraticBezierTo(w * 0.15f, h * 0.75f, w * 0.15f, h * 0.3f)
                    close()
                }
                drawPath(shield, iconColor, style = Stroke(stroke, cap = StrokeCap.Round))
                // Center check
                val check = Path().apply {
                    moveTo(w * 0.35f, h * 0.52f)
                    lineTo(w * 0.47f, h * 0.63f)
                    lineTo(w * 0.65f, h * 0.42f)
                }
                drawPath(check, iconColor, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            IconType.CHAT_QUESTION -> {
                // Speech bubble
                val bubble = Path().apply {
                    moveTo(w * 0.2f, h * 0.2f)
                    lineTo(w * 0.8f, h * 0.2f)
                    quadraticBezierTo(w * 0.9f, h * 0.2f, w * 0.9f, h * 0.35f)
                    lineTo(w * 0.9f, h * 0.65f)
                    quadraticBezierTo(w * 0.9f, h * 0.8f, w * 0.8f, h * 0.8f)
                    lineTo(w * 0.45f, h * 0.8f)
                    lineTo(w * 0.25f, h * 0.92f)
                    lineTo(w * 0.25f, h * 0.8f)
                    lineTo(w * 0.2f, h * 0.8f)
                    quadraticBezierTo(w * 0.1f, h * 0.8f, w * 0.1f, h * 0.65f)
                    lineTo(w * 0.1f, h * 0.35f)
                    quadraticBezierTo(w * 0.1f, h * 0.2f, w * 0.2f, h * 0.2f)
                    close()
                }
                drawPath(bubble, iconColor, style = Stroke(stroke, cap = StrokeCap.Round))
                // Question dot & curve
                drawCircle(color = iconColor, radius = 1.2.dp.toPx(), center = Offset(w * 0.5f, h * 0.63f))
                drawLine(
                    color = iconColor,
                    start = Offset(w * 0.5f, h * 0.52f),
                    end = Offset(w * 0.5f, h * 0.43f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            IconType.QUIZ_CHECK -> {
                // Clipboard with check
                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(w * 0.2f, h * 0.2f),
                    size = Size(w * 0.6f, h * 0.65f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                // Check mark inside
                val check = Path().apply {
                    moveTo(w * 0.35f, h * 0.53f)
                    lineTo(w * 0.47f, h * 0.63f)
                    lineTo(w * 0.65f, h * 0.42f)
                }
                drawPath(check, iconColor, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            IconType.FOCUS_TARGET -> {
                // Crosshair / Target concentric rings
                drawCircle(color = iconColor, radius = w * 0.38f, style = Stroke(stroke))
                drawCircle(color = iconColor, radius = w * 0.16f, style = Stroke(stroke))
                drawCircle(color = iconColor, radius = 2.dp.toPx())
            }
        }
    }
}
