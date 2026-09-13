package com.studylens.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
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
import com.studylens.input.data.StudentProfileEntity
import com.studylens.ui.StudyViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: StudyViewModel,
    onFinish: () -> Unit
) {
    val allProfiles by viewModel.allStudentProfiles.collectAsState()
    val activeProfile by viewModel.activeStudentProfile.collectAsState()

    var isCreatingNew by remember { mutableStateOf(allProfiles.isEmpty()) }
    var currentStep by remember { mutableIntStateOf(1) } // 1: Academic Profile, 2: Study Goals & Timeline

    // Cascading Data Dictionaries
    val levelOptions = listOf(
        "Class 9 - 10 (Secondary)",
        "Class 11 - 12 (Higher Secondary)",
        "Undergraduate / College",
        "Competitive Exam Aspirant"
    )

    val streamsByLevel = mapOf(
        "Class 9 - 10 (Secondary)" to listOf(
            "General Foundation (CBSE/ICSE)",
            "Science & Math Focus",
            "Social Sciences & Languages"
        ),
        "Class 11 - 12 (Higher Secondary)" to listOf(
            "Science (PCM - Physics, Chem, Math)",
            "Science (PCB - Physics, Chem, Biology)",
            "Science (PCMB - All Sciences)",
            "Commerce (Accountancy & Business)",
            "Arts & Humanities"
        ),
        "Undergraduate / College" to listOf(
            "Computer Science & Engineering",
            "Electronics / Electrical Engineering",
            "Mechanical / Civil Engineering",
            "Medicine & Life Sciences (MBBS/BSc)",
            "Commerce & Management (BBA/BCom)",
            "Liberal Arts & Humanities"
        ),
        "Competitive Exam Aspirant" to listOf(
            "JEE Main & Advanced (Engineering)",
            "NEET-UG (Medical)",
            "GATE (Engineering Graduate)",
            "UPSC Civil Services",
            "CAT (Management Entrance)",
            "CUET (University Entrance)"
        )
    )

    val subjectsByStream = mapOf(
        "General Foundation (CBSE/ICSE)" to listOf("Mathematics", "Science", "Social Science", "English"),
        "Science & Math Focus" to listOf("Physics", "Chemistry", "Mathematics", "Biology"),
        "Social Sciences & Languages" to listOf("History", "Geography", "Civics", "English"),
        "Science (PCM - Physics, Chem, Math)" to listOf("Physics", "Chemistry", "Mathematics"),
        "Science (PCB - Physics, Chem, Biology)" to listOf("Physics", "Chemistry", "Biology"),
        "Science (PCMB - All Sciences)" to listOf("Physics", "Chemistry", "Mathematics", "Biology"),
        "Commerce (Accountancy & Business)" to listOf("Accountancy", "Business Studies", "Economics", "Applied Mathematics"),
        "Arts & Humanities" to listOf("History", "Political Science", "Psychology", "Economics"),
        "Computer Science & Engineering" to listOf("Data Structures & Algorithms", "Operating Systems", "Computer Networks", "Database Systems", "Mathematics"),
        "Electronics / Electrical Engineering" to listOf("Circuit Theory", "Digital Electronics", "Signals & Systems", "Electromagnetics"),
        "Mechanical / Civil Engineering" to listOf("Thermodynamics", "Fluid Mechanics", "Engineering Mechanics", "Materials Science"),
        "Medicine & Life Sciences (MBBS/BSc)" to listOf("Anatomy", "Physiology", "Biochemistry", "Pathology"),
        "Commerce & Management (BBA/BCom)" to listOf("Financial Accounting", "Marketing Management", "Corporate Finance", "Business Law"),
        "Liberal Arts & Humanities" to listOf("Philosophy", "Modern History", "International Relations", "Macroeconomics"),
        "JEE Main & Advanced (Engineering)" to listOf("Advanced Physics", "Organic & Physical Chemistry", "Calculus & Coordinate Geometry"),
        "NEET-UG (Medical)" to listOf("Botany", "Zoology", "Organic Chemistry", "Physics for Medical"),
        "GATE (Engineering Graduate)" to listOf("Algorithms", "Computer Architecture", "Theory of Computation", "Operating Systems"),
        "UPSC Civil Services" to listOf("Indian Polity & Constitution", "Modern Indian History", "Geography", "Indian Economy"),
        "CAT (Management Entrance)" to listOf("Quantitative Aptitude", "Verbal Ability & Reading", "Data Interpretation & Logical Reasoning"),
        "CUET (University Entrance)" to listOf("Domain Subjects", "General Aptitude", "Language Comprehension")
    )

    val examsByLevel = mapOf(
        "Class 9 - 10 (Secondary)" to listOf("CBSE Class 10 Board", "ICSE Board", "Midterm Assessment", "Olympiad"),
        "Class 11 - 12 (Higher Secondary)" to listOf("CBSE Class 12 Board", "ISC Board", "JEE Main", "NEET-UG"),
        "Undergraduate / College" to listOf("Semester End Exams", "Mid-Semester Exam", "Technical Placements", "GATE"),
        "Competitive Exam Aspirant" to listOf("JEE Main / Advanced", "NEET-UG", "GATE", "UPSC Prelims", "CAT")
    )

    // Form State
    var name by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf(levelOptions[2]) } // Default Undergrad
    var isLevelDropdownExpanded by remember { mutableStateOf(false) }

    // Stream state dynamically tracks Level
    val currentStreams = streamsByLevel[selectedLevel] ?: streamsByLevel.values.first()
    var selectedStream by remember { mutableStateOf(currentStreams.first()) }
    var isStreamDropdownExpanded by remember { mutableStateOf(false) }

    // When Level changes, automatically reset stream to first item of new level
    LaunchedEffect(selectedLevel) {
        val available = streamsByLevel[selectedLevel] ?: emptyList()
        if (selectedStream !in available && available.isNotEmpty()) {
            selectedStream = available.first()
        }
    }

    // Subjects list dynamically initialized to the selected stream's subjects
    val selectedSubjects = remember { mutableStateListOf<String>() }
    var customSubjectInput by remember { mutableStateOf("") }

    LaunchedEffect(selectedStream) {
        selectedSubjects.clear()
        val defaultSubs = subjectsByStream[selectedStream] ?: listOf("General Subject")
        selectedSubjects.addAll(defaultSubs)
    }

    // Step 2 State
    val availableExams = examsByLevel[selectedLevel] ?: listOf("Next Exam Target")
    var targetExam by remember { mutableStateOf(availableExams.first()) }
    var isExamDropdownExpanded by remember { mutableStateOf(false) }
    var customExamInput by remember { mutableStateOf("") }

    var daysRemaining by remember { mutableStateOf("45") }
    var dailyMinutes by remember { mutableStateOf("60") }

    val daysPresets = listOf("15", "30", "45", "60", "90", "180")
    val minutesPresets = listOf("30", "45", "60", "90", "120")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 36.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Existing Profiles Switcher (if users already exist and we aren't mid-step)
            if (allProfiles.isNotEmpty() && !isCreatingNew) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "SELECT ACTIVE STUDENT PROFILE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.sp
                        )

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
                                        val examText = if (profile.targetExam.isNotBlank() && profile.targetExam != "Not Set") {
                                            "Target: ${profile.targetExam} (${profile.dailyMinutes}m/day)"
                                        } else {
                                            "Exam goal not configured yet"
                                        }
                                        Text(
                                            text = examText,
                                            fontSize = 11.sp,
                                            color = Color(0xFF4F46E5),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

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

                        OutlinedButton(
                            onClick = {
                                isCreatingNew = true
                                currentStep = 1
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4F46E5))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("+ Create Another Student Profile", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // ==========================================
                // 2-STEP ONBOARDING WIZARD
                // ==========================================

                // Stepper Header
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (allProfiles.isNotEmpty()) {
                                TextButton(onClick = { isCreatingNew = false }) {
                                    Text("← Switch Profile", color = Color(0xFF64748B), fontSize = 12.sp)
                                }
                            } else {
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFEEF2FF)
                            ) {
                                Text(
                                    text = if (currentStep == 1) "STEP 1 OF 2: ACADEMIC PROFILE" else "STEP 2 OF 2: STUDY TIMELINE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4F46E5),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress Indicator Bar
                        LinearProgressIndicator(
                            progress = { if (currentStep == 1) 0.5f else 1.0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF4F46E5),
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }

                if (currentStep == 1) {
                    // ==========================================
                    // STEP 1: NAME, LEVEL, STREAM, SUBJECTS
                    // ==========================================
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Tell us about yourself",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "Your AI tutor personalizes its teaching speed and curriculum based on your academic stage.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }

                                // 1. Name Input
                                Column {
                                    Text(
                                        text = "Full Name",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        placeholder = { Text("e.g. Alex, Maya, or Rahul") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = true
                                    )
                                }

                                // 2. Class / College Level Dropdown
                                Column {
                                    Text(
                                        text = "Class / College Level",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    ExposedDropdownMenuBox(
                                        expanded = isLevelDropdownExpanded,
                                        onExpandedChange = { isLevelDropdownExpanded = !isLevelDropdownExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = selectedLevel,
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLevelDropdownExpanded) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = isLevelDropdownExpanded,
                                            onDismissRequest = { isLevelDropdownExpanded = false }
                                        ) {
                                            levelOptions.forEach { level ->
                                                DropdownMenuItem(
                                                    text = { Text(level, fontSize = 13.sp) },
                                                    onClick = {
                                                        selectedLevel = level
                                                        isLevelDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. Stream / Academic Track Dropdown (CASCADING)
                                Column {
                                    Text(
                                        text = "Stream / Academic Track",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    ExposedDropdownMenuBox(
                                        expanded = isStreamDropdownExpanded,
                                        onExpandedChange = { isStreamDropdownExpanded = !isStreamDropdownExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = selectedStream,
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isStreamDropdownExpanded) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = isStreamDropdownExpanded,
                                            onDismissRequest = { isStreamDropdownExpanded = false }
                                        ) {
                                            currentStreams.forEach { streamItem ->
                                                DropdownMenuItem(
                                                    text = { Text(streamItem, fontSize = 13.sp) },
                                                    onClick = {
                                                        selectedStream = streamItem
                                                        isStreamDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // 4. Enrolled Subjects (CASCADING + CUSTOM ENTRY)
                                Column {
                                    Text(
                                        text = "Enrolled Learning Subjects",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )
                                    Text(
                                        text = "Tap to toggle or add custom subjects tailored to your syllabus:",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        selectedSubjects.forEach { sub ->
                                            InputChip(
                                                selected = true,
                                                onClick = {
                                                    if (selectedSubjects.size > 1) {
                                                        selectedSubjects.remove(sub)
                                                    }
                                                },
                                                label = { Text(sub, fontSize = 12.sp) },
                                                trailingIcon = {
                                                    Text("✕", fontSize = 11.sp, color = Color(0xFF64748B))
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = InputChipDefaults.inputChipColors(
                                                    selectedContainerColor = Color(0xFFEEF2FF),
                                                    selectedLabelColor = Color(0xFF4F46E5)
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Add Custom Subject Field
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = customSubjectInput,
                                            onValueChange = { customSubjectInput = it },
                                            placeholder = { Text("+ Add another subject...", fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            singleLine = true
                                        )
                                        Button(
                                            onClick = {
                                                val clean = customSubjectInput.trim()
                                                if (clean.isNotBlank() && clean !in selectedSubjects) {
                                                    selectedSubjects.add(clean)
                                                    customSubjectInput = ""
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                                        ) {
                                            Text("Add", fontSize = 12.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Proceed to Step 2 Button
                                Button(
                                    onClick = {
                                        if (name.isBlank()) {
                                            name = "Student ${allProfiles.size + 1}"
                                        }
                                        currentStep = 2
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                                ) {
                                    Text(
                                        text = "Next: Study Goals & Timeline →",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // STEP 2: EXAM TARGET, TIMELINE & DAILY BUDGET
                    // ==========================================
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { currentStep = 1 },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "Study Goals & Timeline",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "Configure your target exam or skip to set it up later in the Plan tab.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                // Target Exam Dropdown
                                Column {
                                    Text(
                                        text = "Target Exam / Next Test",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    ExposedDropdownMenuBox(
                                        expanded = isExamDropdownExpanded,
                                        onExpandedChange = { isExamDropdownExpanded = !isExamDropdownExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = targetExam,
                                            onValueChange = {},
                                            readOnly = true,
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExamDropdownExpanded) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        ExposedDropdownMenu(
                                            expanded = isExamDropdownExpanded,
                                            onDismissRequest = { isExamDropdownExpanded = false }
                                        ) {
                                            availableExams.forEach { ex ->
                                                DropdownMenuItem(
                                                    text = { Text(ex, fontSize = 13.sp) },
                                                    onClick = {
                                                        targetExam = ex
                                                        isExamDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = customExamInput,
                                        onValueChange = {
                                            customExamInput = it
                                            if (it.isNotBlank()) targetExam = it
                                        },
                                        placeholder = { Text("Or type custom exam name...", fontSize = 12.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }

                                // Days Remaining Presets + Input
                                Column {
                                    Text(
                                        text = "Days Remaining Until Exam",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        daysPresets.forEach { d ->
                                            val isSel = daysRemaining == d
                                            FilterChip(
                                                selected = isSel,
                                                onClick = { daysRemaining = d },
                                                label = { Text("${d}d", fontSize = 12.sp) },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = daysRemaining,
                                        onValueChange = { daysRemaining = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Custom Days") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }

                                // Daily Study Budget Presets + Input
                                Column {
                                    Text(
                                        text = "Daily Study Budget",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        minutesPresets.forEach { m ->
                                            val isSel = dailyMinutes == m
                                            FilterChip(
                                                selected = isSel,
                                                onClick = { dailyMinutes = m },
                                                label = { Text("${m} mins", fontSize = 12.sp) },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = dailyMinutes,
                                        onValueChange = { dailyMinutes = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("Custom Daily Minutes") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Complete Setup CTA
                                Button(
                                    onClick = {
                                        val finalName = name.ifBlank { "Student ${allProfiles.size + 1}" }
                                        val days = daysRemaining.toIntOrNull() ?: 45
                                        val mins = dailyMinutes.toIntOrNull() ?: 60
                                        val examTimestamp = System.currentTimeMillis() + (days.toLong() * 86400000L)

                                        viewModel.createStudentProfile(
                                            name = finalName,
                                            institution = selectedLevel,
                                            stream = selectedStream,
                                            subjects = selectedSubjects.toList(),
                                            targetExam = targetExam.ifBlank { "Target Exam" },
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
                                        text = "Complete Onboarding & Launch Tutor",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Skip for Now (Configure in Plan tab later)
                                TextButton(
                                    onClick = {
                                        val finalName = name.ifBlank { "Student ${allProfiles.size + 1}" }
                                        viewModel.createStudentProfile(
                                            name = finalName,
                                            institution = selectedLevel,
                                            stream = selectedStream,
                                            subjects = selectedSubjects.toList(),
                                            targetExam = "", // Unset so Plan tab prompts setup
                                            examDate = 0L,
                                            dailyMinutes = 60
                                        )
                                        onFinish()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Skip for now (Set up goals in Plan tab later)",
                                        color = Color(0xFF64748B),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
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

