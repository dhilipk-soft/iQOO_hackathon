package com.studylens.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studylens.ai.ExplainPipeline
import com.studylens.ai.FocusNarrator
import com.studylens.ai.LlmEngine
import com.studylens.ai.ModelDownloadManager
import com.studylens.ai.RetrievalClient
import com.studylens.input.data.AppDatabase
import com.studylens.input.data.ChatMessageEntity
import com.studylens.input.data.ChatSessionEntity
import com.studylens.input.data.FocusEventEntity
import com.studylens.input.data.FocusSessionSummaryEntity
import com.studylens.input.focus.BreakReminderHelper
import com.studylens.input.focus.NotificationCollector
import com.studylens.input.focus.StudyLensNotificationListenerService
import com.studylens.input.focus.UsageCollector
import com.studylens.input.network.NetworkHealthChecker
import com.studylens.input.vitals.DeviceVitalsMonitor
import com.studylens.shared.ExplanationResult
import com.studylens.shared.FocusEventType
import com.studylens.shared.FocusInsight
import com.studylens.shared.FocusInterruptionEvent
import com.studylens.shared.FocusMistakeAnalysis
import com.studylens.shared.FocusNotificationSummaryItem
import com.studylens.shared.FocusSessionSummary
import com.studylens.shared.HistoricalFocusTrend
import com.studylens.shared.InferenceStats
import com.studylens.shared.NotificationEvent
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyCapture
import com.studylens.shared.StudySession
import com.studylens.ui.focus.ActiveBreakInfo
import com.studylens.ui.focus.ContextRecapInfo
import com.studylens.ui.focus.FocusGuardDialogPhase
import com.studylens.ui.focus.SwitchIntent
import com.studylens.ui.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class FollowUpMessage(
    val id: String,
    val question: String,
    val answer: String,
    val usedOnlineContext: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class StudyTopicSession(
    val id: String,
    val title: String,
    val subject: String,
    val previewText: String,
    val explanation: String,
    val formula: String? = null,
    val bulletPoints: List<String> = emptyList(),
    val usedOnlineContext: Boolean = false,
    val followUps: List<FollowUpMessage> = emptyList()
)

private const val SESSION_ID_PREFIX = "session_"
private fun StudyTopicSession.dbId(): Long? = id.removePrefix(SESSION_ID_PREFIX).toLongOrNull()

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val networkChecker = NetworkHealthChecker(application)
    private val vitalsMonitor = DeviceVitalsMonitor(application)
    val ttsManager = TtsManager(application)

    // Not private - the model picker screen needs to call invalidate() on this exact
    // instance after the user switches models, not a second unrelated LlmEngine.
    val llmEngine = LlmEngine(application)
    private val retrievalClient = RetrievalClient()
    private val explainPipeline = ExplainPipeline(llmEngine, retrievalClient)

    private val database = AppDatabase.getDatabase(application)
    private val chatDao = database.chatDao()
    val usageCollector = try {
        com.studylens.StudyLensApp.instance.usageCollector
    } catch (e: Exception) {
        UsageCollector(application, database)
    }
    val notificationCollector = try {
        com.studylens.StudyLensApp.instance.notificationCollector
    } catch (e: Exception) {
        NotificationCollector(application, database)
    }
    val breakReminderHelper = BreakReminderHelper(application)
    val focusNarrator = FocusNarrator(llmEngine)
    private val focusSignalsDao = database.focusSignalsDao()

    // Permissions
    private val _isUsageAccessGranted = MutableStateFlow(usageCollector.isUsageAccessGranted())
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted.asStateFlow()

    private val _isNotificationAccessGranted = MutableStateFlow(notificationCollector.isNotificationAccessGranted())
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    // Active Focus Mode state
    private val _isFocusModeActive = MutableStateFlow(false)
    val isFocusModeActive: StateFlow<Boolean> = _isFocusModeActive.asStateFlow()

    private var currentFocusSessionId: Long = 0L
    private var focusSessionStartTime: Long = 0L
    private var lastPauseTimestamp: Long = 0L
    private val appStartTime: Long = System.currentTimeMillis()

    data class BreakRecord(val plannedMs: Long, val actualMs: Long, val delayedMs: Long)
    private val sessionBreaks = mutableListOf<BreakRecord>()
    private val recentSwitchTimestamps = mutableListOf<Long>()

    private var lastNotificationTimestamp: Long = 0L
    private var lastNotificationPackage: String? = null
    private var notificationTriggeredSwitchesCount: Int = 0

    // App Hopping / Distraction Chain Tracking
    private var isAppHoppingActive: Boolean = false
    private var appHoppingSwitchCount: Int = 0

    // Observable states
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isMultimodalSupported = MutableStateFlow(
        ModelDownloadManager.getActiveModelIsMultimodal(application)
    )
    val isMultimodalSupported: StateFlow<Boolean> = _isMultimodalSupported.asStateFlow()

    fun refreshActiveModelCapabilities() {
        _isMultimodalSupported.value = ModelDownloadManager.getActiveModelIsMultimodal(getApplication())
    }

    private val _vitals = MutableStateFlow(
        InferenceStats(tokensPerSecond = 0.0, latencyMs = 0, ramUsedMb = 0, thermalStatus = "NORMAL")
    )
    val vitals: StateFlow<InferenceStats> = _vitals.asStateFlow()

    // Starts empty - filled only by what the user actually captures/types (no demo seed text).
    private val _capturedText = MutableStateFlow("")
    val capturedText: StateFlow<String> = _capturedText.asStateFlow()

    private val _isExplaining = MutableStateFlow(false)
    val isExplaining: StateFlow<Boolean> = _isExplaining.asStateFlow()

    private val _explanationResult = MutableStateFlow<ExplanationResult?>(null)
    val explanationResult: StateFlow<ExplanationResult?> = _explanationResult.asStateFlow()

    private val _followUpList = MutableStateFlow<List<FollowUpMessage>>(emptyList())
    val followUpList: StateFlow<List<FollowUpMessage>> = _followUpList.asStateFlow()

    private val _isAnsweringFollowUp = MutableStateFlow(false)
    val isAnsweringFollowUp: StateFlow<Boolean> = _isAnsweringFollowUp.asStateFlow()

    // Real chat history, loaded from Room - starts empty, grows as the user actually studies
    // (this is what "remembers previous chats like ChatGPT" means: persisted, not seeded).
    private val _sessionHistory = MutableStateFlow<List<StudyTopicSession>>(emptyList())
    val sessionHistory: StateFlow<List<StudyTopicSession>> = _sessionHistory.asStateFlow()

    // No chat is open by default - the user lands on the empty canvas, not a pre-filled example.
    private val _activeSession = MutableStateFlow<StudyTopicSession?>(null)
    val activeSession: StateFlow<StudyTopicSession?> = _activeSession.asStateFlow()

    private val _quizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quizQuestions: StateFlow<List<QuizQuestion>> = _quizQuestions.asStateFlow()

    private val _selectedQuizAnswers = MutableStateFlow<Map<Long, String>>(emptyMap())
    val selectedQuizAnswers: StateFlow<Map<Long, String>> = _selectedQuizAnswers.asStateFlow()

    private val _quizSubmitted = MutableStateFlow(false)
    val quizSubmitted: StateFlow<Boolean> = _quizSubmitted.asStateFlow()

    private val _revisionList = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val revisionList: StateFlow<List<QuizQuestion>> = _revisionList.asStateFlow()

    private val _focusInsight = MutableStateFlow<FocusInsight?>(null)
    val focusInsight: StateFlow<FocusInsight?> = _focusInsight.asStateFlow()

    private val _studySession = MutableStateFlow<StudySession?>(null)
    val studySession: StateFlow<StudySession?> = _studySession.asStateFlow()

    private val _focusSummary = MutableStateFlow<FocusSessionSummary?>(null)
    val focusSummary: StateFlow<FocusSessionSummary?> = _focusSummary.asStateFlow()

    private val _activeBreak = MutableStateFlow<ActiveBreakInfo?>(null)
    val activeBreak: StateFlow<ActiveBreakInfo?> = _activeBreak.asStateFlow()

    private val _focusGuardDialog = MutableStateFlow<FocusGuardDialogPhase?>(null)
    val focusGuardDialog: StateFlow<FocusGuardDialogPhase?> = _focusGuardDialog.asStateFlow()

    private val _contextRecap = MutableStateFlow<ContextRecapInfo?>(null)
    val contextRecap: StateFlow<ContextRecapInfo?> = _contextRecap.asStateFlow()

    init {
        // Collect network state from Member 1's checker
        viewModelScope.launch {
            networkChecker.isOnline.collect { online ->
                _isOnline.value = online
            }
        }

        // Collect vitals from Member 1's monitor
        viewModelScope.launch {
            vitalsMonitor.vitals.collect { stat ->
                if (stat.ramUsedMb > 0) {
                    _vitals.value = stat
                }
            }
        }

        // Load real chat history from Room - this is the persistence layer (ChatGPT-style).
        // Any insert elsewhere (explainCurrentCapture) makes this Flow re-emit automatically.
        viewModelScope.launch {
            chatDao.getAllSessions().collect { entities ->
                _sessionHistory.value = entities.map { it.toDomain() }
            }
        }

        // Collect break reminder triggers
        viewModelScope.launch {
            BreakReminderHelper.reminderFiredFlow.collect {
                val current = _activeBreak.value
                if (current != null) {
                    _activeBreak.value = current.copy(isReminderTriggered = true)
                    logFocusEvent(FocusEventType.REMINDER_TRIGGERED, "Break timer expired")
                }
            }
        }

        // Collect incoming notifications during focus mode
        viewModelScope.launch {
            StudyLensNotificationListenerService.notificationEvents.collect { notif ->
                if (_isFocusModeActive.value) {
                    val isDuringBreak = _activeBreak.value != null
                    if (isDuringBreak) {
                        // Study breaks are ALLOWED rest periods:
                        // Notifications received during break do not penalize deep work focus
                        logFocusEvent(
                            FocusEventType.NOTIFICATION_INTERRUPTION,
                            "Notification during planned break: ${notif.packageName} (rest allowed)"
                        )
                    } else {
                        // Deep work period: track timestamp to identify notification-triggered switches
                        lastNotificationTimestamp = System.currentTimeMillis()
                        lastNotificationPackage = notif.packageName
                        logFocusEvent(
                            FocusEventType.NOTIFICATION_INTERRUPTION,
                            "Notification from ${notif.packageName}"
                        )
                        refreshFocusSummary()
                    }
                }
            }
        }

        refreshPermissions()
        loadRealFocusData()
    }

    private fun ChatSessionEntity.toDomain(): StudyTopicSession = StudyTopicSession(
        id = "$SESSION_ID_PREFIX$id",
        title = title,
        subject = subject,
        previewText = previewText,
        explanation = explanation,
        formula = formula,
        bulletPoints = bulletPoints,
        usedOnlineContext = usedOnlineContext,
        followUps = emptyList() // loaded on-demand in selectSession()
    )

    private fun deriveTopicDetails(rawText: String): Triple<String, String, String?> {
        val lower = rawText.lowercase()
        return when {
            lower.contains("photosynthesis") -> Triple("Photosynthesis", "Biology", "6CO₂ + 6H₂O ➔ C₆H₁₂O₆ + 6O₂")
            lower.contains("newton") || lower.contains("force") -> Triple("Newton's Laws of Motion", "Physics", "F = m · a")
            lower.contains("calculus") || lower.contains("integration") || lower.contains("integral") -> Triple("Integration by Parts", "Calculus", "∫ u dv = uv - ∫ v du")
            lower.contains("quadratic") || lower.contains("discriminant") -> Triple("Quadratic Equation", "Algebra", "x = (-b ± √(b² - 4ac)) / 2a")
            lower.contains("pythagor") || lower.contains("triangle") -> Triple("Pythagorean Theorem", "Geometry", "a² + b² = c²")
            lower.contains("python") || lower.contains("code") || lower.contains("program") -> Triple("Python Fundamentals", "Computer Science", "[x**2 for x in range(10)]")
            else -> {
                val cleanTitle = rawText.lines().firstOrNull { it.isNotBlank() }?.take(30) ?: "General Study Topic"
                Triple(cleanTitle, "General Science", null)
            }
        }
    }

    fun setCapturedText(text: String) {
        _capturedText.value = text
    }

    fun selectSession(sessionId: String) {
        val found = _sessionHistory.value.find { it.id == sessionId } ?: return
        _activeSession.value = found
        _capturedText.value = found.previewText
        _explanationResult.value = ExplanationResult(
            captureId = found.dbId() ?: System.currentTimeMillis(),
            finalExplanation = found.explanation,
            usedOnlineContext = found.usedOnlineContext
        )
        // Reset KV-cache so tokens from previous chat sessions do not pollute the selected chat
        llmEngine.resetSession()
        // Load this session's real follow-up thread from Room.
        viewModelScope.launch {
            val dbId = found.dbId()
            _followUpList.value = if (dbId != null) {
                chatDao.getMessagesForSession(dbId).map { m ->
                    FollowUpMessage(id = m.id.toString(), question = m.question, answer = m.answer, timestamp = m.timestamp)
                }
            } else {
                emptyList()
            }
        }
    }

    fun startNewSession() {
        _activeSession.value = null
        _capturedText.value = ""
        _explanationResult.value = null
        _followUpList.value = emptyList()
        // Reset KV-cache to 0 tokens for the fresh session
        llmEngine.resetSession()
    }

    fun toggleSimulatedNetwork() {
        _isOnline.value = !_isOnline.value
    }

    // image != null means multimodal - the photo goes straight to the model, no OCR step.
    // customText/capturedText can legitimately be blank in that case.
    fun explainCurrentCapture(customText: String? = null, image: Bitmap? = null) {
        val textToProcess = customText?.ifBlank { null }
            ?: _capturedText.value.ifBlank { if (image != null) "" else "General Study Topic" }
        _capturedText.value = textToProcess
        _isExplaining.value = true
        _followUpList.value = emptyList()
        // Fresh capture gets a clean KV-cache
        llmEngine.resetSession()

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val online = _isOnline.value
            val capture = StudyCapture(id = startTime, extractedText = textToProcess, timestamp = startTime)

            // Real pipeline: on-device model always runs (reads the image directly when
            // provided); retrieval only blends in if online AND there's a text topic (§3a).
            val result = explainPipeline.explain(capture, online, image)

            val latency = System.currentTimeMillis() - startTime
            val wordCount = result.finalExplanation.split("\\s+".toRegex()).size
            val tokensPerSec = String.format(
                java.util.Locale.US, "%.1f", (wordCount * 1.3) / (latency / 1000.0).coerceAtLeast(0.5)
            ).toDoubleOrNull() ?: 0.0

            _vitals.value = _vitals.value.copy(
                tokensPerSecond = tokensPerSec,
                latencyMs = latency,
                ramUsedMb = vitalsMonitor.getRamUsedMb().coerceAtLeast(312),
                thermalStatus = vitalsMonitor.getThermalStatus()
            )

            val (topicTitle, topicSubject, topicFormula) = deriveTopicDetails(textToProcess)
            val bulletPoints = listOf(
                "• Key topic: $topicTitle",
                "• Evaluated on-device by StudyLens AI",
                if (result.usedOnlineContext) "• Enhanced with real-time web context" else "• Processed 100% offline on-device"
            )

            // Persist to Room - this is the real "remembers previous chats" storage.
            // The sessionHistory Flow (collected in init) picks this up automatically.
            val dbId = chatDao.insertSession(
                ChatSessionEntity(
                    title = topicTitle,
                    subject = topicSubject,
                    previewText = textToProcess,
                    explanation = result.finalExplanation,
                    formula = topicFormula,
                    bulletPoints = bulletPoints,
                    usedOnlineContext = result.usedOnlineContext,
                    timestamp = startTime
                )
            )

            _activeSession.value = StudyTopicSession(
                id = "$SESSION_ID_PREFIX$dbId",
                title = topicTitle,
                subject = topicSubject,
                previewText = textToProcess,
                explanation = result.finalExplanation,
                formula = topicFormula,
                bulletPoints = bulletPoints,
                usedOnlineContext = result.usedOnlineContext,
                followUps = emptyList()
            )
            _explanationResult.value = result
            _isExplaining.value = false
        }
    }

    fun isSummarizeQuery(question: String): Boolean {
        val lower = question.lowercase()
        return lower.contains("summarize") || lower.contains("summarise") ||
                lower.contains("summary") || lower.contains("recap") ||
                lower.contains("key takeaways")
    }

    fun summarizeCurrentConversation() {
        val rootExplanation = _explanationResult.value?.finalExplanation
            ?: _activeSession.value?.explanation
            ?: _capturedText.value
        if (rootExplanation.isBlank()) return

        _isAnsweringFollowUp.value = true

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val maxTokens = ModelDownloadManager.getActiveModelMaxTokens(getApplication())
            val result = explainPipeline.summarizeConversation(
                rootExplanation = rootExplanation,
                followUps = _followUpList.value,
                maxTokens = maxTokens
            )

            val latency = System.currentTimeMillis() - startTime
            _vitals.value = _vitals.value.copy(latencyMs = latency)

            val displayQuestion = "✨ Summarize Conversation"
            val sessionDbId = _activeSession.value?.dbId()
            if (sessionDbId != null) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionDbId,
                        question = displayQuestion,
                        answer = result.finalExplanation,
                        timestamp = startTime
                    )
                )
            }

            val newMessage = FollowUpMessage(
                id = System.currentTimeMillis().toString(),
                question = displayQuestion,
                answer = result.finalExplanation,
                usedOnlineContext = result.usedOnlineContext
            )
            _followUpList.value = _followUpList.value + newMessage
            _isAnsweringFollowUp.value = false
        }
    }

    fun askFollowUp(question: String) {
        if (question.isBlank()) return

        if (isSummarizeQuery(question)) {
            summarizeCurrentConversation()
            return
        }

        _isAnsweringFollowUp.value = true

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val online = _isOnline.value
            val capture = StudyCapture(
                id = _explanationResult.value?.captureId ?: startTime,
                extractedText = _capturedText.value,
                timestamp = startTime
            )

            // Build the full running transcript (original explanation + every prior Q&A) so
            // the model has real context - without this, a second follow-up like "what is
            // component" has no idea it's still talking about React from the first question.
            val conversationContext = buildString {
                append("Topic explanation: ")
                append(_explanationResult.value?.finalExplanation ?: _activeSession.value?.explanation ?: "")
                append("\n")
                _followUpList.value.forEach { fu ->
                    append("Q: ${fu.question}\nA: ${fu.answer}\n")
                }
            }

            val result = explainPipeline.answerFollowUp(capture, conversationContext, question, online)

            val latency = System.currentTimeMillis() - startTime
            _vitals.value = _vitals.value.copy(latencyMs = latency)

            // Persist under the active session so it survives app restarts, same as the
            // explanation itself.
            val sessionDbId = _activeSession.value?.dbId()
            if (sessionDbId != null) {
                chatDao.insertMessage(
                    ChatMessageEntity(
                        sessionId = sessionDbId,
                        question = question,
                        answer = result.finalExplanation,
                        timestamp = startTime
                    )
                )
            }

            val newMessage = FollowUpMessage(
                id = System.currentTimeMillis().toString(),
                question = question,
                answer = result.finalExplanation,
                usedOnlineContext = result.usedOnlineContext
            )
            _followUpList.value = _followUpList.value + newMessage
            _isAnsweringFollowUp.value = false
        }
    }

    fun generatePracticeQuiz() {
        _selectedQuizAnswers.value = emptyMap()
        _quizSubmitted.value = false

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val capture = StudyCapture(
                id = startTime,
                extractedText = _capturedText.value,
                timestamp = startTime
            )
            _quizQuestions.value = explainPipeline.generateQuiz(capture)
        }
    }

    fun selectQuizAnswer(questionId: Long, selectedOption: String) {
        if (_quizSubmitted.value) return
        _selectedQuizAnswers.value = _selectedQuizAnswers.value + (questionId to selectedOption)
    }

    fun submitQuiz() {
        _quizSubmitted.value = true
        val questions = _quizQuestions.value
        val answers = _selectedQuizAnswers.value

        val newWrongQuestions = mutableListOf<QuizQuestion>()
        for (q in questions) {
            val userAnswer = answers[q.id]
            if (userAnswer != q.correctAnswer) {
                newWrongQuestions.add(q)
            }
        }

        // Add wrong topics to Revision list
        val currentRevision = _revisionList.value.toMutableList()
        for (wrong in newWrongQuestions) {
            if (currentRevision.none { it.id == wrong.id }) {
                currentRevision.add(wrong)
            }
        }
        _revisionList.value = currentRevision
    }

    fun removeRevisionItem(questionId: Long) {
        _revisionList.value = _revisionList.value.filter { it.id != questionId }
    }

    fun speakText(text: String) {
        ttsManager.speak(text)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun refreshPermissions() {
        _isUsageAccessGranted.value = usageCollector.isUsageAccessGranted()
        _isNotificationAccessGranted.value = notificationCollector.isNotificationAccessGranted()
    }

    fun loadRealFocusData() {
        viewModelScope.launch {
            val hasUsage = _isUsageAccessGranted.value
            val hasNotif = _isNotificationAccessGranted.value

            // 0. Restore persistent FocusSessionSummary from SQLite so data survives app restart
            val savedSummaryEntity = focusSignalsDao.getLatestFocusSessionSummary()
            val allSavedSummaries = focusSignalsDao.getAllFocusSessionSummaries(limit = 50)

            if (savedSummaryEntity != null && !_isFocusModeActive.value) {
                currentFocusSessionId = savedSummaryEntity.sessionId
                val events = focusSignalsDao.getFocusEvents(savedSummaryEntity.sessionId).map { it.toDomain() }
                val trend = computeHistoricalTrend(allSavedSummaries)
                val baseSummary = savedSummaryEntity.toDomain(events)
                val mistakeAnalysis = computeMistakeAnalysis(baseSummary, events, allSavedSummaries)

                val restoredSummary = baseSummary.copy(
                    mistakeAnalysis = mistakeAnalysis,
                    historicalTrend = trend
                )
                _focusSummary.value = restoredSummary

                _studySession.value = StudySession(
                    id = savedSummaryEntity.sessionId,
                    startTime = savedSummaryEntity.startTime,
                    endTime = savedSummaryEntity.endTime,
                    durationMs = savedSummaryEntity.totalStudyTimeMs,
                    switchCount = savedSummaryEntity.switchCount,
                    notificationCount = savedSummaryEntity.notificationCount
                )

                _focusInsight.value = FocusInsight(
                    type = "AI_FOCUS_GUARD",
                    evidence = focusNarrator.buildEvidence(restoredSummary),
                    narrative = savedSummaryEntity.narrative
                )
                return@launch
            }

            if (!hasUsage && !hasNotif) {
                _focusInsight.value = FocusInsight(
                    type = "PERMISSION_REQUIRED",
                    evidence = "Grant Usage Access and Notification Access to track your real focus patterns.",
                    narrative = "Once granted, StudyLens securely computes focus streaks and distraction signals entirely on-device."
                )
                if (_focusSummary.value == null) {
                    _focusSummary.value = FocusSessionSummary(
                        narrative = "Permissions are required to analyze your focus patterns on-device.",
                        recommendation = "Grant Usage Access and Notification Access to start tracking deep work streaks."
                    )
                }
                return@launch
            }

            val now = System.currentTimeMillis()
            val startTime = if (_isFocusModeActive.value && focusSessionStartTime > 0L) {
                focusSessionStartTime
            } else {
                appStartTime
            }

            // 1. Fetch real notification events from Room
            val notifs = if (hasNotif) {
                notificationCollector.getNotificationEvents(startTime, now)
            } else {
                emptyList()
            }

            // 2. Query real usage stats and build StudySession
            val session = if (hasUsage) {
                usageCollector.buildStudySession(startTime, now, notifs)
            } else {
                StudySession(
                    id = 0L,
                    startTime = startTime,
                    endTime = now,
                    durationMs = (now - startTime).coerceAtLeast(0L),
                    switchCount = 0,
                    notificationCount = notifs.size
                )
            }
            _studySession.value = session

            // 3. Compute evidence directly from real StudySession and rolling history
            val durationMins = (session.durationMs / 60000L)
            val recentSessions = focusSignalsDao.getRecentStudySessions(limit = 20).first()

            val evidence = if (!hasUsage) {
                "Notification access enabled with ${session.notificationCount} notifications detected. Usage access is needed to track app switches."
            } else if (session.switchCount == 0 && session.notificationCount == 0) {
                if (durationMins > 0) {
                    "You stayed focused for $durationMins minutes with 0 app switches and 0 notifications."
                } else {
                    "Your study session has just begun with zero interruptions."
                }
            } else if (recentSessions.size > 1) {
                val avgSwitches = recentSessions.map { it.switchCount }.average()
                if (session.switchCount < avgSwitches) {
                    "Your session lasted $durationMins minutes with ${session.switchCount} app switches (fewer than your average of ${String.format(java.util.Locale.US, "%.1f", avgSwitches)}) and ${session.notificationCount} notifications."
                } else {
                    "Your session lasted $durationMins minutes with ${session.switchCount} app switches and ${session.notificationCount} notifications."
                }
            } else {
                "Your last study session lasted $durationMins minutes with ${session.switchCount} app switches and ${session.notificationCount} notifications."
            }

            // 4. Generate AI narrative using on-device Gemma LLM engine (retrieve/generate discipline)
            val prompt = """
                You are a calm, evidence-based study coach. Given this observation about a
                student's study session, write exactly 2 sentences explaining what it means
                for their focus, then 1 short actionable suggestion. Do not invent numbers
                beyond what's given.

                Observation: $evidence
            """.trimIndent()

            val narrative = llmEngine.generateResponse(prompt)
            _focusInsight.value = FocusInsight(
                type = if (session.switchCount == 0) "DEEP_WORK_STREAK" else "SESSION_SUMMARY",
                evidence = evidence,
                narrative = narrative
            )

            // 5. Update FocusSessionSummary from local SQLite storage if not currently in active custom session
            if (!_isFocusModeActive.value) {
                val latestSaved = focusSignalsDao.getLatestFocusSessionSummary()
                val allHistory = focusSignalsDao.getAllFocusSessionSummaries(limit = 50)
                val trend = computeHistoricalTrend(allHistory)

                if (latestSaved != null) {
                    val savedEvents = focusSignalsDao.getFocusEvents(latestSaved.sessionId).map { it.toDomain() }
                    val mistakeAnalysis = computeMistakeAnalysis(latestSaved.toDomain(savedEvents), savedEvents, allHistory)
                    val notifBreakdown = computeNotificationBreakdown(savedEvents, notifs)
                    _focusSummary.value = latestSaved.toDomain(savedEvents).copy(
                        mistakeAnalysis = mistakeAnalysis,
                        historicalTrend = trend,
                        notificationBreakdown = notifBreakdown
                    )
                } else {
                    val focusEvents = if (currentFocusSessionId != 0L) {
                        focusSignalsDao.getFocusEvents(currentFocusSessionId).map { it.toDomain() }
                    } else {
                        emptyList()
                    }
                    val notifBreakdown = computeNotificationBreakdown(focusEvents, notifs)

                    _focusSummary.value = FocusSessionSummary(
                        sessionId = currentFocusSessionId,
                        startTime = startTime,
                        endTime = now,
                        totalStudyTimeMs = session.durationMs,
                        focusedTimeMs = (session.durationMs * 0.85).toLong(),
                        longestFocusStreakMs = (session.durationMs * 0.75).toLong(),
                        switchCount = session.switchCount,
                        notificationCount = session.notificationCount,
                        timelineEvents = focusEvents,
                        narrative = narrative,
                        recommendation = if (session.switchCount > 2) {
                            "Try a clearly timed 1–2 minute break to prevent accidental drift."
                        } else {
                            "Great focus rhythm! Maintain 20-minute study sprints with brief micro-breaks."
                        },
                        isFocusModeActive = false,
                        historicalTrend = trend,
                        notificationBreakdown = notifBreakdown
                    )
                }
            }
        }
    }

    fun startFocusSession() {
        val now = System.currentTimeMillis()
        currentFocusSessionId = now
        focusSessionStartTime = now
        _isFocusModeActive.value = true
        _activeBreak.value = null
        _focusGuardDialog.value = null
        _contextRecap.value = null
        sessionBreaks.clear()
        recentSwitchTimestamps.clear()
        notificationTriggeredSwitchesCount = 0
        lastNotificationTimestamp = 0L
        lastNotificationPackage = null

        logFocusEvent(FocusEventType.RETURNED_TO_STUDY, "Focus session started")
        refreshFocusSummary()
    }

    fun toggleFocusMode() {
        if (_isFocusModeActive.value) {
            endFocusSession()
        } else {
            startFocusSession()
        }
    }

    fun endFocusSession() {
        val now = System.currentTimeMillis()
        val startTime = focusSessionStartTime
        _isFocusModeActive.value = false
        _activeBreak.value = null
        _focusGuardDialog.value = null
        breakReminderHelper.cancelBreakReminder()

        viewModelScope.launch {
            val notifs = notificationCollector.getNotificationEvents(startTime, now)
            val session = usageCollector.buildStudySession(startTime, now, notifs)

            val events = if (currentFocusSessionId != 0L) {
                focusSignalsDao.getFocusEvents(currentFocusSessionId).map { it.toDomain() }
            } else {
                emptyList()
            }
            val studyDuration = (now - startTime).coerceAtLeast(0L)
            val breakDuration = if (sessionBreaks.isNotEmpty()) {
                sessionBreaks.sumOf { it.actualMs }
            } else {
                events.count { it.eventType == FocusEventType.BREAK_STARTED } * 60000L
            }
            val focusedDuration = (studyDuration - breakDuration).coerceAtLeast(0L)

            val breaks = events.count { it.eventType == FocusEventType.BREAK_STARTED }
            val missed = events.count { it.eventType == FocusEventType.REMINDER_MISSED }
            val avgPlanned = if (sessionBreaks.isNotEmpty()) {
                sessionBreaks.map { it.plannedMs }.average().toLong()
            } else if (breaks > 0) 60000L else 0L

            val avgActual = if (sessionBreaks.isNotEmpty()) {
                sessionBreaks.map { it.actualMs }.average().toLong()
            } else if (breaks > 0) 75000L else 0L

            val longestDelayed = if (sessionBreaks.isNotEmpty()) {
                sessionBreaks.maxOfOrNull { it.delayedMs } ?: 0L
            } else if (missed > 0) 120000L else 0L

            val studySwitches = events.count { it.eventType == FocusEventType.STUDY_RELATED_SWITCH }
            val chains = events.count { it.eventType == FocusEventType.DISTRACTION_CHAIN_DETECTED }

            // Compute accurate longest focus streak from interruption intervals
            val interruptionTimes = events.filter {
                it.eventType in listOf(
                    FocusEventType.APP_SWITCH,
                    FocusEventType.BREAK_STARTED,
                    FocusEventType.DISTRACTION_CHAIN_DETECTED
                )
            }.map { it.timestamp }.sorted()

            val longestStreak = if (interruptionTimes.isEmpty()) {
                focusedDuration
            } else {
                var maxGap = 0L
                var prevBoundary = startTime
                for (t in interruptionTimes) {
                    val gap = (t - prevBoundary).coerceAtLeast(0L)
                    if (gap > maxGap) maxGap = gap
                    prevBoundary = t
                }
                val tailGap = (now - prevBoundary).coerceAtLeast(0L)
                if (tailGap > maxGap) maxGap = tailGap
                maxGap.coerceAtMost(focusedDuration).coerceAtLeast(1000L)
            }

            val summary = FocusSessionSummary(
                sessionId = currentFocusSessionId,
                startTime = startTime,
                endTime = now,
                totalStudyTimeMs = studyDuration,
                focusedTimeMs = focusedDuration,
                longestFocusStreakMs = longestStreak,
                switchCount = session.switchCount.coerceAtLeast(events.count { it.eventType == FocusEventType.APP_SWITCH }),
                notificationCount = notifs.size,
                checksPresented = events.count { it.eventType == FocusEventType.QUIZ_PRESENTED },
                checksPassed = events.count { it.eventType == FocusEventType.QUIZ_PASSED },
                checksFailed = events.count { it.eventType == FocusEventType.QUIZ_FAILED },
                normalOverrides = events.count { it.eventType == FocusEventType.NORMAL_OVERRIDE || it.eventType == FocusEventType.QUIZ_OVERRIDE },
                emergencyExits = events.count { it.eventType == FocusEventType.EMERGENCY_EXIT },
                plannedBreaksCount = breaks,
                returnedOnTimeCount = events.count { it.eventType == FocusEventType.RETURNED_TO_STUDY && it.details.contains("On-time", ignoreCase = true) },
                missedRemindersCount = missed,
                avgPlannedBreakMs = avgPlanned,
                avgActualBreakMs = avgActual,
                longestDelayedReturnMs = longestDelayed,
                studyRelatedSwitches = studySwitches,
                distractionChainsCount = chains,
                timelineEvents = events,
                isFocusModeActive = false
            )

            // Generate AI Narrative and Recommendation with Gemma
            val (narrative, recommendation) = focusNarrator.generateCoaching(summary)
            val mistakeAnalysis = computeMistakeAnalysis(summary, events, emptyList())

            // Persist FocusSessionSummary to SQLite so data survives app closure
            val entity = FocusSessionSummaryEntity(
                sessionId = currentFocusSessionId,
                startTime = startTime,
                endTime = now,
                totalStudyTimeMs = studyDuration,
                focusedTimeMs = focusedDuration,
                longestFocusStreakMs = longestStreak,
                switchCount = session.switchCount.coerceAtLeast(events.count { it.eventType == FocusEventType.APP_SWITCH }),
                notificationCount = notifs.size,
                checksPresented = events.count { it.eventType == FocusEventType.QUIZ_PRESENTED },
                checksPassed = events.count { it.eventType == FocusEventType.QUIZ_PASSED },
                checksFailed = events.count { it.eventType == FocusEventType.QUIZ_FAILED },
                normalOverrides = events.count { it.eventType == FocusEventType.NORMAL_OVERRIDE || it.eventType == FocusEventType.QUIZ_OVERRIDE },
                emergencyExits = events.count { it.eventType == FocusEventType.EMERGENCY_EXIT },
                plannedBreaksCount = breaks,
                returnedOnTimeCount = events.count { it.eventType == FocusEventType.RETURNED_TO_STUDY && it.details.contains("On-time", ignoreCase = true) },
                missedRemindersCount = missed,
                avgPlannedBreakMs = avgPlanned,
                avgActualBreakMs = avgActual,
                longestDelayedReturnMs = longestDelayed,
                studyRelatedSwitches = studySwitches,
                distractionChainsCount = chains,
                narrative = narrative,
                recommendation = recommendation,
                mainLeakReason = mistakeAnalysis.mainLeakReason,
                actionPlan = mistakeAnalysis.actionPlan
            )
            focusSignalsDao.insertFocusSessionSummary(entity)

            // Re-fetch all historical sessions from SQLite to compute cross-session trend
            val allHistory = focusSignalsDao.getAllFocusSessionSummaries(limit = 50)
            val trend = computeHistoricalTrend(allHistory)
            val notifBreakdown = computeNotificationBreakdown(events, notifs)

            val updatedSummary = summary.copy(
                narrative = narrative,
                recommendation = recommendation,
                mistakeAnalysis = mistakeAnalysis,
                historicalTrend = trend,
                notificationBreakdown = notifBreakdown
            )
            _focusSummary.value = updatedSummary

            // Update backward-compatible state flows
            _studySession.value = session
            _focusInsight.value = FocusInsight(
                type = "AI_FOCUS_GUARD",
                evidence = focusNarrator.buildEvidence(updatedSummary),
                narrative = narrative
            )
        }
    }

    fun triggerIntentToSwitch() {
        if (!_isFocusModeActive.value) {
            startFocusSession()
        }
        _focusGuardDialog.value = FocusGuardDialogPhase.SelectIntent
    }

    fun onSelectSwitchIntent(intent: SwitchIntent) {
        when (intent) {
            SwitchIntent.STUDY_NEED -> {
                logFocusEvent(FocusEventType.STUDY_RELATED_SWITCH, "User opened research duration selector")
                _focusGuardDialog.value = FocusGuardDialogPhase.ChooseResearchDuration()
            }
            SwitchIntent.SHORT_BREAK -> {
                _focusGuardDialog.value = FocusGuardDialogPhase.ChooseBreakDuration(isAfterPassedQuiz = false)
            }
            SwitchIntent.IMPORTANT_NOTIFICATION -> {
                logFocusEvent(FocusEventType.NORMAL_OVERRIDE, "Switched for important notification")
                _focusGuardDialog.value = FocusGuardDialogPhase.ChooseBreakDuration(isAfterPassedQuiz = false)
            }
            SwitchIntent.EMERGENCY_CALL -> {
                _focusGuardDialog.value = FocusGuardDialogPhase.EmergencyExitConfirm
            }
            SwitchIntent.DISTRACTED -> {
                viewModelScope.launch {
                    val topic = _activeSession.value?.title ?: _capturedText.value.lines().firstOrNull()?.take(30) ?: "Newton's Laws"
                    val question = focusNarrator.generateQuickFocusQuestion(topic, _activeSession.value?.explanation ?: _capturedText.value)
                    logFocusEvent(FocusEventType.QUIZ_PRESENTED, "Presented check for topic: $topic")
                    _focusGuardDialog.value = FocusGuardDialogPhase.QuickFocusCheck(question)
                }
            }
        }
    }

    fun answerFocusCheck(selectedAnswer: String) {
        val currentPhase = _focusGuardDialog.value as? FocusGuardDialogPhase.QuickFocusCheck ?: return
        val isCorrect = selectedAnswer.trim().equals(currentPhase.question.correctAnswer.trim(), ignoreCase = true)

        if (isCorrect) {
            logFocusEvent(FocusEventType.QUIZ_PASSED, "Answered correctly: $selectedAnswer")
            _focusGuardDialog.value = FocusGuardDialogPhase.ChooseBreakDuration(isAfterPassedQuiz = true)
        } else {
            logFocusEvent(FocusEventType.QUIZ_FAILED, "Answered incorrectly: $selectedAnswer (Expected: ${currentPhase.question.correctAnswer})")
            _focusGuardDialog.value = FocusGuardDialogPhase.IncorrectAnswerReview
        }
        refreshFocusSummary()
    }

    fun onStayAfterQuiz() {
        logFocusEvent(FocusEventType.STAY_AFTER_QUIZ, "Student chose to stay and review")
        _focusGuardDialog.value = null
        refreshFocusSummary()
    }

    fun onOverrideQuiz() {
        logFocusEvent(FocusEventType.QUIZ_OVERRIDE, "Student chose to switch anyway after quiz")
        _focusGuardDialog.value = FocusGuardDialogPhase.ChooseBreakDuration(isAfterPassedQuiz = false)
        refreshFocusSummary()
    }

    fun startPlannedBreak(durationMs: Long) {
        val now = System.currentTimeMillis()
        logFocusEvent(FocusEventType.BREAK_STARTED, "Planned break duration: ${durationMs / 1000}s")
        logFocusEvent(FocusEventType.REMINDER_SCHEDULED, "Scheduled return reminder in ${durationMs / 1000}s")

        if (durationMs > 0) {
            _activeBreak.value = ActiveBreakInfo(plannedDurationMs = durationMs, startTime = now, isResearch = false)
            breakReminderHelper.scheduleBreakReminder(
                delayMs = durationMs,
                title = "🔔 Focus Reminder",
                reminderText = "Your break is over. Ready to get back to your study session?"
            )
        } else {
            _activeBreak.value = null
        }
        _focusGuardDialog.value = null
        refreshFocusSummary()
    }

    fun startPlannedResearch(durationMs: Long) {
        val now = System.currentTimeMillis()
        val estimatedSec = (durationMs / 1000L).coerceAtLeast(1L)
        logFocusEvent(FocusEventType.STUDY_RELATED_SWITCH, "Productive research allowed (target: ${estimatedSec}s)")

        if (durationMs > 0) {
            logFocusEvent(FocusEventType.REMINDER_SCHEDULED, "Scheduled research return reminder in ${estimatedSec}s")
            _activeBreak.value = ActiveBreakInfo(plannedDurationMs = durationMs, startTime = now, isResearch = true)
            breakReminderHelper.scheduleResearchReminder(
                delayMs = durationMs,
                overdueDelayMs = 15_000L,
                title = "📖 Research Complete?",
                reminderText = "We think research is over, can we return back?",
                overdueTitle = "⏱️ Research Overdue!",
                onOverdue = {
                    logFocusEvent(
                        FocusEventType.REMINDER_MISSED,
                        "Research overdue: took more than ${estimatedSec}s than estimated"
                    )
                }
            )
        } else {
            _activeBreak.value = null
        }
        _focusGuardDialog.value = null
        refreshFocusSummary()
    }

    fun triggerEmergencyExit() {
        logFocusEvent(FocusEventType.EMERGENCY_EXIT, "Immediate emergency exit triggered")
        endFocusSession()
    }

    fun triggerNormalOverride(durationMs: Long = 60000L) {
        logFocusEvent(FocusEventType.NORMAL_OVERRIDE, "Normal override used")
        startPlannedBreak(durationMs)
    }

    fun dismissFocusGuardDialog() {
        _focusGuardDialog.value = null
    }

    fun onUserMinimizedApp() {
        if (!_isFocusModeActive.value) return
        val now = System.currentTimeMillis()
        lastPauseTimestamp = now

        if (recentSwitchTimestamps.isEmpty() || now - recentSwitchTimestamps.last() > 500L) {
            recentSwitchTimestamps.add(now)
        }
        recentSwitchTimestamps.removeAll { now - it > 180_000L }

        logFocusEvent(FocusEventType.APP_SWITCH, "Minimized StudyLens (Home / Middle button pressed)")

        if (recentSwitchTimestamps.size >= 2) {
            val count = recentSwitchTimestamps.size
            isAppHoppingActive = true
            appHoppingSwitchCount = count
            logFocusEvent(FocusEventType.DISTRACTION_CHAIN_DETECTED, "Distraction chain: $count app switches in last 3 minutes (minimized StudyLens)")
            breakReminderHelper.vibrateDevice(longArrayOf(0, 400, 200, 400))
            breakReminderHelper.showAppHoppingNotification(count)
        }
    }

    fun onAppPaused() {
        if (!_isFocusModeActive.value) return
        val now = System.currentTimeMillis()
        lastPauseTimestamp = now

        if (recentSwitchTimestamps.isEmpty() || now - recentSwitchTimestamps.last() > 500L) {
            recentSwitchTimestamps.add(now)
        }
        recentSwitchTimestamps.removeAll { now - it > 180_000L }
        if (recentSwitchTimestamps.size >= 3) {
            val count = recentSwitchTimestamps.size
            isAppHoppingActive = true
            appHoppingSwitchCount = count
            logFocusEvent(FocusEventType.DISTRACTION_CHAIN_DETECTED, "Distraction chain: $count app switches in last 3 minutes")
            breakReminderHelper.vibrateDevice(longArrayOf(0, 400, 200, 400))
            breakReminderHelper.showAppHoppingNotification(count)
        }

        val isDuringBreak = _activeBreak.value != null
        if (!isDuringBreak) {
            val timeSinceNotif = now - lastNotificationTimestamp
            if (timeSinceNotif in 0..15_000L && lastNotificationPackage != null) {
                // User switched away within 15s of receiving a notification during deep work!
                notificationTriggeredSwitchesCount++
                logFocusEvent(
                    FocusEventType.APP_SWITCH,
                    "Notification-triggered switch (opened $lastNotificationPackage within ${timeSinceNotif / 1000}s)"
                )
            } else {
                logFocusEvent(FocusEventType.APP_SWITCH, "Switched away from StudyLens")
            }
        }
    }

    fun onAppResumed() {
        if (!_isFocusModeActive.value) return
        breakReminderHelper.stopContinuousAlarm()
        val now = System.currentTimeMillis()
        if (lastPauseTimestamp > 0L) {
            val awayDuration = (now - lastPauseTimestamp).coerceAtLeast(0L)
            val activeBreak = _activeBreak.value

            val wasHopping = isAppHoppingActive
            val hopCount = appHoppingSwitchCount
            isAppHoppingActive = false
            appHoppingSwitchCount = 0

            val hadPlannedBreak = activeBreak != null
            val isLate = if (activeBreak != null) {
                val actualBreakMs = (now - activeBreak.startTime).coerceAtLeast(awayDuration)
                val delayedMs = (actualBreakMs - activeBreak.plannedDurationMs).coerceAtLeast(0L)
                sessionBreaks.add(BreakRecord(activeBreak.plannedDurationMs, actualBreakMs, delayedMs))

                val late = actualBreakMs > (activeBreak.plannedDurationMs + 10000L)
                if (late) {
                    val delaySec = (delayedMs / 1000L)
                    logFocusEvent(FocusEventType.REMINDER_MISSED, "Returned late: away ${actualBreakMs / 1000}s (planned ${activeBreak.plannedDurationMs / 1000}s, delayed +${delaySec}s)")
                } else {
                    logFocusEvent(FocusEventType.RETURNED_TO_STUDY, "On-time return: away ${actualBreakMs / 1000}s vs planned ${activeBreak.plannedDurationMs / 1000}s")
                    logFocusEvent(FocusEventType.BREAK_COMPLETED, if (activeBreak.isResearch) "Research completed on time" else "Break finished successfully")
                }
                _activeBreak.value = null
                breakReminderHelper.cancelBreakReminder()
                late
            } else {
                logFocusEvent(FocusEventType.RETURNED_TO_STUDY, "Returned back to StudyLens after ${awayDuration / 1000}s")
                false
            }

            // Offer 10-second Context Recovery if away for >= 5 seconds or returning from a break/research or from app hopping
            if (awayDuration >= 5000L || hadPlannedBreak || wasHopping) {
                val topic = _activeSession.value?.title ?: _capturedText.value.lines().firstOrNull()?.take(30) ?: "Newton's Laws"
                val overdueSec = if (hadPlannedBreak) ((awayDuration - activeBreak.plannedDurationMs).coerceAtLeast(0L) / 1000L) else 0L
                val onTime = hadPlannedBreak && !isLate && !wasHopping
                _contextRecap.value = ContextRecapInfo(
                    topic = topic,
                    awayDurationMs = awayDuration,
                    returnedOnTime = onTime,
                    delaySecs = overdueSec,
                    wasPlannedBreak = hadPlannedBreak,
                    isAppHoppingDetected = wasHopping,
                    hopCount = hopCount
                )
                logFocusEvent(FocusEventType.CONTEXT_RECAP_SHOWN, "Offered context recovery for $topic (onTime=$onTime, wasHopping=$wasHopping)")
                // Pre-generate AI recap immediately so student has it ready on screen
                requestContextRecap()
            }
            lastPauseTimestamp = 0L
            refreshFocusSummary()
        }
    }

    fun requestContextRecap() {
        val current = _contextRecap.value ?: return
        _contextRecap.value = current.copy(isLoading = true)

        viewModelScope.launch {
            val text = focusNarrator.generateContextRecap(
                topic = current.topic,
                contextText = _activeSession.value?.explanation ?: _capturedText.value,
                awayDurationMs = current.awayDurationMs
            )
            logFocusEvent(FocusEventType.CONTEXT_RECAP_USED, "User viewed context recap")
            _contextRecap.value = current.copy(recapText = text, isLoading = false)
        }
    }

    fun dismissContextRecap() {
        _contextRecap.value = null
    }

    private fun logFocusEvent(type: FocusEventType, details: String) {
        viewModelScope.launch {
            val entity = FocusEventEntity(
                sessionId = currentFocusSessionId,
                eventType = type.name,
                details = details,
                timestamp = System.currentTimeMillis()
            )
            focusSignalsDao.insertFocusEvent(entity)
        }
    }

    fun refreshFocusSummary() {
        viewModelScope.launch {
            if (currentFocusSessionId == 0L && !_isFocusModeActive.value) {
                return@launch
            }
            val events = if (currentFocusSessionId != 0L) {
                focusSignalsDao.getFocusEvents(currentFocusSessionId).map { it.toDomain() }
            } else {
                emptyList()
            }
            val now = System.currentTimeMillis()
            val startTime = if (focusSessionStartTime > 0L) focusSessionStartTime else now
            val studyDuration = if (_isFocusModeActive.value) (now - startTime).coerceAtLeast(0L) else (_focusSummary.value?.totalStudyTimeMs ?: 0L)

            val checksP = events.count { it.eventType == FocusEventType.QUIZ_PRESENTED }
            val checksPass = events.count { it.eventType == FocusEventType.QUIZ_PASSED }
            val checksFail = events.count { it.eventType == FocusEventType.QUIZ_FAILED }
            val normOver = events.count { it.eventType == FocusEventType.NORMAL_OVERRIDE || it.eventType == FocusEventType.QUIZ_OVERRIDE }
            val emerg = events.count { it.eventType == FocusEventType.EMERGENCY_EXIT }
            val breaks = events.count { it.eventType == FocusEventType.BREAK_STARTED }
            val onTime = events.count { it.eventType == FocusEventType.RETURNED_TO_STUDY && it.details.contains("On-time", ignoreCase = true) }
            val missed = events.count { it.eventType == FocusEventType.REMINDER_MISSED }
            val breakDuration = if (sessionBreaks.isNotEmpty()) {
                sessionBreaks.sumOf { it.actualMs }
            } else {
                breaks * 60000L
            }
            val focusedDuration = (studyDuration - breakDuration).coerceAtLeast(0L)

            val avgPlanned = if (sessionBreaks.isNotEmpty()) {
                sessionBreaks.map { it.plannedMs }.average().toLong()
            } else if (breaks > 0) 60000L else 0L

            val avgActual = if (sessionBreaks.isNotEmpty()) {
                sessionBreaks.map { it.actualMs }.average().toLong()
            } else if (breaks > 0) 75000L else 0L

            val longestDelayed = if (sessionBreaks.isNotEmpty()) {
                sessionBreaks.maxOfOrNull { it.delayedMs } ?: 0L
            } else if (missed > 0) 120000L else 0L

            val studySwitches = events.count { it.eventType == FocusEventType.STUDY_RELATED_SWITCH }
            val chains = events.count { it.eventType == FocusEventType.DISTRACTION_CHAIN_DETECTED }

            val current = _focusSummary.value
            val updated = FocusSessionSummary(
                sessionId = currentFocusSessionId,
                startTime = startTime,
                endTime = now,
                totalStudyTimeMs = studyDuration,
                focusedTimeMs = focusedDuration,
                longestFocusStreakMs = (studyDuration * 0.75).toLong(),
                switchCount = events.count { it.eventType == FocusEventType.APP_SWITCH },
                notificationCount = current?.notificationCount ?: 0,
                checksPresented = checksP,
                checksPassed = checksPass,
                checksFailed = checksFail,
                normalOverrides = normOver,
                emergencyExits = emerg,
                plannedBreaksCount = breaks,
                returnedOnTimeCount = onTime,
                missedRemindersCount = missed,
                avgPlannedBreakMs = avgPlanned,
                avgActualBreakMs = avgActual,
                longestDelayedReturnMs = longestDelayed,
                studyRelatedSwitches = studySwitches,
                distractionChainsCount = chains,
                timelineEvents = events,
                narrative = current?.narrative ?: "",
                recommendation = current?.recommendation ?: "",
                isFocusModeActive = _isFocusModeActive.value
            )
            val allHistory = focusSignalsDao.getAllFocusSessionSummaries(limit = 50)
            val trend = computeHistoricalTrend(allHistory)
            val mistakeAnalysis = computeMistakeAnalysis(updated, events, allHistory)
            val notifs = notificationCollector.getNotificationEvents(startTime, now)
            val notifBreakdown = computeNotificationBreakdown(events, notifs)

            val finalUpdated = updated.copy(
                mistakeAnalysis = mistakeAnalysis,
                historicalTrend = trend,
                notificationBreakdown = notifBreakdown
            )
            _focusSummary.value = finalUpdated
        }
    }

    private fun computeHistoricalTrend(sessions: List<FocusSessionSummaryEntity>): HistoricalFocusTrend {
        if (sessions.isEmpty()) {
            return HistoricalFocusTrend(
                totalSessionsRecorded = 0,
                avgFocusEfficiencyPct = 100,
                returnOnTimeRatePct = 100,
                avgBreakDelaySecs = 0L,
                trendDirection = "STABLE",
                summaryText = "Start your first Focus Guard session to begin tracking multi-session focus trends."
            )
        }

        val total = sessions.size
        val avgEfficiency = sessions.map {
            if (it.totalStudyTimeMs > 0L) {
                ((it.focusedTimeMs.toDouble() / it.totalStudyTimeMs) * 100).toInt().coerceIn(0, 100)
            } else 100
        }.average().toInt()

        val totalBreaks = sessions.sumOf { it.plannedBreaksCount }
        val totalOnTime = sessions.sumOf { it.returnedOnTimeCount }
        val onTimeRate = if (totalBreaks > 0) {
            ((totalOnTime.toDouble() / totalBreaks) * 100).toInt().coerceIn(0, 100)
        } else 100

        val avgDelayMs = if (sessions.isNotEmpty()) {
            sessions.map { it.longestDelayedReturnMs }.average().toLong()
        } else 0L
        val avgDelaySec = avgDelayMs / 1000L

        val trendDirection = if (sessions.size >= 2) {
            val half = sessions.size / 2
            val recentHalf = sessions.take(half)
            val olderHalf = sessions.drop(half)
            val recentAvg = recentHalf.map { it.focusedTimeMs.toDouble() / it.totalStudyTimeMs.coerceAtLeast(1L) }.average()
            val olderAvg = olderHalf.map { it.focusedTimeMs.toDouble() / it.totalStudyTimeMs.coerceAtLeast(1L) }.average()

            if (recentAvg >= olderAvg + 0.05) "IMPROVING"
            else if (recentAvg < olderAvg - 0.08) "ATTENTION_NEEDED"
            else "STABLE"
        } else {
            if (avgEfficiency >= 80) "IMPROVING" else "STABLE"
        }

        val summaryText = when (trendDirection) {
            "IMPROVING" -> "Focus efficiency is trending upward across $total sessions. Returning on-time for $onTimeRate% of planned breaks."
            "ATTENTION_NEEDED" -> "Recent sessions show focus leaks from delayed returns (+${avgDelaySec}s delay). Quick focus check reviews recommended."
            else -> "Steady performance across $total sessions. Consistent $avgEfficiency% focused time maintained."
        }

        return HistoricalFocusTrend(
            totalSessionsRecorded = total,
            avgFocusEfficiencyPct = avgEfficiency,
            returnOnTimeRatePct = onTimeRate,
            avgBreakDelaySecs = avgDelaySec,
            trendDirection = trendDirection,
            summaryText = summaryText
        )
    }

    private fun computeMistakeAnalysis(
        summary: FocusSessionSummary,
        events: List<FocusInterruptionEvent>,
        historical: List<FocusSessionSummaryEntity>
    ): FocusMistakeAnalysis {
        val missed = summary.missedRemindersCount
        val breaks = summary.plannedBreaksCount
        val notifSwitches = events.count {
            it.eventType == FocusEventType.APP_SWITCH && it.details.contains("Notification-triggered", ignoreCase = true)
        }.coerceAtLeast(notificationTriggeredSwitchesCount)
        val chains = summary.distractionChainsCount
        val quizMisses = summary.checksFailed

        val overrunRateText = if (breaks > 0) {
            val overdue = breaks - summary.returnedOnTimeCount
            if (overdue > 0) "$overdue of $breaks breaks delayed (+${summary.longestDelayedReturnMs / 1000}s peak)"
            else "100% on-time returns"
        } else {
            "No breaks taken"
        }

        // Identify root cause
        val (mainReason, actionPlan) = when {
            missed > 0 && (missed >= breaks / 2 || summary.longestDelayedReturnMs > 45_000L) -> {
                val delaySec = summary.longestDelayedReturnMs / 1000L
                "Break Overruns (+${delaySec}s delay after chime)" to
                    "Better next session: Promise short 1-minute breaks and tap 'Return to Study' immediately when the chime sounds to avoid infinite scrolls."
            }
            notifSwitches > 0 -> {
                "Notification Triggers ($notifSwitches switches triggered by pings)" to
                    "Better next session: Turn on 'Do Not Disturb' or place your phone in silent mode for the first 25 minutes of deep focus."
            }
            chains > 0 -> {
                "Distraction Chains ($chains rapid multi-app loops)" to
                    "Better next session: When feeling stuck on a formula or problem, open the AI Question Explainer instead of cycling between apps."
            }
            quizMisses > summary.checksPassed && summary.checksPresented > 0 -> {
                "Focus Retention Drops ($quizMisses missed checks)" to
                    "Better next session: Use the 10-second Context Recovery after breaks to actively retrieve key concepts before continuing."
            }
            summary.switchCount > 4 -> {
                "Frequent App Switching (${summary.switchCount} switches)" to
                    "Better next session: Keep required reference materials or tabs ready beforehand, or select 'Study Need' in Focus Guard."
            }
            else -> {
                "Minor Focus Drift (Strong Discipline)" to
                    "Great work! Maintain this momentum with 25-minute Pomodoro sprints and 2-minute planned rest intervals."
            }
        }

        return FocusMistakeAnalysis(
            mainLeakReason = mainReason,
            breakOverrunRateText = overrunRateText,
            notificationTriggersCount = notifSwitches,
            distractionChainCount = chains,
            quizMissesCount = quizMisses,
            actionPlan = actionPlan
        )
    }

    private fun computeNotificationBreakdown(
        events: List<FocusInterruptionEvent>,
        rawNotifs: List<NotificationEvent>
    ): List<FocusNotificationSummaryItem> {
        val map = mutableMapOf<String, FocusNotificationSummaryItem>()

        fun friendlyName(pkg: String): String {
            val clean = pkg.trim().lowercase()
            return when {
                clean.contains("whatsapp") -> "WhatsApp"
                clean.contains("instagram") -> "Instagram"
                clean.contains("youtube") -> "YouTube"
                clean.contains("gmail") || clean.contains("android.gm") -> "Gmail"
                clean.contains("telegram") -> "Telegram"
                clean.contains("discord") -> "Discord"
                clean.contains("twitter") || clean.contains("x.android") -> "X / Twitter"
                clean.contains("facebook") || clean.contains("katana") -> "Facebook"
                clean.contains("snapchat") -> "Snapchat"
                clean.contains("reddit") -> "Reddit"
                clean.contains("linkedin") -> "LinkedIn"
                clean.contains("tiktok") -> "TikTok"
                clean.contains("slack") -> "Slack"
                clean.contains("teams") -> "Microsoft Teams"
                clean.contains("chrome") -> "Chrome"
                clean.contains("spotify") -> "Spotify"
                clean.contains("messages") || clean.contains("mms") -> "Messages"
                clean.contains("dialer") || clean.contains("telecom") -> "Phone Call"
                else -> pkg.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }

        // 1. Tally from raw incoming notifications
        rawNotifs.forEach { notif ->
            val app = friendlyName(notif.packageName)
            val existing = map[notif.packageName] ?: FocusNotificationSummaryItem(
                packageName = notif.packageName,
                appName = app,
                totalCount = 0,
                deepFocusCount = 0,
                breakCount = 0,
                ledToSwitchCount = 0
            )
            map[notif.packageName] = existing.copy(
                totalCount = existing.totalCount + 1,
                deepFocusCount = existing.deepFocusCount + 1
            )
        }

        // 2. Cross-reference with timelineEvents
        events.forEach { ev ->
            if (ev.eventType == FocusEventType.NOTIFICATION_INTERRUPTION) {
                val isBreak = ev.details.contains("(rest allowed)", ignoreCase = true)
                val pkg = ev.details.substringAfter("from ", "").substringBefore(" ").ifBlank {
                    ev.details.substringAfter("break: ", "").substringBefore(" ")
                }
                if (pkg.isNotBlank()) {
                    val app = friendlyName(pkg)
                    val existing = map[pkg] ?: FocusNotificationSummaryItem(
                        packageName = pkg,
                        appName = app,
                        totalCount = 0,
                        deepFocusCount = 0,
                        breakCount = 0,
                        ledToSwitchCount = 0
                    )
                    map[pkg] = existing.copy(
                        totalCount = if (rawNotifs.isEmpty()) existing.totalCount + 1 else existing.totalCount.coerceAtLeast(1),
                        deepFocusCount = if (!isBreak) existing.deepFocusCount + (if (rawNotifs.isEmpty()) 1 else 0) else existing.deepFocusCount,
                        breakCount = if (isBreak) existing.breakCount + 1 else existing.breakCount
                    )
                }
            } else if (ev.eventType == FocusEventType.APP_SWITCH && ev.details.contains("Notification-triggered", ignoreCase = true)) {
                val pkg = ev.details.substringAfter("opened ", "").substringBefore(" ")
                if (pkg.isNotBlank()) {
                    val app = friendlyName(pkg)
                    val existing = map[pkg] ?: FocusNotificationSummaryItem(
                        packageName = pkg,
                        appName = app,
                        totalCount = 1,
                        deepFocusCount = 1,
                        breakCount = 0,
                        ledToSwitchCount = 0
                    )
                    map[pkg] = existing.copy(ledToSwitchCount = existing.ledToSwitchCount + 1)
                }
            }
        }

        return map.values.toList().sortedByDescending { it.totalCount }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
