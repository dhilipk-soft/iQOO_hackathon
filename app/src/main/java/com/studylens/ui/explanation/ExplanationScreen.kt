package com.studylens.ui.explanation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studylens.shared.ExplanationResult

@Composable
fun ExplanationScreen(
    capturedText: String,
    explanationResult: ExplanationResult?,
    onGenerateQuiz: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Study Explanation", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Original Captured Text:", style = MaterialTheme.typography.titleMedium)
                Text(capturedText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AI Explanation:", style = MaterialTheme.typography.titleMedium)
                Text(
                    explanationResult?.finalExplanation ?: "Generating explanation...",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (explanationResult?.usedOnlineContext == true) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AssistChip(
                        onClick = { },
                        label = { Text("Used Online Context") }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onGenerateQuiz,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Take Practice Quiz")
        }
    }
}
