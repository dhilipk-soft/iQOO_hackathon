package com.studylens.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylens.input.data.ConceptMasteryEntity
import com.studylens.input.data.ExamPlanEntity
import com.studylens.input.data.StudentProfileEntity
import com.studylens.ui.onboarding.ProfileTopBarPill

@Composable
fun ExamPlannerScreen(
    activeProfile: StudentProfileEntity?,
    examPlan: ExamPlanEntity?,
    conceptMasteries: List<ConceptMasteryEntity>,
    onUpdateExamPlan: (daysRemaining: Int, dailyMinutes: Int, planBreakdown: String, targetScore: Int) -> Unit = { _, _, _, _ -> },
    onOpenProfileDialog: () -> Unit = {},
    onLaunchStudySession: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    var editExamName by remember(examPlan) { mutableStateOf(examPlan?.examName ?: activeProfile?.targetExam ?: "Next Exam") }
    var editDays by remember(examPlan) { mutableStateOf(examPlan?.daysRemaining?.toString() ?: "30") }
    var editDailyMins by remember(examPlan) { mutableStateOf(examPlan?.dailyMinutes?.toString() ?: "60") }
    var editTargetScore by remember(examPlan) { mutableStateOf(examPlan?.targetScore?.toString() ?: "90") }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = "Update Exam Target",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editExamName,
                        onValueChange = { editExamName = it },
                        label = { Text("Exam / Test Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDays,
                        onValueChange = { editDays = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Days Remaining") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDailyMins,
                        onValueChange = { editDailyMins = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Daily Study Budget (Minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editTargetScore,
                        onValueChange = { editTargetScore = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Target Score (%)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val days = editDays.toIntOrNull() ?: 30
                        val mins = editDailyMins.toIntOrNull() ?: 60
                        val target = editTargetScore.toIntOrNull() ?: 90

                        // Dynamically generate plan breakdown from student's weakest topics
                        val weakest = conceptMasteries.minByOrNull { it.masteryScore }
                        val primarySubject = activeProfile?.subjects?.firstOrNull() ?: "Core Subject"
                        val dynamicPlan = if (weakest != null && weakest.masteryScore < 60) {
                            "${mins / 3}m Remedial Practice (${weakest.concept}) ||| ${mins / 3}m Socratic Problem Solving ($primarySubject) ||| ${mins / 3}m Spaced Diagnostic Quiz"
                        } else {
                            "${mins / 2}m Core Topic Acceleration ($primarySubject) ||| ${mins / 2}m Socratic Application & Retrieval Practice"
                        }

                        onUpdateExamPlan(days, mins, dynamicPlan, target)
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save & Recalculate Plan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FE))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Exam-Aware Adaptive Planner",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "Dynamic Schedule Driven by Your Input & Mastery",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                ProfileTopBarPill(
                    activeProfile = activeProfile,
                    onClick = onOpenProfileDialog
                )
            }
        }

        // 2. Exam Target Card with Edit button
        item {
            val now = System.currentTimeMillis()
            val realDaysLeft = if ((activeProfile?.examDate ?: 0L) > now) {
                ((activeProfile!!.examDate - now) / 86400000L).coerceAtLeast(1).toInt()
            } else {
                examPlan?.daysRemaining ?: 30
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = examPlan?.examName ?: activeProfile?.targetExam ?: "Next Exam Target",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "${activeProfile?.name ?: "Student"} • ${activeProfile?.institution ?: "Level"} (${activeProfile?.stream ?: "Track"})",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEEF2FF)
                        ) {
                            Text(
                                text = "${realDaysLeft}d left",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("DAILY BUDGET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                Text("${examPlan?.dailyMinutes ?: 60} mins", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("TARGET SCORE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                Text("${examPlan?.targetScore ?: 90}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("REVISION LOOP", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                Text("Spaced (1-3-7d)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6366F1))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4F46E5))
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit Target Exam & Daily Budget", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Dynamic Rebalancing Explainer Banner
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF0FDF4),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFBBF7D0)),
                    width = 1.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Dynamic Re-balancing: As you master concepts or make mistakes during Socratic chat, your daily timetable automatically shifts to focus on struggle areas without changing your exam deadline.",
                        fontSize = 11.sp,
                        color = Color(0xFF15803D),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // 4. Today's Time-Blocked Schedule
        item {
            Text(
                text = "Today's Time-Blocked Study Plan",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
        }

        val subject1 = activeProfile?.subjects?.getOrNull(0) ?: "Core Subjects"
        val subject2 = activeProfile?.subjects?.getOrNull(1) ?: "Active Practice"
        val totalMins = examPlan?.dailyMinutes ?: activeProfile?.dailyMinutes ?: 60
        val defaultBlocks = listOf(
            "${totalMins / 3}m Socratic Exploration & Problem Solving ($subject1)",
            "${totalMins / 3}m Weak Concept Reinforcement ($subject2)",
            "${totalMins / 3}m Spaced Diagnostic Quiz"
        )
        val blocks = examPlan?.currentDayPlan?.split("|||")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: defaultBlocks

        items(blocks.size) { index ->
            val block = blocks[index]
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEEF2FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = Color(0xFF4F46E5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = block,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1E293B)
                        )
                        val subtitle = if (block.contains("Remedial")) {
                            "Targeted struggle area fix based on your quiz & chat logs"
                        } else if (block.contains("Advanced") || block.contains("Acceleration")) {
                            "High mastery detected: Accelerated pace"
                        } else {
                            "Personalized curriculum recommendation"
                        }
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = if (block.contains("Remedial")) Color(0xFFEF4444) else Color(0xFF64748B)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = Color(0xFF4F46E5),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 5. Start Session CTA
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onLaunchStudySession,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Today's Time-Blocked Session", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
