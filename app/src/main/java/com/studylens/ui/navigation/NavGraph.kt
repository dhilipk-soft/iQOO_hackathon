package com.studylens.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.studylens.ui.StudyViewModel
import com.studylens.ui.chat.StudyChatScreen
import com.studylens.ui.focus.FocusScreen
import com.studylens.ui.home.HomeScreen
import com.studylens.ui.quiz.QuizResultScreen
import com.studylens.ui.quiz.QuizScreen
import com.studylens.ui.revision.RevisionScreen
import com.studylens.ui.settings.SettingsScreen

sealed class Screen(val route: String, val label: String) {
    object Home : Screen("home", "Home")
    object StudyChat : Screen("study_chat", "Home")
    object Quiz : Screen("quiz", "Quiz")
    object QuizResult : Screen("quiz_result", "Quiz Result")
    object Revision : Screen("revision", "Revision")
    object Focus : Screen("focus", "Focus")
    object Settings : Screen("settings", "Settings")
}

@Composable
fun NavGraph(
    viewModel: StudyViewModel? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val actualViewModel = viewModel ?: remember {
        StudyViewModel(context.applicationContext as android.app.Application)
    }
    val navController = rememberNavController()

    val activeSession by actualViewModel.activeSession.collectAsState()
    val sessionHistory by actualViewModel.sessionHistory.collectAsState()
    val capturedText by actualViewModel.capturedText.collectAsState()
    val isExplaining by actualViewModel.isExplaining.collectAsState()
    val explanationResult by actualViewModel.explanationResult.collectAsState()
    val followUpList by actualViewModel.followUpList.collectAsState()
    val isAnsweringFollowUp by actualViewModel.isAnsweringFollowUp.collectAsState()
    val quizQuestions by actualViewModel.quizQuestions.collectAsState()
    val selectedQuizAnswers by actualViewModel.selectedQuizAnswers.collectAsState()
    val isQuizSubmitted by actualViewModel.quizSubmitted.collectAsState()
    val revisionList by actualViewModel.revisionList.collectAsState()
    val focusInsight by actualViewModel.focusInsight.collectAsState()
    val studySession by actualViewModel.studySession.collectAsState()
    val vitals by actualViewModel.vitals.collectAsState()
    val isOnline by actualViewModel.isOnline.collectAsState()
    val isSpeaking by actualViewModel.ttsManager.isSpeaking.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isWelcomeHome = currentRoute == Screen.Home.route

    Scaffold(
        bottomBar = {
            // Consistent 3-tab Bottom Navigation Across All Inner Pages (Image 1 Bottom Banner)
            if (!isWelcomeHome) {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFEEF2FF)),
                        width = 1.dp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NavigationBar(
                        containerColor = Color.White,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(64.dp)
                    ) {
                        val isStudyTab = currentRoute in listOf(
                            Screen.StudyChat.route,
                            Screen.Quiz.route,
                            Screen.QuizResult.route,
                            Screen.Revision.route
                        )

                        // 1. Home / Study Tab
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Home",
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Home",
                                    fontSize = 12.sp,
                                    fontWeight = if (isStudyTab) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            selected = isStudyTab,
                            onClick = {
                                navController.navigate(Screen.StudyChat.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF4F46E5),
                                selectedTextColor = Color(0xFF4F46E5),
                                unselectedIconColor = Color(0xFF94A3B8),
                                unselectedTextColor = Color(0xFF94A3B8),
                                indicatorColor = Color(0xFFEEF2FF)
                            )
                        )

                        // 2. Focus Insights Tab
                        val isFocusTab = currentRoute == Screen.Focus.route
                        NavigationBarItem(
                            icon = {
                                FocusBarIcon(tint = if (isFocusTab) Color(0xFF4F46E5) else Color(0xFF94A3B8))
                            },
                            label = {
                                Text(
                                    text = "Focus",
                                    fontSize = 12.sp,
                                    fontWeight = if (isFocusTab) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            selected = isFocusTab,
                            onClick = {
                                navController.navigate(Screen.Focus.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF4F46E5),
                                selectedTextColor = Color(0xFF4F46E5),
                                unselectedIconColor = Color(0xFF94A3B8),
                                unselectedTextColor = Color(0xFF94A3B8),
                                indicatorColor = Color(0xFFEEF2FF)
                            )
                        )

                        // 3. Settings Tab
                        val isSettingsTab = currentRoute == Screen.Settings.route
                        NavigationBarItem(
                            icon = {
                                SettingsGearIcon(tint = if (isSettingsTab) Color(0xFF4F46E5) else Color(0xFF94A3B8))
                            },
                            label = {
                                Text(
                                    text = "Settings",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSettingsTab) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            selected = isSettingsTab,
                            onClick = {
                                navController.navigate(Screen.Settings.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF4F46E5),
                                selectedTextColor = Color(0xFF4F46E5),
                                unselectedIconColor = Color(0xFF94A3B8),
                                unselectedTextColor = Color(0xFF94A3B8),
                                indicatorColor = Color(0xFFEEF2FF)
                            )
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFFF8F9FE)
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 1. Welcome / Home Screen (Image 1 Screen 1)
            composable(Screen.Home.route) {
                HomeScreen(
                    onGetStarted = {
                        navController.navigate(Screen.StudyChat.route)
                    }
                )
            }

            // 2. ChatGPT-Style Study Chat Screen (Image 1 Screen 3 & 4 + Image 2)
            composable(Screen.StudyChat.route) {
                StudyChatScreen(
                    activeSession = activeSession,
                    sessionHistory = sessionHistory,
                    explanationResult = explanationResult,
                    followUpList = followUpList,
                    isExplaining = isExplaining,
                    isAnsweringFollowUp = isAnsweringFollowUp,
                    isSpeaking = isSpeaking,
                    isOnline = isOnline,
                    vitals = vitals,
                    onSelectSession = { sessionId ->
                        actualViewModel.selectSession(sessionId)
                    },
                    onStartNewSession = {
                        actualViewModel.startNewSession()
                    },
                    onAskQuestion = { question ->
                        if (activeSession == null || explanationResult == null) {
                            actualViewModel.explainCurrentCapture(question)
                        } else {
                            actualViewModel.askFollowUp(question)
                        }
                    },
                    onSpeakText = { text ->
                        actualViewModel.speakText(text)
                    },
                    onStopSpeaking = {
                        actualViewModel.stopSpeaking()
                    },
                    onTakeQuiz = {
                        actualViewModel.generatePracticeQuiz()
                        navController.navigate(Screen.Quiz.route)
                    },
                    onToggleSimulatedNetwork = {
                        actualViewModel.toggleSimulatedNetwork()
                    }
                )
            }

            // 3. Quiz Screen (Image 1 Screen 5)
            composable(Screen.Quiz.route) {
                QuizScreen(
                    questions = quizQuestions,
                    selectedAnswers = selectedQuizAnswers,
                    isSubmitted = isQuizSubmitted,
                    onSelectOption = { qId, opt -> actualViewModel.selectQuizAnswer(qId, opt) },
                    onSubmitQuiz = {
                        actualViewModel.submitQuiz()
                        navController.navigate(Screen.QuizResult.route)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // 4. Quiz Result Screen (Image 1 Screen 6)
            composable(Screen.QuizResult.route) {
                QuizResultScreen(
                    questions = quizQuestions,
                    selectedAnswers = selectedQuizAnswers,
                    onBack = { navController.popBackStack() },
                    onViewRevisionList = {
                        navController.navigate(Screen.Revision.route)
                    },
                    onRetakeQuiz = {
                        actualViewModel.generatePracticeQuiz()
                        navController.navigate(Screen.Quiz.route)
                    }
                )
            }

            // 5. Revision List Screen (Image 1 Screen 7)
            composable(Screen.Revision.route) {
                RevisionScreen(
                    revisionList = revisionList,
                    onRestudyTopic = { item ->
                        actualViewModel.selectSession("session_quadratic")
                        navController.navigate(Screen.StudyChat.route)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // 6. Focus Insights Screen (Image 1 Screen 8)
            composable(Screen.Focus.route) {
                FocusScreen(
                    session = studySession,
                    focusInsight = focusInsight
                )
            }

            // 7. Settings Screen (Image 1 Screen 9)
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun FocusBarIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val strokeW = 2.2.dp.toPx()
        val spacing = 5.dp.toPx()
        val cx = size.width / 2f
        val botY = size.height - 3.dp.toPx()

        // Bar 1 (left)
        drawLine(tint, Offset(cx - spacing, botY), Offset(cx - spacing, botY - 7.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
        // Bar 2 (center, taller)
        drawLine(tint, Offset(cx, botY), Offset(cx, botY - 14.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
        // Bar 3 (right, medium)
        drawLine(tint, Offset(cx + spacing, botY), Offset(cx + spacing, botY - 10.dp.toPx()), strokeWidth = strokeW, cap = StrokeCap.Round)
    }
}

@Composable
fun SettingsGearIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = 6.dp.toPx()
        val strokeW = 1.8.dp.toPx()

        drawCircle(tint, radius = r, center = Offset(cx, cy), style = Stroke(width = strokeW))
        drawCircle(tint, radius = 2.2.dp.toPx(), center = Offset(cx, cy))

        // 6 gear cogs
        for (i in 0 until 6) {
            val angle = i * (Math.PI / 3.0)
            val cosA = Math.cos(angle).toFloat()
            val sinA = Math.sin(angle).toFloat()
            val startX = cx + (r - 0.5.dp.toPx()) * cosA
            val startY = cy + (r - 0.5.dp.toPx()) * sinA
            val endX = cx + (r + 3.dp.toPx()) * cosA
            val endY = cy + (r + 3.dp.toPx()) * sinA
            drawLine(tint, Offset(startX, startY), Offset(endX, endY), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
