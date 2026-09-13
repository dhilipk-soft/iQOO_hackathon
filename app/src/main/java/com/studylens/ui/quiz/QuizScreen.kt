package com.studylens.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylens.shared.QuizQuestion

@Composable
fun QuizScreen(
    questions: List<QuizQuestion>,
    selectedAnswers: Map<Long, String>,
    isSubmitted: Boolean,
    isGeneratingQuiz: Boolean = false,
    onSelectOption: (Long, String) -> Unit,
    onSubmitQuiz: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentQuestionIndex by remember(questions) { mutableIntStateOf(0) }

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
                text = "Diagnostic Quiz",
                color = Color(0xFF1E1B4B),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))

            if (!isGeneratingQuiz && questions.isNotEmpty()) {
                val safeIndex = currentQuestionIndex.coerceIn(0, questions.size - 1)
                Text(
                    text = "${safeIndex + 1} of ${questions.size}",
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Spacer(modifier = Modifier.size(38.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isGeneratingQuiz) {
            // Modern AI Generation Loading State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color(0xFFEEF2FF),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI TUTOR GENERATOR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5),
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        CircularProgressIndicator(
                            color = Color(0xFF4F46E5),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Synthesizing Personalized Quiz",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Analyzing your recent Socratic conversation and on-device knowledge model to generate conceptual multi-choice questions...",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else if (questions.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Questions Available",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Start a discussion in the Study Chat to allow your AI Tutor to craft diagnostic quizzes from your learning topics.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Back to Study")
                        }
                    }
                }
            }
        } else {
            val safeIndex = currentQuestionIndex.coerceIn(0, questions.size - 1)
            val currentQuestion = questions[safeIndex]
            val optionsList = currentQuestion.options.orEmpty()
            val selectedOption = selectedAnswers[currentQuestion.id]

            // Progress Bar
            LinearProgressIndicator(
                progress = { ((safeIndex + 1).toFloat() / questions.size.toFloat()).coerceIn(0.1f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF4F46E5),
                trackColor = Color(0xFFE2E8F0)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Topic Badge
            Surface(
                color = Color(0xFFEEF2FF),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = currentQuestion.topic.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4F46E5),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Question Text
            Text(
                text = currentQuestion.question,
                color = Color(0xFF1E1B4B),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Options List (A, B, C, D)
            val optionLabels = listOf("A", "B", "C", "D")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                optionsList.forEachIndexed { index, optionText ->
                    val label = optionLabels.getOrElse(index) { "${index + 1}" }
                    val isSelected = selectedOption == optionText

                    Surface(
                        onClick = { onSelectOption(currentQuestion.id, optionText) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = optionText,
                                color = if (isSelected) Color(0xFF1E1B4B) else Color(0xFF334155),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Navigation Row: [← Previous] [Next → / Submit Quiz]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (safeIndex > 0) {
                    OutlinedButton(
                        onClick = { currentQuestionIndex-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4F46E5))
                    ) {
                        Text("← Previous", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (safeIndex < questions.size - 1) {
                    Button(
                        onClick = { currentQuestionIndex++ },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Next Question →", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onSubmitQuiz,
                        modifier = Modifier
                            .weight(if (safeIndex > 0) 1.5f else 1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Submit Quiz (${selectedAnswers.size}/${questions.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

