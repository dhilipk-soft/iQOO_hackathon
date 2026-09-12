package com.studylens.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studylens.ai.LlmEngine
import com.studylens.ai.ModelCatalog
import com.studylens.ai.ModelDownloadManager
import com.studylens.ai.ModelDownloadState

/**
 * In-app model picker - browse the bundled catalog (models.json), download an ungated
 * model over plain HTTPS, and switch LlmEngine to use it.
 */
@Composable
fun ModelPickerScreen(
    llmEngine: LlmEngine,
    onBack: () -> Unit,
    onModelActivated: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadManager = remember { ModelDownloadManager(context) }
    val models = remember { ModelCatalog.loadModels(context) }

    var activeFilename by remember { mutableStateOf(ModelDownloadManager.getActiveModelFilename(context)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FE))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1E1B4B))
            }
            Text(
                text = "Choose AI Model",
                color = Color(0xFF1E1B4B),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "All models run 100% on-device, zero network calls once downloaded. " +
                "Multimodal models support image upload, while text-only models process text prompts.",
            color = Color(0xFF64748B),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        errorMessage?.let { message ->
            Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Text(message, color = Color(0xFFB91C1C), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
            }
        }

        if (models.isEmpty()) {
            Text(
                "Couldn't load the model list (models.json missing or malformed).",
                color = Color(0xFFB91C1C),
                fontSize = 13.sp
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(models) { model ->
                val downloadState by downloadManager.observeDownload(model.id)
                    .collectAsState(initial = ModelDownloadState.Idle)
                val isDownloaded = downloadManager.isDownloaded(model) || downloadState is ModelDownloadState.Success
                val isActive = activeFilename == model.filename && isDownloaded

                LaunchedEffect(downloadState) {
                    if (downloadState is ModelDownloadState.Success && activeFilename != model.filename) {
                        downloadManager.setActiveModel(model)
                        llmEngine.invalidate()
                        onModelActivated()
                        activeFilename = model.filename
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            if (isActive) listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
                            else listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF))
                        ),
                        width = 1.dp
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E1B4B))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(model.description, fontSize = 12.sp, color = Color(0xFF64748B), lineHeight = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${model.sizeBytes / (1024 * 1024)} MB" + if (isDownloaded) " • downloaded" else "",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            if (isActive) {
                                Surface(color = Color(0xFFEEF2FF), shape = RoundedCornerShape(10.dp)) {
                                    Text(
                                        "Active",
                                        color = Color(0xFF4F46E5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val downloading = downloadState as? ModelDownloadState.Downloading
                        if (downloading != null) {
                            LinearProgressIndicator(
                                progress = { downloading.progressPct / 100f },
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                color = Color(0xFF4F46E5)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "${downloading.progressPct}% - safe to leave this screen",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { downloadManager.cancelDownload(model) }) {
                                    Text("Cancel", fontSize = 12.sp, color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            val failed = downloadState as? ModelDownloadState.Failed
                            if (failed != null) {
                                Text(
                                    "Download failed: ${failed.message}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB91C1C),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Button(
                                onClick = {
                                    errorMessage = null
                                    when {
                                        isActive -> Unit
                                        isDownloaded -> {
                                            downloadManager.setActiveModel(model)
                                            llmEngine.invalidate()
                                            onModelActivated()
                                            activeFilename = model.filename
                                        }
                                        !model.available -> {
                                            errorMessage = "${model.displayName} isn't hosted yet - check back soon."
                                        }
                                        else -> downloadManager.enqueueDownload(model)
                                    }
                                },
                                enabled = !isActive,
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4F46E5),
                                    disabledContainerColor = Color(0xFFEEF2FF),
                                    disabledContentColor = Color(0xFF4F46E5)
                                )
                            ) {
                                Text(
                                    text = when {
                                        isActive -> "Active"
                                        isDownloaded -> "Switch to this model"
                                        !model.available -> "Not available yet"
                                        failed != null -> "Retry download"
                                        else -> "Download"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
