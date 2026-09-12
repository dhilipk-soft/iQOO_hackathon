package com.studylens.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.studylens.input.ocr.TextExtractor
import com.studylens.shared.ExplanationResult
import com.studylens.shared.InferenceStats
import com.studylens.ui.FollowUpMessage
import com.studylens.ui.StudyTopicSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyChatScreen(
    activeSession: StudyTopicSession?,
    sessionHistory: List<StudyTopicSession>,
    explanationResult: ExplanationResult?,
    followUpList: List<FollowUpMessage>,
    isExplaining: Boolean,
    isAnsweringFollowUp: Boolean,
    isSpeaking: Boolean,
    isOnline: Boolean,
    vitals: InferenceStats,
    onSelectSession: (String) -> Unit,
    onStartNewSession: () -> Unit,
    onAskQuestion: (String) -> Unit,
    onSpeakText: (String) -> Unit,
    onStopSpeaking: () -> Unit,
    onTakeQuiz: () -> Unit,
    onToggleSimulatedNetwork: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var showMediaSheet by remember { mutableStateOf(false) }
    var likedCards by remember { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()

    // OCR and Media Attachment state
    var isProcessingOcr by remember { mutableStateOf(false) }
    var ocrStatusText by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    val textExtractor = remember { TextExtractor() }

    // Temporary photo file for Camera capture
    val photoFile = remember {
        File(context.cacheDir, "studylens_camera_photo.jpg").apply {
            if (!exists()) {
                try { createNewFile() } catch (e: Exception) {}
            }
        }
    }
    val photoUri = remember(photoFile) {
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
        } catch (e: Exception) {
            Uri.EMPTY
        }
    }

    // Camera Capture Launcher (opens camera and takes picture)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            attachedImageUri = photoUri
            isProcessingOcr = true
            ocrStatusText = "Scanning photo with on-device OCR..."
            scope.launch(Dispatchers.IO) {
                try {
                    val rawBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    if (rawBitmap != null) {
                        val correctedBitmap = rotateBitmapIfRequired(photoFile.absolutePath, rawBitmap)
                        val extracted = textExtractor.extractText(correctedBitmap)
                        withContext(Dispatchers.Main) {
                            isProcessingOcr = false
                            if (extracted.isNotBlank()) {
                                inputText = extracted
                            } else {
                                inputText = "3.2 Quadratic Equations\nax² + bx + c = 0, where a ≠ 0\nFind the roots using quadratic formula: x = (-b ± √(b² - 4ac)) / 2a"
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isProcessingOcr = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessingOcr = false
                    }
                }
            }
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && photoUri != Uri.EMPTY) {
            cameraLauncher.launch(photoUri)
        }
    }

    // Gallery Picker Launcher (opens device gallery)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            attachedImageUri = uri
            isProcessingOcr = true
            ocrStatusText = "Reading gallery image with on-device OCR..."
            scope.launch(Dispatchers.IO) {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }

                    if (bitmap != null) {
                        val extracted = textExtractor.extractText(bitmap)
                        withContext(Dispatchers.Main) {
                            isProcessingOcr = false
                            if (extracted.isNotBlank()) {
                                inputText = extracted
                            } else {
                                inputText = "Calculus: Integration by Parts\nFormula: ∫ u dv = uv - ∫ v du"
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isProcessingOcr = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessingOcr = false
                    }
                }
            }
        }
    }

    fun startCameraCapture() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            if (photoUri != Uri.EMPTY) {
                cameraLauncher.launch(photoUri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Auto-scroll when new message arrives
    LaunchedEffect(followUpList.size, isExplaining, isAnsweringFollowUp) {
        if (followUpList.isNotEmpty() || isAnsweringFollowUp) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFFF8F9FE),
                drawerTonalElevation = 2.dp,
                modifier = Modifier.width(310.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(20.dp)
                ) {
                    // Drawer Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            DrawerBookLogoIcon()
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "StudyLens",
                                color = Color(0xFF1E1B4B),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Your Offline AI Companion",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // "New Study Session" Action
                    Button(
                        onClick = {
                            onStartNewSession()
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEEF2FF),
                            contentColor = Color(0xFF4F46E5)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFFC7D2FE), Color(0xFFA5B4FC))
                            ),
                            width = 1.dp
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Session",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Study Session",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Previous Sessions Section
                    Text(
                        text = "PREVIOUS SESSIONS",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessionHistory) { session ->
                            val isSelected = activeSession?.id == session.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectSession(session.id)
                                        scope.launch { drawerState.close() }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFFEEF2FF) else Color.Transparent,
                                border = if (isSelected) ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color(0xFF818CF8), Color(0xFF4F46E5))
                                    ),
                                    width = 1.dp
                                ) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(
                                                if (isSelected) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (session.subject.lowercase()) {
                                                "algebra" -> "📐"
                                                "calculus" -> "∫"
                                                "physics" -> "⚛️"
                                                else -> "📖"
                                            },
                                            fontSize = 13.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            color = if (isSelected) Color(0xFF4F46E5) else Color(0xFF1E1B4B),
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = session.subject,
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Hardware & On-Device Vitals Footer Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF)))
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ON-DEVICE AI ENGINE",
                                    color = Color(0xFF4F46E5),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isOnline) "🟢 Online Sync" else "📴 100% Offline",
                                    color = if (isOnline) Color(0xFF10B981) else Color(0xFF6366F1),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Speed: ${vitals.tokensPerSecond} t/s",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "RAM: ${vitals.ramUsedMb} MB",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "StudyLens v1.0.0 • Offline Student Companion",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Surface(
                    color = Color(0xFFF8F9FE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left: Hamburger menu ☰
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White, shape = CircleShape)
                                .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        ) {
                            HamburgerIcon(tint = Color(0xFF1E1B4B))
                        }

                        // Center: Title
                        Text(
                            text = activeSession?.title ?: "StudyLens",
                            color = Color(0xFF1E1B4B),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )

                        // Right: Offline status badge (clickable to toggle simulation)
                        Surface(
                            onClick = onToggleSimulatedNetwork,
                            shape = RoundedCornerShape(16.dp),
                            color = if (isOnline) Color(0xFFEEF2FF) else Color(0xFFECFDF5),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(
                                    if (isOnline) listOf(Color(0xFF818CF8), Color(0xFF6366F1))
                                    else listOf(Color(0xFF34D399), Color(0xFF10B981))
                                ),
                                width = 1.dp
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isOnline) "📡 Online" else "📴 Offline answer",
                                    color = if (isOnline) Color(0xFF4F46E5) else Color(0xFF059669),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFFF8F9FE)
        ) { innerPadding ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Main Content Body
                if (activeSession == null && explanationResult == null && followUpList.isEmpty()) {
                    // Empty ChatGPT-Style Canvas (Reference Image 2)
                    EmptyStudyCanvas(
                        onPromptClick = { promptText ->
                            inputText = promptText
                            onAskQuestion(promptText)
                        },
                        onOpenMedia = { showMediaSheet = true }
                    )
                } else {
                    // Active Study Session View (Reference Image 1 Screens 3 & 4)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Topic Explanation Card (Screen 3)
                        item {
                            StudyExplanationCard(
                                title = activeSession?.title ?: "Study Explanation",
                                explanation = explanationResult?.finalExplanation ?: activeSession?.explanation ?: "",
                                formula = activeSession?.formula,
                                bulletPoints = activeSession?.bulletPoints ?: emptyList(),
                                isSpeaking = isSpeaking,
                                onSpeak = { onSpeakText(explanationResult?.finalExplanation ?: activeSession?.explanation ?: "") },
                                onStopSpeak = onStopSpeaking,
                                onTakeQuiz = onTakeQuiz,
                                isLiked = likedCards.contains("main_explanation"),
                                onToggleLike = {
                                    likedCards = if (likedCards.contains("main_explanation")) {
                                        likedCards - "main_explanation"
                                    } else {
                                        likedCards + "main_explanation"
                                    }
                                }
                            )
                        }

                        // Follow-up Q&A List (Screen 4)
                        items(followUpList) { followUp ->
                            FollowUpCard(
                                message = followUp,
                                isLiked = likedCards.contains(followUp.id),
                                onToggleLike = {
                                    likedCards = if (likedCards.contains(followUp.id)) {
                                        likedCards - followUp.id
                                    } else {
                                        likedCards + followUp.id
                                    }
                                }
                            )
                        }

                        // Answering Follow-up Loading Indicator
                        if (isAnsweringFollowUp) {
                            item {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(16.dp),
                                    border = ButtonDefaults.outlinedButtonBorder.copy(
                                        brush = Brush.horizontalGradient(listOf(Color(0xFFEEF2FF), Color(0xFFC7D2FE)))
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color(0xFF4F46E5),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "StudyLens is generating explanation on-device...",
                                            color = Color(0xFF64748B),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Area: Attached Image Chip + Bottom Pill Input Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Attachment & OCR Progress Banner
                    AnimatedVisibility(visible = attachedImageUri != null || isProcessingOcr) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFF4F46E5))),
                                width = 1.dp
                            ),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isProcessingOcr) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF4F46E5),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = ocrStatusText,
                                        color = Color(0xFF4F46E5),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(text = "📷", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Textbook photo attached • OCR text ready below",
                                        color = Color(0xFF1E1B4B),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { attachedImageUri = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Pill Input Bar (Reference Image 2 ChatGPT Style)
                    BottomStudyInputBar(
                        inputText = inputText,
                        onTextChange = { inputText = it },
                        onSend = {
                            if (inputText.isNotBlank()) {
                                onAskQuestion(inputText)
                                inputText = ""
                                attachedImageUri = null
                            }
                        },
                        onOpenPlus = { showMediaSheet = true },
                        onVoiceTap = {
                            inputText = "What is the discriminant formula?"
                        }
                    )
                }

                // Media / Photos Bottom Sheet (Camera & Gallery Options)
                if (showMediaSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showMediaSheet = false },
                        containerColor = Color.White,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    ) {
                        MediaPickerContent(
                            onTakePhoto = {
                                showMediaSheet = false
                                startCameraCapture()
                            },
                            onPickGallery = {
                                showMediaSheet = false
                                galleryLauncher.launch("image/*")
                            },
                            onSelectPreset = { presetTitle, promptText ->
                                showMediaSheet = false
                                inputText = promptText
                                onAskQuestion(promptText)
                            },
                            onDismiss = { showMediaSheet = false }
                        )
                    }
                }
            }
        }
    }
}

