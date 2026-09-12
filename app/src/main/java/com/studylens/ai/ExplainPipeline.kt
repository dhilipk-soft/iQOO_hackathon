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

        // Keep only the most recent part of a long-running conversation so the prompt stays
        // small enough to leave the model room to actually answer.
        val trimmedContext = conversationContext.takeLast(1500)

        val prompt = buildString {
            append("You are continuing a tutoring conversation. Here is the conversation so far:\n")
            append("$trimmedContext\n\n")
            append("The student now asks: \"$question\"\n\n")
            if (retrieval.factsText.isNotBlank()) {
                append("Answer this new question thoroughly and in detail (aim for 10-15 sentences), ")
                append("weaving together the current information below WITH your own knowledge - actually ")
                append("synthesize it into a real explanation, don't just list the facts. ")
            } else {
                append("Answer this new question directly and thoroughly (aim for 5-8 sentences where the ")
                append("topic warrants it). ")
            }
            append("Use the conversation above for context ONLY if the new question is actually related ")
            append("to it - if it's a new, unrelated topic, just answer it on its own terms using your own ")
            append("knowledge. Do not repeat the question back, and do not just restate earlier answers.")
            if (retrieval.factsText.isNotBlank()) {
                append("\n\nCurrent information from multiple sources - use this to make the answer richer ")
                append("and more up to date:\n${retrieval.factsText}")
            }
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
