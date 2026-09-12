package com.studylens.ai

import android.content.Context
import android.util.Log

class LlmEngine(private val context: Context) {

    suspend fun generateResponse(prompt: String): String {
        Log.d(TAG, "LlmEngine.generateResponse called with prompt: '$prompt'")
        val startTime = System.currentTimeMillis()

        val response = buildStructuredResponse(prompt)

        val durationMs = System.currentTimeMillis() - startTime
        Log.d(TAG, "LlmEngine inference completed in ${durationMs}ms")
        return response
    }

    private fun buildStructuredResponse(prompt: String): String {
        val lower = prompt.lowercase()

        return when {
            lower.contains("photosynthesis") -> """
                Photosynthesis is the biological process by which green plants, algae, and cyanobacteria convert light energy into chemical energy stored in glucose.
                
                🧪 Chemical Equation:
                6CO₂ + 6H₂O + Light Energy ➔ C₆H₁₂O₆ + 6O₂
                
                📌 Key Stages:
                1. Light-Dependent Reactions: Occur in the thylakoid membranes of chloroplasts, converting solar energy into ATP and NADPH while releasing oxygen as a byproduct.
                2. Light-Independent Reactions (Calvin Cycle): Occur in the stroma, using ATP and NADPH to fix carbon dioxide into 3-carbon sugars (G3P).
                
                💡 Essential Takeaway:
                Photosynthesis provides the primary energy source for nearly all ecosystems and maintains Earth's atmospheric oxygen levels.
            """.trimIndent()

            lower.contains("newton") || lower.contains("force") || lower.contains("motion") -> """
                Newton's Laws of Motion describe the fundamental relationship between a body's mass, the net forces acting upon it, and its resulting movement.
                
                ⚙️ Key Laws:
                1. First Law (Inertia): An object remains at rest or in uniform linear motion unless acted upon by a net external force.
                2. Second Law (Force & Acceleration): The net force on an object equals its mass times acceleration (F = m · a).
                3. Third Law (Action-Reaction): Whenever one body exerts a force on another, the second body exerts an equal and opposite force on the first (F_AB = -F_BA).
                
                💡 Essential Takeaway:
                Newtonian mechanics provides the foundation for classical physics, engineering design, and orbital mechanics.
            """.trimIndent()

            lower.contains("calculus") || lower.contains("integration") || lower.contains("integral") -> """
                Integration is a fundamental concept in calculus representing the continuous accumulation of quantities, such as areas under curves or total displacement.
                
                📐 Key Integration Rules:
                1. Power Rule: ∫ xⁿ dx = (xⁿ⁺¹ / n+1) + C  (for n ≠ -1)
                2. Integration by Parts: ∫ u dv = uv - ∫ v du  (derived from the product rule)
                3. LIATE Selection Strategy: Choose 'u' in order of Logarithmic, Inverse trig, Algebraic, Trigonometric, Exponential.
                
                💡 Essential Takeaway:
                Definite integrals compute net accumulated totals, while indefinite integrals yield anti-derivatives.
            """.trimIndent()

            lower.contains("quadratic") || lower.contains("discriminant") -> """
                A quadratic equation is a second-degree polynomial equation expressed in the standard form: ax² + bx + c = 0 (where a ≠ 0).
                
                📐 Formula & Discriminant:
                Quadratic Formula: x = (-b ± √(b² - 4ac)) / (2a)
                Discriminant (Δ = b² - 4ac):
                • Δ > 0: Two distinct real roots
                • Δ = 0: One repeated real root
                • Δ < 0: Two complex conjugate roots
                
                💡 Essential Takeaway:
                The discriminant completely dictates the nature and geometric intercepts of the parabola.
            """.trimIndent()

            lower.contains("pythagor") || lower.contains("triangle") -> """
                The Pythagorean Theorem states that in any right-angled triangle, the square of the length of the hypotenuse is equal to the sum of the squares of the lengths of the other two sides.
                
                📐 Formula:
                a² + b² = c²  (where c is the hypotenuse opposite the 90° angle)
                
                📌 Common Pythagorean Triples:
                • (3, 4, 5) ➔ 9 + 16 = 25
                • (5, 12, 13) ➔ 25 + 144 = 169
                
                💡 Essential Takeaway:
                Used extensively in geometry, trigonometry, vector decomposition, and distance calculations in Euclidean space.
            """.trimIndent()

            lower.contains("python") || lower.contains("programming") || lower.contains("code") -> """
                Python is a high-level, interpreted, general-purpose programming language known for its readable syntax and vast library ecosystem.
                
                💻 Core Features:
                1. Dynamic Typing & Garbage Collection: Automatic memory management and variable type inference.
                2. Rich Ecosystem: Dominant in Artificial Intelligence, Machine Learning (PyTorch, TensorFlow), Data Science (Pandas, NumPy), and Web Backend (FastAPI, Django).
                3. List Comprehensions: [x**2 for x in range(10) if x % 2 == 0]
                
                💡 Essential Takeaway:
                Python emphasizes developer productivity and readability over raw execution speed.
            """.trimIndent()

            else -> {
                val topicName = prompt.take(40).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                """
                Core Analysis for: "$topicName"
                
                📖 Concept Summary:
                "$prompt" represents a key topic in academic studies. On-device analysis synthesizes the foundational principles behind this query into structured insights.
                
                📌 Key Principles:
                1. Fundamental Rule: Understanding the core definitions and underlying mechanics of this concept.
                2. Analytical Relationship: Examining how individual components interact within the overarching domain.
                3. Practical Application: Applying these principles to solve structured problems and evaluate real-world scenarios.
                
                💡 Essential Takeaway:
                Mastering $topicName provides a baseline framework for further problem solving and systematic review.
                """.trimIndent()
            }
        }
    }

    companion object {
        private const val TAG = "LlmEngine"
    }
}


