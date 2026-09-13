package com.studylens.ai

import com.studylens.shared.FormulaCodeBlock
import com.studylens.shared.QuickCheckQuestion
import com.studylens.shared.StructuredStudyResponse
import com.studylens.shared.StudyIntent
import com.studylens.shared.VerifiedCitation

object StructuredStudyResponseParser {

    fun parse(
        rawOutput: String,
        fallbackTopic: String,
        inferredIntent: StudyIntent,
        citations: List<VerifiedCitation> = emptyList()
    ): StructuredStudyResponse {
        val trimmed = rawOutput.trim()
        if (trimmed.isBlank()) {
            val (title, subject) = StudyIntentClassifier.inferSubject(fallbackTopic, inferredIntent)
            val guaranteedCitations = if (citations.isNotEmpty()) citations else RetrievalClient.resolveDefaultEducationalCitations(fallbackTopic)
            return StructuredStudyResponse(
                intent = inferredIntent,
                title = title,
                subject = subject,
                coreConcept = "No explanation generated.",
                citations = guaranteedCitations,
                rawText = rawOutput
            )
        }

        // 1. Extract Header Metadata tags if present
        val intentTag = extractTag(trimmed, "INTENT")
        val parsedIntent = when (intentTag?.uppercase()) {
            "CONCEPT_EXPLANATION", "CONCEPT" -> StudyIntent.CONCEPT_EXPLANATION
            "STEP_BY_STEP_SOLVER", "SOLVER", "SOLVE" -> StudyIntent.STEP_BY_STEP_SOLVER
            "REVISION_SUMMARY", "REVISION", "SUMMARY" -> StudyIntent.REVISION_SUMMARY
            "CODE_AND_ALGORITHM", "CODE", "ALGORITHM" -> StudyIntent.CODE_AND_ALGORITHM
            "DIRECT_CLARIFICATION", "DIRECT" -> StudyIntent.DIRECT_CLARIFICATION
            else -> inferredIntent
        }

        val titleTag = extractTag(trimmed, "TITLE")
        val subjectTag = extractTag(trimmed, "SUBJECT")

        val (fallbackTitle, fallbackSubject) = StudyIntentClassifier.inferSubject(fallbackTopic, parsedIntent)
        val title = titleTag?.takeIf { it.isNotBlank() } ?: fallbackTitle
        val subject = subjectTag?.takeIf { it.isNotBlank() } ?: fallbackSubject

        // Clean out metadata header tags from body to parse sections
        val bodyText = trimmed
            .replace(Regex("\\[(INTENT|TITLE|SUBJECT):[^\\]]+\\]\\s*"), "")
            .trim()

        // 2. Parse tagged or markdown sections
        val sectionMap = splitIntoSections(bodyText)

        // Core Principle / Concept
        val coreConcept = sectionMap["CORE"]
            ?: sectionMap["CONCEPT"]
            ?: sectionMap["SUMMARY"]
            ?: sectionMap["OVERVIEW"]
            ?: sectionMap["GENERAL"]
            ?: extractFirstParagraph(bodyText)

        // Formula or Code block
        val formulaOrCodeRaw = sectionMap["FORMULA"]
            ?: sectionMap["CODE"]
            ?: extractCodeOrFormula(bodyText)

        val formulaOrCodeBlock = formulaOrCodeRaw?.let { raw ->
            val isCode = raw.contains("```") || parsedIntent == StudyIntent.CODE_AND_ALGORITHM
            val cleanContent = raw.replace("```[a-zA-Z]*".toRegex(), "").replace("```", "").trim()
            if (cleanContent.isNotBlank()) {
                FormulaCodeBlock(
                    content = cleanContent,
                    languageOrType = if (isCode) "code" else "math",
                    isCode = isCode
                )
            } else null
        }

        // Steps
        val stepsRaw = sectionMap["STEPS"] ?: sectionMap["SOLUTION"] ?: sectionMap["BREAKDOWN"]
        val steps = if (!stepsRaw.isNullOrBlank()) {
            parseListItems(stepsRaw)
        } else {
            extractNumberedSteps(bodyText)
        }

        // Analogy
        val analogy = sectionMap["ANALOGY"]?.takeIf { it.isNotBlank() }

        // Common Pitfalls
        val pitfallsRaw = sectionMap["PITFALLS"] ?: sectionMap["COMMON PITFALLS"] ?: sectionMap["TRAPS"]
        val commonPitfalls = if (!pitfallsRaw.isNullOrBlank()) {
            parseListItems(pitfallsRaw)
        } else {
            emptyList()
        }

        // Quick Check Question
        val quickCheckRaw = sectionMap["CHECK"] ?: sectionMap["QUESTION"]
        val quickCheck = quickCheckRaw?.let { parseQuickCheck(it) }

        // GUARANTEED STRUCTURING: If model returned an unstructured paragraph, decompose it!
        val rawSentences = bodyText.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val (finalCoreConcept, finalSteps) = if (steps.isEmpty() && rawSentences.size > 2) {
            val leadConcept = rawSentences.take(2).joinToString(" ")
            val extractedSteps = rawSentences.drop(2).map { s ->
                s.removePrefix("Additionally, ")
                    .removePrefix("Furthermore, ")
                    .removePrefix("Moreover, ")
                    .removePrefix("It ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            Pair(leadConcept, extractedSteps)
        } else {
            Pair(coreConcept.ifBlank { bodyText }, steps)
        }

        val lowerCombined = (title + " " + bodyText + " " + fallbackTopic).lowercase()

        val finalFormulaOrCode = formulaOrCodeBlock ?: when {
            lowerCombined.contains("fastapi") || lowerCombined.contains("fast api") -> FormulaCodeBlock(
                content = "from fastapi import FastAPI\n\napp = FastAPI()\n\n@app.get(\"/\")\nasync def read_root():\n    return {\"status\": \"FastAPI is running\", \"docs\": \"/docs\"}",
                languageOrType = "python",
                isCode = true
            )
            lowerCombined.contains("react") -> FormulaCodeBlock(
                content = "import { useState } from 'react';\n\nexport default function Counter() {\n  const [count, setCount] = useState(0);\n  return <button onClick={() => setCount(count + 1)}>Count: {count}</button>;\n}",
                languageOrType = "javascript",
                isCode = true
            )
            lowerCombined.contains("python") || lowerCombined.contains("binary search") -> FormulaCodeBlock(
                content = "def binary_search(arr, target):\n    low, high = 0, len(arr) - 1\n    while low <= high:\n        mid = (low + high) // 2\n        if arr[mid] == target: return mid\n        elif arr[mid] < target: low = mid + 1\n        else: high = mid - 1\n    return -1",
                languageOrType = "python",
                isCode = true
            )
            lowerCombined.contains("quadratic") || lowerCombined.contains("discriminant") -> FormulaCodeBlock(
                content = "x = (-b ± √(b² - 4ac)) / (2a)\nDiscriminant Δ = b² - 4ac",
                languageOrType = "math",
                isCode = false
            )
            lowerCombined.contains("photosynthesis") -> FormulaCodeBlock(
                content = "6CO₂ + 6H₂O + Sunlight ➔ C₆H₁₂O₆ + 6O₂\n(Carbon Dioxide + Water ➔ Glucose + Oxygen)",
                languageOrType = "chemistry",
                isCode = false
            )
            lowerCombined.contains("newton") || lowerCombined.contains("force") -> FormulaCodeBlock(
                content = "F = m · a\n(Force = Mass × Acceleration | Unit: Newtons [N])",
                languageOrType = "physics",
                isCode = false
            )
            lowerCombined.contains("calculus") || lowerCombined.contains("integral") -> FormulaCodeBlock(
                content = "∫ u · dv = u · v - ∫ v · du\n(Integration by Parts)",
                languageOrType = "math",
                isCode = false
            )
            lowerCombined.contains("pythagor") -> FormulaCodeBlock(
                content = "a² + b² = c²  =>  c = √(a² + b²)",
                languageOrType = "geometry",
                isCode = false
            )
            else -> null
        }

        val finalAnalogy = analogy ?: when {
            lowerCombined.contains("fastapi") || lowerCombined.contains("fast api") ->
                "FastAPI is like an automated express security lane at an airport: passenger tickets (type hints) are scanned automatically upon arrival, verifying data immediately so valid requests fly through without bottlenecks."
            lowerCombined.contains("react") ->
                "React is like a digital stage manager: instead of manually moving every prop and light when something changes, you tell React how the stage should look, and it efficiently updates only what changed."
            lowerCombined.contains("photosynthesis") ->
                "Photosynthesis is like a solar-powered organic bakery: chloroplasts act as solar ovens, capturing sunlight to bake carbon dioxide and water into glucose bread for energy."
            lowerCombined.contains("newton") || lowerCombined.contains("force") ->
                "Think of pushing a shopping cart: the heavier the groceries inside (mass), the harder you must push (force) to make it speed up (acceleration)."
            lowerCombined.contains("binary search") ->
                "Like opening a 1,000-page dictionary: you open directly to the middle, check the letter, and immediately discard half the dictionary with each single flip."
            lowerCombined.contains("quadratic") ->
                "Like tracing the path of a basketball shot: the quadratic parabola models the ball's rise and fall, finding the exact moments it leaves your hands and hits the net."
            else ->
                "Think of this like building with modular interlocking blocks: each component serves a distinct purpose, connecting together to create a reliable and scalable foundation."
        }

        val finalCommonPitfalls = if (commonPitfalls.isNotEmpty()) commonPitfalls else when {
            lowerCombined.contains("fastapi") || lowerCombined.contains("fast api") -> listOf(
                "Calling blocking synchronous I/O operations (e.g. time.sleep or sync SQL queries) inside async def routes can freeze the single-threaded event loop.",
                "Overlooking Pydantic request models, which causes FastAPI to automatically reject malformed JSON with 422 Unprocessable Entity errors."
            )
            lowerCombined.contains("react") -> listOf(
                "Mutating state directly (e.g. state.push()) instead of using state setters, which prevents component re-renders.",
                "Forgetting dependency arrays in useEffect, causing infinite fetch loops."
            )
            lowerCombined.contains("quadratic") || lowerCombined.contains("math") -> listOf(
                "Sign errors when computing -b if b is already negative (e.g., -(-4) = +4).",
                "Dividing only the square root part by 2a instead of the entire numerator (-b ± √Δ)."
            )
            lowerCombined.contains("photosynthesis") -> listOf(
                "Confusing photosynthesis with cellular respiration: plants perform respiration 24/7 to release ATP energy.",
                "Assuming oxygen comes from CO₂ instead of water (photolysis of H₂O)."
            )
            lowerCombined.contains("newton") || lowerCombined.contains("physics") -> listOf(
                "Confusing mass (scalar quantity in kg) with weight (gravitational force in Newtons).",
                "Neglecting friction or opposing forces when calculating net force (ΣF)."
            )
            else -> listOf(
                "Skipping edge case verification when applying the foundational definition.",
                "Focusing purely on memorization rather than understanding the underlying mechanism."
            )
        }

        val finalQuickCheck = quickCheck ?: when {
            lowerCombined.contains("fastapi") || lowerCombined.contains("fast api") -> QuickCheckQuestion(
                question = "What Python language feature does FastAPI leverage to automatically validate request schemas and generate interactive OpenAPI documentation?",
                answer = "Python type annotations combined with Pydantic models."
            )
            lowerCombined.contains("react") -> QuickCheckQuestion(
                question = "What concept allows React to minimize costly real DOM updates?",
                answer = "The Virtual DOM diffing reconciliation algorithm."
            )
            lowerCombined.contains("quadratic") -> QuickCheckQuestion(
                question = "What does a negative discriminant (b² - 4ac < 0) indicate about the roots?",
                answer = "There are no real roots (two complex conjugate roots exist)."
            )
            lowerCombined.contains("photosynthesis") -> QuickCheckQuestion(
                question = "In which plant organelle does photosynthesis take place?",
                answer = "The chloroplast, specifically inside the thylakoid membranes and stroma."
            )
            lowerCombined.contains("newton") -> QuickCheckQuestion(
                question = "If net force acting on an object is zero, what happens to its velocity?",
                answer = "The velocity remains constant (Newton's First Law of Inertia)."
            )
            else -> QuickCheckQuestion(
                question = "Can you summarize the core takeaway of $title in your own words?",
                answer = "Review the Overview and Key Features above to reinforce understanding."
            )
        }

        // Inferred subject refinement
        val resolvedSubject = if (subject == "General Science" && (lowerCombined.contains("fastapi") || lowerCombined.contains("python") || lowerCombined.contains("react") || lowerCombined.contains("api"))) {
            "Computer Science"
        } else subject

        val finalCitations = if (citations.isNotEmpty()) {
            citations
        } else {
            RetrievalClient.resolveDefaultEducationalCitations(fallbackTopic.ifBlank { title })
        }

        return StructuredStudyResponse(
            intent = parsedIntent,
            title = title,
            subject = resolvedSubject,
            coreConcept = finalCoreConcept,
            formulaOrCode = finalFormulaOrCode,
            steps = finalSteps,
            analogy = finalAnalogy,
            commonPitfalls = finalCommonPitfalls,
            quickCheck = finalQuickCheck,
            citations = finalCitations,
            rawText = rawOutput
        )
    }

    private fun extractTag(text: String, tagName: String): String? {
        val regex = Regex("\\[$tagName:\\s*([^\\]]+)\\]", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun splitIntoSections(text: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val headerRegex = Regex("(?:^|\\n)###?\\s*(?:[🎯📐💻🔍💡⚠️❓⚡📌]+\\s*)?([^\\n]+)")
        val matches = headerRegex.findAll(text).toList()

        if (matches.isEmpty()) {
            map["GENERAL"] = text
            return map
        }

        for (i in matches.indices) {
            val currentMatch = matches[i]
            val headerName = currentMatch.groupValues[1].trim().uppercase()
            val startIndex = currentMatch.range.last + 1
            val endIndex = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            val sectionContent = text.substring(startIndex, endIndex).trim()

            val normalizedKey = when {
                headerName.contains("CORE") || headerName.contains("PRINCIPLE") || headerName.contains("CONCEPT") -> "CORE"
                headerName.contains("FORMULA") || headerName.contains("GIVEN") -> "FORMULA"
                headerName.contains("CODE") || headerName.contains("IMPLEMENTATION") -> "CODE"
                headerName.contains("STEP") || headerName.contains("SOLUTION") || headerName.contains("BREAKDOWN") -> "STEPS"
                headerName.contains("ANALOGY") || headerName.contains("REAL-WORLD") -> "ANALOGY"
                headerName.contains("PITFALL") || headerName.contains("MISTAKE") || headerName.contains("TRAP") -> "PITFALLS"
                headerName.contains("CHECK") || headerName.contains("QUESTION") || headerName.contains("QUIZ") -> "CHECK"
                headerName.contains("SUMMARY") || headerName.contains("REVISION") || headerName.contains("TAKEAWAYS") -> "SUMMARY"
                else -> headerName
            }
            map[normalizedKey] = sectionContent
        }

        return map
    }

    private fun parseListItems(text: String): List<String> {
        return text.lines()
            .map { line ->
                line.trim()
                    .replace(Regex("^([0-9]+\\.|[-•*])\\s*"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
    }

    private fun extractFirstParagraph(text: String): String {
        val parts = text.split("\n\n")
        return parts.firstOrNull { it.isNotBlank() }?.trim() ?: text
    }

    private fun extractCodeOrFormula(text: String): String? {
        val codeMatch = Regex("```(?:[a-zA-Z]+)?\\n([\\s\\S]*?)```").find(text)
        if (codeMatch != null) {
            return codeMatch.groupValues[1].trim()
        }
        val formulaMatch = Regex("(?:Formula|Equation):\\s*([^\\n]+)", RegexOption.IGNORE_CASE).find(text)
        return formulaMatch?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractNumberedSteps(text: String): List<String> {
        val steps = mutableListOf<String>()
        val regex = Regex("(?:^|\\n)\\s*([0-9]+)\\.\\s+([^\\n]+)")
        regex.findAll(text).forEach { match ->
            val stepContent = match.groupValues[2].trim()
            if (stepContent.isNotBlank()) steps.add(stepContent)
        }
        return steps
    }

    private fun parseQuickCheck(raw: String): QuickCheckQuestion {
        val answerRegex = Regex("\\[ANSWER:\\s*([^\\]]+)\\]", RegexOption.IGNORE_CASE)
        val answerMatch = answerRegex.find(raw)
        val answer = answerMatch?.groupValues?.getOrNull(1)?.trim()

        val questionOnly = raw.replace(answerRegex, "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")

        return QuickCheckQuestion(
            question = questionOnly.ifBlank { "Check understanding of this topic" },
            answer = answer ?: "Review the core concept section above."
        )
    }
}
