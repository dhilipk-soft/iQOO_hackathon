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
    val title: String,
    val subject: String,
    val previewText: String,
    val explanation: String,
    val formula: String?,
    val bulletPoints: List<String>,
    val usedOnlineContext: Boolean,
    val timestamp: Long,
    val imagePath: String? = null  // Path to captured session image in internal storage
)

data class ChatMessageEntity(
    val id: Long = 0,
    val sessionId: Long,
    val question: String,
    val answer: String,
    val timestamp: Long,
<<<<<<< HEAD
    val imagePath: String? = null  // Path to uploaded image for this follow-up message
=======
    val usedOnlineContext: Boolean = false
>>>>>>> 6151f416595a7c84b23fc5a115b4ff341926ae5c
)

private fun List<String>.toDbString(): String = joinToString("|||")
private fun String.toStringList(): List<String> = if (isBlank()) emptyList() else split("|||")

// ----------------------------------------------------
// SQLite schema
// ----------------------------------------------------

class DbHelper(context: Context) : SQLiteOpenHelper(context, "studylens_db", null, 3) {
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
                startTime INTEGER NOT NULL,
                endTime INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                switchCount INTEGER NOT NULL,
                notificationCount INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS chat_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                subject TEXT NOT NULL,
                previewText TEXT NOT NULL,
                explanation TEXT NOT NULL,
                formula TEXT,
                bulletPoints TEXT NOT NULL,
                usedOnlineContext INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                imagePath TEXT
            )"""
        )
        db.execSQL(
            """CREATE TABLE chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sessionId INTEGER NOT NULL,
                question TEXT NOT NULL,
                answer TEXT NOT NULL,
<<<<<<< HEAD
                timestamp INTEGER NOT NULL,
                imagePath TEXT
=======
                usedOnlineContext INTEGER NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL
>>>>>>> 6151f416595a7c84b23fc5a115b4ff341926ae5c
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
<<<<<<< HEAD
        // Safe migration: add imagePath columns if they don't exist (for existing installs)
        try { db.execSQL("ALTER TABLE chat_sessions ADD COLUMN imagePath TEXT") } catch (_: Exception) {}
        try { db.execSQL("ALTER TABLE chat_messages ADD COLUMN imagePath TEXT") } catch (_: Exception) {}
=======
        try {
            db.execSQL("ALTER TABLE chat_messages ADD COLUMN usedOnlineContext INTEGER NOT NULL DEFAULT 0;")
        } catch (_: Exception) {}
