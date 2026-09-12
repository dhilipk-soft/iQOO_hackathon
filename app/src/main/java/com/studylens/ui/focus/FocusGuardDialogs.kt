package com.studylens.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun FocusGuardDialogHost(
    dialogPhase: FocusGuardDialogPhase?,
    onSelectIntent: (SwitchIntent) -> Unit,
    onAnswerCheck: (String) -> Unit,
    onStayReview: () -> Unit,
    onOverrideQuiz: () -> Unit,
    onSelectBreakDuration: (Long) -> Unit,
    onSelectResearchDuration: (Long) -> Unit = onSelectBreakDuration,
    onConfirmEmergencyExit: () -> Unit,
    onDismiss: () -> Unit
) {
    if (dialogPhase == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp)),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            when (dialogPhase) {
                is FocusGuardDialogPhase.SelectIntent -> {
                    SelectIntentDialogContent(
                        onSelectIntent = onSelectIntent,
                        onDismiss = onDismiss
                    )
                }
                is FocusGuardDialogPhase.QuickFocusCheck -> {
                    QuickFocusCheckDialogContent(
                        phase = dialogPhase,
                        onAnswerSelected = onAnswerCheck,
                        onTakeBreak = { onSelectBreakDuration(60000L) },
                        onDismiss = onDismiss
                    )
                }
                is FocusGuardDialogPhase.ChooseBreakDuration -> {
                    ChooseBreakDurationDialogContent(
                        isAfterQuiz = dialogPhase.isAfterPassedQuiz,
                        onSelectDuration = onSelectBreakDuration,
                        onDismiss = onDismiss
                    )
                }
                is FocusGuardDialogPhase.EmergencyExitConfirm -> {
                    EmergencyExitDialogContent(
                        onConfirm = onConfirmEmergencyExit,
                        onDismiss = onDismiss
                    )
                }
                is FocusGuardDialogPhase.IncorrectAnswerReview -> {
                    IncorrectAnswerReviewDialogContent(
                        onStay = onStayReview,
                        onOverride = onOverrideQuiz,
                        onTakeBreak = { onSelectBreakDuration(60000L) }
                    )
                }
                is FocusGuardDialogPhase.StudyNeedAllowed -> {
                    StudyNeedAllowedDialogContent(
                        note = dialogPhase.note,
                        onDismiss = onDismiss
                    )
                }
                is FocusGuardDialogPhase.ChooseResearchDuration -> {
                    ChooseResearchDurationDialogContent(
                        onSelectDuration = onSelectResearchDuration,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun SelectIntentDialogContent(
    onSelectIntent: (SwitchIntent) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFEEF2FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🧠", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Why are you switching?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E1B4B)
                    )
                    Text(
                        text = "StudyLens AI Focus Guard",
                        fontSize = 12.sp,
                        color = Color(0xFF6366F1),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "We don't simply block your apps. Let us know your intent so we can respect your study plan, break, or emergency.",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IntentOptionRow(
                icon = "📖",
                title = "I need this for my study",
                subtitle = "Look up a reference or search a topic",
                badge = "Productive",
                badgeColor = Color(0xFF10B981),
                onClick = { onSelectIntent(SwitchIntent.STUDY_NEED) }
            )
            IntentOptionRow(
                icon = "☕",
                title = "I need a short break",
                subtitle = "Take a quick timed rest with a return reminder",
                badge = "Planned",
                badgeColor = Color(0xFF6366F1),
                onClick = { onSelectIntent(SwitchIntent.SHORT_BREAK) }
            )
            IntentOptionRow(
                icon = "🚨",
                title = "Urgent emergency call",
                subtitle = "Exit Focus Mode immediately with zero delay",
                badge = "Immediate",
                badgeColor = Color(0xFFEF4444),
                onClick = { onSelectIntent(SwitchIntent.EMERGENCY_CALL) }
            )
            IntentOptionRow(
                icon = "⚠️",
                title = "I'm getting distracted",
                subtitle = "Take a 10-second Quick Focus Check first",
                badge = "Focus Check",
                badgeColor = Color(0xFFF59E0B),
                onClick = { onSelectIntent(SwitchIntent.DISTRACTED) }
            )
            IntentOptionRow(
                icon = "⚙️",
                title = "Normal override",
                subtitle = "Switch apps anyway without quiz",
                badge = "Override",
                badgeColor = Color(0xFF64748B),
                onClick = { onSelectIntent(SwitchIntent.IMPORTANT_NOTIFICATION) }
            )
        }
    }
}

@Composable
private fun IntentOptionRow(
    icon: String,
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF))),
            width = 1.dp
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White, CircleShape)
                    .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                    Surface(
                        color = badgeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
fun QuickFocusCheckDialogContent(
    phase: FocusGuardDialogPhase.QuickFocusCheck,
    onAnswerSelected: (String) -> Unit,
    onTakeBreak: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }
    val question = phase.question
    val options = question.options.orEmpty()

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFFFEF3C7), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "QUICK FOCUS CHECK",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFB45309),
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "You were studying ${question.topic}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = question.question,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1B4B),
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        val letters = listOf("A", "B", "C", "D")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEachIndexed { index, option ->
                val isSelected = selectedOption == option
                val letter = letters.getOrElse(index) { "${index + 1}" }

                Surface(
                    onClick = { selectedOption = option },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) Color(0xFFEEF2FF) else Color(0xFFF8FAFC),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            if (isSelected) listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                            else listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF))
                        ),
                        width = if (isSelected) 1.5.dp else 1.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF475569)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = option,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = Color(0xFF1E293B)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                selectedOption?.let { onAnswerSelected(it) }
            },
            enabled = selectedOption != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4F46E5),
                disabledContainerColor = Color(0xFFCBD5E1)
            )
        ) {
            Text("Submit Answer", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = onTakeBreak,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Tired? Take a planned break instead",
                fontSize = 12.sp,
                color = Color(0xFF6366F1),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ChooseBreakDurationDialogContent(
    isAfterQuiz: Boolean,
    onSelectDuration: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        if (isAfterQuiz) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFDCFCE7), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0xFF16A34A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "✓ Correct! Recall verified.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534)
                    )
                    Text(
                        text = "You can take a short break.",
                        fontSize = 11.sp,
                        color = Color(0xFF15803D)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "⏰ When should I remind you to return?",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1B4B)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "StudyLens will send a gentle heads-up notification when your break ends.",
            fontSize = 12.sp,
            color = Color(0xFF64748B)
        )

        Spacer(modifier = Modifier.height(18.dp))

        val breakOptions = listOf(
            Triple("10 seconds (Demo test)", 10000L, "⚡ Instant testing"),
            Triple("1 minute break", 60000L, "☕ Quick stretch"),
            Triple("5 minutes break", 300000L, "🚶 Short walk"),
            Triple("Until I return manually", 0L, "🕒 No reminder")
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            breakOptions.forEach { (label, durationMs, hint) ->
                Surface(
                    onClick = { onSelectDuration(durationMs) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF))),
                        width = 1.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = hint,
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Text(
                            text = "Start",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        TextButton(
            onClick = { onSelectDuration(0L) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip reminder", color = Color(0xFF94A3B8), fontSize = 13.sp)
        }
    }
}

@Composable
fun EmergencyExitDialogContent(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFFFEE2E2), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "🚨 Need to leave urgently?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1B4B)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You can turn off Focus Mode immediately. Your choice will be respected and recorded as an emergency exit, which will NOT negatively impact your focus streak.",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
        ) {
            Text("TURN OFF FOCUS MODE", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stay in Focus Mode", color = Color(0xFF64748B), fontSize = 13.sp)
        }
    }
}

