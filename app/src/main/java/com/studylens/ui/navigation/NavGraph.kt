package com.studylens.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.studylens.shared.ExplanationResult
import com.studylens.shared.InferenceStats
import com.studylens.shared.QuizQuestion
import com.studylens.ui.capture.CaptureScreen
import com.studylens.ui.explanation.ExplanationScreen
import com.studylens.ui.focus.FocusScreen
import com.studylens.ui.quiz.QuizScreen
import com.studylens.ui.revision.RevisionScreen
import com.studylens.ui.vitals.DeviceVitalsStrip

sealed class Screen(val route: String) {
    object Capture : Screen("capture")
    object Explanation : Screen("explanation")
    object Quiz : Screen("quiz")
    object Revision : Screen("revision")
    object Focus : Screen("focus")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    var currentCapturedText by remember { mutableStateOf("") }
    var currentExplanation by remember { mutableStateOf<ExplanationResult?>(null) }
    var currentQuiz by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }

    Scaffold(
        topBar = {
            DeviceVitalsStrip(
                stats = InferenceStats(tokensPerSecond = 24.5, latencyMs = 120, ramUsedMb = 320, thermalStatus = "NORMAL"),
                isOnline = true
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Capture.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Capture.route) {
                CaptureScreen(
                    onTextCaptured = { text ->
                        currentCapturedText = text
                        currentExplanation = ExplanationResult(
                            captureId = 1L,
                            finalExplanation = "Integration by parts is a technique derived from the product rule of calculus to evaluate integrals of products of functions.",
                            usedOnlineContext = true
                        )
                        navController.navigate(Screen.Explanation.route)
                    }
                )
            }
            composable(Screen.Explanation.route) {
                ExplanationScreen(
                    capturedText = currentCapturedText,
                    explanationResult = currentExplanation,
                    onGenerateQuiz = {
                        currentQuiz = listOf(
                            QuizQuestion(
                                id = 1L,
                                captureId = 1L,
                                topic = "Calculus",
                                question = "Which rule is integration by parts derived from?",
                                options = listOf("Product Rule", "Chain Rule", "Quotient Rule", "Power Rule"),
                                correctAnswer = "Product Rule"
                            )
                        )
                        navController.navigate(Screen.Quiz.route)
                    }
                )
            }
            composable(Screen.Quiz.route) {
                QuizScreen(
                    questions = currentQuiz,
                    onFinishQuiz = {
                        navController.navigate(Screen.Focus.route)
                    }
                )
            }
            composable(Screen.Revision.route) {
                RevisionScreen()
            }
            composable(Screen.Focus.route) {
                FocusScreen()
            }
        }
    }
}
