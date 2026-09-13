package com.studylens.ai

import com.studylens.input.data.AppDatabase
import com.studylens.input.data.ConceptMasteryEntity
import com.studylens.input.data.ExamPlanEntity
import com.studylens.input.data.MisconceptionLogEntity
import com.studylens.input.data.PendingQuizEntity
import com.studylens.input.data.QuizAttemptEntity
import com.studylens.input.data.StudentProfileEntity
import kotlinx.coroutines.flow.StateFlow

enum class ActionType {
    EXPLAIN_FUNDAMENTAL,
    TARGET_MISCONCEPTION,
    SOCRATIC_GUIDE,
    ADVANCED_CHALLENGE
}

data class NextLearningAction(
    val actionType: ActionType,
    val title: String,
    val description: String,
    val focusConcept: String,
    val promptGuidance: String
)

data class EvaluationResult(
    val isCorrect: Boolean,
    val errorType: String?,
    val feedbackMessage: String,
    val detectedMisconception: String? = null,
    val masteryDelta: Int
)

class LearningTwinManager(private val database: AppDatabase) {
    private val dao = database.learningTwinDao()

    val activeProfileFlow: StateFlow<StudentProfileEntity?> = dao.activeProfileFlow
    val allProfilesFlow: StateFlow<List<StudentProfileEntity>> = dao.allProfilesFlow
    val conceptMasteryFlow: StateFlow<List<ConceptMasteryEntity>> = dao.conceptMasteryFlow
    val misconceptionsFlow: StateFlow<List<MisconceptionLogEntity>> = dao.misconceptionsFlow
    val activeExamPlanFlow: StateFlow<ExamPlanEntity?> = dao.activeExamPlanFlow
    val quizAttemptsFlow: StateFlow<List<QuizAttemptEntity>> = dao.quizAttemptsFlow
    val pendingQuizzesFlow: StateFlow<List<PendingQuizEntity>> = dao.pendingQuizzesFlow

    suspend fun switchStudentProfile(studentId: String) {
        dao.setActiveProfile(studentId)
    }

    suspend fun createStudentProfile(
        name: String,
        institution: String,
        stream: String,
        subjects: List<String>,
        targetExam: String,
        examDate: Long,
        dailyMinutes: Int
    ): String {
        val profile = StudentProfileEntity(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            institution = institution,
            stream = stream,
            subjects = subjects,
            targetExam = targetExam,
            examDate = examDate,
            dailyMinutes = dailyMinutes,
            learningStyle = "Interactive & Adaptive",
            strengths = "Curious learner, visual problem solver",
            weaknesses = "",
            isCurrent = true
        )
        return dao.createProfile(profile)
    }

    suspend fun registerChatTopic(subject: String, topic: String, concept: String, sourceSessionId: Long) {
        val studentId = activeProfileFlow.value?.id ?: return
        dao.registerConceptFromChat(
            studentId = studentId,
            subject = subject,
            topic = topic,
            concept = concept,
            sourceChatSessionId = sourceSessionId
        )
    }

    suspend fun recordQuizAttempt(
        concept: String,
        topic: String,
        question: String,
        selectedAnswer: String,
        correctAnswer: String,
        isCorrect: Boolean,
        errorType: String? = null,
        misconception: String? = null
    ) {
        val studentId = activeProfileFlow.value?.id ?: return
        dao.recordQuizAttempt(
            studentId = studentId,
            concept = concept,
            topic = topic,
            question = question,
            selectedAnswer = selectedAnswer,
            correctAnswer = correctAnswer,
            isCorrect = isCorrect,
            errorType = errorType,
            misconception = misconception
        )
    }

    suspend fun resetDemoData() {
        dao.resetDemoData()
    }

    suspend fun updateExamPlan(daysRemaining: Int, dailyMinutes: Int, planBreakdown: String, targetScore: Int = 90) {
        dao.updateExamPlan(daysRemaining, dailyMinutes, planBreakdown, targetScore)
    }

