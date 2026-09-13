package com.studylens.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.studylens.input.data.StudentProfileEntity
import com.studylens.ui.StudyViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileManagementDialog(
    viewModel: StudyViewModel,
    onDismiss: () -> Unit,
    onAddNewProfile: () -> Unit
) {
    val activeProfile by viewModel.activeStudentProfile.collectAsState()
    val allProfiles by viewModel.allStudentProfiles.collectAsState()

    var isEditing by remember { mutableStateOf(false) }

    // Edit form states
    var editName by remember(activeProfile) { mutableStateOf(activeProfile?.name ?: "") }
    var editInstitution by remember(activeProfile) { mutableStateOf(activeProfile?.institution ?: "Class 12") }
    var editStream by remember(activeProfile) { mutableStateOf(activeProfile?.stream ?: "Science (PCM)") }
    var editTargetExam by remember(activeProfile) { mutableStateOf(activeProfile?.targetExam ?: "CBSE Board Exam") }
    var editDays by remember(activeProfile) {
        val now = System.currentTimeMillis()
        val days = if ((activeProfile?.examDate ?: 0L) > now) {
            ((activeProfile!!.examDate - now) / 86400000L).coerceAtLeast(1).toString()
        } else {
            "45"
        }
        mutableStateOf(days)
    }
    var editDailyMinutes by remember(activeProfile) { mutableStateOf(activeProfile?.dailyMinutes?.toString() ?: "60") }

    val institutionPresets = listOf("Class 10", "Class 11", "Class 12", "Undergrad / College", "Competitive Exam")
    val streamPresets = listOf("Science (PCM)", "Science (PCB)", "Commerce", "Engineering", "Arts / Humanities")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isEditing) "Edit Student Profile" else "Student Account Manager",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = if (isEditing) "Update your personal AI tutor settings" else "Switch accounts or customize your profile",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                if (!isEditing) {
                    // ACCOUNT SELECTION LIST
                    Text(
                        text = "ACTIVE ACCOUNTS (${allProfiles.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        letterSpacing = 1.sp
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allProfiles) { profile ->
                            val isSelected = activeProfile?.id == profile.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFEEF2FF) else Color(0xFFF8FAFC),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0)
                                    ),
                                    width = if (isSelected) 1.5.dp else 1.dp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.switchStudentProfile(profile.id)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFF4F46E5) else Color(0xFFCBD5E1)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = profile.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = profile.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "${profile.institution} • ${profile.stream}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        Text(
                                            text = "Target: ${profile.targetExam}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF4F46E5),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color(0xFF4F46E5),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons: Edit Current Profile & Add New Profile
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isEditing = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit Profile", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                onDismiss()
                                onAddNewProfile()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+ Add User", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.seedDemoPresentationData()
                                onDismiss()
                            }
                        ) {
                            Text("⚡ Reviewer Mode: Load Sample Data", fontSize = 11.sp, color = Color(0xFF6366F1), fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // EDIT PROFILE FORM
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Text("Level / Class", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            institutionPresets.forEach { inst ->
                                val isSel = editInstitution == inst
                                FilterChip(
                                    selected = isSel,
                                    onClick = { editInstitution = inst },
                                    label = { Text(inst, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Text("Stream", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            streamPresets.forEach { str ->
                                val isSel = editStream == str
                                FilterChip(
                                    selected = isSel,
                                    onClick = { editStream = str },
                                    label = { Text(str, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = editTargetExam,
                            onValueChange = { editTargetExam = it },
                            label = { Text("Target Exam") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editDays,
                                onValueChange = { editDays = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Days Left") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editDailyMinutes,
                                onValueChange = { editDailyMinutes = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Mins/Day") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = { isEditing = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back", color = Color(0xFF64748B))
                        }

                        Button(
                            onClick = {
                                val cur = activeProfile
                                if (cur != null) {
                                    val days = editDays.toIntOrNull() ?: 30
                                    val mins = editDailyMinutes.toIntOrNull() ?: 60
                                    val newExamDate = System.currentTimeMillis() + (days.toLong() * 86400000L)

                                    val updated = cur.copy(
                                        name = editName.ifBlank { cur.name },
                                        institution = editInstitution,
                                        stream = editStream,
                                        targetExam = editTargetExam.ifBlank { cur.targetExam },
                                        examDate = newExamDate,
                                        dailyMinutes = mins
                                    )
                                    viewModel.updateStudentProfile(updated)
                                }
                                isEditing = false
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (allProfiles.size > 1 && activeProfile != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteStudentProfile(activeProfile!!.id)
                                isEditing = false
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete Student Profile", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileTopBarPill(
    activeProfile: StudentProfileEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEEF2FF),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFC7D2FE)),
            width = 1.dp
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4F46E5)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = activeProfile?.name?.take(1)?.uppercase() ?: "👤",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = activeProfile?.let { "${it.name} • ${it.institution}" } ?: "Sign In / Profile",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF4338CA)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "▾",
                fontSize = 11.sp,
                color = Color(0xFF6366F1),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
