package com.studylens.ui.capture

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CaptureScreen(
    onTextCaptured: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var sampleText by remember { mutableStateOf("Calculus Integration by Parts Formula: \n∫ u dv = uv - ∫ v du") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Camera Capture & OCR Preview", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = sampleText,
            onValueChange = { sampleText = it },
            label = { Text("Extracted Text") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onTextCaptured(sampleText) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Explain Concept")
        }
    }
}
