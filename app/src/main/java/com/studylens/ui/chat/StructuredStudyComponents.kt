package com.studylens.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylens.shared.FormulaCodeBlock
import com.studylens.shared.QuickCheckQuestion
import com.studylens.shared.StructuredStudyResponse
import com.studylens.shared.StudyIntent
import com.studylens.shared.VerifiedCitation
import com.studylens.ui.FollowUpMessage

/**
 * Enterprise Intent Selector Bar: Allows the student to select their desired pedagogical
 * learning mode or leave it on "Auto".
 */
@Composable
fun IntentSelectorStrip(
    selectedIntent: StudyIntent,
    onSelectIntent: (StudyIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StudyIntent.values().forEach { intent ->
            val isSelected = intent == selectedIntent
            Surface(
                onClick = { onSelectIntent(intent) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) Color(0xFF4F46E5) else Color.White,
                border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))),
                    width = 1.dp
                ),
                shadowElevation = if (isSelected) 3.dp else 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = intent.icon,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = intent.displayName,
                        color = if (isSelected) Color.White else Color(0xFF334155),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Verified Sources Strip: Displays capped 2-3 high authority citations as interactive cards.
 */
@Composable
fun VerifiedSourcesStrip(
    citations: List<VerifiedCitation>,
    modifier: Modifier = Modifier
) {
    if (citations.isEmpty()) return

    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Text(
                text = "📚 Verified Academic Sources",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFFEEF2FF), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${citations.size} sources",
                    fontSize = 10.sp,
                    color = Color(0xFF4F46E5),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            citations.forEach { citation ->
                Surface(
                    onClick = {
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(citation.url))
                            context.startActivity(browserIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open source URL", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))),
                        width = 1.dp
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (citation.isEducational) "🎓" else "🌐",
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = citation.domain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                maxLines = 1
                            )
                            if (citation.title.isNotBlank() && citation.title != citation.domain) {
                                Text(
                                    text = citation.title,
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "↗",
                            fontSize = 11.sp,
                            color = Color(0xFF6366F1),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formula / Code Block with one-tap copy button and distinct styling.
 */
@Composable
fun FormulaCodeBlockView(
    block: FormulaCodeBlock,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF38BDF8), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (block.isCode) "💻 CODE / IMPLEMENTATION" else "📐 FORMULA & LAW",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Formula/Code", block.content))
                        isCopied = true
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCopied) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Copied",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(12.dp)
                            )
                        } else {
                            Text(text = "📋", fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isCopied) "Copied" else "Copy",
                            fontSize = 10.sp,
                            color = if (isCopied) Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = block.content,
                color = Color(0xFFF1F5F9),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * Step-by-Step timeline showing numbered progression nodes.
 */
@Composable
fun StepTimelineView(
    steps: List<String>,
    modifier: Modifier = Modifier
) {
    if (steps.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "🔍 Step-by-Step Breakdown",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E1B4B)
        )

        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color(0xFF4F46E5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = step,
                    color = Color(0xFF334155),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Interactive Quick-Check Question with Tap-to-Reveal Answer.
 */
@Composable
fun QuickCheckCard(
    question: QuickCheckQuestion,
    modifier: Modifier = Modifier
) {
    var isRevealed by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFF0FDF4),
        shape = RoundedCornerShape(14.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(listOf(Color(0xFF86EFAC), Color(0xFF34D399))),
            width = 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "❓", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CHECK YOUR UNDERSTANDING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF166534),
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = question.question,
                color = Color(0xFF14532D),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!isRevealed) {
                Surface(
                    onClick = { isRevealed = true },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF22C55E)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tap to reveal answer 💡",
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFDCFCE7),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF86EFAC), Color(0xFF4ADE80))),
                        width = 1.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "✅ Answer:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = question.answer,
                            fontSize = 12.sp,
                            color = Color(0xFF14532D),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Enterprise Structured Study Card: Fully modular educational presentation.
 */
