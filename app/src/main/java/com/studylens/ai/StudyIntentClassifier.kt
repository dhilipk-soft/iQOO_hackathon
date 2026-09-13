package com.studylens.ai

import com.studylens.shared.StudyIntent

object StudyIntentClassifier {

    // Keywords that EXPLICITLY request code generation (write, implement, code, program, etc.)
    private val EXPLICIT_CODE_TRIGGERS = setOf(
        "write a", "write the", "write code", "write function", "write program",
        "code for", "code a", "implement", "implementation", "program for", "program to",
        "create a function", "create a program", "give me the code", "give code",
        "show code", "show the code", "make a function", "make a program",
        "build a function", "build the function", "how to code", "how to implement",
        "how to write", "algorithm for", "script for", "script to",
        "reverse a string", "reverse string", "is prime", "is_prime", "prime check",
        "fibonacci series", "factorial of", "sieve of", "binary search code",
        "sort algorithm", "sorting algorithm"
    )

    // Tech/CS concept keywords - used to tag subject area only, NOT to force CODE_AND_ALGORITHM intent
    private val TECH_CONCEPT_KEYWORDS = setOf(
        "python", "kotlin", "java", "c++", "javascript", "typescript", "swift",
        "fastapi", "fast api", "pydantic", "react", "django", "flask", "node", "express",
        "api", "endpoint", "framework", "backend", "frontend", "database", "sql", "git",
        "algorithm", "complexity", "big o", "recursion", "data structure", "linked list",
        "hashmap", "stack", "queue", "tree", "graph", "fibonacci", "factorial", "sieve",
        "binary search", "sorting", "loop", "pointer", "compile", "runtime", "debugging"
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

        // 2. Code & Programming intent - ONLY when user explicitly requests code/implementation
        // "What is FastAPI" or "Explain pydantic" = CONCEPT, not CODE.
        // "Write a FastAPI endpoint" or "implement binary search" = CODE.
        val hasExplicitCodeBlock = lower.contains("```") || lower.contains("def ") ||
                (lower.contains("fun ") && !lower.startsWith("fun "))  // Kotlin fun keyword in snippet
        val hasExplicitCodeRequest = EXPLICIT_CODE_TRIGGERS.any { lower.contains(it) }

        if (hasExplicitCodeBlock || hasExplicitCodeRequest) {
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
                    lower.contains("backend") || lower.contains("frontend") || lower.contains("sql") ||
                    lower.contains("prime") || lower.contains("prome") || lower.contains("reverse") || lower.contains("palindrome") ||
                    lower.contains("fibonacci") || lower.contains("factorial") || lower.contains("string") -> "Computer Science"
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
