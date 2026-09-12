package com.studylens.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studylens.ai.ExplainPipeline
import com.studylens.ai.LlmEngine
import com.studylens.ai.RetrievalClient
import com.studylens.input.network.NetworkHealthChecker
import com.studylens.input.vitals.DeviceVitalsMonitor
import com.studylens.shared.ExplanationResult
import com.studylens.shared.FocusInsight
import com.studylens.shared.InferenceStats
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyCapture
import com.studylens.shared.StudySession
import com.studylens.ui.tts.TtsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FollowUpMessage(
    val id: String,
    val question: String,
    val answer: String,
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
    val followUps: List<FollowUpMessage> = emptyList()
)

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val networkChecker = NetworkHealthChecker(application)
    private val vitalsMonitor = DeviceVitalsMonitor(application)
    val ttsManager = TtsManager(application)

    private val llmEngine = LlmEngine(application)
    private val retrievalClient = RetrievalClient()
    private val explainPipeline = ExplainPipeline(llmEngine, retrievalClient)

    // Observable states
    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _vitals = MutableStateFlow(
        InferenceStats(
            tokensPerSecond = 28.4,
            latencyMs = 95,
            ramUsedMb = 312,
            thermalStatus = "NORMAL"
        )
    )
    val vitals: StateFlow<InferenceStats> = _vitals.asStateFlow()

    private val _capturedText = MutableStateFlow("Calculus: Integration by Parts\nFormula: ∫ u dv = uv - ∫ v du\nUsed when integrating the product of two functions.")
    val capturedText: StateFlow<String> = _capturedText.asStateFlow()

    private val _isExplaining = MutableStateFlow(false)
    val isExplaining: StateFlow<Boolean> = _isExplaining.asStateFlow()

    private val _explanationResult = MutableStateFlow<ExplanationResult?>(null)
    val explanationResult: StateFlow<ExplanationResult?> = _explanationResult.asStateFlow()

    private val _followUpList = MutableStateFlow<List<FollowUpMessage>>(emptyList())
    val followUpList: StateFlow<List<FollowUpMessage>> = _followUpList.asStateFlow()

    private val _isAnsweringFollowUp = MutableStateFlow(false)
    val isAnsweringFollowUp: StateFlow<Boolean> = _isAnsweringFollowUp.asStateFlow()

    private val defaultSessions = listOf(
        StudyTopicSession(
            id = "session_quadratic",
            title = "Quadratic Equation",
            subject = "Algebra",
            previewText = "x = (-b ± √(b² - 4ac)) / 2a",
            explanation = """
                A quadratic equation is a second degree polynomial equation of the form ax² + bx + c = 0, where a ≠ 0. It has at most two solutions.
                The solutions can be found using the quadratic formula:
            """.trimIndent(),
            formula = "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
            bulletPoints = listOf(
                "• a, b, c are constants",
                "• b² - 4ac is called the discriminant",
                "• It determines the nature of the roots"
            ),
            followUps = listOf(
                FollowUpMessage(
                    id = "fu_1",
                    question = "What does the discriminant tell us?",
                    answer = "The discriminant is the value b² - 4ac.\n\n• If b² - 4ac > 0: two real and distinct roots.\n• If b² - 4ac = 0: one real root (both roots are equal).\n• If b² - 4ac < 0: no real roots (complex roots)."
                )
            )
        ),
        StudyTopicSession(
            id = "session_calculus",
            title = "Calculus: Integration by Parts",
            subject = "Calculus",
            previewText = "∫ u dv = uv - ∫ v du",
            explanation = "Integration by parts is derived from the product rule of calculus. By integrating both sides and rearranging, we obtain the formula:",
            formula = "\\int u\\,dv = uv - \\int v\\,du",
            bulletPoints = listOf(
                "• Used when integrating products of two functions",
                "• Choose 'u' using LIATE priority rule (Log, Inverse trig, Algebraic, Trig, Exp)",
                "• Differentiate u to get du, integrate dv to get v"
            ),
            followUps = emptyList()
        ),
        StudyTopicSession(
            id = "session_newton",
            title = "Newton's Third Law",
            subject = "Physics",
            previewText = "F_AB = -F_BA",
            explanation = "Whenever one body exerts a force on a second body, the second body exerts an equal and opposite force on the first.",
            formula = "F_{AB} = -F_{BA}",
            bulletPoints = listOf(
                "• Forces always occur in matched pairs",
                "• Action and reaction forces act on different bodies",
                "• Magnitude is equal, direction is opposite"
            ),
            followUps = emptyList()
        )
    )

    private val _sessionHistory = MutableStateFlow<List<StudyTopicSession>>(defaultSessions)
    val sessionHistory: StateFlow<List<StudyTopicSession>> = _sessionHistory.asStateFlow()

    private val _activeSession = MutableStateFlow<StudyTopicSession?>(defaultSessions.first())
    val activeSession: StateFlow<StudyTopicSession?> = _activeSession.asStateFlow()

    private val _quizQuestions = MutableStateFlow<List<QuizQuestion>>(
        listOf(
            QuizQuestion(
                id = 1L,
                captureId = 101L,
                topic = "General form of quadratic equation",
                question = "What is the general form of a quadratic equation?",
                options = listOf("ax + b = 0", "ax² + bx + c = 0", "ax³ + bx² + c = 0", "a/x + b = 0"),
                correctAnswer = "ax² + bx + c = 0"
            ),
            QuizQuestion(
                id = 2L,
                captureId = 101L,
                topic = "Meaning of discriminant",
                question = "What is the formula for the discriminant of ax² + bx + c = 0?",
                options = listOf("b² - 4ac", "2a / b", "√(a² + b²)", "c / a"),
                correctAnswer = "b² - 4ac"
            ),
            QuizQuestion(
                id = 3L,
                captureId = 101L,
                topic = "Number of roots",
                question = "If the discriminant b² - 4ac > 0, how many real roots exist?",
                options = listOf("Two real and distinct roots", "No real roots", "One repeated root", "Infinite roots"),
                correctAnswer = "Two real and distinct roots"
            )
        )
    )
    val quizQuestions: StateFlow<List<QuizQuestion>> = _quizQuestions.asStateFlow()

    private val _selectedQuizAnswers = MutableStateFlow<Map<Long, String>>(
        mapOf(
            1L to "ax² + bx + c = 0",
            2L to "2a / b", // Intentionally wrong to show 2/3 correct like in Image 1!
            3L to "Two real and distinct roots"
        )
    )
    val selectedQuizAnswers: StateFlow<Map<Long, String>> = _selectedQuizAnswers.asStateFlow()

    private val _quizSubmitted = MutableStateFlow(false)
    val quizSubmitted: StateFlow<Boolean> = _quizSubmitted.asStateFlow()

    private val _revisionList = MutableStateFlow<List<QuizQuestion>>(
        listOf(
            QuizQuestion(
                id = 2L,
                captureId = 101L,
                topic = "Discriminant",
                question = "Meaning of discriminant in quadratic equations",
                options = listOf("b² - 4ac", "2a / b", "√(a² + b²)", "c / a"),
                correctAnswer = "b² - 4ac"
            ),
            QuizQuestion(
                id = 4L,
                captureId = 101L,
                topic = "Nature of Roots",
                question = "How the discriminant determines real vs complex roots",
                options = listOf("b² - 4ac > 0 gives two real roots", "b² - 4ac = 0 gives complex roots"),
                correctAnswer = "b² - 4ac > 0 gives two real roots"
            )
        )
    )
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
    }

    fun setCapturedText(text: String) {
        _capturedText.value = text
    }

    fun selectSession(sessionId: String) {
        val found = _sessionHistory.value.find { it.id == sessionId }
        if (found != null) {
            _activeSession.value = found
            _capturedText.value = found.previewText
            _explanationResult.value = ExplanationResult(
                captureId = System.currentTimeMillis(),
                finalExplanation = found.explanation,
                usedOnlineContext = false
            )
            _followUpList.value = found.followUps
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
        val textToProcess = customText ?: _capturedText.value
        _capturedText.value = textToProcess
        _isExplaining.value = true
        _followUpList.value = emptyList()

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val online = _isOnline.value

            // Simulate realistic on-device token generation or use pipeline
            delay(900)

            val explanationText = if (online) {
                """
                Integration by Parts is derived from the product rule of differentiation: d/dx[u*v] = u'v + uv'.
                
                By integrating both sides and rearranging:
                ∫ u dv = uv - ∫ v du.
                
                💡 Rule of thumb (LIATE): Choose 'u' in this priority order:
                1. Logarithmic functions (e.g. ln x)
                2. Inverse trigonometric (e.g. arctan x)
                3. Algebraic (e.g. x², 3x)
                4. Trigonometric (e.g. sin x, cos x)
                5. Exponential (e.g. eˣ)
                
                Choose 'dv' as the remaining part that is straightforward to integrate.
                """.trimIndent()
            } else {
                """
                Integration by parts formula:
                ∫ u dv = uv - ∫ v du.
                
                It comes directly from reversing the product rule of calculus. Use the LIATE rule to pick 'u':
                Logs, Inverse trig, Algebraic, Trig, Exponential.
                
                Differentiate 'u' to get du, and integrate 'dv' to get v.
                """.trimIndent()
            }

            val latency = System.currentTimeMillis() - startTime
            val wordCount = explanationText.split("\\s+".toRegex()).size
            val tokensPerSec = String.format(java.util.Locale.US, "%.1f", (wordCount * 1.3) / (latency / 1000.0).coerceAtLeast(0.5)).toDoubleOrNull() ?: 28.5

            _vitals.value = InferenceStats(
                tokensPerSecond = tokensPerSec,
                latencyMs = latency,
                ramUsedMb = 318,
                thermalStatus = "NORMAL"
            )

            _explanationResult.value = ExplanationResult(
                captureId = System.currentTimeMillis(),
                finalExplanation = explanationText,
                usedOnlineContext = online
            )
            _isExplaining.value = false
        }
    }

    fun askFollowUp(question: String) {
        if (question.isBlank()) return
        _isAnsweringFollowUp.value = true

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            delay(800)

            val answerText = when {
                question.contains("liate", ignoreCase = true) ->
                    "LIATE stands for Logarithmic, Inverse trigonometric, Algebraic, Trigonometric, Exponential. It is a heuristic to choose which function to differentiate as 'u'."
                question.contains("example", ignoreCase = true) ->
                    "For ∫ x*cos(x) dx: Pick u = x (Algebraic) and dv = cos(x)dx. Then du = dx, v = sin(x). Result = x*sin(x) - ∫ sin(x)dx = x*sin(x) + cos(x) + C."
                else ->
                    "Great question! When applying ∫ u dv = uv - ∫ v du, always check whether differentiating 'u' simplifies the expression. If it becomes more complex, swap choices."
            }

            val latency = System.currentTimeMillis() - startTime
            _vitals.value = _vitals.value.copy(
                tokensPerSecond = 31.2,
                latencyMs = latency
            )

            val newMessage = FollowUpMessage(
                id = System.currentTimeMillis().toString(),
                question = question,
                answer = answerText
            )
            _followUpList.value = _followUpList.value + newMessage
            _isAnsweringFollowUp.value = false
        }
    }

    fun generatePracticeQuiz() {
        _selectedQuizAnswers.value = emptyMap()
        _quizSubmitted.value = false

        _quizQuestions.value = listOf(
            QuizQuestion(
                id = 1L,
                captureId = 101L,
                topic = "Calculus (Derivation)",
                question = "Integration by parts is derived from which fundamental rule of calculus?",
                options = listOf("The Product Rule", "The Chain Rule", "The Quotient Rule", "L'Hôpital's Rule"),
                correctAnswer = "The Product Rule"
            ),
            QuizQuestion(
                id = 2L,
                captureId = 101L,
                topic = "LIATE Strategy",
                question = "According to the LIATE rule, which function type has the highest priority to be chosen as 'u'?",
                options = listOf("Algebraic (x²)", "Logarithmic (ln x)", "Exponential (eˣ)", "Trigonometric (cos x)"),
                correctAnswer = "Logarithmic (ln x)"
            ),
            QuizQuestion(
                id = 3L,
                captureId = 101L,
                topic = "Formula Verification",
                question = "What is the correct right-hand side of ∫ u dv?",
                options = listOf("uv - ∫ v du", "uv + ∫ v du", "u'v - v'u", "∫ u du - ∫ v dv"),
                correctAnswer = "uv - ∫ v du"
            )
        )
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
