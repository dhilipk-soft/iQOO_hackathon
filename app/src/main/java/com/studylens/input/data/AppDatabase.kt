package com.studylens.input.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.studylens.shared.AppEvent
import com.studylens.shared.FocusEventType
import com.studylens.shared.FocusInterruptionEvent
import com.studylens.shared.FocusSessionSummary
import com.studylens.shared.NotificationEvent
import com.studylens.shared.StudyCapture
import com.studylens.shared.StudySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Plain SQLite persistence — replaces the previous Room implementation.
 *
 * Why: Room's annotation processor (kapt/KSP) can't read code compiled by Kotlin 2.4+
 * (confirmed: even Room's newest stable release, 2.8.5, is one metadata version short -
 * there is no Room release yet, stable or alpha, that supports it). Kotlin 2.4 is required
 * for litertlm-android (multimodal support). This file is a direct, same-shape replacement:
 * every method name, parameter, and return type below matches what the old Room DAOs
 * exposed, so StudyViewModel.kt, UsageCollector.kt, NotificationCollector.kt, and
 * StudyLensNotificationListenerService.kt did NOT need to change at all.
 *
 * Flow-returning queries (previously Room's auto-updating queries) are now backed by a
 * MutableStateFlow that's manually re-emitted after every insert - same reactive contract
 * from the caller's point of view.
 */

// ----------------------------------------------------
// "Entities" - plain data classes now, no annotations needed
// ----------------------------------------------------

data class StudyCaptureEntity(val id: Long = 0, val extractedText: String, val timestamp: Long) {
    fun toDomain(): StudyCapture = StudyCapture(id, extractedText, timestamp)
}

data class AppEventEntity(val id: Long = 0, val packageName: String, val eventType: Int, val timestamp: Long) {
    fun toDomain(): AppEvent = AppEvent(id, packageName, eventType, timestamp)
}

data class NotificationEventEntity(val id: Long = 0, val packageName: String, val timestamp: Long) {
    fun toDomain(): NotificationEvent = NotificationEvent(id, packageName, timestamp)
}

data class StudySessionEntity(
    val id: Long = 0,
    val studentId: String = "",
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val switchCount: Int,
    val notificationCount: Int
) {
    fun toDomain(): StudySession = StudySession(id, startTime, endTime, durationMs, switchCount, notificationCount)
}

data class FocusEventEntity(
    val id: Long = 0,
    val sessionId: Long,
    val eventType: String,
    val details: String,
    val timestamp: Long
) {
    fun toDomain(): FocusInterruptionEvent = FocusInterruptionEvent(
        id = id,
        sessionId = sessionId,
        eventType = try {
            FocusEventType.valueOf(eventType)
        } catch (e: Exception) {
            FocusEventType.APP_SWITCH
        },
        details = details,
        timestamp = timestamp
    )
}

data class FocusSessionSummaryEntity(
    val id: Long = 0,
    val sessionId: Long,
    val startTime: Long,
    val endTime: Long,
    val totalStudyTimeMs: Long,
    val focusedTimeMs: Long,
    val longestFocusStreakMs: Long,
    val switchCount: Int,
    val notificationCount: Int,
    val checksPresented: Int,
    val checksPassed: Int,
    val checksFailed: Int,
    val normalOverrides: Int,
    val emergencyExits: Int,
    val plannedBreaksCount: Int,
    val returnedOnTimeCount: Int,
    val missedRemindersCount: Int,
    val avgPlannedBreakMs: Long,
    val avgActualBreakMs: Long,
    val longestDelayedReturnMs: Long,
    val studyRelatedSwitches: Int = 0,
    val distractionChainsCount: Int = 0,
    val narrative: String,
    val recommendation: String,
    val mainLeakReason: String = "",
    val actionPlan: String = ""
) {
    fun toDomain(events: List<FocusInterruptionEvent> = emptyList()): FocusSessionSummary = FocusSessionSummary(
        sessionId = sessionId,
        startTime = startTime,
        endTime = endTime,
        totalStudyTimeMs = totalStudyTimeMs,
        focusedTimeMs = focusedTimeMs,
        longestFocusStreakMs = longestFocusStreakMs,
        switchCount = switchCount,
        notificationCount = notificationCount,
        checksPresented = checksPresented,
        checksPassed = checksPassed,
        checksFailed = checksFailed,
        normalOverrides = normalOverrides,
        emergencyExits = emergencyExits,
        plannedBreaksCount = plannedBreaksCount,
        returnedOnTimeCount = returnedOnTimeCount,
        missedRemindersCount = missedRemindersCount,
        avgPlannedBreakMs = avgPlannedBreakMs,
        avgActualBreakMs = avgActualBreakMs,
        longestDelayedReturnMs = longestDelayedReturnMs,
        studyRelatedSwitches = studyRelatedSwitches,
        distractionChainsCount = distractionChainsCount,
        timelineEvents = events,
        narrative = narrative,
        recommendation = recommendation,
        isFocusModeActive = false
    )
}


data class ChatSessionEntity(
    val id: Long = 0,
    val studentId: String = "",
    val title: String,
    val subject: String,
    val previewText: String,
    val explanation: String,
    val formula: String?,
    val bulletPoints: List<String>,
    val usedOnlineContext: Boolean,
    val timestamp: Long
)

data class ChatMessageEntity(
    val id: Long = 0,
    val sessionId: Long,
    val question: String,
    val answer: String,
    val timestamp: Long
)

// ----------------------------------------------------
// Personal Learning Twin & Adaptive Engine Entities
// ----------------------------------------------------

data class StudentProfileEntity(
    val id: String, // UUID or "student_a"
    val name: String,
    val institution: String = "Class 10", // e.g. "Class 12", "IIT Madras", etc.
    val stream: String = "Science (PCM)", // e.g. "Science (PCM)", "Commerce", etc.
    val subjects: List<String> = listOf("Physics", "Mathematics"),
    val targetExam: String = "Board Exam",
    val examDate: Long = 0L,
    val dailyMinutes: Int = 60,
    val learningStyle: String = "Interactive",
    val strengths: String = "",
    val weaknesses: String = "",
    val isCurrent: Boolean = false
)

data class QuizAttemptEntity(
    val id: Long = 0,
    val studentId: String,
    val concept: String,
    val topic: String,
    val question: String,
    val selectedAnswer: String,
    val correctAnswer: String,
    val isCorrect: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class PendingQuizEntity(
    val id: Long = 0,
    val studentId: String,
    val concept: String,
    val topic: String,
    val sourceChatSessionId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
)

data class ConceptMasteryEntity(
    val id: Long = 0,
    val studentId: String,
    val subject: String,
    val topic: String,
    val concept: String,
    val masteryScore: Int, // 0..100
    val attempts: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val lastErrorType: String? = null,
    val activeMisconception: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class MisconceptionLogEntity(
    val id: Long = 0,
    val studentId: String,
    val concept: String,
    val mistakePattern: String,
    val sampleAnswer: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isResolved: Boolean = false
)

data class ExamPlanEntity(
    val id: Long = 0,
    val studentId: String,
    val examName: String,
    val daysRemaining: Int,
    val dailyMinutes: Int,
    val targetScore: Int,
    val currentDayPlan: String // Pipe-separated or text breakdown
)

private fun List<String>.toDbString(): String = joinToString("|||")
private fun String.toStringList(): List<String> = if (isBlank()) emptyList() else split("|||")

// ----------------------------------------------------
// SQLite schema
// ----------------------------------------------------

class DbHelper(context: Context) : SQLiteOpenHelper(context, "studylens_db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE study_captures (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                extractedText TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE app_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                packageName TEXT NOT NULL,
                eventType INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE notification_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                packageName TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE study_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL DEFAULT '',
                startTime INTEGER NOT NULL,
                endTime INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                switchCount INTEGER NOT NULL,
                notificationCount INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE chat_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                subject TEXT NOT NULL,
                previewText TEXT NOT NULL,
                explanation TEXT NOT NULL,
                formula TEXT,
                bulletPoints TEXT NOT NULL,
                usedOnlineContext INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessionId INTEGER NOT NULL,
                question TEXT NOT NULL,
                answer TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS focus_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessionId INTEGER NOT NULL,
                eventType TEXT NOT NULL,
                details TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS focus_session_summaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessionId INTEGER NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER NOT NULL,
                totalStudyTimeMs INTEGER NOT NULL,
                focusedTimeMs INTEGER NOT NULL,
                longestFocusStreakMs INTEGER NOT NULL,
                switchCount INTEGER NOT NULL,
                notificationCount INTEGER NOT NULL,
                checksPresented INTEGER NOT NULL,
                checksPassed INTEGER NOT NULL,
                checksFailed INTEGER NOT NULL,
                normalOverrides INTEGER NOT NULL,
                emergencyExits INTEGER NOT NULL,
                plannedBreaksCount INTEGER NOT NULL,
                returnedOnTimeCount INTEGER NOT NULL,
                missedRemindersCount INTEGER NOT NULL,
                avgPlannedBreakMs INTEGER NOT NULL,
                avgActualBreakMs INTEGER NOT NULL,
                longestDelayedReturnMs INTEGER NOT NULL,
                studyRelatedSwitches INTEGER NOT NULL DEFAULT 0,
                distractionChainsCount INTEGER NOT NULL DEFAULT 0,
                narrative TEXT NOT NULL,
                recommendation TEXT NOT NULL,
                mainLeakReason TEXT NOT NULL DEFAULT '',
                actionPlan TEXT NOT NULL DEFAULT ''
            )"""
        )

        // Learning Twin & Exam Planner Tables
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS student_profiles (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                institution TEXT NOT NULL,
                stream TEXT NOT NULL,
                subjects TEXT NOT NULL,
                targetExam TEXT NOT NULL,
                examDate INTEGER NOT NULL DEFAULT 0,
                dailyMinutes INTEGER NOT NULL DEFAULT 60,
                learningStyle TEXT NOT NULL DEFAULT '',
                strengths TEXT NOT NULL DEFAULT '',
                weaknesses TEXT NOT NULL DEFAULT '',
                isCurrent INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS concept_mastery (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL,
                subject TEXT NOT NULL,
                topic TEXT NOT NULL,
                concept TEXT NOT NULL,
                masteryScore INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                correctCount INTEGER NOT NULL DEFAULT 0,
                incorrectCount INTEGER NOT NULL DEFAULT 0,
                lastErrorType TEXT,
                activeMisconception TEXT,
                lastUpdated INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS misconception_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL,
                concept TEXT NOT NULL,
                mistakePattern TEXT NOT NULL,
                sampleAnswer TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                isResolved INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS exam_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL,
                examName TEXT NOT NULL,
                daysRemaining INTEGER NOT NULL,
                dailyMinutes INTEGER NOT NULL,
                targetScore INTEGER NOT NULL,
                currentDayPlan TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS quiz_attempts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL,
                concept TEXT NOT NULL,
                topic TEXT NOT NULL,
                question TEXT NOT NULL,
                selectedAnswer TEXT NOT NULL,
                correctAnswer TEXT NOT NULL,
                isCorrect INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS pending_quizzes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL,
                concept TEXT NOT NULL,
                topic TEXT NOT NULL,
                sourceChatSessionId INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                isCompleted INTEGER NOT NULL DEFAULT 0
            )"""
        )
        seedLearningTwinData(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS focus_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessionId INTEGER NOT NULL,
                eventType TEXT NOT NULL,
                details TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS focus_session_summaries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessionId INTEGER NOT NULL,
                startTime INTEGER NOT NULL,
                endTime INTEGER NOT NULL,
                totalStudyTimeMs INTEGER NOT NULL,
                focusedTimeMs INTEGER NOT NULL,
                longestFocusStreakMs INTEGER NOT NULL,
                switchCount INTEGER NOT NULL,
                notificationCount INTEGER NOT NULL,
                checksPresented INTEGER NOT NULL,
                checksPassed INTEGER NOT NULL,
                checksFailed INTEGER NOT NULL,
                normalOverrides INTEGER NOT NULL,
                emergencyExits INTEGER NOT NULL,
                plannedBreaksCount INTEGER NOT NULL,
                returnedOnTimeCount INTEGER NOT NULL,
                missedRemindersCount INTEGER NOT NULL,
                avgPlannedBreakMs INTEGER NOT NULL,
                avgActualBreakMs INTEGER NOT NULL,
                longestDelayedReturnMs INTEGER NOT NULL,
                studyRelatedSwitches INTEGER NOT NULL DEFAULT 0,
                distractionChainsCount INTEGER NOT NULL DEFAULT 0,
                narrative TEXT NOT NULL,
                recommendation TEXT NOT NULL,
                mainLeakReason TEXT NOT NULL DEFAULT '',
                actionPlan TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS student_profiles (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                institution TEXT NOT NULL,
                stream TEXT NOT NULL,
                subjects TEXT NOT NULL,
                targetExam TEXT NOT NULL,
                examDate INTEGER NOT NULL DEFAULT 0,
                dailyMinutes INTEGER NOT NULL DEFAULT 60,
                learningStyle TEXT NOT NULL DEFAULT '',
                strengths TEXT NOT NULL DEFAULT '',
                weaknesses TEXT NOT NULL DEFAULT '',
                isCurrent INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS concept_mastery (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL,
                subject TEXT NOT NULL,
                topic TEXT NOT NULL,
                concept TEXT NOT NULL,
                masteryScore INTEGER NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                correctCount INTEGER NOT NULL DEFAULT 0,
                incorrectCount INTEGER NOT NULL DEFAULT 0,
                lastErrorType TEXT,
                activeMisconception TEXT,
                lastUpdated INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS misconception_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL,
                concept TEXT NOT NULL,
                mistakePattern TEXT NOT NULL,
                sampleAnswer TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                isResolved INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS exam_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL,
                examName TEXT NOT NULL,
                daysRemaining INTEGER NOT NULL,
                dailyMinutes INTEGER NOT NULL,
                targetScore INTEGER NOT NULL,
                currentDayPlan TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS quiz_attempts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL,
                concept TEXT NOT NULL,
                topic TEXT NOT NULL,
                question TEXT NOT NULL,
                selectedAnswer TEXT NOT NULL,
                correctAnswer TEXT NOT NULL,
                isCorrect INTEGER NOT NULL,
                timestamp INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS pending_quizzes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                studentId TEXT NOT NULL,
                concept TEXT NOT NULL,
                topic TEXT NOT NULL,
                sourceChatSessionId INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                isCompleted INTEGER NOT NULL DEFAULT 0
            )"""
        )
        seedLearningTwinData(db)
    }

    private fun seedLearningTwinData(db: SQLiteDatabase) {
        val countCursor = db.rawQuery("SELECT COUNT(*) FROM student_profiles", null)
        var count = 0
        if (countCursor.moveToFirst()) {
            count = countCursor.getInt(0)
        }
        countCursor.close()
        if (count > 0) return

        // 1. Seed Student A (Aarav - Math/Analytical Strong)
        val now = System.currentTimeMillis()
        db.execSQL(
            """INSERT INTO student_profiles (id, name, institution, stream, subjects, targetExam, examDate, dailyMinutes, learningStyle, strengths, weaknesses, isCurrent)
               VALUES ('student_a', 'Aarav Sharma', 'Class 10', 'Science (PCM)', 'Physics|||Mathematics|||Chemistry', 'CBSE Board Exam', ${now + 12L * 86400000L}, 90, 'Mathematical / Analytical', 'Fast arithmetic, formula manipulation, circuit diagrams', 'Physical intuition, verbal concept explanations', 0)"""
        )
        db.execSQL("INSERT INTO concept_mastery (studentId, subject, topic, concept, masteryScore, attempts, correctCount, incorrectCount, lastErrorType, activeMisconception, lastUpdated) VALUES ('student_a', 'Physics', 'Electricity', 'Voltage', 92, 12, 11, 1, 'CALCULATION', NULL, $now)")
        db.execSQL("INSERT INTO concept_mastery (studentId, subject, topic, concept, masteryScore, attempts, correctCount, incorrectCount, lastErrorType, activeMisconception, lastUpdated) VALUES ('student_a', 'Physics', 'Electricity', 'Current', 88, 10, 9, 1, 'CALCULATION', NULL, $now)")
        db.execSQL("INSERT INTO concept_mastery (studentId, subject, topic, concept, masteryScore, attempts, correctCount, incorrectCount, lastErrorType, activeMisconception, lastUpdated) VALUES ('student_a', 'Physics', 'Electricity', 'Ohm''s Law', 85, 15, 13, 2, 'CALCULATION', NULL, $now)")
        db.execSQL("INSERT INTO concept_mastery (studentId, subject, topic, concept, masteryScore, attempts, correctCount, incorrectCount, lastErrorType, activeMisconception, lastUpdated) VALUES ('student_a', 'Physics', 'Electricity', 'Resistance', 78, 14, 11, 3, 'UNIT_ERROR', NULL, $now)")

        db.execSQL("INSERT INTO exam_plans (studentId, examName, daysRemaining, dailyMinutes, targetScore, currentDayPlan) VALUES ('student_a', 'CBSE Class 10 Physics Midterm', 12, 90, 95, '25m Advanced Circuits ||| 35m Joule Heating Practice ||| 20m Formula Speed Drill ||| 10m Review')")

        // 2. Seed Student B (Priya - Conceptual Strong, Math/Formula Struggle)
        db.execSQL(
            """INSERT INTO student_profiles (id, name, institution, stream, subjects, targetExam, examDate, dailyMinutes, learningStyle, strengths, weaknesses, isCurrent)
               VALUES ('student_b', 'Priya Patel', 'Class 10', 'Science (PCM)', 'Physics|||Biology|||Chemistry', 'CBSE Board Exam', ${now + 12L * 86400000L}, 60, 'Conceptual / Intuitive', 'Physical intuition, qualitative analogies, high curiosity', 'Formula algebra inversion, dividing in Ohm''s Law (R = V * I)', 1)"""
        )
        db.execSQL("INSERT INTO concept_mastery (studentId, subject, topic, concept, masteryScore, attempts, correctCount, incorrectCount, lastErrorType, activeMisconception, lastUpdated) VALUES ('student_b', 'Physics', 'Electricity', 'Voltage', 86, 8, 7, 1, 'CALCULATION', NULL, $now)")
        db.execSQL("INSERT INTO concept_mastery (studentId, subject, topic, concept, masteryScore, attempts, correctCount, incorrectCount, lastErrorType, activeMisconception, lastUpdated) VALUES ('student_b', 'Physics', 'Electricity', 'Current', 80, 8, 6, 2, 'CALCULATION', NULL, $now)")
        db.execSQL("INSERT INTO concept_mastery (studentId, subject, topic, concept, masteryScore, attempts, correctCount, incorrectCount, lastErrorType, activeMisconception, lastUpdated) VALUES ('student_b', 'Physics', 'Electricity', 'Ohm''s Law', 55, 10, 5, 5, 'FORMULA_INVERSION', 'Inverts V = I*R when calculating R', $now)")
        db.execSQL("INSERT INTO concept_mastery (studentId, subject, topic, concept, masteryScore, attempts, correctCount, incorrectCount, lastErrorType, activeMisconception, lastUpdated) VALUES ('student_b', 'Physics', 'Electricity', 'Resistance', 32, 9, 3, 6, 'FORMULA_INVERSION', 'Multiplies V * I instead of V / I', $now)")

        db.execSQL("INSERT INTO misconception_logs (studentId, concept, mistakePattern, sampleAnswer, timestamp, isResolved) VALUES ('student_b', 'Resistance', 'Inverted formula: uses R = V * I (multiplies 10V * 2A = 20 ohms)', '20 Ohms', $now, 0)")
        db.execSQL("INSERT INTO exam_plans (studentId, examName, daysRemaining, dailyMinutes, targetScore, currentDayPlan) VALUES ('student_b', 'CBSE Class 10 Physics Midterm', 12, 60, 85, '20m Remedial Resistance Division ||| 20m Ohm''s Law Guided Socratic Practice ||| 15m Voltage Review ||| 5m Recap')")

        // Seed initial quiz attempts and pending quizzes for Priya so attending/attended sections have initial demo data
        db.execSQL("INSERT INTO quiz_attempts (studentId, concept, topic, question, selectedAnswer, correctAnswer, isCorrect, timestamp) VALUES ('student_b', 'Voltage', 'Electricity', 'What unit is electric potential difference measured in?', 'Volts', 'Volts', 1, ${now - 3600000L})")
        db.execSQL("INSERT INTO quiz_attempts (studentId, concept, topic, question, selectedAnswer, correctAnswer, isCorrect, timestamp) VALUES ('student_b', 'Resistance', 'Electricity', 'If V=10V and I=2A, what is the resistance?', '20 Ohms', '5 Ohms', 0, ${now - 1800000L})")
        db.execSQL("INSERT INTO pending_quizzes (studentId, concept, topic, sourceChatSessionId, timestamp, isCompleted) VALUES ('student_b', 'Ohm''s Law', 'Electricity', 1, $now, 0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS study_captures")
        db.execSQL("DROP TABLE IF EXISTS app_events")
        db.execSQL("DROP TABLE IF EXISTS notification_events")
        db.execSQL("DROP TABLE IF EXISTS study_sessions")
        db.execSQL("DROP TABLE IF EXISTS chat_sessions")
        db.execSQL("DROP TABLE IF EXISTS chat_messages")
        db.execSQL("DROP TABLE IF EXISTS focus_events")
        db.execSQL("DROP TABLE IF EXISTS focus_session_summaries")
        db.execSQL("DROP TABLE IF EXISTS student_profiles")
        db.execSQL("DROP TABLE IF EXISTS concept_mastery")
        db.execSQL("DROP TABLE IF EXISTS misconception_logs")
        db.execSQL("DROP TABLE IF EXISTS exam_plans")
        db.execSQL("DROP TABLE IF EXISTS quiz_attempts")
        db.execSQL("DROP TABLE IF EXISTS pending_quizzes")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }
}

// ----------------------------------------------------
// "DAOs" - same method names/signatures as the old Room interfaces
// ----------------------------------------------------

class StudyCaptureDao(private val helper: DbHelper) {
    suspend fun insert(capture: StudyCaptureEntity): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("extractedText", capture.extractedText)
            put("timestamp", capture.timestamp)
        }
        helper.writableDatabase.insert("study_captures", null, values)
    }

    private val _allCaptures = MutableStateFlow<List<StudyCaptureEntity>>(emptyList())

    fun getAllCaptures(): Flow<List<StudyCaptureEntity>> = _allCaptures.asStateFlow()

    suspend fun getCaptureById(id: Long): StudyCaptureEntity? = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery("SELECT * FROM study_captures WHERE id = ? LIMIT 1", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toStudyCapture() else null
        }
    }

    suspend fun deleteCapture(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete("study_captures", "id = ?", arrayOf(id.toString()))
        Unit
    }

    private fun Cursor.toStudyCapture() = StudyCaptureEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        extractedText = getString(getColumnIndexOrThrow("extractedText")),
        timestamp = getLong(getColumnIndexOrThrow("timestamp"))
    )
}

class FocusSignalsDao(private val helper: DbHelper) {
    private val _recentStudySessions = MutableStateFlow<List<StudySessionEntity>>(emptyList())

    init {
        // Seed with whatever was already saved from a previous run, same as ChatDao -
        // callers shouldn't need a fresh insert this session just to see history.
        try {
            val results = mutableListOf<StudySessionEntity>()
            helper.readableDatabase.rawQuery(
                "SELECT * FROM study_sessions ORDER BY startTime DESC LIMIT 20", null
            ).use { c ->
                while (c.moveToNext()) {
                    results.add(
                        StudySessionEntity(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            startTime = c.getLong(c.getColumnIndexOrThrow("startTime")),
                            endTime = c.getLong(c.getColumnIndexOrThrow("endTime")),
                            durationMs = c.getLong(c.getColumnIndexOrThrow("durationMs")),
                            switchCount = c.getInt(c.getColumnIndexOrThrow("switchCount")),
                            notificationCount = c.getInt(c.getColumnIndexOrThrow("notificationCount"))
                        )
                    )
                }
            }
            _recentStudySessions.value = results
        } catch (e: Exception) {
            // Table may not exist yet at this exact instant on a brand-new install - the
            // next insertStudySession() call refreshes it correctly regardless.
        }
    }

    suspend fun insertAppEvent(event: AppEventEntity): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("packageName", event.packageName)
            put("eventType", event.eventType)
            put("timestamp", event.timestamp)
        }
        helper.writableDatabase.insert("app_events", null, values)
    }

    suspend fun getAppEvents(startTime: Long, endTime: Long): List<AppEventEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AppEventEntity>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM app_events WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp ASC",
            arrayOf(startTime.toString(), endTime.toString())
        ).use { c ->
            while (c.moveToNext()) {
                results.add(
                    AppEventEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        packageName = c.getString(c.getColumnIndexOrThrow("packageName")),
                        eventType = c.getInt(c.getColumnIndexOrThrow("eventType")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        results
    }

    suspend fun insertNotificationEvent(event: NotificationEventEntity): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("packageName", event.packageName)
            put("timestamp", event.timestamp)
        }
        helper.writableDatabase.insert("notification_events", null, values)
    }

    suspend fun getNotificationEvents(startTime: Long, endTime: Long): List<NotificationEventEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<NotificationEventEntity>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM notification_events WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp ASC",
            arrayOf(startTime.toString(), endTime.toString())
        ).use { c ->
            while (c.moveToNext()) {
                results.add(
                    NotificationEventEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        packageName = c.getString(c.getColumnIndexOrThrow("packageName")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        results
    }

    suspend fun insertStudySession(session: StudySessionEntity): Long {
        val id = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("studentId", session.studentId)
                put("startTime", session.startTime)
                put("endTime", session.endTime)
                put("durationMs", session.durationMs)
                put("switchCount", session.switchCount)
                put("notificationCount", session.notificationCount)
            }
            helper.writableDatabase.insert("study_sessions", null, values)
        }
        refreshRecentStudySessions(20)
        return id
    }

    fun getRecentStudySessions(limit: Int = 20): Flow<List<StudySessionEntity>> {
        // Fire-and-forget initial load; callers already treat this as a StateFlow-style
        // stream that emits again on every insert, same as the old Room Flow query.
        return _recentStudySessions.asStateFlow()
    }

    suspend fun refreshRecentStudySessions(limit: Int = 20) = withContext(Dispatchers.IO) {
        val results = mutableListOf<StudySessionEntity>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM study_sessions ORDER BY startTime DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                results.add(
                    StudySessionEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        studentId = c.getString(c.getColumnIndexOrThrow("studentId")),
                        startTime = c.getLong(c.getColumnIndexOrThrow("startTime")),
                        endTime = c.getLong(c.getColumnIndexOrThrow("endTime")),
                        durationMs = c.getLong(c.getColumnIndexOrThrow("durationMs")),
                        switchCount = c.getInt(c.getColumnIndexOrThrow("switchCount")),
                        notificationCount = c.getInt(c.getColumnIndexOrThrow("notificationCount"))
                    )
                )
            }
        }
        _recentStudySessions.value = results
    }

    private val _focusEvents = MutableStateFlow<List<FocusEventEntity>>(emptyList())

    suspend fun insertFocusEvent(event: FocusEventEntity): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("sessionId", event.sessionId)
            put("eventType", event.eventType)
            put("details", event.details)
            put("timestamp", event.timestamp)
        }
        val id = helper.writableDatabase.insert("focus_events", null, values)
        refreshFocusEvents(event.sessionId)
        id
    }

    suspend fun getFocusEvents(sessionId: Long): List<FocusEventEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FocusEventEntity>()
        try {
            helper.readableDatabase.rawQuery(
                "SELECT * FROM focus_events WHERE sessionId = ? ORDER BY timestamp ASC",
                arrayOf(sessionId.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    results.add(
                        FocusEventEntity(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            sessionId = c.getLong(c.getColumnIndexOrThrow("sessionId")),
                            eventType = c.getString(c.getColumnIndexOrThrow("eventType")),
                            details = c.getString(c.getColumnIndexOrThrow("details")),
                            timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Degrade gracefully
        }
        results
    }

    suspend fun getAllFocusEvents(): List<FocusEventEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FocusEventEntity>()
        try {
            helper.readableDatabase.rawQuery(
                "SELECT * FROM focus_events ORDER BY timestamp ASC",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    results.add(
                        FocusEventEntity(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            sessionId = c.getLong(c.getColumnIndexOrThrow("sessionId")),
                            eventType = c.getString(c.getColumnIndexOrThrow("eventType")),
                            details = c.getString(c.getColumnIndexOrThrow("details")),
                            timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Degrade gracefully
        }
        results
    }

    fun getRecentFocusEvents(): Flow<List<FocusEventEntity>> = _focusEvents.asStateFlow()

    suspend fun refreshFocusEvents(sessionId: Long? = null) = withContext(Dispatchers.IO) {
        val list = if (sessionId != null && sessionId > 0) getFocusEvents(sessionId) else getAllFocusEvents()
        _focusEvents.value = list
    }

    suspend fun insertFocusSessionSummary(summary: FocusSessionSummaryEntity): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("sessionId", summary.sessionId)
            put("startTime", summary.startTime)
            put("endTime", summary.endTime)
            put("totalStudyTimeMs", summary.totalStudyTimeMs)
            put("focusedTimeMs", summary.focusedTimeMs)
            put("longestFocusStreakMs", summary.longestFocusStreakMs)
            put("switchCount", summary.switchCount)
            put("notificationCount", summary.notificationCount)
            put("checksPresented", summary.checksPresented)
            put("checksPassed", summary.checksPassed)
            put("checksFailed", summary.checksFailed)
            put("normalOverrides", summary.normalOverrides)
            put("emergencyExits", summary.emergencyExits)
            put("plannedBreaksCount", summary.plannedBreaksCount)
            put("returnedOnTimeCount", summary.returnedOnTimeCount)
            put("missedRemindersCount", summary.missedRemindersCount)
            put("avgPlannedBreakMs", summary.avgPlannedBreakMs)
            put("avgActualBreakMs", summary.avgActualBreakMs)
            put("longestDelayedReturnMs", summary.longestDelayedReturnMs)
            put("studyRelatedSwitches", summary.studyRelatedSwitches)
            put("distractionChainsCount", summary.distractionChainsCount)
            put("narrative", summary.narrative)
            put("recommendation", summary.recommendation)
            put("mainLeakReason", summary.mainLeakReason)
            put("actionPlan", summary.actionPlan)
        }
        helper.writableDatabase.insert("focus_session_summaries", null, values)
    }

    suspend fun getLatestFocusSessionSummary(): FocusSessionSummaryEntity? = withContext(Dispatchers.IO) {
        var result: FocusSessionSummaryEntity? = null
        try {
            helper.readableDatabase.rawQuery(
                "SELECT * FROM focus_session_summaries ORDER BY endTime DESC LIMIT 1",
                null
            ).use { c ->
                if (c.moveToNext()) {
                    result = mapFocusSessionSummary(c)
                }
            }
        } catch (e: Exception) {
            // Table may not exist yet or empty
        }
        result
    }

    suspend fun getAllFocusSessionSummaries(limit: Int = 50): List<FocusSessionSummaryEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FocusSessionSummaryEntity>()
        try {
            helper.readableDatabase.rawQuery(
                "SELECT * FROM focus_session_summaries ORDER BY endTime DESC LIMIT ?",
                arrayOf(limit.toString())
            ).use { c ->
                while (c.moveToNext()) {
                    list.add(mapFocusSessionSummary(c))
                }
            }
        } catch (e: Exception) {
            // Table may not exist yet or empty
        }
        list
    }

    private fun mapFocusSessionSummary(c: Cursor): FocusSessionSummaryEntity {
        return FocusSessionSummaryEntity(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            sessionId = c.getLong(c.getColumnIndexOrThrow("sessionId")),
            startTime = c.getLong(c.getColumnIndexOrThrow("startTime")),
            endTime = c.getLong(c.getColumnIndexOrThrow("endTime")),
            totalStudyTimeMs = c.getLong(c.getColumnIndexOrThrow("totalStudyTimeMs")),
            focusedTimeMs = c.getLong(c.getColumnIndexOrThrow("focusedTimeMs")),
            longestFocusStreakMs = c.getLong(c.getColumnIndexOrThrow("longestFocusStreakMs")),
            switchCount = c.getInt(c.getColumnIndexOrThrow("switchCount")),
            notificationCount = c.getInt(c.getColumnIndexOrThrow("notificationCount")),
            checksPresented = c.getInt(c.getColumnIndexOrThrow("checksPresented")),
            checksPassed = c.getInt(c.getColumnIndexOrThrow("checksPassed")),
            checksFailed = c.getInt(c.getColumnIndexOrThrow("checksFailed")),
            normalOverrides = c.getInt(c.getColumnIndexOrThrow("normalOverrides")),
            emergencyExits = c.getInt(c.getColumnIndexOrThrow("emergencyExits")),
            plannedBreaksCount = c.getInt(c.getColumnIndexOrThrow("plannedBreaksCount")),
            returnedOnTimeCount = c.getInt(c.getColumnIndexOrThrow("returnedOnTimeCount")),
            missedRemindersCount = c.getInt(c.getColumnIndexOrThrow("missedRemindersCount")),
            avgPlannedBreakMs = c.getLong(c.getColumnIndexOrThrow("avgPlannedBreakMs")),
            avgActualBreakMs = c.getLong(c.getColumnIndexOrThrow("avgActualBreakMs")),
            longestDelayedReturnMs = c.getLong(c.getColumnIndexOrThrow("longestDelayedReturnMs")),
            studyRelatedSwitches = c.getInt(c.getColumnIndexOrThrow("studyRelatedSwitches")),
            distractionChainsCount = c.getInt(c.getColumnIndexOrThrow("distractionChainsCount")),
            narrative = c.getString(c.getColumnIndexOrThrow("narrative")),
            recommendation = c.getString(c.getColumnIndexOrThrow("recommendation")),
            mainLeakReason = if (c.getColumnIndex("mainLeakReason") != -1) c.getString(c.getColumnIndex("mainLeakReason")) ?: "" else "",
            actionPlan = if (c.getColumnIndex("actionPlan") != -1) c.getString(c.getColumnIndex("actionPlan")) ?: "" else ""
        )
    }
}

class ChatDao(private val helper: DbHelper) {
    private val _allSessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())

    init {
        // Loads whatever's already persisted (e.g. from a previous app run) on first access.
        refreshSessionsBlockingSafe()
    }

    suspend fun insertSession(session: ChatSessionEntity): Long {
        val id = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("studentId", session.studentId)
                put("title", session.title)
                put("subject", session.subject)
                put("previewText", session.previewText)
                put("explanation", session.explanation)
                put("formula", session.formula)
                put("bulletPoints", session.bulletPoints.toDbString())
                put("usedOnlineContext", if (session.usedOnlineContext) 1 else 0)
                put("timestamp", session.timestamp)
            }
            helper.writableDatabase.insert("chat_sessions", null, values)
        }
        refreshSessions()
        return id
    }

    fun getAllSessions(): Flow<List<ChatSessionEntity>> = _allSessions.asStateFlow()

    suspend fun insertMessage(message: ChatMessageEntity): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("sessionId", message.sessionId)
            put("question", message.question)
            put("answer", message.answer)
            put("timestamp", message.timestamp)
        }
        helper.writableDatabase.insert("chat_messages", null, values)
    }

    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ChatMessageEntity>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM chat_messages WHERE sessionId = ? ORDER BY timestamp ASC",
            arrayOf(sessionId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                results.add(
                    ChatMessageEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        sessionId = c.getLong(c.getColumnIndexOrThrow("sessionId")),
                        question = c.getString(c.getColumnIndexOrThrow("question")),
                        answer = c.getString(c.getColumnIndexOrThrow("answer")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        results
    }

    private suspend fun refreshSessions() = withContext(Dispatchers.IO) {
        _allSessions.value = queryAllSessions()
    }

    private fun refreshSessionsBlockingSafe() {
        try {
            _allSessions.value = queryAllSessions()
        } catch (e: Exception) {
            // Table may not exist yet on a brand-new install at this exact instant
        }
    }

    private fun queryAllSessions(): List<ChatSessionEntity> {
        val results = mutableListOf<ChatSessionEntity>()
        helper.readableDatabase.rawQuery("SELECT * FROM chat_sessions ORDER BY timestamp DESC", null).use { c ->
            while (c.moveToNext()) {
                results.add(
                    ChatSessionEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        studentId = c.getString(c.getColumnIndexOrThrow("studentId")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        subject = c.getString(c.getColumnIndexOrThrow("subject")),
                        previewText = c.getString(c.getColumnIndexOrThrow("previewText")),
                        explanation = c.getString(c.getColumnIndexOrThrow("explanation")),
                        formula = c.getString(c.getColumnIndexOrThrow("formula")),
                        bulletPoints = c.getString(c.getColumnIndexOrThrow("bulletPoints")).toStringList(),
                        usedOnlineContext = c.getInt(c.getColumnIndexOrThrow("usedOnlineContext")) == 1,
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        return results
    }
}

class LearningTwinDao(private val helper: DbHelper) {
    private val _activeProfile = MutableStateFlow<StudentProfileEntity?>(null)
    val activeProfileFlow: StateFlow<StudentProfileEntity?> = _activeProfile.asStateFlow()

    private val _allProfiles = MutableStateFlow<List<StudentProfileEntity>>(emptyList())
    val allProfilesFlow: StateFlow<List<StudentProfileEntity>> = _allProfiles.asStateFlow()

    private val _conceptMasteryList = MutableStateFlow<List<ConceptMasteryEntity>>(emptyList())
    val conceptMasteryFlow: StateFlow<List<ConceptMasteryEntity>> = _conceptMasteryList.asStateFlow()

    private val _misconceptionsList = MutableStateFlow<List<MisconceptionLogEntity>>(emptyList())
    val misconceptionsFlow: StateFlow<List<MisconceptionLogEntity>> = _misconceptionsList.asStateFlow()

    private val _activeExamPlan = MutableStateFlow<ExamPlanEntity?>(null)
    val activeExamPlanFlow: StateFlow<ExamPlanEntity?> = _activeExamPlan.asStateFlow()

    private val _quizAttemptsList = MutableStateFlow<List<QuizAttemptEntity>>(emptyList())
    val quizAttemptsFlow: StateFlow<List<QuizAttemptEntity>> = _quizAttemptsList.asStateFlow()

    private val _pendingQuizzesList = MutableStateFlow<List<PendingQuizEntity>>(emptyList())
    val pendingQuizzesFlow: StateFlow<List<PendingQuizEntity>> = _pendingQuizzesList.asStateFlow()

    init {
        refreshStateSafe()
    }

    fun refreshStateSafe() {
        try {
            val profiles = queryAllProfiles()
            _allProfiles.value = profiles
            var profile = queryActiveProfile()
            if (profile == null && profiles.isNotEmpty()) {
                profile = profiles.first()
            }
            _activeProfile.value = profile
            if (profile != null) {
                _conceptMasteryList.value = queryMastery(profile.id)
                _misconceptionsList.value = queryMisconceptions(profile.id)
                _activeExamPlan.value = queryExamPlan(profile.id)
                _quizAttemptsList.value = queryQuizAttempts(profile.id)
                _pendingQuizzesList.value = queryPendingQuizzes(profile.id)
            } else {
                _conceptMasteryList.value = emptyList()
                _misconceptionsList.value = emptyList()
                _activeExamPlan.value = null
                _quizAttemptsList.value = emptyList()
                _pendingQuizzesList.value = emptyList()
            }
        } catch (e: Exception) {
            // Tables might be initializing
        }
    }

    suspend fun getActiveProfile(): StudentProfileEntity? = withContext(Dispatchers.IO) {
        queryActiveProfile()
    }

    suspend fun getAllProfiles(): List<StudentProfileEntity> = withContext(Dispatchers.IO) {
        queryAllProfiles()
    }

    suspend fun createProfile(profile: StudentProfileEntity): String = withContext(Dispatchers.IO) {
        val studentId = if (profile.id.isNotBlank()) profile.id else java.util.UUID.randomUUID().toString()
        helper.writableDatabase.beginTransaction()
        try {
            helper.writableDatabase.execSQL("UPDATE student_profiles SET isCurrent = 0")
            val values = ContentValues().apply {
                put("id", studentId)
                put("name", profile.name)
                put("institution", profile.institution)
                put("stream", profile.stream)
                put("subjects", profile.subjects.toDbString())
                put("targetExam", profile.targetExam)
                put("examDate", profile.examDate)
                put("dailyMinutes", profile.dailyMinutes)
                put("learningStyle", profile.learningStyle)
                put("strengths", profile.strengths)
                put("weaknesses", profile.weaknesses)
                put("isCurrent", 1)
            }
            helper.writableDatabase.insertWithOnConflict(
                "student_profiles",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )

            // Calculate days remaining
            val now = System.currentTimeMillis()
            val days = if (profile.examDate > now) {
                ((profile.examDate - now) / 86400000L).coerceAtLeast(1).toInt()
            } else {
                30
            }
            val primarySubject = profile.subjects.firstOrNull() ?: "Foundation"
            val examName = if (profile.targetExam.isNotBlank()) profile.targetExam else "Next Exam"
            val planValues = ContentValues().apply {
                put("studentId", studentId)
                put("examName", examName)
                put("daysRemaining", days)
                put("dailyMinutes", profile.dailyMinutes.coerceAtLeast(15))
                put("targetScore", 90)
                put("currentDayPlan", "${profile.dailyMinutes / 2}m Diagnostic & Topic Practice ($primarySubject) ||| ${profile.dailyMinutes / 2}m Weak Concept Reinforcement")
            }
            helper.writableDatabase.insertWithOnConflict(
                "exam_plans",
                null,
                planValues,
                SQLiteDatabase.CONFLICT_REPLACE
            )

            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
        refreshStateSafe()
        studentId
    }

    suspend fun setActiveProfile(studentId: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.beginTransaction()
        try {
            helper.writableDatabase.execSQL("UPDATE student_profiles SET isCurrent = 0")
            helper.writableDatabase.execSQL("UPDATE student_profiles SET isCurrent = 1 WHERE id = ?", arrayOf(studentId))
            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
        refreshStateSafe()
    }

    suspend fun registerConceptFromChat(
        studentId: String,
        subject: String,
        topic: String,
        concept: String,
        sourceChatSessionId: Long
    ) = withContext(Dispatchers.IO) {
        if (studentId.isBlank() || concept.isBlank()) return@withContext
        val now = System.currentTimeMillis()

        // 1. Register in concept_mastery if not yet present
        val existing = queryMastery(studentId).firstOrNull { it.concept.equals(concept, ignoreCase = true) }
        if (existing == null) {
            val values = ContentValues().apply {
                put("studentId", studentId)
                put("subject", subject)
                put("topic", topic)
                put("concept", concept)
                put("masteryScore", 50)
                put("attempts", 0)
                put("correctCount", 0)
                put("incorrectCount", 0)
                put("lastUpdated", now)
            }
            helper.writableDatabase.insert("concept_mastery", null, values)
        }

        // 2. Add to pending_quizzes if not already scheduled
        val existingPending = queryPendingQuizzes(studentId).firstOrNull {
            it.concept.equals(concept, ignoreCase = true) && !it.isCompleted
        }
        if (existingPending == null) {
            val pValues = ContentValues().apply {
                put("studentId", studentId)
                put("concept", concept)
                put("topic", topic)
                put("sourceChatSessionId", sourceChatSessionId)
                put("timestamp", now)
                put("isCompleted", 0)
            }
            helper.writableDatabase.insert("pending_quizzes", null, pValues)
        }
        refreshStateSafe()
    }

    suspend fun recordQuizAttempt(
        studentId: String,
        concept: String,
        topic: String,
        question: String,
        selectedAnswer: String,
        correctAnswer: String,
        isCorrect: Boolean,
        errorType: String? = null,
        misconception: String? = null
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Insert into quiz_attempts
        val attemptValues = ContentValues().apply {
            put("studentId", studentId)
            put("concept", concept)
            put("topic", topic)
            put("question", question)
            put("selectedAnswer", selectedAnswer)
            put("correctAnswer", correctAnswer)
            put("isCorrect", if (isCorrect) 1 else 0)
            put("timestamp", now)
        }
        helper.writableDatabase.insert("quiz_attempts", null, attemptValues)

        // 2. Mark pending quiz as completed
        helper.writableDatabase.execSQL(
            "UPDATE pending_quizzes SET isCompleted = 1 WHERE studentId = ? AND LOWER(concept) = LOWER(?)",
            arrayOf(studentId, concept)
        )

        // 3. Update or create concept mastery
        val list = queryMastery(studentId)
        val target = list.firstOrNull { it.concept.equals(concept, ignoreCase = true) }
        val delta = if (isCorrect) 12 else -10

        if (target != null) {
            val newScore = (target.masteryScore + delta).coerceIn(0, 100)
            val newAttempts = target.attempts + 1
            val newCorrect = target.correctCount + (if (isCorrect) 1 else 0)
            val newIncorrect = target.incorrectCount + (if (isCorrect) 0 else 1)
            val values = ContentValues().apply {
                put("masteryScore", newScore)
                put("attempts", newAttempts)
                put("correctCount", newCorrect)
                put("incorrectCount", newIncorrect)
                if (errorType != null) put("lastErrorType", errorType)
                if (misconception != null) put("activeMisconception", misconception)
                put("lastUpdated", now)
            }
            helper.writableDatabase.update("concept_mastery", values, "id = ?", arrayOf(target.id.toString()))
        } else {
            val initialScore = if (isCorrect) 75 else 40
            val values = ContentValues().apply {
                put("studentId", studentId)
                put("subject", "General")
                put("topic", topic)
                put("concept", concept)
                put("masteryScore", initialScore)
                put("attempts", 1)
                put("correctCount", if (isCorrect) 1 else 0)
                put("incorrectCount", if (isCorrect) 0 else 1)
                if (errorType != null) put("lastErrorType", errorType)
                if (misconception != null) put("activeMisconception", misconception)
                put("lastUpdated", now)
            }
            helper.writableDatabase.insert("concept_mastery", null, values)
        }

        // 4. Log misconception if incorrect
        if (!isCorrect && misconception != null) {
            val mValues = ContentValues().apply {
                put("studentId", studentId)
                put("concept", concept)
                put("mistakePattern", misconception)
                put("sampleAnswer", selectedAnswer)
                put("timestamp", now)
                put("isResolved", 0)
            }
            helper.writableDatabase.insert("misconception_logs", null, mValues)
        }
        refreshStateSafe()
    }

    suspend fun updateMastery(
        conceptName: String,
        delta: Int,
        isCorrect: Boolean,
        errorType: String? = null,
        misconception: String? = null
    ) = withContext(Dispatchers.IO) {
        val currentProfile = _activeProfile.value ?: return@withContext
        val list = queryMastery(currentProfile.id)
        val target = list.firstOrNull { it.concept.equals(conceptName, ignoreCase = true) }
        val now = System.currentTimeMillis()

        if (target != null) {
            val newScore = (target.masteryScore + delta).coerceIn(0, 100)
            val newAttempts = target.attempts + 1
            val newCorrect = target.correctCount + (if (isCorrect) 1 else 0)
            val newIncorrect = target.incorrectCount + (if (isCorrect) 0 else 1)
            val values = ContentValues().apply {
                put("masteryScore", newScore)
                put("attempts", newAttempts)
                put("correctCount", newCorrect)
                put("incorrectCount", newIncorrect)
                if (errorType != null) put("lastErrorType", errorType)
                if (misconception != null) put("activeMisconception", misconception)
                put("lastUpdated", now)
            }
            helper.writableDatabase.update("concept_mastery", values, "id = ?", arrayOf(target.id.toString()))
        }
        refreshStateSafe()
    }

    suspend fun logMisconception(
        concept: String,
        mistakePattern: String,
        sampleAnswer: String
    ) = withContext(Dispatchers.IO) {
        val currentProfile = _activeProfile.value ?: return@withContext
        val values = ContentValues().apply {
            put("studentId", currentProfile.id)
            put("concept", concept)
            put("mistakePattern", mistakePattern)
            put("sampleAnswer", sampleAnswer)
            put("timestamp", System.currentTimeMillis())
            put("isResolved", 0)
        }
        helper.writableDatabase.insert("misconception_logs", null, values)
        refreshStateSafe()
    }

    suspend fun resolveMisconception(concept: String) = withContext(Dispatchers.IO) {
        val currentProfile = _activeProfile.value ?: return@withContext
        helper.writableDatabase.execSQL(
            "UPDATE misconception_logs SET isResolved = 1 WHERE studentId = ? AND concept = ?",
            arrayOf(currentProfile.id, concept)
        )
        helper.writableDatabase.execSQL(
            "UPDATE concept_mastery SET activeMisconception = NULL WHERE studentId = ? AND concept = ?",
            arrayOf(currentProfile.id, concept)
        )
        refreshStateSafe()
    }

    suspend fun updateExamPlan(daysRemaining: Int, dailyMinutes: Int, planBreakdown: String, targetScore: Int = 90) = withContext(Dispatchers.IO) {
        val currentProfile = _activeProfile.value ?: return@withContext
        val values = ContentValues().apply {
            put("daysRemaining", daysRemaining)
            put("dailyMinutes", dailyMinutes)
            put("currentDayPlan", planBreakdown)
            put("targetScore", targetScore)
        }
        helper.writableDatabase.update("exam_plans", values, "studentId = ?", arrayOf(currentProfile.id))
        refreshStateSafe()
    }

    suspend fun resetDemoData() = withContext(Dispatchers.IO) {
        helper.writableDatabase.beginTransaction()
        try {
            helper.writableDatabase.execSQL("DELETE FROM student_profiles")
            helper.writableDatabase.execSQL("DELETE FROM concept_mastery")
            helper.writableDatabase.execSQL("DELETE FROM misconception_logs")
            helper.writableDatabase.execSQL("DELETE FROM exam_plans")
            helper.writableDatabase.execSQL("DELETE FROM quiz_attempts")
            helper.writableDatabase.execSQL("DELETE FROM pending_quizzes")
            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
        val helperOnOpenMethod = DbHelper::class.java.getDeclaredMethod("seedLearningTwinData", SQLiteDatabase::class.java)
        helperOnOpenMethod.isAccessible = true
        helperOnOpenMethod.invoke(helper, helper.writableDatabase)
        refreshStateSafe()
    }

    private fun queryActiveProfile(): StudentProfileEntity? {
        helper.readableDatabase.rawQuery("SELECT * FROM student_profiles WHERE isCurrent = 1 LIMIT 1", null).use { c ->
            if (c.moveToFirst()) {
                return StudentProfileEntity(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    institution = c.getString(c.getColumnIndexOrThrow("institution")),
                    stream = c.getString(c.getColumnIndexOrThrow("stream")),
                    subjects = c.getString(c.getColumnIndexOrThrow("subjects")).toStringList(),
                    targetExam = c.getString(c.getColumnIndexOrThrow("targetExam")),
                    examDate = c.getLong(c.getColumnIndexOrThrow("examDate")),
                    dailyMinutes = c.getInt(c.getColumnIndexOrThrow("dailyMinutes")),
                    learningStyle = c.getString(c.getColumnIndexOrThrow("learningStyle")),
                    strengths = c.getString(c.getColumnIndexOrThrow("strengths")),
                    weaknesses = c.getString(c.getColumnIndexOrThrow("weaknesses")),
                    isCurrent = true
                )
            }
        }
        return null
    }

    private fun queryAllProfiles(): List<StudentProfileEntity> {
        val list = mutableListOf<StudentProfileEntity>()
        helper.readableDatabase.rawQuery("SELECT * FROM student_profiles ORDER BY name ASC", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    StudentProfileEntity(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        institution = c.getString(c.getColumnIndexOrThrow("institution")),
                        stream = c.getString(c.getColumnIndexOrThrow("stream")),
                        subjects = c.getString(c.getColumnIndexOrThrow("subjects")).toStringList(),
                        targetExam = c.getString(c.getColumnIndexOrThrow("targetExam")),
                        examDate = c.getLong(c.getColumnIndexOrThrow("examDate")),
                        dailyMinutes = c.getInt(c.getColumnIndexOrThrow("dailyMinutes")),
                        learningStyle = c.getString(c.getColumnIndexOrThrow("learningStyle")),
                        strengths = c.getString(c.getColumnIndexOrThrow("strengths")),
                        weaknesses = c.getString(c.getColumnIndexOrThrow("weaknesses")),
                        isCurrent = c.getInt(c.getColumnIndexOrThrow("isCurrent")) == 1
                    )
                )
            }
        }
        return list
    }

    private fun queryMastery(studentId: String): List<ConceptMasteryEntity> {
        val list = mutableListOf<ConceptMasteryEntity>()
        helper.readableDatabase.rawQuery("SELECT * FROM concept_mastery WHERE studentId = ? ORDER BY masteryScore ASC", arrayOf(studentId)).use { c ->
            while (c.moveToNext()) {
                list.add(
                    ConceptMasteryEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        studentId = c.getString(c.getColumnIndexOrThrow("studentId")),
                        subject = c.getString(c.getColumnIndexOrThrow("subject")),
                        topic = c.getString(c.getColumnIndexOrThrow("topic")),
                        concept = c.getString(c.getColumnIndexOrThrow("concept")),
                        masteryScore = c.getInt(c.getColumnIndexOrThrow("masteryScore")),
                        attempts = c.getInt(c.getColumnIndexOrThrow("attempts")),
                        correctCount = c.getInt(c.getColumnIndexOrThrow("correctCount")),
                        incorrectCount = c.getInt(c.getColumnIndexOrThrow("incorrectCount")),
                        lastErrorType = c.getString(c.getColumnIndexOrThrow("lastErrorType")),
                        activeMisconception = c.getString(c.getColumnIndexOrThrow("activeMisconception")),
                        lastUpdated = c.getLong(c.getColumnIndexOrThrow("lastUpdated"))
                    )
                )
            }
        }
        return list
    }

    private fun queryMisconceptions(studentId: String): List<MisconceptionLogEntity> {
        val list = mutableListOf<MisconceptionLogEntity>()
        helper.readableDatabase.rawQuery("SELECT * FROM misconception_logs WHERE studentId = ? AND isResolved = 0 ORDER BY timestamp DESC", arrayOf(studentId)).use { c ->
            while (c.moveToNext()) {
                list.add(
                    MisconceptionLogEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        studentId = c.getString(c.getColumnIndexOrThrow("studentId")),
                        concept = c.getString(c.getColumnIndexOrThrow("concept")),
                        mistakePattern = c.getString(c.getColumnIndexOrThrow("mistakePattern")),
                        sampleAnswer = c.getString(c.getColumnIndexOrThrow("sampleAnswer")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        isResolved = c.getInt(c.getColumnIndexOrThrow("isResolved")) == 1
                    )
                )
            }
        }
        return list
    }

    private fun queryExamPlan(studentId: String): ExamPlanEntity? {
        helper.readableDatabase.rawQuery("SELECT * FROM exam_plans WHERE studentId = ? LIMIT 1", arrayOf(studentId)).use { c ->
            if (c.moveToFirst()) {
                return ExamPlanEntity(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    studentId = c.getString(c.getColumnIndexOrThrow("studentId")),
                    examName = c.getString(c.getColumnIndexOrThrow("examName")),
                    daysRemaining = c.getInt(c.getColumnIndexOrThrow("daysRemaining")),
                    dailyMinutes = c.getInt(c.getColumnIndexOrThrow("dailyMinutes")),
                    targetScore = c.getInt(c.getColumnIndexOrThrow("targetScore")),
                    currentDayPlan = c.getString(c.getColumnIndexOrThrow("currentDayPlan"))
                )
            }
        }
        return null
    }

    private fun queryQuizAttempts(studentId: String): List<QuizAttemptEntity> {
        val list = mutableListOf<QuizAttemptEntity>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM quiz_attempts WHERE studentId = ? ORDER BY timestamp DESC",
            arrayOf(studentId)
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    QuizAttemptEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        studentId = c.getString(c.getColumnIndexOrThrow("studentId")),
                        concept = c.getString(c.getColumnIndexOrThrow("concept")),
                        topic = c.getString(c.getColumnIndexOrThrow("topic")),
                        question = c.getString(c.getColumnIndexOrThrow("question")),
                        selectedAnswer = c.getString(c.getColumnIndexOrThrow("selectedAnswer")),
                        correctAnswer = c.getString(c.getColumnIndexOrThrow("correctAnswer")),
                        isCorrect = c.getInt(c.getColumnIndexOrThrow("isCorrect")) == 1,
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        return list
    }

    private fun queryPendingQuizzes(studentId: String): List<PendingQuizEntity> {
        val list = mutableListOf<PendingQuizEntity>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM pending_quizzes WHERE studentId = ? AND isCompleted = 0 ORDER BY timestamp DESC",
            arrayOf(studentId)
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    PendingQuizEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        studentId = c.getString(c.getColumnIndexOrThrow("studentId")),
                        concept = c.getString(c.getColumnIndexOrThrow("concept")),
                        topic = c.getString(c.getColumnIndexOrThrow("topic")),
                        sourceChatSessionId = c.getLong(c.getColumnIndexOrThrow("sourceChatSessionId")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        isCompleted = c.getInt(c.getColumnIndexOrThrow("isCompleted")) == 1
                    )
                )
            }
        }
        return list
    }
}

// ----------------------------------------------------
// Database - same public shape as the old Room version:
// AppDatabase.getDatabase(context).chatDao() / .focusSignalsDao() / .studyCaptureDao()
// ----------------------------------------------------

class AppDatabase private constructor(context: Context) {
    private val helper = DbHelper(context)

    private val _studyCaptureDao = StudyCaptureDao(helper)
    private val _focusSignalsDao = FocusSignalsDao(helper)
    private val _chatDao = ChatDao(helper)
    private val _learningTwinDao = LearningTwinDao(helper)

    fun studyCaptureDao(): StudyCaptureDao = _studyCaptureDao
    fun focusSignalsDao(): FocusSignalsDao = _focusSignalsDao
    fun chatDao(): ChatDao = _chatDao
    fun learningTwinDao(): LearningTwinDao = _learningTwinDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
