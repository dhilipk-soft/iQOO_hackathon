package com.studylens.ai

import com.studylens.shared.StudyIntent
import com.studylens.shared.VerifiedCitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredEducationalOutputTest {

    @Test
    fun testIntentClassifier_mathEquation_returnsSolver() {
        val intent = StudyIntentClassifier.classify("Solve 3x^2 + 5x - 2 = 0")
        assertEquals(StudyIntent.STEP_BY_STEP_SOLVER, intent)
    }

    @Test
    fun testIntentClassifier_programmingCode_returnsCodeAndAlgorithm() {
        val intent = StudyIntentClassifier.classify("Explain how binary search works in Python with time complexity")
        assertEquals(StudyIntent.CODE_AND_ALGORITHM, intent)
    }

    @Test
    fun testIntentClassifier_summaryRequest_returnsRevisionSummary() {
        val intent = StudyIntentClassifier.classify("Give me a quick summary cheat sheet of the French Revolution")
        assertEquals(StudyIntent.REVISION_SUMMARY, intent)
    }

    @Test
    fun testIntentClassifier_userManualOverride_isRespected() {
        val intent = StudyIntentClassifier.classify("Photosynthesis", userPreference = StudyIntent.REVISION_SUMMARY)
        assertEquals(StudyIntent.REVISION_SUMMARY, intent)
    }

    @Test
    fun testStructuredResponseParser_withCompleteTags() {
        val sampleLlmOutput = """
            [INTENT: STEP_BY_STEP_SOLVER]
            [TITLE: Quadratic Formula Roots]
            [SUBJECT: Algebra]

            ### 🎯 CORE PRINCIPLE
            The quadratic formula solves for roots of ax^2 + bx + c = 0.

            ### 📐 FORMULA & GIVEN
            x = (-b ± √(b² - 4ac)) / (2a)

            ### 🔍 STEP-BY-STEP BREAKDOWN
            1. Identify coefficients a, b, and c.
            2. Compute discriminant b^2 - 4ac.
            3. Substitute into formula.

            ### 💡 REAL-WORLD ANALOGY
            Like finding where a thrown ball lands on the ground.

            ### ⚠️ COMMON PITFALLS
            • Forgetting that -b is positive when b is negative.
            • Not dividing the entire numerator by 2a.

            ### ❓ CHECK YOUR UNDERSTANDING
            What happens when the discriminant is zero?
            [ANSWER: There is exactly one real root (repeated).]
        """.trimIndent()

        val parsed = StructuredStudyResponseParser.parse(
            rawOutput = sampleLlmOutput,
            fallbackTopic = "Quadratic Equations",
            inferredIntent = StudyIntent.STEP_BY_STEP_SOLVER
        )

        assertEquals(StudyIntent.STEP_BY_STEP_SOLVER, parsed.intent)
        assertEquals("Quadratic Formula Roots", parsed.title)
        assertEquals("Algebra", parsed.subject)
        assertTrue(parsed.coreConcept.contains("quadratic formula solves"))
        assertNotNull(parsed.formulaOrCode)
        assertEquals("x = (-b ± √(b² - 4ac)) / (2a)", parsed.formulaOrCode?.content)
        assertEquals(3, parsed.steps.size)
        assertNotNull(parsed.analogy)
        assertEquals(2, parsed.commonPitfalls.size)
        assertNotNull(parsed.quickCheck)
        assertEquals("There is exactly one real root (repeated).", parsed.quickCheck?.answer)
    }

    @Test
    fun testStructuredResponseParser_fallbackUntaggedText() {
        val unstructured = "Photosynthesis is the process by which green plants convert sunlight into glucose."

        val parsed = StructuredStudyResponseParser.parse(
            rawOutput = unstructured,
            fallbackTopic = "Photosynthesis in plants",
            inferredIntent = StudyIntent.CONCEPT_EXPLANATION
        )

        assertEquals(StudyIntent.CONCEPT_EXPLANATION, parsed.intent)
        assertEquals("Biology", parsed.subject)
        assertTrue(parsed.coreConcept.contains("Photosynthesis"))
    }

    @Test
    fun testCitationDomainExtraction() {
        val (wikiDomain, isEduWiki) = extractCleanDomain("https://en.wikipedia.org/wiki/Photosynthesis")
        assertEquals("Wikipedia", wikiDomain)
        assertTrue(isEduWiki)

        val (khanDomain, isEduKhan) = extractCleanDomain("https://www.khanacademy.org/science/biology")
        assertEquals("Khan Academy", khanDomain)
        assertTrue(isEduKhan)

        val (mitDomain, isEduMit) = extractCleanDomain("https://ocw.mit.edu/courses/physics")
        assertEquals("MIT OpenCourseWare", mitDomain)
        assertTrue(isEduMit)
    }
}