>>>>>>> 6151f416595a7c84b23fc5a115b4ff341926ae5c
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Matches the old fallbackToDestructiveMigration() behavior - fine for a hackathon
        // prototype, no production user data to preserve across schema changes.
        db.execSQL("DROP TABLE IF EXISTS study_captures")
        db.execSQL("DROP TABLE IF EXISTS app_events")
        db.execSQL("DROP TABLE IF EXISTS notification_events")
        db.execSQL("DROP TABLE IF EXISTS study_sessions")
        db.execSQL("DROP TABLE IF EXISTS chat_sessions")
        db.execSQL("DROP TABLE IF EXISTS chat_messages")
        db.execSQL("DROP TABLE IF EXISTS focus_events")
        db.execSQL("DROP TABLE IF EXISTS focus_session_summaries")
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
                put("title", session.title)
                put("subject", session.subject)
                put("previewText", session.previewText)
                put("explanation", session.explanation)
                put("formula", session.formula)
                put("bulletPoints", session.bulletPoints.toDbString())
                put("usedOnlineContext", if (session.usedOnlineContext) 1 else 0)
                put("timestamp", session.timestamp)
                put("imagePath", session.imagePath)
            }
            helper.writableDatabase.insert("chat_sessions", null, values)
        }
        refreshSessions()
        return id
    }

    suspend fun updateSession(session: ChatSessionEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("title", session.title)
            put("subject", session.subject)
            put("previewText", session.previewText)
            put("explanation", session.explanation)
            put("formula", session.formula)
            put("bulletPoints", session.bulletPoints.toDbString())
            put("usedOnlineContext", if (session.usedOnlineContext) 1 else 0)
            put("timestamp", session.timestamp)
        }
        helper.writableDatabase.update("chat_sessions", values, "id = ?", arrayOf(session.id.toString()))
        refreshSessions()
    }

    suspend fun getUnenrichedSessions(): List<ChatSessionEntity> = withContext(Dispatchers.IO) {
        getSessionsNeedingEnrichment()
    }

    suspend fun getSessionsNeedingEnrichment(): List<ChatSessionEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ChatSessionEntity>()
        val query = """
            SELECT DISTINCT s.id, s.title, s.subject, s.previewText, s.explanation, s.formula, s.bulletPoints, s.usedOnlineContext, s.timestamp
            FROM chat_sessions s
            LEFT JOIN chat_messages m ON s.id = m.sessionId
            WHERE s.usedOnlineContext = 0 
               OR s.explanation LIKE '%The answer is **-%'
               OR s.explanation LIKE '%Calculation: 10 - 15%'
               OR s.explanation LIKE '%Calculation: 5 - 8%'
               OR m.answer LIKE '%Sorry, I couldn%' 
               OR m.answer LIKE '%Based on on-device knowledge%'
               OR m.answer LIKE '%The answer is **-%'
               OR m.answer LIKE '%Calculation: 10 - 15%'
               OR m.answer LIKE '%Calculation: 5 - 8%'
            ORDER BY s.timestamp ASC
        """.trimIndent()
        helper.readableDatabase.rawQuery(query, null).use { c ->
            while (c.moveToNext()) {
                results.add(
                    ChatSessionEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
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
        results
    }

    fun getAllSessions(): Flow<List<ChatSessionEntity>> = _allSessions.asStateFlow()

    suspend fun insertMessage(message: ChatMessageEntity): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("sessionId", message.sessionId)
            put("question", message.question)
            put("answer", message.answer)
            put("usedOnlineContext", if (message.usedOnlineContext) 1 else 0)
            put("timestamp", message.timestamp)
            put("imagePath", message.imagePath)
        }
        helper.writableDatabase.insert("chat_messages", null, values)
    }

    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ChatMessageEntity>()
        helper.readableDatabase.rawQuery(
            "SELECT * FROM chat_messages WHERE sessionId = ? ORDER BY timestamp ASC",
            arrayOf(sessionId.toString())
        ).use { c ->
            val onlineCol = c.getColumnIndex("usedOnlineContext")
            while (c.moveToNext()) {
                val isOnline = if (onlineCol >= 0) {
                    c.getInt(onlineCol) == 1
                } else {
                    c.getString(c.getColumnIndexOrThrow("answer")).contains("📚 Sources:")
                }
                results.add(
                    ChatMessageEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        sessionId = c.getLong(c.getColumnIndexOrThrow("sessionId")),
                        question = c.getString(c.getColumnIndexOrThrow("question")),
                        answer = c.getString(c.getColumnIndexOrThrow("answer")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
<<<<<<< HEAD
                        imagePath = c.getColumnIndex("imagePath").takeIf { it >= 0 }
                            ?.let { idx -> if (c.isNull(idx)) null else c.getString(idx) }
=======
                        usedOnlineContext = isOnline
>>>>>>> 6151f416595a7c84b23fc5a115b4ff341926ae5c
                    )
                )
            }
        }
        results
    }

    suspend fun updateMessage(message: ChatMessageEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("question", message.question)
            put("answer", message.answer)
            put("usedOnlineContext", if (message.usedOnlineContext) 1 else 0)
            put("timestamp", message.timestamp)
        }
        helper.writableDatabase.update("chat_messages", values, "id = ?", arrayOf(message.id.toString()))
    }

    private suspend fun refreshSessions() = withContext(Dispatchers.IO) {
        _allSessions.value = queryAllSessions()
    }

    private fun refreshSessionsBlockingSafe() {
        // Only used once at construction time (app startup) to seed the StateFlow with
        // whatever was already saved from a previous run.
        try {
            _allSessions.value = queryAllSessions()
        } catch (e: Exception) {
            // Table may not exist yet on a brand-new install at this exact instant - the
            // next insert's refreshSessions() call will populate it correctly regardless.
        }
    }

    private fun queryAllSessions(): List<ChatSessionEntity> {
        val results = mutableListOf<ChatSessionEntity>()
        helper.readableDatabase.rawQuery("SELECT * FROM chat_sessions ORDER BY timestamp DESC", null).use { c ->
            while (c.moveToNext()) {
                results.add(
                    ChatSessionEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        subject = c.getString(c.getColumnIndexOrThrow("subject")),
                        previewText = c.getString(c.getColumnIndexOrThrow("previewText")),
                        explanation = c.getString(c.getColumnIndexOrThrow("explanation")),
                        formula = c.getString(c.getColumnIndexOrThrow("formula")),
                        bulletPoints = c.getString(c.getColumnIndexOrThrow("bulletPoints")).toStringList(),
                        usedOnlineContext = c.getInt(c.getColumnIndexOrThrow("usedOnlineContext")) == 1,
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        imagePath = c.getColumnIndex("imagePath").takeIf { it >= 0 }
                            ?.let { idx -> if (c.isNull(idx)) null else c.getString(idx) }
                    )
                )
            }
        }
        return results
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

    fun studyCaptureDao(): StudyCaptureDao = _studyCaptureDao
    fun focusSignalsDao(): FocusSignalsDao = _focusSignalsDao
    fun chatDao(): ChatDao = _chatDao

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
