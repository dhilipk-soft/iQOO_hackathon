package com.studylens.ui.capture

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
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

data class TextbookPreset(
    val title: String,
    val icon: String,
    val sampleText: String
)

@Composable
fun CaptureScreen(
    currentText: String,
    onTextChange: (String) -> Unit,
    onExplainRequested: (String) -> Unit,
    isProcessing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val presets = remember {
        listOf(
            TextbookPreset(
                title = "Calculus: By Parts",
                icon = "📐",
                sampleText = "Calculus Integration by Parts:\nFormula: ∫ u dv = uv - ∫ v du\nDerived from the product rule of calculus. Choose u via LIATE rule."
            ),
            TextbookPreset(
                title = "Physics: Newton's 3rd",
                icon = "⚡",
                sampleText = "Newton's Third Law of Motion:\nFor every action, there is an equal and opposite reaction.\nForces always occur in matched action-reaction pairs acting on different bodies."
            ),
            TextbookPreset(
                title = "Bio: Photosynthesis",
                icon = "🌿",
                sampleText = "Photosynthesis Light-Dependent Reactions:\n6CO₂ + 6H₂O + Sunlight → C₆H₁₂O₆ + 6O₂\nOccurs in thylakoid membranes where chlorophyll absorbs photons to produce ATP and NADPH."
            )
        )
    }

    // Scanning animation for viewfinder
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanProgress"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Privacy Guarantee Banner (Prominent on Capture Screen)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF38BDF8), Color(0xFF10B981))
                )
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Privacy Shield",
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "100% On-Device • No Account • No Cloud • Zero Data Leaves Phone",
                    color = Color(0xFFE2E8F0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Camera Viewfinder Box with Reticle Corner guides
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF111827))
                .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Corner reticle accents
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(8.dp))
            )

            // Scanning light beam
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = (-80).dp + (160.dp * scanProgress))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFF38BDF8), Color(0xFF34D399), Color.Transparent)
                        )
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Camera Viewfinder",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "AI Optical Text Recognition",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Align textbook page or formula inside frame",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Demo Topic Presets for fast on-stage demonstration
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Demo Presets:",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { preset ->
                    FilterChip(
                        selected = currentText.startsWith(preset.sampleText.take(20)),
                        onClick = { onTextChange(preset.sampleText) },
                        label = {
                            Text(
                                text = "${preset.icon} ${preset.title}",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1),
                            selectedContainerColor = Color(0xFF0369A1),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // OCR Extracted Text Box
        OutlinedTextField(
            value = currentText,
            onValueChange = onTextChange,
            label = { Text("Extracted Textbook Content (Editable)", color = Color(0xFF94A3B8)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color(0xFFE2E8F0),
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A)
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Action Shutter Button: "Explain Concept"
        Button(
            onClick = { onExplainRequested(currentText) },
            enabled = currentText.isNotBlank() && !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2563EB),
                disabledContainerColor = Color(0xFF1E293B)
            )
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Analyzing on-device...", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Explain Concept Aloud",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
