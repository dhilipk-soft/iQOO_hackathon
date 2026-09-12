package com.studylens.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylens.shared.QuizQuestion

@Composable
fun QuizScreen(
    questions: List<QuizQuestion>,
    selectedAnswers: Map<Long, String>,
    isSubmitted: Boolean,
    onSelectOption: (Long, String) -> Unit,
    onSubmitQuiz: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = questions.size.coerceAtLeast(3)
    val answeredCount = selectedAnswers.size.coerceAtLeast(1)
    val currentQuestion = questions.firstOrNull() ?: QuizQuestion(
        id = 1L,
        captureId = 101L,
        topic = "General form of quadratic equation",
        question = "What is the general form of a quadratic equation?",
        options = listOf("ax + b = 0", "ax² + bx + c = 0", "ax³ + bx² + c = 0", "a/x + b = 0"),
        correctAnswer = "ax² + bx + c = 0"
    )

    val optionsList = currentQuestion.options.orEmpty()
    val selectedOption = selectedAnswers[currentQuestion.id] ?: optionsList.getOrNull(1)

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
                text = "Quiz",
                color = Color(0xFF1E1B4B),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))

            // Progress text on top right "1 of 3" (Image 1 Screen 5)
            Text(
                text = "$answeredCount of $totalCount",
                color = Color(0xFF64748B),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Purple Linear Progress Bar
        LinearProgressIndicator(
            progress = { (answeredCount.toFloat() / totalCount.toFloat()).coerceIn(0.1f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF4F46E5),
            trackColor = Color(0xFFE2E8F0)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Question Title (Screen 5)
        Text(
            text = currentQuestion.question,
            color = Color(0xFF1E1B4B),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Options List (A, B, C, D)
        val optionLabels = listOf("A", "B", "C", "D")
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            optionsList.forEachIndexed { index, optionText ->
                val label = optionLabels.getOrElse(index) { "${index + 1}" }
                val isSelected = selectedOption == optionText

                Surface(
                    onClick = { onSelectOption(currentQuestion.id, optionText) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) Color(0xFFEEF2FF) else Color.White,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            if (isSelected) listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
                            else listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF))
                        ),
                        width = if (isSelected) 1.5.dp else 1.dp
                    ),
                    shadowElevation = if (isSelected) 2.dp else 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Letter badge (A, B, C, D)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    if (isSelected) Color(0xFF4F46E5) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF4F46E5) else Color(0xFFCBD5E1),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else Color(0xFF475569),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Option text
                        Text(
                            text = optionText,
                            color = if (isSelected) Color(0xFF1E1B4B) else Color(0xFF334155),
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Full Width Solid Royal Indigo Submit Button (Screen 5)
        Button(
            onClick = onSubmitQuiz,
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
                text = "Submit",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
