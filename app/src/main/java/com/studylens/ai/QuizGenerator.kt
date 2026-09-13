package com.studylens.ai

import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyCapture

class QuizGenerator(
    private val llmEngine: LlmEngine,
    private val retrievalClient: RetrievalClient? = null
) {

    suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion> {
        return generateQuiz(
            contextText = capture.extractedText,
            topic = "Captured Notes"
        )
    }

    suspend fun generateQuiz(
        contextText: String,
        topic: String = "Study Topic",
        isOnline: Boolean = false
    ): List<QuizQuestion> {
        val trimmedContext = contextText.trim().take(1800)
        
        // Optional online fact retrieval if connected
        val retrievedFacts = if (isOnline && retrievalClient != null && topic.isNotBlank()) {
            try {
                val res = retrievalClient.fetchOnlineContext(topic, "", "")
                res.factsText.take(600)
            } catch (e: Exception) {
                ""
            }
        } else ""

        val prompt = buildString {
            append("You are an expert academic tutor. Generate exactly 3 distinct multiple-choice quiz questions ")
            append("testing deep conceptual understanding based strictly on this student's recent study context and discussion.\n\n")
            append("Topic: $topic\n")
            if (trimmedContext.isNotBlank()) {
                append("Student Context & Chat Discussion:\n$trimmedContext\n\n")
            }
            if (retrievedFacts.isNotBlank()) {
                append("Verified Reference Facts:\n$retrievedFacts\n\n")
            }
            append("Output EXACTLY 3 questions in this format, with nothing else:\n")
            append("Q: <Question 1 text>\n")
            append("A) <Option A>\n")
            append("B) <Option B>\n")
            append("C) <Option C>\n")
            append("D) <Option D>\n")
            append("ANSWER: <Letter A, B, C, or D>\n\n")
            append("Q: <Question 2 text>\n")
            append("A) <Option A>\n")
            append("B) <Option B>\n")
            append("C) <Option C>\n")
            append("D) <Option D>\n")
            append("ANSWER: <Letter A, B, C, or D>\n\n")
            append("Q: <Question 3 text>\n")
            append("A) <Option A>\n")
            append("B) <Option B>\n")
            append("C) <Option C>\n")
            append("D) <Option D>\n")
            append("ANSWER: <Letter A, B, C, or D>\n")
        }

        return try {
            val raw = llmEngine.generateResponse(prompt)
            val parsed = parseMultiQuiz(raw, topic)
            if (parsed.isNotEmpty()) parsed else generateTopicFallbacks(topic, contextText)
        } catch (e: Exception) {
            generateTopicFallbacks(topic, contextText)
        }
    }

    private fun parseMultiQuiz(raw: String, topic: String): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var currentQ: String? = null
        val currentOpts = mutableListOf<String>()
        var currentAns: String? = null
        var qId = 1L

        for (line in lines) {
            when {
                line.startsWith("Q:", ignoreCase = true) -> {
                    // If previous question was accumulated, commit it
                    if (currentQ != null && currentOpts.size >= 2) {
                        val ansLetter = currentAns?.firstOrNull()?.uppercaseChar() ?: 'A'
                        val ansIdx = "ABCD".indexOf(ansLetter).coerceIn(0, currentOpts.size - 1)
                        questions.add(
                            QuizQuestion(
                                id = qId++,
                                captureId = System.currentTimeMillis() + qId,
                                topic = topic,
                                question = currentQ,
                                options = currentOpts.toList(),
                                correctAnswer = currentOpts.getOrElse(ansIdx) { currentOpts[0] }
                            )
                        )
                    }
                    currentQ = line.substringAfter(":").trim()
                    currentOpts.clear()
                    currentAns = null
                }
                line.startsWith("A)", ignoreCase = true) ||
                line.startsWith("B)", ignoreCase = true) ||
                line.startsWith("C)", ignoreCase = true) ||
                line.startsWith("D)", ignoreCase = true) -> {
                    currentOpts.add(line.substring(2).trim())
                }
                line.startsWith("A.", ignoreCase = true) ||
                line.startsWith("B.", ignoreCase = true) ||
                line.startsWith("C.", ignoreCase = true) ||
                line.startsWith("D.", ignoreCase = true) -> {
                    currentOpts.add(line.substring(2).trim())
                }
                line.startsWith("ANSWER:", ignoreCase = true) -> {
                    currentAns = line.substringAfter(":").trim()
                }
            }
        }

        // Commit trailing question
        if (currentQ != null && currentOpts.size >= 2) {
            val ansLetter = currentAns?.firstOrNull()?.uppercaseChar() ?: 'A'
            val ansIdx = "ABCD".indexOf(ansLetter).coerceIn(0, currentOpts.size - 1)
            questions.add(
                QuizQuestion(
                    id = qId++,
                    captureId = System.currentTimeMillis() + qId,
                    topic = topic,
                    question = currentQ,
                    options = currentOpts.toList(),
                    correctAnswer = currentOpts.getOrElse(ansIdx) { currentOpts[0] }
                )
            )
        }

        return questions
    }

    private fun generateTopicFallbacks(topic: String, contextText: String): List<QuizQuestion> {
        val cleanTopic = topic.ifBlank { "Core Concept" }
        return listOf(
            QuizQuestion(
                id = 1L,
                captureId = System.currentTimeMillis() + 1,
                topic = cleanTopic,
                question = "What is the primary fundamental principle behind $cleanTopic?",
                options = listOf(
                    "It establishes the foundational rules governing systemic interactions",
                    "It is only an empirical approximation with no theoretical basis",
                    "It applies exclusively in vacuum conditions",
                    "It contradicts the standard conservation laws"
                ),
                correctAnswer = "It establishes the foundational rules governing systemic interactions"
            ),
            QuizQuestion(
                id = 2L,
                captureId = System.currentTimeMillis() + 2,
                topic = cleanTopic,
                question = "In the context of $cleanTopic, how does varying key parameters affect system outcome?",
                options = listOf(
                    "Outcomes remain invariant regardless of boundary changes",
                    "Key relationships scale predictably according to the governing formula",
                    "The system enters instantaneous thermal breakdown",
                    "Parameters can only assume binary values (0 or 1)"
                ),
                correctAnswer = "Key relationships scale predictably according to the governing formula"
            ),
            QuizQuestion(
                id = 3L,
                captureId = System.currentTimeMillis() + 3,
                topic = cleanTopic,
                question = "Which practical problem-solving step is essential when analyzing $cleanTopic?",
                options = listOf(
                    "Identify known variables and relevant conservation principles first",
                    "Ignore initial boundary conditions completely",
                    "Substitute values before writing the analytical equation",
                    "Assume infinite resistance in all components"
                ),
                correctAnswer = "Identify known variables and relevant conservation principles first"
            )
        )
    }
}

