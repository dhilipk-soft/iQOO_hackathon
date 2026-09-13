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

    @Test
    fun testStructuredResponseParser_fastApiQuery_generatesCodeAndGuaranteedCitations() {
        val rawLlmParagraph = "Fast API is a web framework that enables the development of APIs by allowing developers to define request parameters, bodies, and responses with type annotations. It utilizes asynchronous endpoints using async/await and supports both synchronous and non-blocking I/O operations. Fast API integrates with popular Python tools and libraries such as SQLAlchemy, Tortoise ORM, JWT authentication, and dependency injection systems, making it easier to isolate concerns and testability. Additionally, it emphasizes standards-based design and promotes interoperability, which encourages faster development and adoption."

        val testCitations = listOf(
            VerifiedCitation("FastAPI Documentation", "https://fastapi.tiangolo.com", "FastAPI Docs", true)
        )
        val parsed = StructuredStudyResponseParser.parse(
            rawOutput = rawLlmParagraph,
            fallbackTopic = "What is fast api",
            inferredIntent = StudyIntent.CONCEPT_EXPLANATION,
            citations = testCitations
        )

        assertEquals("Computer Science", parsed.subject)
        assertTrue(parsed.coreConcept.isNotBlank())
        assertNotNull(parsed.formulaOrCode)
        assertTrue(parsed.formulaOrCode!!.isCode)
        assertTrue(parsed.formulaOrCode!!.content.contains("FastAPI"))
        assertTrue(parsed.steps.isNotEmpty())
        assertNotNull(parsed.analogy)
        assertTrue(parsed.commonPitfalls.isNotEmpty())
        assertNotNull(parsed.quickCheck)
        assertTrue(parsed.citations.isNotEmpty())
        assertTrue(parsed.citations.any { it.domain.contains("FastAPI") || it.url.contains("fastapi") })
    }

    @Test
    fun testIntentClassifier_defaultAuto_routesCodeQueriesAccurately() {
        // When no intent is explicitly selected by user, default is AUTO
        val primeIntent = StudyIntentClassifier.classify("Can you give me code for prime number", userPreference = StudyIntent.AUTO)
        assertEquals(StudyIntent.CODE_AND_ALGORITHM, primeIntent)

        val promeIntent = StudyIntentClassifier.classify("prome number", userPreference = StudyIntent.AUTO)
        assertEquals(StudyIntent.CODE_AND_ALGORITHM, promeIntent)

        val reverseIntent = StudyIntentClassifier.classify("reverse a string", userPreference = StudyIntent.AUTO)
        assertEquals(StudyIntent.CODE_AND_ALGORITHM, reverseIntent)
    }

    @Test
    fun testStructuredResponseParser_primeNumber_modelFailureRecoversWithCompleteEdgeCases() {
        val failureText = "Sorry, I couldn't generate an explanation just now. Please try again."

        val parsed = StructuredStudyResponseParser.parse(
            rawOutput = failureText,
            fallbackTopic = "Can you give me code for prime number",
            inferredIntent = StudyIntent.CODE_AND_ALGORITHM
        )

        // 1. Must NOT leak the model failure text
        assertTrue(!parsed.coreConcept.contains("Sorry"))
        assertTrue(!parsed.coreConcept.contains("couldn't generate"))

        // 2. Intent and subject must be correctly attributed
        assertEquals(StudyIntent.CODE_AND_ALGORITHM, parsed.intent)
        assertEquals("Computer Science", parsed.subject)

        // 3. Mathematical precision: primes > 1, 0 and 1 are not prime
        assertTrue(parsed.coreConcept.contains("positive natural number strictly greater than 1"))
        assertTrue(parsed.coreConcept.contains("0 and 1 are neither prime nor composite"))

        // 4. Code block must exist and cover edge cases (n <= 1, 2, 3, even numbers, 6k ± 1)
        assertNotNull(parsed.formulaOrCode)
        assertTrue(parsed.formulaOrCode!!.isCode)
        val code = parsed.formulaOrCode!!.content
        assertTrue("Code should handle n <= 1 edge case", code.contains("if n <= 1"))
        assertTrue("Code should handle 2 and 3 edge case", code.contains("if n <= 3"))
        assertTrue("Code should filter even numbers and multiples of 3", code.contains("n % 2 == 0 or n % 3 == 0"))
        assertTrue("Code should use sqrt(n) loop", code.contains("i * i <= n"))
        assertTrue("Code should include test cases with negatives and 0", code.contains("is_prime(-5)") && code.contains("is_prime(0)"))

        // 5. Steps must highlight boundary conditions and time complexity
        assertTrue(parsed.steps.size >= 4)
        assertTrue(parsed.steps.any { it.contains("Boundary Edge Cases") || it.contains("≤ 1") })

        // 6. Real-world analogy & Common pitfalls
        assertNotNull(parsed.analogy)
        assertTrue(parsed.analogy!!.contains("Fundamental Theorem of Arithmetic") || parsed.analogy!!.contains("chemical elements"))
        assertTrue(parsed.commonPitfalls.any { it.contains("Treating 1 as a prime number") })
        assertTrue(parsed.commonPitfalls.any { it.contains("O(√n)") || it.contains("O(n)") })

        // 7. Quick check question & answer
        assertNotNull(parsed.quickCheck)
        assertTrue(parsed.quickCheck!!.question.contains("2 the only even prime number"))
    }

    @Test
    fun testStructuredResponseParser_reverseString_coversEdgeCases() {
        val rawShortText = "To reverse a string in Python, you can use slicing or two pointers."

        val parsed = StructuredStudyResponseParser.parse(
            rawOutput = rawShortText,
            fallbackTopic = "reverse a string",
            inferredIntent = StudyIntent.CODE_AND_ALGORITHM
        )

        assertEquals(StudyIntent.CODE_AND_ALGORITHM, parsed.intent)
        assertEquals("Computer Science", parsed.subject)

        assertNotNull(parsed.formulaOrCode)
        assertTrue(parsed.formulaOrCode!!.isCode)
        val code = parsed.formulaOrCode!!.content
        assertTrue("Code must handle empty/single char edge cases", code.contains("len(s) <= 1"))
        assertTrue("Code must use two pointers", code.contains("left < right"))
        assertTrue("Code must assert edge cases", code.contains("reverse_string(\"\")") && code.contains("reverse_string(\"a\")"))

        assertNotNull(parsed.analogy)
        assertTrue(parsed.commonPitfalls.any { it.contains("immutable strings") })
        assertNotNull(parsed.quickCheck)
    }

    @Test
    fun testStructuredResponseParser_evenNumber_offlineHandlesMathematicalEdgeCases() {
        val parsed = StructuredStudyResponseParser.parse(
            rawOutput = "",
            fallbackTopic = "What is even number?",
            inferredIntent = StudyIntent.CONCEPT_EXPLANATION,
            citations = emptyList() // Offline mode: strictly empty citations!
        )

        assertEquals("Mathematics", parsed.subject)
        assertTrue("Offline on-device mode must have zero web citations", parsed.citations.isEmpty())
        assertTrue(parsed.coreConcept.contains("divisible by 2 with no remainder"))
        assertTrue(parsed.coreConcept.contains("0 is an even number"))
        assertNotNull(parsed.formulaOrCode)
        assertTrue(parsed.formulaOrCode!!.content.contains("n = 2k"))
        assertTrue(parsed.steps.any { it.contains("0 is completely even") })
        assertNotNull(parsed.analogy)
        assertTrue(parsed.analogy!!.contains("pairing socks") || parsed.analogy!!.contains("shoes"))
        assertTrue(parsed.commonPitfalls.any { it.contains("Believing that 0 is neither even nor odd") })
        assertNotNull(parsed.quickCheck)
        assertTrue(parsed.quickCheck!!.question.contains("0 an even number"))
    }
}


