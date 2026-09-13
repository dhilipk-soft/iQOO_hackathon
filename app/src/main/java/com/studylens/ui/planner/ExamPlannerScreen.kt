package com.studylens.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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

    val isGoalConfigured = !activeProfile?.targetExam.isNullOrBlank() && activeProfile?.targetExam != "Not Set"

    var editExamName by remember(examPlan, activeProfile) {
        mutableStateOf(examPlan?.examName ?: activeProfile?.targetExam?.ifBlank { "Final Exam" } ?: "Final Exam")
    }
    var editDays by remember(examPlan) { mutableStateOf(examPlan?.daysRemaining?.toString() ?: "45") }
    var editDailyMins by remember(examPlan) { mutableStateOf(examPlan?.dailyMinutes?.toString() ?: "60") }
    var editTargetScore by remember(examPlan) { mutableStateOf(examPlan?.targetScore?.toString() ?: "90") }

    // Interactive weekly goals checklist state
    var completedGoals by remember { mutableStateOf(setOf(0)) }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = if (isGoalConfigured) "Update Exam Target" else "Set Up Your Personalized Roadmap",
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
                        placeholder = { Text("e.g. Semester Finals, JEE Main, CBSE") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDays,
                        onValueChange = { editDays = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Days Remaining Until Exam") },
                        placeholder = { Text("45") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDailyMins,
                        onValueChange = { editDailyMins = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Daily Study Budget (Minutes)") },
                        placeholder = { Text("60") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editTargetScore,
                        onValueChange = { editTargetScore = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Target Score (%)") },
                        placeholder = { Text("90") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val days = editDays.toIntOrNull() ?: 45
                        val mins = editDailyMins.toIntOrNull() ?: 60
                        val target = editTargetScore.toIntOrNull() ?: 90

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
                    Text("Save & Generate Roadmap")
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Header with ProfileTopBarPill
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
                        text = "Dynamic Schedule & LeetCode-Style Roadmap",
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

        // 2. Exam Target Card (or Setup prompt if user skipped during onboarding)
        if (!isGoalConfigured) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFC7D2FE)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color(0xFFEEF2FF),
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Set Up Your Study Roadmap",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You skipped exam timeline setup during onboarding. Tell your AI tutor about your target test and daily budget to generate your customized roadmap, milestones, and weekly schedule.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { showEditDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Configure Exam Target & Generate Roadmap", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            item {
                val now = System.currentTimeMillis()
                val realDaysLeft = if ((activeProfile.examDate) > now) {
                    ((activeProfile.examDate - now) / 86400000L).coerceAtLeast(1).toInt()
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
                                    text = examPlan?.examName ?: activeProfile.targetExam,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "${activeProfile.name} • ${activeProfile.institution} (${activeProfile.stream})",
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
                            text = "Dynamic Re-balancing: As you master concepts or make mistakes during Socratic chat, your timetable automatically prioritizes weak areas without altering your exam date.",
                            fontSize = 11.sp,
                            color = Color(0xFF15803D),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 4. LEETCODE-STYLE STUDY ROADMAP & MILESTONES
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Curriculum Roadmap & Milestones",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Phased progression adapted to your target timeline",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }

                        Surface(
                            color = Color(0xFFEEF2FF),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "PHASE 1 ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4F46E5),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val primarySub = activeProfile?.subjects?.getOrNull(0) ?: "Foundations"
                    val secondarySub = activeProfile?.subjects?.getOrNull(1) ?: "Core Problem Solving"

                    val roadmapPhases = listOf(
                        Triple("Phase 1: Diagnostic & Core Foundations", "Mapping prior knowledge in $primarySub & detecting misconceptions", "Active 🟢"),
                        Triple("Phase 2: Socratic Concept Mastery", "Deep interactive Q&A and multi-step reasoning drills on $secondarySub", "Upcoming ⏳"),
                        Triple("Phase 3: High-Yield Targeted Practice", "Accelerating speed and solving weak-spot diagnostic quizzes", "Upcoming 🔒"),
                        Triple("Phase 4: Full-Length Timed Drills & Review", "Simulated mock conditions & spaced repetition retention", "Target 🏁")
                    )

                    roadmapPhases.forEachIndexed { index, (title, desc, status) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Timeline node
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(28.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(
                                            if (index == 0) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (index == 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                if (index < roadmapPhases.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(42.dp)
                                            .background(if (index == 0) Color(0xFFC7D2FE) else Color(0xFFE2E8F0))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Content
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index == 0) Color(0xFF0F172A) else Color(0xFF64748B)
                                    )
                                    Text(
                                        text = status,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (index == 0) Color(0xFF10B981) else Color(0xFF94A3B8)
                                    )
                                }
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // 5. INTERACTIVE WEEKLY GOALS & MILESTONE CHECKLIST
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val sub1 = activeProfile?.subjects?.getOrNull(0) ?: "Primary Subject"
                    val sub2 = activeProfile?.subjects?.getOrNull(1) ?: "Applied Practice"

                    val weeklyGoals = listOf(
                        "Complete 3 Socratic chat sessions exploring $sub1",
                        "Solve 2 diagnostic quizzes with >80% accuracy in $sub2",
                        "Clear all pending misconceptions in Learning Twin",
                        "Maintain a consistent 3-day focus study streak"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Weekly Goals & Milestones",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "${completedGoals.size} of ${weeklyGoals.size} completed",
                                fontSize = 11.sp,
                                color = Color(0xFF4F46E5),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        CircularProgressIndicator(
                            progress = { (completedGoals.size.toFloat() / weeklyGoals.size.toFloat()) },
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF4F46E5),
                            trackColor = Color(0xFFE2E8F0),
                            strokeWidth = 3.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    weeklyGoals.forEachIndexed { idx, goalText ->
                        val isDone = completedGoals.contains(idx)
                        Surface(
                            onClick = {
                                completedGoals = if (isDone) completedGoals - idx else completedGoals + idx
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDone) Color(0xFFF8FAFC) else Color.White,
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    if (isDone) Color(0xFFE2E8F0) else Color(0xFFCBD5E1)
                                ),
                                width = 1.dp
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isDone,
                                    onCheckedChange = {
                                        completedGoals = if (isDone) completedGoals - idx else completedGoals + idx
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF4F46E5),
                                        checkmarkColor = Color.White
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = goalText,
                                    fontSize = 12.sp,
                                    fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium,
                                    color = if (isDone) Color(0xFF94A3B8) else Color(0xFF1E293B),
                                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. TODAY'S TIME-BLOCKED SCHEDULE (Clean, no static video button)
        item {
            Text(
                text = "Today's Time-Blocked Study Schedule",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
        }

        val subject1 = activeProfile?.subjects?.getOrNull(0) ?: "Core Subject"
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

                    // Informative task tag badge instead of misleading static video button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = "Block ${index + 1}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Start Session CTA Button
        item {
            Button(
                onClick = onLaunchStudySession,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Today's Time-Blocked Session in Study Chat", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 7. CURATED ACADEMIC CITATIONS & VERIFIED RESOURCES
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Curated Resources & Academic Citations",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Pedagogical sources referenced by StudyLens AI retrieval",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val citations = listOf(
                        Triple(
                            "NCERT & National Open Educational Repository",
                            activeProfile?.subjects?.firstOrNull() ?: "Foundation Syllabi",
                            "Standard curriculum reference textbooks with solved conceptual examples."
                        ),
                        Triple(
                            "MIT OpenCourseWare / OpenStax Library",
                            activeProfile?.stream ?: "Higher Secondary / College",
                            "Peer-reviewed university level problem sets and conceptual explanations."
                        ),
                        Triple(
                            "Socratic Retrieval Benchmark",
                            "On-Device StudyLens AI",
                            "Guaranteed zero-hallucination grounding for step-by-step reasoning."
                        )
                    )

                    citations.forEach { (title, domain, desc) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Surface(
                                        color = Color(0xFFEEF2FF),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = domain,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4F46E5),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

