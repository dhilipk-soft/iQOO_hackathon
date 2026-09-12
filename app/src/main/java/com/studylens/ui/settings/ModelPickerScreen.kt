package com.studylens.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import com.studylens.ai.DownloadableModel
import com.studylens.ai.LlmEngine
import com.studylens.ai.ModelCatalog
import com.studylens.ai.ModelDownloadManager
import com.studylens.ai.ModelDownloadState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-app model picker - browse the bundled catalog (models.json), download an ungated
 * model over plain HTTPS (no Hugging Face login), and switch LlmEngine to use it.
 * Gemma stays as the always-present default; this adds options, it doesn't replace it.
 */
@Composable
fun ModelPickerScreen(
    llmEngine: LlmEngine,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { ModelDownloadManager(context) }
    val models = remember { ModelCatalog.loadModels(context) }

    var activeFilename by remember { mutableStateOf(ModelDownloadManager.getActiveModelFilename(context)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Which model is currently being test-loaded after a switch - shows a spinner on that
    // row's button and blocks other switches until it resolves (success = keep it, failure =
    // roll back to whatever was active before, with the real reason shown).
    var verifyingModelId by remember { mutableStateOf<String?>(null) }

    fun switchToModel(model: DownloadableModel) {
        val previousModel = models.find { it.filename == activeFilename }
        errorMessage = null
        verifyingModelId = model.id
        downloadManager.setActiveModel(model)
        llmEngine.invalidate()
        activeFilename = model.filename
        scope.launch {
            // NonCancellable: rememberCoroutineScope() is tied to this screen's lifecycle,
            // so navigating away mid-verification would otherwise cancel this job right when
            // it matters most - setActiveModel() above already committed to SharedPreferences
            // synchronously, so without this, leaving the screen before verification finishes
            // permanently strands the model as "active" with no actual confirmation it loads,
            // and no rollback ever runs. This guarantees the verify-then-commit-or-rollback
            // sequence always finishes regardless of navigation.
            withContext(NonCancellable) {
                val failureReason = llmEngine.verifyActiveModelLoads()
                if (failureReason != null) {
                    // Roll back - this model doesn't actually work on this device/library
                    // combo (e.g. an unsupported file format), so leaving it "active" would
                    // just mean every future chat message silently fails.
                    if (previousModel != null) {
                        downloadManager.setActiveModel(previousModel)
                    } else {
                        downloadManager.clearActiveModel()
                    }
                    llmEngine.invalidate()
                    activeFilename = ModelDownloadManager.getActiveModelFilename(context)
                    errorMessage = "${model.displayName} couldn't be loaded on this device: $failureReason"
                }
                verifyingModelId = null
            }
        }
    }

    fun deleteModel(model: DownloadableModel) {
        downloadManager.deleteDownloadedModel(model)
        llmEngine.invalidate()
        activeFilename = ModelDownloadManager.getActiveModelFilename(context)
        errorMessage = null
    }

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
                "Bigger models may reason better but use more storage, RAM, and battery.",
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
                // The actual file on disk is the only source of truth - NOT downloadState,
                // which is WorkManager's cached status keyed by model.id. If a catalog entry's
                // filename/URL ever changes while its id stays the same (e.g. correcting a
                // .task entry to the right .litertlm file), a stale "Success" from the OLD
                // download under that id would otherwise make the picker claim the NEW file
                // is downloaded when it was never actually fetched - which is exactly the bug
                // that caused "Switch to this model" to be offered before a real download ever
                // happened, always failing instantly with "Model file not found on disk."
                val isDownloaded = downloadManager.isDownloaded(model)
                // "Active" means the model is both the selected preference AND actually
                // present on disk - a filename can be the default preference before anything
                // is downloaded, which must not be shown as if the model is ready to chat with.
                val isActive = activeFilename == model.filename && isDownloaded
                val isVerifying = verifyingModelId == model.id

                // Once WorkManager reports success, activate the model automatically -
                // this also fires correctly if the user left the screen mid-download and
                // comes back after it finished. Never for a knownIncompatible model (none of
                // the current catalog entries can even be downloaded new right now, but this
                // guards the auto-switch specifically, independent of the button below).
                LaunchedEffect(downloadState) {
                    if (downloadState is ModelDownloadState.Success &&
                        activeFilename != model.filename &&
                        !model.knownIncompatible
                    ) {
                        switchToModel(model)
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
                                    text = "${model.sizeBytes / (1024 * 1024)} MB" +
                                        (if (isDownloaded) " • downloaded" else "") +
                                        (if (model.knownIncompatible) " • not compatible with this device" else ""),
                                    fontSize = 11.sp,
                                    color = if (model.knownIncompatible) Color(0xFFB91C1C) else Color(0xFF94A3B8)
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
                            // Delete frees up storage (these are multi-GB files) - disabled
                            // while a switch to/from this model is being verified so the file
                            // can't disappear out from under an in-flight load attempt.
                            if (isDownloaded && !isVerifying) {
                                IconButton(
                                    onClick = { deleteModel(model) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete downloaded model",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
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
                                        model.knownIncompatible -> {
                                            errorMessage = "${model.displayName} can't run on this device - " +
                                                "its file format isn't supported by this app's engine (see above)."
                                        }
                                        isActive -> Unit // already active, nothing to do
                                        isDownloaded -> switchToModel(model)
                                        !model.available -> {
                                            errorMessage = "${model.displayName} isn't hosted yet - check back soon."
                                        }
                                        else -> downloadManager.enqueueDownload(model)
                                    }
                                },
                                enabled = !model.knownIncompatible && !isActive && !isVerifying,
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4F46E5),
                                    disabledContainerColor = Color(0xFFEEF2FF),
                                    disabledContentColor = Color(0xFF4F46E5)
                                )
                            ) {
                                if (isVerifying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color(0xFF4F46E5),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = when {
                                            model.knownIncompatible -> "Not compatible with this device"
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
}