    fun computeNextBestAction(): NextLearningAction {
        val profile = activeProfileFlow.value ?: return NextLearningAction(
            actionType = ActionType.SOCRATIC_GUIDE,
            title = "Guided Practice",
            description = "Start with interactive problem solving.",
            focusConcept = "Electricity",
            promptGuidance = "Guide with helpful Socratic hints."
        )

        val masteries = conceptMasteryFlow.value
        val weakest = masteries.minByOrNull { it.masteryScore }

        // 1. Active misconception takes highest priority
        val misconceptionConcept = masteries.firstOrNull { !it.activeMisconception.isNullOrBlank() }
        if (misconceptionConcept != null) {
            return NextLearningAction(
                actionType = ActionType.TARGET_MISCONCEPTION,
                title = "Resolve Misconception: ${misconceptionConcept.concept}",
                description = "Student pattern: ${misconceptionConcept.activeMisconception}. Re-anchor fundamental formula derivation.",
                focusConcept = misconceptionConcept.concept,
                promptGuidance = "The student has an active misconception: '${misconceptionConcept.activeMisconception}'. Do NOT solve it for them. Challenge them gently on how the formula is rearranged before doing any numbers."
            )
        }

        // 2. Fundamental explanation needed if mastery < 40%
        if (weakest != null && weakest.masteryScore < 40) {
            return NextLearningAction(
                actionType = ActionType.EXPLAIN_FUNDAMENTAL,
                title = "Build Core Foundation: ${weakest.concept}",
                description = "Mastery is only ${weakest.masteryScore}%. Focus on intuitive physical definitions, analogies, and simple examples.",
                focusConcept = weakest.concept,
                promptGuidance = "The student has low mastery (${weakest.masteryScore}%) in ${weakest.concept}. Explain using a simple physical analogy (like water flowing through a pipe) before introducing math."
            )
        }

        // 3. Medium mastery: Socratic guidance
        if (weakest != null && weakest.masteryScore < 75) {
            return NextLearningAction(
                actionType = ActionType.SOCRATIC_GUIDE,
                title = "Socratic Mastery: ${weakest.concept}",
                description = "Current mastery is ${weakest.masteryScore}%. Guide the student through multi-step calculation without giving raw answers.",
                focusConcept = weakest.concept,
                promptGuidance = "The student is at ${weakest.masteryScore}% mastery in ${weakest.concept}. Practice Socratic tutoring: ask guiding questions, never give the direct solution."
            )
        }

        // 4. High mastery: Challenge / Exam mode
        val conceptToChallenge = weakest?.concept ?: "Ohm's Law"
        return NextLearningAction(
            actionType = ActionType.ADVANCED_CHALLENGE,
            title = "Advanced Circuit Application",
            description = "High foundational mastery! Present exam-level multi-step circuit problems to test edge cases.",
            focusConcept = conceptToChallenge,
            promptGuidance = "The student has high mastery. Present an advanced, non-standard problem that combines series/parallel reasoning or power calculations."
        )
    }

    /**
     * Builds the Personal Learning Twin context block to prepend to LiteRT-LM prompts.
     */
    fun buildTwinPromptContext(socraticMode: Boolean = true): String {
        val profile = activeProfileFlow.value ?: return ""
        val nextAction = computeNextBestAction()
        val masteries = conceptMasteryFlow.value

        return buildString {
            append("=== [ON-DEVICE PERSONAL LEARNING TWIN ACTIVE] ===\n")
            append("Student Profile: ${profile.name} | Level: ${profile.institution} | Stream: ${profile.stream}\n")
            append("Enrolled Subjects: ${profile.subjects.joinToString(", ")}\n")
            append("Target Exam: ${profile.targetExam}\n")
            append("Cognitive Style: ${profile.learningStyle}\n")
            if (profile.strengths.isNotBlank()) append("Strengths: ${profile.strengths}\n")
            if (profile.weaknesses.isNotBlank()) append("Struggle Areas: ${profile.weaknesses}\n")
            append("Concept Masteries: ")
            append(masteries.joinToString(", ") { "${it.concept}: ${it.masteryScore}%" })
            append("\n")
            append("Prescribed Action: ${nextAction.actionType} (${nextAction.title})\n")
            append("Pedagogical Guidance: ${nextAction.promptGuidance}\n")

            if (socraticMode) {
                append("\n*** STRICT SOCRATIC RULE ***\n")
                append("1. NEVER give the direct final numerical answer or complete solution to any problem.\n")
                append("2. When the student asks a question or makes a calculation, point out the relationship or ask a guiding question.\n")
                append("3. Praise accurate reasoning, and isolate errors into formula, calculation, or units.\n")
                append("4. Keep responses concise (2-4 sentences max), focused on helping the student take the NEXT step themselves.\n")
            }
            append("=== [END LEARNING TWIN CONTEXT] ===\n\n")
        }
    }

