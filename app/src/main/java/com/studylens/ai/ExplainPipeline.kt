package com.studylens.ai

import android.graphics.Bitmap
import com.studylens.BuildConfig
import com.studylens.shared.ExplanationResult
import com.studylens.shared.QuizQuestion
import com.studylens.shared.StudyBrain
import com.studylens.shared.StudyCapture

class ExplainPipeline(
    private val llmEngine: LlmEngine,
    private val retrievalClient: RetrievalClient
) : StudyBrain {

    private fun appendCitations(text: String, citations: List<WebCitation>): String {
        if (citations.isEmpty()) return text
        return text + "\n\n📚 Sources:\n" + citations.joinToString("\n") { "• ${it.title}\n  ${it.url}" }
    }

    // Step 1 (retrieve, only if online AND there's a text topic to search for) ->
    // Step 2 (combine) -> Step 3 (generate, always local - reads the image directly when
    // one is provided, no OCR involved)
    override suspend fun explain(capture: StudyCapture, isOnline: Boolean, image: Bitmap?): ExplanationResult {
        // With no OCR step, an image-only capture has no text topic to search the web
        // for - retrieval only makes sense when there's actual text (typed, or alongside
        // the image).
        val retrieval = if (isOnline && capture.extractedText.isNotBlank()) {
            retrievalClient.fetchOnlineContext(
                capture.extractedText,
                BuildConfig.OPENROUTER_API_KEY,
                BuildConfig.GROQ_API_KEY
            )
        } else {
            RetrievalResult("")
        }

        val prompt = buildString {
            append("You are a patient tutor explaining to a student with limited internet access. ")
            if (image != null) {
                append("Look at the attached image (a textbook page or handwritten problem) and ")
                append("explain what it's teaching. ")
            }
            if (retrieval.factsText.isNotBlank()) {
                // Online + retrieval succeeded - there's real multi-source material to work
                // with, so ask for genuine synthesis, not just a longer version of the same
                // generic answer.
                append("Give a thorough, detailed explanation (aim for 10-15 sentences, organized into ")
                append("clear points or short paragraphs) that weaves together the current information ")
                append("below WITH your own subject knowledge - don't just append the facts as a list, ")
                append("actually explain how they fit into the topic. Be substantive, not repetitive.\n\n")
            } else {
                append("Give a clear, detailed explanation - aim for 5-8 sentences (more if the topic ")
                append("genuinely needs it). Be thorough, don't pad with filler, but don't be overly brief ")
                append("either.\n\n")
            }
            if (capture.extractedText.isNotBlank()) {
                append("Content: ${capture.extractedText}\n")
            }
            if (retrieval.factsText.isNotBlank()) {
                append("\nCurrent information from multiple sources - use this to make the explanation ")
                append("richer and more up to date:\n${retrieval.factsText}\n")
            }
        }
        val explanation = llmEngine.generateResponse(prompt, image) // always runs, on-device, this is the guarantee

        return ExplanationResult(
            captureId = capture.id,
            finalExplanation = appendCitations(explanation, retrieval.citations),
            usedOnlineContext = retrieval.factsText.isNotBlank()
        )
    }

    override suspend fun answerFollowUp(
        capture: StudyCapture,
        conversationContext: String,
        question: String,
        isOnline: Boolean
    ): ExplanationResult {
        // Retrieve using the actual follow-up question, not the original captured text -
        // that's what's actually relevant to this specific turn of the conversation.
        val retrieval = if (isOnline) {
            retrievalClient.fetchOnlineContext(
                question,
                BuildConfig.OPENROUTER_API_KEY,
                BuildConfig.GROQ_API_KEY
            )
        } else {
            RetrievalResult("")
        }

        // Keep only recent context (last 350 chars) so the on-device SLM stays comfortably
        // within prefill limits and avoids KV cache overflow.
        val trimmedContext = conversationContext.takeLast(350).trim()

        val prompt = buildString {
            append("You are StudyLens, a knowledgeable and friendly educational AI tutor.\n\n")
            append("Student's Question:\n\"$question\"\n\n")

            if (retrieval.factsText.isNotBlank()) {
                append("Live Information & Enriched Facts:\n")
                append("${retrieval.factsText.trim()}\n\n")
                append("Task: Explain the answer to \"$question\" thoroughly and clearly for the student, incorporating the live facts above.\n")
            } else {
                append("Task: Explain the answer to \"$question\" clearly, thoroughly, and directly for the student using your knowledge.\n")
            }

            append("Guidelines:\n")
            append("- Focus completely on answering \"$question\". Provide a clear definition, core principles, and helpful examples.\n")
            append("- If this is a new question or topic, explain it directly. Do NOT repeat, summarize, or revert to earlier topics unless specifically asked to compare them.\n")
            append("- Do NOT start with \"Based on the context you provided\" or mention these system guidelines.\n\n")

            if (trimmedContext.isNotBlank()) {
                append("Earlier Conversation (for background reference only, if the question refers to previous messages):\n")
                append("$trimmedContext\n\n")
            }

            append("Tutor explanation for student:")
        }
        val answer = llmEngine.generateResponse(prompt)

        return ExplanationResult(
            captureId = capture.id,
            finalExplanation = appendCitations(answer, retrieval.citations),
            usedOnlineContext = retrieval.factsText.isNotBlank()
        )
    }

    override suspend fun generateQuiz(capture: StudyCapture): List<QuizQuestion> {
        val quizGen = QuizGenerator(llmEngine)
        return quizGen.generateQuiz(capture)
    }
}