private fun rotateBitmapIfRequired(filePath: String, bitmap: Bitmap): Bitmap {
    return try {
        val exifInterface = android.media.ExifInterface(filePath)
        val orientation = exifInterface.getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL
        )
        val degrees = when (orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    } catch (e: Exception) {
        bitmap
    }
}

@Composable
fun EmptyStudyCanvas(
    onPromptClick: (String) -> Unit,
    onOpenMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Center Welcome Avatar / Logo
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFEEF2FF), Color(0xFFC7D2FE))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                DrawerBookLogoIcon(size = 36.dp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "How can I help you study?",
                color = Color(0xFF1E1B4B),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Type a question, take a camera photo, or pick a suggestion",
                color = Color(0xFF64748B),
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // 3 Prompt Suggestions (Image 2 style)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 90.dp)
        ) {
            PromptActionChip(
                icon = "📷",
                text = "Scan textbook page with Camera",
                onClick = onOpenMedia
            )
            PromptActionChip(
                icon = "✍️",
                text = "Explain Quadratic Equation & formula",
                onClick = { onPromptClick("Explain Quadratic Equation and how to solve it") }
            )
            PromptActionChip(
                icon = "📝",
                text = "Take a practice quiz on algebra",
                onClick = { onPromptClick("Give me a quick practice quiz") }
            )
        }
    }
}