@Composable
fun IncorrectAnswerReviewDialogContent(
    onStay: () -> Unit,
    onOverride: () -> Unit,
    onTakeBreak: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "Not quite yet 💡",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1B4B)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "You missed the question. Would you like to stay for a quick review, take a planned rest, or switch anyway?",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onStay,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text("Stay & Review Topic", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = onTakeBreak,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Take a Planned Break", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4F46E5))
        }

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(
            onClick = onOverride,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Switch Anyway (Override)", fontSize = 12.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
fun StudyNeedAllowedDialogContent(
    note: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = "📖 Productive Study Need",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF047857)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = note,
            fontSize = 13.sp,
            color = Color(0xFF334155),
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text("Continue Research", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ChooseResearchDurationDialogContent(
    onSelectDuration: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFECFDF5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📖", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Productive Research",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857)
                    )
                    Text(
                        text = "Set a return time so you don't lose focus",
                        fontSize = 11.sp,
                        color = Color(0xFF059669)
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "At what time do you need to return to your study content?",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1E293B)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "StudyLens will send a reminder when we think your research is done!",
            fontSize = 11.sp,
            color = Color(0xFF64748B),
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ResearchDurationOption(
                label = "15 Seconds (Quick Lookup / Demo)",
                sublabel = "Trigger quick chime: 'We think research is done'",
                tag = "⚡ Recommended",
                onClick = { onSelectDuration(15_000L) }
            )
            ResearchDurationOption(
                label = "30 Seconds (Definition / Formula)",
                sublabel = "Look up a quick term or mathematical equation",
                tag = null,
                onClick = { onSelectDuration(30_000L) }
            )
            ResearchDurationOption(
                label = "1 Minute (Article / Diagram)",
                sublabel = "Check sample problems or diagrams in your browser",
                tag = null,
                onClick = { onSelectDuration(60_000L) }
            )
            ResearchDurationOption(
                label = "2 Minutes (Deep Reference)",
                sublabel = "Thorough reference reading before returning",
                tag = null,
                onClick = { onSelectDuration(120_000L) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = { onSelectDuration(0L) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open reference without timer", fontSize = 12.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun ResearchDurationOption(
    label: String,
    sublabel: String,
    tag: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF8FAFC),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))),
            width = 1.dp
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    if (tag != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = sublabel,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
            Text("➔", fontSize = 14.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ContextRecapDialog(
    recapInfo: ContextRecapInfo?,
    onRequestRecap: () -> Unit,
    onDismiss: () -> Unit
) {
    if (recapInfo == null) return

    val awayMins = (recapInfo.awayDurationMs / 60000L)
    val awaySecs = (recapInfo.awayDurationMs % 60000L) / 1000L
    val durationText = if (awayMins > 0) "${awayMins}m ${awaySecs}s" else "${awaySecs}s"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp)),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                val (headerIcon, headerTitle, headerSubtitle) = when {
                    recapInfo.isAppHoppingDetected -> Triple(
                        "⚠️",
                        "App Hopping Detected",
                        "${recapInfo.hopCount} rapid app switches in deep focus"
                    )
                    recapInfo.wasPlannedBreak && recapInfo.returnedOnTime -> Triple(
                        "👋",
                        "Welcome back!",
                        "You were away for $durationText"
                    )
                    recapInfo.wasPlannedBreak && !recapInfo.returnedOnTime -> Triple(
                        "⏱️",
                        "Welcome back!",
                        "Overdue return after $durationText away"
                    )
                    else -> Triple(
                        "📚",
                        "Refocus Your Mind",
                        "Returned to study after $durationText away"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(headerIcon, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = headerTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (recapInfo.isAppHoppingDetected) Color(0xFFDC2626) else Color(0xFF1E1B4B)
                            )
                            Text(
                                text = headerSubtitle,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Context Status Banner
                if (recapInfo.isAppHoppingDetected) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFFFECDD3), Color(0xFFF87171))),
                            width = 1.dp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚠️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Rapid App Hopping Detected (${recapInfo.hopCount} switches)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B)
                                )
                                Text(
                                    text = "Break the distraction loop! Gemma recovered your study context to help you refocus immediately.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }
                    }
                } else if (recapInfo.wasPlannedBreak && recapInfo.returnedOnTime) {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFF86EFAC), Color(0xFF22C55E))),
                            width = 1.dp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✓", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Returned right on time!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                                Text(
                                    text = "You kept your break promise. High focus discipline!",
                                    fontSize = 11.sp,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }
                } else if (recapInfo.wasPlannedBreak && !recapInfo.returnedOnTime) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFFFECDD3), Color(0xFFF87171))),
                            width = 1.dp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏱️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Returned +${recapInfo.delaySecs}s overdue",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B)
                                )
                                Text(
                                    text = "Ready to reset and dive back into deep focus?",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFFEEF2FF),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFFC7D2FE), Color(0xFFA5B4FC))),
                            width = 1.dp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💡", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Ready to Resume Study",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3730A3)
                                )
                                Text(
                                    text = "Gemma loaded your 10-second topic recap to get you back in the flow.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF4338CA)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ACTIVE STUDY TOPIC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = recapInfo.topic,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (recapInfo.recapText != null) {
                    Surface(
                        color = Color(0xFFEEF2FF),
                        shape = RoundedCornerShape(14.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFFC7D2FE), Color(0xFF818CF8))),
                            width = 1.dp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✨", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "10-Second Context Recovery",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4338CA)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = recapInfo.recapText,
                                fontSize = 13.sp,
                                color = Color(0xFF1E1B4B),
                                lineHeight = 19.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("Resume Studying", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (recapInfo.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF4F46E5), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Restoring study context...",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Would you like a 10-second AI recap from Gemma to smoothly transition your mind back into learning?",
                        fontSize = 13.sp,
                        color = Color(0xFF475569),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = onRequestRecap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("✨ Quick Recap", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue without recap", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
