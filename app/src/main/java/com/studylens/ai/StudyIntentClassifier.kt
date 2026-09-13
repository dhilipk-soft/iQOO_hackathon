package com.studylens.ai

import com.studylens.shared.StudyIntent

object StudyIntentClassifier {

    private val CODE_KEYWORDS = setOf(
        "python", "kotlin", "java", "c++", "javascript", "typescript", "swift",
        "algorithm", "complexity", "big o", "recursion", "array", "linked list",
        "binary search", "sorting", "hashmap", "function", "class", "syntax",
        "loop", "pointer", "compile", "runtime", "data structure", "debugging",
        "fastapi", "react", "api", "endpoint", "framework", "backend", "frontend",
        "database", "sql", "git", "django", "flask", "node", "express"
    )

    private val SOLVER_KEYWORDS = setOf(
        "solve", "calculate", "evaluate", "find the value", "equation", "formula",
        "integral", "derivative", "differential", "velocity", "acceleration", "force",
        "mass", "momentum", "torque", "voltage", "current", "resistance", "quadratic",
        "matrix", "vector", "polynomial", "logarithm", "trigonometry", "sin(", "cos(",
        "tan(", "lim ", "d/dx", "dx", "derive", "m/s", "kg", "joule", "newton"
    )

    private val REVISION_KEYWORDS = setOf(
        "summary", "summarize", "recap", "cheat sheet", "revision", "key points",
        "takeaways", "cheat-sheet", "overview", "outline", "flashcard", "exam review",
        "quick review", "high yield", "high-yield", "bullet points"
    )

    private val DIRECT_QA_STARTERS = listOf(
        "who ", "when ", "where ", "which year", "define ", "name the", "what is the name",
        "is it true", "yes or no"
    )

    fun classify(text: String, userPreference: StudyIntent = StudyIntent.AUTO): StudyIntent {
        if (userPreference != StudyIntent.AUTO) {
            return userPreference
        }

        val trimmed = text.trim()
        if (trimmed.isBlank()) return StudyIntent.CONCEPT_EXPLANATION

        val lower = trimmed.lowercase()

        // 1. Revision / Summary explicit intent
        if (REVISION_KEYWORDS.any { lower.contains(it) }) {
            return StudyIntent.REVISION_SUMMARY
        }

        // 2. Code & Programming intent
        if (lower.contains("```") || lower.contains("def ") || lower.contains("fun ") ||
            CODE_KEYWORDS.any { lower.contains(it) }
        ) {
            return StudyIntent.CODE_AND_ALGORITHM
        }

        // 3. Problem solving & STEM computation
        val hasMathSymbol = lower.contains("=") || lower.contains("∫") || lower.contains("√") ||
                lower.contains("±") || lower.contains("^") || lower.contains("÷")
        val hasSolverWord = SOLVER_KEYWORDS.any { lower.contains(it) }

        if (hasSolverWord || (hasMathSymbol && trimmed.length < 150)) {
            return StudyIntent.STEP_BY_STEP_SOLVER
        }

        // 4. Quick factual clarification
        val wordCount = trimmed.split("\\s+".toRegex()).size
        if (wordCount <= 8 && DIRECT_QA_STARTERS.any { lower.startsWith(it) }) {
            return StudyIntent.DIRECT_CLARIFICATION
        }

        // 5. Default pedagogical anchor: Concept Deep-Dive
        return StudyIntent.CONCEPT_EXPLANATION
    }

    fun inferSubject(text: String, intent: StudyIntent): Pair<String, String> {
        val lower = text.lowercase()
        val subject = when {
            lower.contains("photosynthesis") || lower.contains("cell") || lower.contains("dna") ||
                    lower.contains("mitosis") || lower.contains("biology") || lower.contains("organism") -> "Biology"
            lower.contains("newton") || lower.contains("force") || lower.contains("velocity") ||
                    lower.contains("gravity") || lower.contains("physics") || lower.contains("energy") -> "Physics"
            lower.contains("calculus") || lower.contains("integral") || lower.contains("derivative") -> "Calculus"
            lower.contains("quadratic") || lower.contains("algebra") || lower.contains("equation") ||
                    lower.contains("polynomial") -> "Algebra"
            lower.contains("triangle") || lower.contains("circle") || lower.contains("angle") ||
                    lower.contains("geometry") -> "Geometry"
            lower.contains("python") || lower.contains("algorithm") || lower.contains("code") ||
                    lower.contains("function") || lower.contains("programming") || lower.contains("fastapi") ||
                    lower.contains("fast api") || lower.contains("api") || lower.contains("react") ||
                    lower.contains("backend") || lower.contains("frontend") || lower.contains("sql") -> "Computer Science"
            lower.contains("molecule") || lower.contains("reaction") || lower.contains("acid") ||
                    lower.contains("chemistry") || lower.contains("element") -> "Chemistry"
            lower.contains("history") || lower.contains("revolution") || lower.contains("war") ||
                    lower.contains("century") -> "History"
            intent == StudyIntent.STEP_BY_STEP_SOLVER -> "STEM / Problem Solving"
            intent == StudyIntent.CODE_AND_ALGORITHM -> "Computer Science"
            else -> "General Science"
        }

        val firstMeaningfulLine = text.lines()
            .map { it.trim().trim('#', '*', '-', ' ') }
            .firstOrNull { it.isNotBlank() && !it.startsWith("[") }
            ?.take(40) ?: "Study Concept"

        return Pair(firstMeaningfulLine, subject)
    }
}
