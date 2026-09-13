package com.studylens.ui.explanation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.lazy.LazyColumn
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
import com.studylens.shared.ExplanationResult
import com.studylens.ui.FollowUpMessage

@Composable
fun ExplanationScreen(
    capturedText: String,
    explanationResult: ExplanationResult?,
    isExplaining: Boolean,
    isSpeaking: Boolean,
    onSpeak: (String) -> Unit,
    onStopSpeak: () -> Unit,
    followUpList: List<FollowUpMessage>,
    isAnsweringFollowUp: Boolean,
    onAskFollowUp: (String) -> Unit,
    onGenerateQuiz: () -> Unit,
    onSimulateNetworkToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var followUpInput by remember { mutableStateOf("") }
    var showCapturedOriginal by remember { mutableStateOf(false) }

    val explanationText = explanationResult?.finalExplanation ?: ""
    val isOnlineContext = explanationResult?.usedOnlineContext == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Top Bar with Demo Badge (TODAY'S #1 PRIORITY)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prominent Badge: "📴 Offline answer" vs "📡 Enhanced with live info"
                    Surface(
                        color = if (isOnlineContext) Color(0xFF0C4A6E) else Color(0xFF064E3B),
                        shape = RoundedCornerShape(16.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.5.dp,
                            brush = Brush.horizontalGradient(
                                if (isOnlineContext)
                                    listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
                                else
                                    listOf(Color(0xFF34D399), Color(0xFF10B981))
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isOnlineContext) "📡 Enhanced with live info" else "📴 Offline answer",
                                color = if (isOnlineContext) Color(0xFF7DD3FC) else Color(0xFF6EE7B7),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Test toggle for demo presentation (allows switching mode without breaking device state)
                    TextButton(
                        onClick = onSimulateNetworkToggle,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                    ) {
                        Text(
                            text = "Toggle Mode",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 2. Collapsible Original Text Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Original Textbook Snippet",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { showCapturedOriginal = !showCapturedOriginal },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (showCapturedOriginal) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand/Collapse",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }
                        if (showCapturedOriginal) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = capturedText,
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // 3. Main Explanation Card with TTS Speak Bar
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF334155), Color(0xFF475569))
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Concept Breakdown",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Speak / Stop Button
                            FilledTonalButton(
                                onClick = {
                                    if (isSpeaking) onStopSpeak() else onSpeak(explanationText)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSpeaking) Color(0xFFDC2626) else Color(0xFF0284C7),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) Icons.Default.Clear else Icons.Default.PlayArrow,
                                    contentDescription = "Speak explanation",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSpeaking) "Stop" else "Listen",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isExplaining) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color(0xFF38BDF8),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Synthesizing explanation locally...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            }
                        } else {
                            Text(
                                text = explanationText.ifBlank { "No explanation generated yet." },
                                color = Color(0xFFF1F5F9),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            // 4. Follow-Up Questions History
            if (followUpList.isNotEmpty()) {
                item {
                    Text(
                        text = "Follow-Up Discussion",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                items(followUpList) { msg ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF131D2E))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Q: ${msg.question}",
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = msg.answer,
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 5. Follow-Up Input Row (Text Field + Voice Mic Button + Ask Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = followUpInput,
                onValueChange = { followUpInput = it },
                placeholder = { Text("Ask follow-up (e.g. give an example)...", fontSize = 12.sp, color = Color(0xFF64748B)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrect = false,
                    imeAction = ImeAction.Send
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    // Voice Mic Button (Never replaces text field)
                    IconButton(
                        onClick = {
                            followUpInput = "Can you give me a simple step-by-step example?"
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Voice Input",
                            tint = Color(0xFF38BDF8)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A)
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (followUpInput.isNotBlank()) {
                        onAskFollowUp(followUpInput)
                        followUpInput = ""
                    }
                },
                enabled = followUpInput.isNotBlank() && !isAnsweringFollowUp,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                modifier = Modifier.height(52.dp)
            ) {
                if (isAnsweringFollowUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Ask", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
