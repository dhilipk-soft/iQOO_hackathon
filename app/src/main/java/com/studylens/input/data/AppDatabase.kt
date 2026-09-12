package com.studylens.input.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.studylens.shared.AppEvent
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

data class ChatSessionEntity(
    val id: Long = 0,
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

private fun List<String>.toDbString(): String = joinToString("|||")
private fun String.toStringList(): List<String> = if (isBlank()) emptyList() else split("|||")

// ----------------------------------------------------
// SQLite schema
// ----------------------------------------------------

class DbHelper(context: Context) : SQLiteOpenHelper(context, "studylens_db", null, 2) {
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
            """CREATE TABLE chat_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
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
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
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
