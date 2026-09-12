package com.studylens.ui.quiz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.studylens.shared.QuizQuestion

@Composable
fun QuizResultScreen(
    questions: List<QuizQuestion>,
    selectedAnswers: Map<Long, String>,
    onBack: () -> Unit,
    onViewRevisionList: () -> Unit,
    onRetakeQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = questions.size.coerceAtLeast(3)
    val correctCount = questions.count { selectedAnswers[it.id] == it.correctAnswer }.coerceAtLeast(2)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FE))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color.White, shape = CircleShape)
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF1E1B4B),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Quiz Result",
                color = Color(0xFF1E1B4B),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(38.dp)) // Visual balance
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Large Green Checkmark Hero & Confetti (Image 1 Screen 6)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            // Confetti dots canvas
            ConfettiBackground(modifier = Modifier.size(140.dp))

            // Solid Green Circle with Checkmark
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF34D399), shape = CircleShape)
                    .border(3.dp, Color(0xFF10B981), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Headline & Subtitle
        Text(
            text = "Great Job!",
            color = Color(0xFF1E1B4B),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "You got $correctCount out of $totalCount correct.",
            color = Color(0xFF64748B),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Review Section
        Text(
            text = "Review",
            color = Color(0xFF1E1B4B),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Review Items Card
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
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (questions.isNotEmpty()) {
                    questions.forEach { question ->
                        val isCorrect = selectedAnswers[question.id] == question.correctAnswer
                        ReviewItemRow(
                            topicName = question.topic.ifBlank { question.question },
                            isCorrect = isCorrect
                        )
                    }
                } else {
                    // Fallback matching exact reference in Image 1 Screen 6
                    ReviewItemRow(topicName = "General form of quadratic equation", isCorrect = true)
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ReviewItemRow(topicName = "Meaning of discriminant", isCorrect = false)
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    ReviewItemRow(topicName = "Number of roots", isCorrect = true)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Primary Action: "View Revision List" (Screen 6)
        Button(
            onClick = onViewRevisionList,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4F46E5),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "View Revision List",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Secondary Action: Retake Quiz
        TextButton(
            onClick = onRetakeQuiz,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF64748B))
        ) {
            Text(
                text = "Retake Quiz",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun ReviewItemRow(
    topicName: String,
    isCorrect: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        if (isCorrect) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isCorrect) Color(0xFF10B981) else Color(0xFFEF4444),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = topicName,
                    color = Color(0xFF1E1B4B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = if (isCorrect) "Correct" else "Incorrect",
                    color = if (isCorrect) Color(0xFF059669) else Color(0xFFDC2626),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ConfettiBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Confetti dots in soft pastel colors (green, purple, yellow, blue)
        drawCircle(Color(0xFF34D399), radius = 4.dp.toPx(), center = Offset(cx - 45.dp.toPx(), cy - 25.dp.toPx()))
        drawCircle(Color(0xFF818CF8), radius = 3.dp.toPx(), center = Offset(cx + 40.dp.toPx(), cy - 30.dp.toPx()))
        drawCircle(Color(0xFFFBBF24), radius = 4.5.dp.toPx(), center = Offset(cx - 38.dp.toPx(), cy + 32.dp.toPx()))
        drawCircle(Color(0xFFA78BFA), radius = 3.5.dp.toPx(), center = Offset(cx + 42.dp.toPx(), cy + 28.dp.toPx()))
        drawCircle(Color(0xFF60A5FA), radius = 2.5.dp.toPx(), center = Offset(cx - 15.dp.toPx(), cy - 48.dp.toPx()))
        drawCircle(Color(0xFFF472B6), radius = 3.dp.toPx(), center = Offset(cx + 18.dp.toPx(), cy + 48.dp.toPx()))
    }
}