    /**
     * Evaluates student answers in chat or quiz to update the Personal Learning Twin.
     */
    suspend fun evaluateAnswer(conceptName: String, studentAnswer: String): EvaluationResult {
        val cleanAnswer = studentAnswer.trim().lowercase()

        // Demo scenario: Ohm's Law / Resistance question
        // "Calculate resistance if V = 10V and I = 2A" (Correct is 5 Ohms)
        if (cleanAnswer.contains("20") || cleanAnswer.contains("v * i") || cleanAnswer.contains("v*i") || cleanAnswer.contains("multiply")) {
            // Inverted formula misconception! Multiplied instead of divided
            val misconception = "Inverted formula: Multiplied V * I (10 * 2 = 20) instead of dividing R = V / I"
            dao.updateMastery(conceptName, delta = -5, isCorrect = false, errorType = "FORMULA_INVERSION", misconception = misconception)
            dao.logMisconception(conceptName, mistakePattern = "R = V * I instead of V / I", sampleAnswer = studentAnswer)
            return EvaluationResult(
                isCorrect = false,
                errorType = "FORMULA_INVERSION",
                feedbackMessage = "Notice how you multiplied 10V × 2A = 20. But remember Ohm's law: V = I × R. If you want to isolate R, do you multiply or divide V by I?",
                detectedMisconception = misconception,
                masteryDelta = -5
            )
        }

        if (cleanAnswer.contains("5") || cleanAnswer.contains("5 ohm") || cleanAnswer.contains("5ohm") || cleanAnswer.contains("five")) {
            // Correct!
            dao.updateMastery(conceptName, delta = 10, isCorrect = true, errorType = null, misconception = null)
            dao.resolveMisconception(conceptName)

            // Dynamically update the exam plan if this was a struggle topic
            val currentPlan = activeExamPlanFlow.value
            if (currentPlan != null && currentPlan.currentDayPlan.contains("Remedial Resistance")) {
                val updatedPlan = currentPlan.currentDayPlan.replace("20m Remedial Resistance Division", "25m Advanced Series/Parallel Circuits (Accelerated)")
                dao.updateExamPlan(currentPlan.daysRemaining, currentPlan.dailyMinutes, updatedPlan)
            }

            return EvaluationResult(
                isCorrect = true,
                errorType = null,
                feedbackMessage = "Spot on! R = V / I = 10V / 2A = 5 Ω. You correctly rearranged the formula! Mastery increased by +10.",
                detectedMisconception = null,
                masteryDelta = 10
            )
        }

        if (cleanAnswer.contains("7") || cleanAnswer.contains("8") || cleanAnswer.contains("12")) {
            dao.updateMastery(conceptName, delta = -3, isCorrect = false, errorType = "CALCULATION", misconception = null)
            return EvaluationResult(
                isCorrect = false,
                errorType = "CALCULATION",
                feedbackMessage = "Check the arithmetic: V = 10, I = 2. R = V / I = 10 / 2. What is 10 divided by 2?",
                detectedMisconception = null,
                masteryDelta = -3
            )
        }

        // Generic encouragement
        return EvaluationResult(
            isCorrect = false,
            errorType = "UNKNOWN",
            feedbackMessage = "Let's review the formula: V = I × R. How can we rearrange it to solve for R?",
            detectedMisconception = null,
            masteryDelta = 0
        )
    }
}