@Composable
fun StructuredExplanationCard(
    response: StructuredStudyResponse?,
    fallbackTitle: String,
    fallbackExplanation: String,
    usedOnlineContext: Boolean,
    citations: List<VerifiedCitation>,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
    onTakeQuiz: () -> Unit,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title = response?.title?.ifBlank { fallbackTitle } ?: fallbackTitle
    val subject = response?.subject ?: "General Study"
    val intent = response?.intent ?: StudyIntent.CONCEPT_EXPLANATION
    val coreText = response?.coreConcept?.ifBlank { fallbackExplanation } ?: fallbackExplanation
    val activeCitations = response?.citations?.takeIf { it.isNotEmpty() } ?: citations

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFFE0E7FF), Color(0xFFC7D2FE))
            ),
            width = 1.dp
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header: Subject, Intent & Engine Verification Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Subject Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9)
                    ) {
                        Text(
                            text = subject,
                            color = Color(0xFF475569),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Intent Pill
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEEF2FF),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFFC7D2FE), Color(0xFFA5B4FC))),
                            width = 1.dp
                        )
                    ) {
                        Text(
                            text = "${intent.icon} ${intent.displayName}",
                            color = Color(0xFF4F46E5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Online/Offline Verification Status
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (usedOnlineContext) Color(0xFFEEF2FF) else Color(0xFFECFDF5)
                ) {
                    Text(
                        text = if (usedOnlineContext) "📡 Verified Web" else "📴 100% On-Device",
                        color = if (usedOnlineContext) Color(0xFF4F46E5) else Color(0xFF059669),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Topic Title
            Text(
                text = title,
                color = Color(0xFF1E1B4B),
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Core Principle / Concept
            Text(
                text = coreText,
                color = Color(0xFF334155),
                fontSize = 14.sp,
                lineHeight = 22.sp
            )

            // Formula / Code Box if available
            response?.formulaOrCode?.let { formulaBlock ->
                Spacer(modifier = Modifier.height(14.dp))
                FormulaCodeBlockView(block = formulaBlock)
            }

            // Step-by-Step Breakdown if available
            response?.steps?.takeIf { it.isNotEmpty() }?.let { stepsList ->
                Spacer(modifier = Modifier.height(14.dp))
                StepTimelineView(steps = stepsList)
            }

            // Real-World Analogy Callout
            response?.analogy?.let { analogyText ->
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))),
                        width = 1.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💡", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REAL-WORLD ANALOGY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = analogyText,
                            color = Color(0xFF92400E),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            // Common Pitfalls Callout
            response?.commonPitfalls?.takeIf { it.isNotEmpty() }?.let { pitfallsList ->
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFF1F2),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFFECDD3), Color(0xFFF43F5E))),
                        width = 1.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "⚠️", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "COMMON PITFALLS & EXAM TRAPS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFBE123C),
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        pitfallsList.forEach { pitfall ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(text = "•", color = Color(0xFFBE123C), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = pitfall,
                                    color = Color(0xFF881337),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Quick Check Question
            response?.quickCheck?.let { qc ->
                Spacer(modifier = Modifier.height(14.dp))
                QuickCheckCard(question = qc)
            }

            // Verified Academic Sources Strip
            if (activeCitations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                VerifiedSourcesStrip(citations = activeCitations)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons Row: TTS Audio Button
            Button(
                onClick = { if (isSpeaking) onStopSpeak() else onSpeak() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F46E5),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSpeaking) "⏹️ Stop Audio" else "🔊 Listen to Explanation",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Actions: Copy, Thumbs up, Practice Quiz
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleLike,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Helpful",
                            tint = if (isLiked) Color(0xFF4F46E5) else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Study Notes", coreText))
                            Toast.makeText(context, "Explanation copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(text = "📋", fontSize = 15.sp)
                    }
                }

                TextButton(
                    onClick = onTakeQuiz,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF4F46E5))
                ) {
                    Text(
                        text = "Take Practice Quiz",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Enterprise Structured Follow-Up Card: Shows student question + structured response.
 */
@Composable
fun StructuredFollowUpCard(
    message: FollowUpMessage,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val structured = message.structuredResponse

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // User Question Bubble
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                color = Color(0xFFEEF2FF),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFFC7D2FE), Color(0xFFA5B4FC)))
                )
            ) {
                Text(
                    text = message.question,
                    color = Color(0xFF1E1B4B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // AI Answer Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF))),
                width = 1.dp
            ),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (message.usedOnlineContext) Color(0xFFEEF2FF) else Color(0xFFECFDF5)
                    ) {
                        Text(
                            text = if (message.usedOnlineContext) "📡 Verified Web Context" else "📴 On-Device AI",
                            color = if (message.usedOnlineContext) Color(0xFF4F46E5) else Color(0xFF059669),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (structured != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = "${structured.intent.icon} ${structured.intent.displayName}",
                                color = Color(0xFF475569),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Core explanation
                Text(
                    text = structured?.coreConcept?.ifBlank { message.answer } ?: message.answer,
                    color = Color(0xFF334155),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )

                // Optional formula / code
                structured?.formulaOrCode?.let { formulaBlock ->
                    Spacer(modifier = Modifier.height(10.dp))
                    FormulaCodeBlockView(block = formulaBlock)
                }

                // Optional steps
                structured?.steps?.takeIf { it.isNotEmpty() }?.let { stepsList ->
                    Spacer(modifier = Modifier.height(10.dp))
                    StepTimelineView(steps = stepsList)
                }

                // Optional citations
                val activeCitations = structured?.citations?.takeIf { it.isNotEmpty() } ?: message.citations
                if (activeCitations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    VerifiedSourcesStrip(citations = activeCitations)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onToggleLike,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Helpful",
                            tint = if (isLiked) Color(0xFF4F46E5) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
