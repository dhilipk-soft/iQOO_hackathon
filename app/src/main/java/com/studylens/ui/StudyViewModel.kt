package com.studylens.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studylens.ai.ExplainPipeline
import com.studylens.ai.LlmEngine
import com.studylens.ai.RetrievalClient
import com.studylens.input.data.AppDatabase
import com.studylens.input.data.ChatMessageEntity
import com.studylens.input.data.ChatSessionEntity
import com.studylens.input.network.NetworkHealthChecker
import com.studylens.input.vitals.DeviceVitalsMonitor
import com.studylens.shared.ExplanationResult
import com.studylens.shared.FocusInsight
import com.studylens.shared.InferenceStats
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyCapture
import com.studylens.shared.StudySession
import com.studylens.ui.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val llmEngine = LlmEngine(application)
    private val retrievalClient = RetrievalClient()
    private val explainPipeline = ExplainPipeline(llmEngine, retrievalClient)

    private val chatDao = AppDatabase.getDatabase(application).chatDao()

    // Observable states
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

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

    private val _focusInsight = MutableStateFlow(
        FocusInsight(
            type = "DEEP_WORK_STREAK",
            evidence = "You stayed on this problem for 6 minutes before switching apps.",
            narrative = "Sessions without a switch tend to finish about 2x faster."
        )
    )
    val focusInsight: StateFlow<FocusInsight> = _focusInsight.asStateFlow()

    private val _studySession = MutableStateFlow(
        StudySession(
            id = 101L,
            startTime = System.currentTimeMillis() - (42 * 60 * 1000L),
            endTime = System.currentTimeMillis(),
            durationMs = 42 * 60 * 1000L,
            switchCount = 1,
            notificationCount = 3
        )
    )
    val studySession: StateFlow<StudySession> = _studySession.asStateFlow()

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
    }

    fun toggleSimulatedNetwork() {
        _isOnline.value = !_isOnline.value
    }

    fun explainCurrentCapture(customText: String? = null) {
        val textToProcess = customText?.ifBlank { null } ?: _capturedText.value.ifBlank { "General Study Topic" }
        _capturedText.value = textToProcess
        _isExplaining.value = true
        _followUpList.value = emptyList()

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val online = _isOnline.value
            val capture = StudyCapture(id = startTime, extractedText = textToProcess, timestamp = startTime)

            // Real pipeline: on-device model always runs; retrieval only blends in if online (§3a).
            val result = explainPipeline.explain(capture, online)

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

    fun askFollowUp(question: String) {
        if (question.isBlank()) return
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

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
