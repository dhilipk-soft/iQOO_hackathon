package com.studylens.ai

import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyCapture

class QuizGenerator(private val llmEngine: LlmEngine) {

    suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion> {
        val prompt = """
            Based on this content, write exactly 1 short multiple-choice quiz question that
            tests understanding of the key idea. Reply in EXACTLY this format, nothing else:
            Q: <question>
            A) <option>
            B) <option>
            C) <option>
            D) <option>
            ANSWER: <letter>

            Content: ${capture.extractedText}
        """.trimIndent()

        val raw = llmEngine.generateResponse(prompt)
        return parseQuiz(raw, capture.id)
    }

    private fun parseQuiz(raw: String, captureId: Long): List<QuizQuestion> {
        return try {
            val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val question = lines.first { it.startsWith("Q:") }.removePrefix("Q:").trim()
            val options = listOf("A)", "B)", "C)", "D)").mapNotNull { prefix ->
                lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
            }
            val answerLetter = lines.first { it.startsWith("ANSWER:") }.removePrefix("ANSWER:").trim()
            val answerIndex = "ABCD".indexOf(answerLetter.firstOrNull() ?: 'A').coerceAtLeast(0)
            val correctAnswer = options.getOrElse(answerIndex) { options.firstOrNull().orEmpty() }

            listOf(
                QuizQuestion(
                    id = 1L,
                    captureId = captureId,
                    topic = "Study Topic",
                    question = question,
                    options = options.ifEmpty { null },
                    correctAnswer = correctAnswer
                )
            )
        } catch (e: Exception) {
            // Parsing failed (model returned something unexpected) — fall back to a
            // still-usable generic question instead of crashing the quiz screen.
            listOf(
                QuizQuestion(
                    id = 1L,
                    captureId = captureId,
                    topic = "Study Topic",
                    question = "In your own words, what was the main idea of this material?",
                    options = null,
                    correctAnswer = ""
                )
            )
        }
    }
}
