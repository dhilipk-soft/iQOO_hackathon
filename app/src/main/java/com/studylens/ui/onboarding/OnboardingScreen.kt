package com.studylens.ui.onboarding

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylens.input.data.StudentProfileEntity
import com.studylens.ui.StudyViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    viewModel: StudyViewModel,
    onFinish: () -> Unit
) {
    val allProfiles by viewModel.allStudentProfiles.collectAsState()
    val activeProfile by viewModel.activeStudentProfile.collectAsState()

    var isCreatingNew by remember { mutableStateOf(false) }

    // Form inputs
    var name by remember { mutableStateOf("") }
    var selectedInstitution by remember { mutableStateOf("Class 12") }
    var customInstitution by remember { mutableStateOf("") }

    var selectedStream by remember { mutableStateOf("Science (PCM)") }
    var customStream by remember { mutableStateOf("") }

    val availableSubjects = listOf(
        "Physics", "Mathematics", "Chemistry", "Biology",
        "Computer Science", "Economics", "Accountancy", "History"
    )
    val selectedSubjects = remember { mutableStateListOf("Physics", "Mathematics") }

    var targetExam by remember { mutableStateOf("CBSE Board Exam") }
    var daysRemaining by remember { mutableStateOf("45") }
    var dailyMinutes by remember { mutableStateOf("60") }

    val institutionPresets = listOf("Class 10", "Class 11", "Class 12", "Undergrad / College", "Competitive Exam")
    val streamPresets = listOf("Science (PCM)", "Science (PCB)", "Commerce", "Engineering", "Arts / Humanities")
    val examPresets = listOf("CBSE Board Exam", "JEE Main & Advanced", "NEET-UG", "College Semester", "General Knowledge")
    val dailyMinutePresets = listOf("30", "45", "60", "90", "120")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 40.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Banner
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))),
                                RoundedCornerShape(16.dp)
                            )
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                text = "STUDYLENS AI PROFILE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA5B4FC),
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Personalized Learning Twin",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sign in with your student profile or create a custom personalized AI agent for your exam stream.",
                                fontSize = 13.sp,
                                color = Color(0xFFE0E7FF),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // Existing Profiles (Multi-User Login Switcher)
            if (allProfiles.isNotEmpty()) {
                item {
                    Text(
                        text = "SELECT AN ACTIVE PROFILE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        allProfiles.forEach { profile ->
                            val isSelected = activeProfile?.id == profile.id
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFEEF2FF) else Color.White
                                ),
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4F46E5))
                                } else {
                                    androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.switchStudentProfile(profile.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                if (isSelected) Color(0xFF4F46E5) else Color(0xFFE0E7FF),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = if (isSelected) Color.White else Color(0xFF4338CA)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = profile.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "${profile.institution} • ${profile.stream}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = "Target: ${profile.targetExam} (${profile.dailyMinutes}m/day)",
                                            fontSize = 11.sp,
                                            color = Color(0xFF4F46E5),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(Color(0xFF4F46E5), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onFinish,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text(
                            text = "Continue with ${activeProfile?.name ?: "Selected Profile"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Create New Profile Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCreatingNew) "ENTER STUDENT DETAILS" else "WANT TO ADD ANOTHER USER?",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.sp
                    )
                    TextButton(onClick = { isCreatingNew = !isCreatingNew }) {
                        Text(
                            text = if (isCreatingNew) "Cancel" else "+ Add New Profile",
                            color = Color(0xFF4F46E5),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // New Profile Form
            if (isCreatingNew || allProfiles.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Personalized Agent Setup",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )

                            // 1. Student Name
                            Column {
                                Text(
                                    text = "Your Full Name",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF334155)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    placeholder = { Text("e.g. Aarav Sharma or Priya Patel") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                            }

                            // 2. Class / College
                            Column {
                                Text(
                                    text = "Class / College Level",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF334155)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    institutionPresets.forEach { inst ->
                                        val isSel = selectedInstitution == inst
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { selectedInstitution = inst },
                                            label = { Text(inst, fontSize = 12.sp) },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }

                            // 3. Stream / Domain
                            Column {
                                Text(
                                    text = "Stream / Academic Track",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF334155)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    streamPresets.forEach { str ->
                                        val isSel = selectedStream == str
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { selectedStream = str },
                                            label = { Text(str, fontSize = 12.sp) },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }

                            // 4. Subjects to Learn
                            Column {
                                Text(
                                    text = "Subjects You Are Learning",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF334155)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    availableSubjects.forEach { sub ->
                                        val isSel = selectedSubjects.contains(sub)
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                if (isSel) {
                                                    if (selectedSubjects.size > 1) selectedSubjects.remove(sub)
                                                } else {
                                                    selectedSubjects.add(sub)
                                                }
                                            },
                                            label = { Text(sub, fontSize = 12.sp) },
                                            leadingIcon = if (isSel) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }

                            // 5. Target Exam / Entrance
                            Column {
                                Text(
                                    text = "Target Exam / Next Test",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF334155)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    examPresets.forEach { ex ->
                                        val isSel = targetExam == ex
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { targetExam = ex },
                                            label = { Text(ex, fontSize = 12.sp) },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = targetExam,
                                    onValueChange = { targetExam = it },
                                    placeholder = { Text("Or enter custom exam name...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                            }

                            // 6. Days Remaining & Daily Study Minutes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Days Until Exam",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = daysRemaining,
                                        onValueChange = { daysRemaining = it.filter { ch -> ch.isDigit() } },
                                        placeholder = { Text("30") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = true
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Daily Goal (Mins)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = dailyMinutes,
                                        onValueChange = { dailyMinutes = it.filter { ch -> ch.isDigit() } },
                                        placeholder = { Text("60") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = true
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Save Button
                            Button(
                                onClick = {
                                    val finalName = name.ifBlank { "Student ${allProfiles.size + 1}" }
                                    val days = daysRemaining.toIntOrNull() ?: 30
                                    val mins = dailyMinutes.toIntOrNull() ?: 60
                                    val examTimestamp = System.currentTimeMillis() + (days.toLong() * 86400000L)

                                    viewModel.createStudentProfile(
                                        name = finalName,
                                        institution = selectedInstitution,
                                        stream = selectedStream,
                                        subjects = selectedSubjects.toList(),
                                        targetExam = targetExam.ifBlank { "Next Exam" },
                                        examDate = examTimestamp,
                                        dailyMinutes = mins
                                    )
                                    onFinish()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                            ) {
                                Text(
                                    text = "Start Personalized AI Learning",
                                    fontSize = 15.sp,
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
