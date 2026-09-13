package com.studylens.ui.twin

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylens.input.data.ConceptMasteryEntity
import com.studylens.input.data.MisconceptionLogEntity
import com.studylens.input.data.PendingQuizEntity
import com.studylens.input.data.QuizAttemptEntity
import com.studylens.input.data.StudentProfileEntity

import com.studylens.ui.onboarding.ProfileTopBarPill

@Composable
fun LearningTwinScreen(
    activeProfile: StudentProfileEntity?,
    allProfiles: List<StudentProfileEntity> = emptyList(),
    conceptMasteries: List<ConceptMasteryEntity>,
    misconceptions: List<MisconceptionLogEntity>,
    pendingQuizzes: List<PendingQuizEntity> = emptyList(),
    quizAttempts: List<QuizAttemptEntity> = emptyList(),
    onSwitchProfile: (String) -> Unit,
    onStartQuiz: (PendingQuizEntity) -> Unit = {},
    onOpenProfileDialog: () -> Unit = {},
    onResetDemo: () -> Unit = {},
    onStartSocraticChat: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FE))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Sleek Header with Professional Branding & Profile Top Bar Pill
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        com.studylens.ui.navigation.TwinBrainIcon(tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Learning Twin",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Adaptive Diagnostic Engine",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                ProfileTopBarPill(
                    activeProfile = activeProfile,
                    onClick = onOpenProfileDialog
                )
            }
        }

        // 2. Tab Navigation: "Quizzes to Attend", "Attended Quizzes", "Concept Mastery"
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tabs = listOf(
                        "Quizzes to Attend" to pendingQuizzes.size,
                        "Attended Quizzes" to quizAttempts.size,
                        "Concept Mastery" to conceptMasteries.size
                    )

                    tabs.forEachIndexed { index, (label, count) ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF4F46E5) else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF64748B)
                                )
                                if (count > 0) {
                                    Text(
                                        text = "($count)",
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color(0xFFE0E7FF) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // TAB CONTENT
        when (selectedTab) {
            0 -> {
                // TAB 0: QUIZZES TO ATTEND (Based on user's chat history)
                if (pendingQuizzes.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "All Caught Up!",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Quizzes are generated directly from topics you discuss in the Study chat. Ask a question or snap notes to unlock new diagnostic quizzes!",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onStartSocraticChat,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                                ) {
                                    Text(
                                        text = "Ask a Question in Study Chat",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "DIAGNOSTIC QUIZZES QUEUED FROM YOUR CHATS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.8.sp
                        )
                    }

                    items(pendingQuizzes) { quiz ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = Color(0xFFEEF2FF),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = quiz.topic.uppercase(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4F46E5),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Ready to test",
                                            fontSize = 11.sp,
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = quiz.concept,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "Generated from chat discussion",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                Button(
                                    onClick = { onStartQuiz(quiz) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                                ) {
                                    Text("Take Quiz", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // TAB 1: ATTENDED QUIZZES (Historical quiz answers & results)
                if (quizAttempts.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Quizzes Attended Yet",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Once you complete quizzes, your questions, answers, and score adjustments will be logged here.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "QUIZ HISTORY & ACCURACY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.8.sp
                        )
                    }

                    items(quizAttempts) { attempt ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (attempt.isCorrect) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${attempt.concept} (${attempt.topic})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Surface(
                                        color = if (attempt.isCorrect) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (attempt.isCorrect) "PASSED (+12)" else "INCORRECT (-10)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (attempt.isCorrect) Color(0xFF065F46) else Color(0xFF991B1B),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = attempt.question,
                                    fontSize = 12.sp,
                                    color = Color(0xFF334155),
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Your Answer: ${attempt.selectedAnswer}",
                                        fontSize = 11.sp,
                                        color = if (attempt.isCorrect) Color(0xFF059669) else Color(0xFFDC2626),
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (!attempt.isCorrect) {
                                        Text(
                                            text = "Correct: ${attempt.correctAnswer}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF059669),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // TAB 2: CONCEPT MASTERY MAP & SOCRATIC TUTOR
                if (conceptMasteries.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No Mastery Data Yet",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "As you ask questions in Study Chat and take quizzes, your personal AI twin extracts and models your mastery in real-time.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "EXTRACTED CONCEPT MASTERY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.8.sp
                        )
                    }

                    items(conceptMasteries) { mastery ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = mastery.concept,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "${mastery.subject} • ${mastery.topic}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Text(
                                        text = "${mastery.masteryScore}%",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = when {
                                            mastery.masteryScore >= 80 -> Color(0xFF10B981)
                                            mastery.masteryScore >= 50 -> Color(0xFFF59E0B)
                                            else -> Color(0xFFEF4444)
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { (mastery.masteryScore / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = when {
                                        mastery.masteryScore >= 80 -> Color(0xFF10B981)
                                        mastery.masteryScore >= 50 -> Color(0xFFF59E0B)
                                        else -> Color(0xFFEF4444)
                                    },
                                    trackColor = Color(0xFFF1F5F9)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Attempts: ${mastery.attempts} (✓ ${mastery.correctCount} / ✗ ${mastery.incorrectCount})",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    if (!mastery.activeMisconception.isNullOrBlank()) {
                                        Text(
                                            text = "Active Misconception",
                                            fontSize = 11.sp,
                                            color = Color(0xFFEF4444),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