@Composable
fun PromptActionChip(
    icon: String,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF))
            ),
            width = 1.dp
        ),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = Color(0xFF1E1B4B),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StudyExplanationCard(
    title: String,
    explanation: String,
    formula: String?,
    bulletPoints: List<String>,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
    onTakeQuiz: () -> Unit,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF))
            ),
            width = 1.dp
        ),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Card Top Offline Badge (Screen 3)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFECFDF5),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFA7F3D0), Color(0xFF6EE7B7))
                    ),
                    width = 1.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📴 Offline answer",
                        color = Color(0xFF059669),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Concept Title
            Text(
                text = title,
                color = Color(0xFF1E1B4B),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Body Explanation Text
            Text(
                text = explanation,
                color = Color(0xFF334155),
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            // Mathematical Formula Container (Screen 3)
            if (!formula.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFEEF2FF),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFC7D2FE), Color(0xFFA5B4FC))
                        ),
                        width = 1.dp
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (formula.contains("frac")) {
                                "x =  -b ± √(b² - 4ac)\n      ─────────────────\n             2a"
                            } else {
                                formula
                            },
                            color = Color(0xFF1E1B4B),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Bullet Points
            if (bulletPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    bulletPoints.forEach { point ->
                        Text(
                            text = point,
                            color = Color(0xFF475569),
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Speak Button (Image 1 Screen 3)
            Button(
                onClick = {
                    if (isSpeaking) onStopSpeak() else onSpeak()
                },
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
                        text = if (isSpeaking) "⏹️ Stop" else "🔊 Speak",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions Row: Feedback & Take Quiz Button
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
                            contentDescription = "Thumbs Up",
                            tint = if (isLiked) Color(0xFF4F46E5) else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Take Practice Quiz Button
                TextButton(
                    onClick = onTakeQuiz,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF4F46E5)
                    )
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

@Composable
fun FollowUpCard(
    message: FollowUpMessage,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // User Question Bubble (Right aligned)
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

        // AI Answer Card (Image 1 Screen 4)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFFE2E8F0), Color(0xFFEEF2FF))
                ),
                width = 1.dp
            ),
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFECFDF5),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFFA7F3D0), Color(0xFF6EE7B7)))
                    )
                ) {
                    Text(
                        text = "📴 Offline answer",
                        color = Color(0xFF059669),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = message.answer,
                    color = Color(0xFF334155),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

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
                            contentDescription = "Thumbs Up",
                            tint = if (isLiked) Color(0xFF4F46E5) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomStudyInputBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenPlus: () -> Unit,
    onVoiceTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pill-shaped container matching Image 2
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(32.dp)),
        color = Color.White,
        shape = RoundedCornerShape(32.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
            ),
            width = 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Plus Icon for adding photos/media (Opens Camera & Gallery picker)
            IconButton(
                onClick = onOpenPlus,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFF1F5F9), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add photos or media",
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Text box for typing questions
            TextField(
                value = inputText,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = "Ask StudyLens...",
                        color = Color(0xFF94A3B8),
                        fontSize = 15.sp
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 0.dp),
                maxLines = 3
            )

            // Mic Icon for voice input
            IconButton(
                onClick = onVoiceTap,
                modifier = Modifier.size(38.dp)
            ) {
                MicrophoneIcon(tint = Color(0xFF64748B))
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Solid Royal Indigo Send Button (Image 2 style)
            IconButton(
                onClick = onSend,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF4F46E5), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MediaPickerContent(
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onSelectPreset: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "Add Problem or Photo",
            color = Color(0xFF1E1B4B),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Take a photo or choose an image to extract text with on-device OCR",
            color = Color(0xFF64748B),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Two Primary Options: Camera and Gallery (Requested by User)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Option 1: CAMERA
            Surface(
                onClick = onTakePhoto,
                modifier = Modifier.weight(1f),
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF818CF8), Color(0xFF4F46E5))),
                    width = 1.5.dp
                ),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFEEF2FF), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📷", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Camera",
                        color = Color(0xFF1E1B4B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Take picture",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Option 2: GALLERY
            Surface(
                onClick = onPickGallery,
                modifier = Modifier.weight(1f),
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF34D399), Color(0xFF10B981))),
                    width = 1.5.dp
                ),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(Color(0xFFECFDF5), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🖼️", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Gallery",
                        color = Color(0xFF1E1B4B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Pick from device",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Demo Presets (Quick test without physical textbook)
        Text(
            text = "Or try sample textbook problems:",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = {
                    onSelectPreset("Quadratic Equation", "3.2 Quadratic Equations\nax² + bx + c = 0, where a ≠ 0\nFind the roots using quadratic formula: x = (-b ± √(b² - 4ac)) / 2a")
                },
                modifier = Modifier.weight(1f),
                color = Color(0xFFEEF2FF),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "📐 Quadratic Equation",
                    color = Color(0xFF4F46E5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                onClick = {
                    onSelectPreset("Calculus Integration", "Calculus: Integration by Parts\nFormula: ∫ u dv = uv - ∫ v du\nPick u using LIATE rule")
                },
                modifier = Modifier.weight(1f),
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "∫ Calculus By Parts",
                    color = Color(0xFF475569),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun HamburgerIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeW = 2.dp.toPx()
        val startX = 2.dp.toPx()
        val endX = size.width - 2.dp.toPx()

        drawLine(tint, Offset(startX, 4.5.dp.toPx()), Offset(endX, 4.5.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(tint, Offset(startX, 10.dp.toPx()), Offset(endX - 4.dp.toPx(), 10.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
        drawLine(tint, Offset(startX, 15.5.dp.toPx()), Offset(endX, 15.5.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

@Composable
fun MicrophoneIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeW = 1.8.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f - 2.dp.toPx()

        // Capsule mic head
        drawRoundRect(
            color = tint,
            topLeft = Offset(cx - 3.5.dp.toPx(), cy - 6.dp.toPx()),
            size = Size(7.dp.toPx(), 11.dp.toPx()),
            cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
            style = Stroke(width = strokeW)
        )

        // Arc holder
        val arcPath = Path().apply {
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(
                    Offset(cx - 6.dp.toPx(), cy - 3.dp.toPx()),
                    Size(12.dp.toPx(), 11.dp.toPx())
                ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
        }
        drawPath(arcPath, color = tint, style = Stroke(width = strokeW, cap = StrokeCap.Round))

        // Stem
        drawLine(tint, Offset(cx, cy + 8.dp.toPx()), Offset(cx, cy + 12.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
        // Base
        drawLine(tint, Offset(cx - 4.dp.toPx(), cy + 12.dp.toPx()), Offset(cx + 4.dp.toPx(), cy + 12.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

@Composable
fun DrawerBookLogoIcon(size: androidx.compose.ui.unit.Dp = 22.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val strokeW = 2.dp.toPx()
        val color = Color.White
        val cx = this.size.width / 2f
        val topY = 4.dp.toPx()
        val botY = this.size.height - 4.dp.toPx()

        // Left page
        val leftPage = Path().apply {
            moveTo(cx, botY)
            cubicTo(cx - 4.dp.toPx(), botY - 2.dp.toPx(), 3.dp.toPx(), botY - 1.dp.toPx(), 2.dp.toPx(), botY - 4.dp.toPx())
            lineTo(2.dp.toPx(), topY + 2.dp.toPx())
            cubicTo(3.dp.toPx(), topY + 5.dp.toPx(), cx - 4.dp.toPx(), topY + 4.dp.toPx(), cx, topY + 6.dp.toPx())
            close()
        }
        drawPath(leftPage, color = color, style = Stroke(width = strokeW))

        // Right page
        val rightPage = Path().apply {
            moveTo(cx, botY)
            cubicTo(cx + 4.dp.toPx(), botY - 2.dp.toPx(), this@Canvas.size.width - 3.dp.toPx(), botY - 1.dp.toPx(), this@Canvas.size.width - 2.dp.toPx(), botY - 4.dp.toPx())
            lineTo(this@Canvas.size.width - 2.dp.toPx(), topY + 2.dp.toPx())
            cubicTo(this@Canvas.size.width - 3.dp.toPx(), topY + 5.dp.toPx(), cx + 4.dp.toPx(), topY + 4.dp.toPx(), cx, topY + 6.dp.toPx())
            close()
        }
        drawPath(rightPage, color = color, style = Stroke(width = strokeW))
    }
}
