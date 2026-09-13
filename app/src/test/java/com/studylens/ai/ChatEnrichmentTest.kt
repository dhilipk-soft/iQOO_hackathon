package com.studylens.ai

import com.studylens.input.data.ChatSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatEnrichmentTest {

    @Test
    fun testEnrichSessionTransformsOfflineContent() {
        val offlineSession = ChatSessionEntity(
            id = 1L,
            title = "Photosynthesis",
            subject = "Biology",
            previewText = "How does photosynthesis work?",
            explanation = "Photosynthesis is the process by which plants use sunlight, water, and carbon dioxide to create oxygen and energy in the form of sugar.",
            formula = "6CO₂ + 6H₂O ➔ C₆H₁₂O₆ + 6O₂",
            bulletPoints = listOf(
                "• Key topic: Photosynthesis",
                "• Evaluated on-device by StudyLens AI",
                "• Processed 100% offline on-device"
            ),
            usedOnlineContext = false,
            timestamp = 1726000000000L
        )

        assertFalse("Initial session must be marked as offline", offlineSession.usedOnlineContext)

        val webFacts = "Recent 2026 research highlights engineered chlorophyll variants capable of absorbing near-infrared wavelengths, significantly boosting agricultural yields."
        val citations = listOf(
            WebCitation(title = "Nature Plants - Far-Red Photosynthesis", url = "https://nature.com/articles/plants-2026"),
            WebCitation(title = "ScienceDirect Biology", url = "https://sciencedirect.com/science/article/pii/12345")
        )
        val retrievalResult = RetrievalResult(factsText = webFacts, citations = citations)

        val enriched = ChatEnrichmentWorker.enrichSession(offlineSession, retrievalResult)

        // 1. Must now be marked as usedOnlineContext = true (Online answer)
        assertTrue("Enriched session must have usedOnlineContext = true", enriched.usedOnlineContext)

        // 2. Must retain original explanation AND contain web summary section
        assertTrue("Must contain original explanation", enriched.explanation.contains(offlineSession.explanation))
        assertTrue("Must contain Web-Enriched Knowledge Summary header", enriched.explanation.contains("🌐 Web-Enriched Knowledge Summary:"))
        assertTrue("Must contain the retrieved web facts", enriched.explanation.contains(webFacts))

        // 3. Must contain citations sources
        assertTrue("Must contain Sources section", enriched.explanation.contains("📚 Sources:"))
        assertTrue("Must include first citation", enriched.explanation.contains("Nature Plants - Far-Red Photosynthesis"))
        assertTrue("Must include citation URL", enriched.explanation.contains("https://nature.com/articles/plants-2026"))

        // 4. Bullet points must be updated
        assertFalse("Offline bullet must be replaced", enriched.bulletPoints.contains("• Processed 100% offline on-device"))
        assertTrue("Online bullet must be present", enriched.bulletPoints.contains("• Enhanced with real-time web context"))
    }

    @Test
    fun testEnrichSessionWithoutCitations() {
        val offlineSession = ChatSessionEntity(
            id = 2L,
            title = "Gravity",
            subject = "Physics",
            previewText = "Explain gravity",
            explanation = "Gravity is a fundamental force attracting two bodies.",
            formula = "F = G(m1*m2)/r^2",
            bulletPoints = listOf("• Processed 100% offline on-device"),
            usedOnlineContext = false,
            timestamp = 1726000010000L
        )

        val retrieval = RetrievalResult(factsText = "General relativity describes gravity as spacetime curvature.", citations = emptyList())
        val enriched = ChatEnrichmentWorker.enrichSession(offlineSession, retrieval)

        assertTrue(enriched.usedOnlineContext)
        assertTrue(enriched.explanation.contains("🌐 Web-Enriched Knowledge Summary:"))
        assertTrue(enriched.explanation.contains("General relativity describes gravity as spacetime curvature."))
        assertFalse("Should not include Sources header if citations list is empty", enriched.explanation.contains("📚 Sources:"))
        assertTrue(enriched.bulletPoints.contains("• Enhanced with real-time web context"))
    }

    @Test
    fun testEnrichmentIdempotency() {
        // Once a session has usedOnlineContext = true, background workers or queries filtering
        // for usedOnlineContext = 0 will ignore it, ensuring it is never re-fetched.
        val initialOffline = ChatSessionEntity(
            id = 42L,
            title = "Newton's Laws",
            subject = "Physics",
            previewText = "Newton's laws of motion",
            explanation = "1. Inertia, 2. F=ma, 3. Action-reaction",
            formula = "F = ma",
            bulletPoints = listOf("• Processed 100% offline on-device"),
            usedOnlineContext = false,
            timestamp = 1726000020000L
        )

        val retrieval = RetrievalResult(factsText = "Newtonian mechanics serves as the foundation of classical dynamics.")
        val enriched = ChatEnrichmentWorker.enrichSession(initialOffline, retrieval)

        val sessionList = listOf(initialOffline, enriched)
        // Simulate query: SELECT * FROM chat_sessions WHERE usedOnlineContext = 0
        val unenrichedList = sessionList.filter { !it.usedOnlineContext }

        assertEquals(1, unenrichedList.size)
        assertEquals(initialOffline.id, unenrichedList.first().id)

        // After updating initialOffline to enriched in database:
        val updatedList = listOf(enriched)
        val secondUnenrichedQuery = updatedList.filter { !it.usedOnlineContext }
        assertTrue("No sessions remain unenriched once updated", secondUnenrichedQuery.isEmpty())
    }

    @Test
    fun testTrySimpleMathEvaluation() {
        val mult = ChatEnrichmentWorker.trySimpleMath("What is 2 * 200?")
        assertTrue("Must calculate multiplication", mult != null && mult.contains("400"))

        val multLowerX = ChatEnrichmentWorker.trySimpleMath("2 x 200")
        assertTrue("Must handle x operator", multLowerX != null && multLowerX.contains("400"))

        val add = ChatEnrichmentWorker.trySimpleMath("calculate 15 + 35")
        assertTrue("Must calculate addition", add != null && add.contains("50"))

        val div = ChatEnrichmentWorker.trySimpleMath("what is 100 / 4?")
        assertTrue("Must calculate division", div != null && div.contains("25"))

        val sub = ChatEnrichmentWorker.trySimpleMath("solve 50 - 20")
        assertTrue("Must calculate subtraction", sub != null && sub.contains("30"))

        // Ensure dates are not matched as subtraction
        val dateCheck = ChatEnrichmentWorker.trySimpleMath("Release date is 2026-09-13")
        assertEquals("Date string must not trigger math", null, dateCheck)

        // Ensure non-math questions never trigger math evaluation
        val continentCheck = ChatEnrichmentWorker.trySimpleMath("What is the smallest continent")
        assertEquals("Geography question must not trigger math", null, continentCheck)

        val capitalCheck = ChatEnrichmentWorker.trySimpleMath("What is the capital of India?")
        assertEquals("Capital question must not trigger math", null, capitalCheck)

        // Ensure prompt boilerplate like "aim for 10-15 sentences" or "aim for 5-8 sentences" is never evaluated as math
        val promptWithOnlineBoilerplate = """
            Give a thorough, detailed explanation (aim for 10-15 sentences, organized into clear points or short paragraphs) that weaves together the current information below WITH your own subject knowledge.
            Content: What is the smallest continent
            Current information from multiple sources:
            Australia is the smallest continent.
        """.trimIndent()
        val onlinePromptMath = ChatEnrichmentWorker.trySimpleMath(promptWithOnlineBoilerplate)
        assertEquals("Prompt template with 10-15 sentences must not trigger math", null, onlinePromptMath)

        val promptWithOfflineBoilerplate = """
            Give a clear, detailed explanation - aim for 5-8 sentences.
            Content: What is the capital of India
        """.trimIndent()
        val offlinePromptMath = ChatEnrichmentWorker.trySimpleMath(promptWithOfflineBoilerplate)
        assertEquals("Prompt template with 5-8 sentences must not trigger math", null, offlinePromptMath)
    }

    @Test
    fun testEnrichSessionWithMultiQuestionSessionAndCleansesErrors() {
        val sessionWithError = ChatSessionEntity(
            id = 13L,
            title = "Where mumbai is located?",
            subject = "Geography",
            previewText = "Where mumbai is located?",
            explanation = "Sorry, I couldn't generate an explanation just now. Please try again.",
            formula = null,
            bulletPoints = listOf("• Processed 100% offline on-device"),
            usedOnlineContext = false,
            timestamp = 1726000030000L
        )

        val messages = listOf(
            com.studylens.input.data.ChatMessageEntity(
                id = 14L,
                sessionId = 13L,
                question = "What is 2 * 200?",
                answer = "Sorry, I couldn't generate an explanation just now. Please try again.",
                timestamp = 1726000035000L
            ),
            com.studylens.input.data.ChatMessageEntity(
                id = 15L,
                sessionId = 13L,
                question = "What is the capital of India?",
                answer = "New Delhi is the capital of India.",
                timestamp = 1726000040000L
            )
        )

        val retrieval = RetrievalResult(
            factsText = "Mumbai is the financial capital of India, situated on the western coast along the Arabian Sea.",
            citations = listOf(WebCitation("Maharashtra Tourism", "https://maharashtratourism.gov.in"))
        )

        val enriched = ChatEnrichmentWorker.enrichSession(sessionWithError, retrieval, messages)

        // 1. Error message must be completely eliminated
        assertFalse("Error message must be stripped", enriched.explanation.contains("Sorry, I couldn't generate"))
        assertFalse("Must not say file is not available", enriched.explanation.contains("file is not available"))

        // 2. Must contain web summary and citations
        assertTrue("Must contain web summary", enriched.explanation.contains("🌐 Web-Enriched Knowledge Summary:"))
        assertTrue("Must contain Mumbai facts", enriched.explanation.contains("Mumbai is the financial capital"))

        // 3. Must contain comprehensive Questions Explored summary
        assertTrue("Must contain Questions Explored section", enriched.explanation.contains("📋 Questions Explored in this Session:"))
        assertTrue("Must include first question", enriched.explanation.contains("What is 2 * 200?"))
        assertTrue("Must include second question", enriched.explanation.contains("What is the capital of India?"))
        assertTrue("Must include resolved math answer for first question", enriched.explanation.contains("400"))
        assertTrue("Must include second answer snippet", enriched.explanation.contains("New Delhi is the capital"))
    }

    @Test
    fun testEnrichSessionCleansesFalseMathCalculationFromNonMathQuery() {
        val corruptedSession = ChatSessionEntity(
            id = 17L,
            title = "What is the smallest continent",
            subject = "General Science",
            previewText = "What is the smallest continent",
            explanation = "The answer is **-5**.\n\nCalculation: 10 - 15 = -5.\nSubtraction removes 15 from 10, leaving a difference of -5.",
            formula = null,
            bulletPoints = listOf("• Processed 100% offline on-device"),
            usedOnlineContext = false,
            timestamp = 1726000050000L
        )

        val retrieval = RetrievalResult(
            factsText = "Australia is the smallest continent by land area on Earth, covering approximately 7.7 million square kilometers.",
            citations = listOf(WebCitation("National Geographic", "https://nationalgeographic.org/encyclopedia/continent"))
        )

        val enriched = ChatEnrichmentWorker.enrichSession(corruptedSession, retrieval, emptyList())

        // 1. The false math answer "-5" and "10 - 15 = -5" must be completely removed
        assertFalse("Must strip false math answer -5", enriched.explanation.contains("The answer is **-5**"))
        assertFalse("Must strip false math calculation", enriched.explanation.contains("Calculation: 10 - 15"))

        // 2. Must contain the true geographical facts
        assertTrue("Must contain Australia facts", enriched.explanation.contains("Australia is the smallest continent"))
        assertTrue("Must contain web enriched header", enriched.explanation.contains("🌐 Web-Enriched Knowledge Summary:"))
        assertTrue("Must have usedOnlineContext true", enriched.usedOnlineContext)
    }
}
