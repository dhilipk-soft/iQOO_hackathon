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
            return StructuredStudyResponse(
                intent = inferredIntent,
                title = title,
                subject = subject,
                coreConcept = "No explanation generated.",
                citations = citations,
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

        return StructuredStudyResponse(
            intent = parsedIntent,
            title = title,
            subject = subject,
            coreConcept = coreConcept.ifBlank { bodyText },
            formulaOrCode = formulaOrCodeBlock,
            steps = steps,
            analogy = analogy,
            commonPitfalls = commonPitfalls,
            quickCheck = quickCheck,
            citations = citations,
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
