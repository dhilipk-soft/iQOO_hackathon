package com.studylens.input.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.studylens.shared.AppEvent
import com.studylens.shared.NotificationEvent
import com.studylens.shared.StudyCapture
import com.studylens.shared.StudySession
import kotlinx.coroutines.flow.Flow

// ----------------------------------------------------
// Entities
// ----------------------------------------------------

@Entity(tableName = "study_captures")
data class StudyCaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val extractedText: String,
    val timestamp: Long
) {
    fun toDomain(): StudyCapture = StudyCapture(id, extractedText, timestamp)
}

@Entity(tableName = "app_events")
data class AppEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val eventType: Int,
    val timestamp: Long
) {
    fun toDomain(): AppEvent = AppEvent(id, packageName, eventType, timestamp)
}

@Entity(tableName = "notification_events")
data class NotificationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long
) {
    fun toDomain(): NotificationEvent = NotificationEvent(id, packageName, timestamp)
}

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val switchCount: Int,
    val notificationCount: Int
) {
    fun toDomain(): StudySession = StudySession(
        id = id,
        startTime = startTime,
        endTime = endTime,
        durationMs = durationMs,
        switchCount = switchCount,
        notificationCount = notificationCount
    )
}

// Chat persistence — real conversation history (ChatGPT-style), replacing the old
// in-memory-only seeded sessions.
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    val previewText: String,
    val explanation: String,
    val formula: String?,
    val bulletPoints: List<String>,
    val usedOnlineContext: Boolean,
    val timestamp: Long
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val question: String,
    val answer: String,
    val timestamp: Long
)

class Converters {
    @TypeConverter
    fun fromStringList(list: List<String>): String = list.joinToString("|||")

    @TypeConverter
    fun toStringList(data: String): List<String> = if (data.isBlank()) emptyList() else data.split("|||")
}

@Dao
interface ChatDao {
    @Insert
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessageEntity>
}

// ----------------------------------------------------
// DAOs
// ----------------------------------------------------

@Dao
interface StudyCaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capture: StudyCaptureEntity): Long

    @Query("SELECT * FROM study_captures ORDER BY timestamp DESC")
    fun getAllCaptures(): Flow<List<StudyCaptureEntity>>

    @Query("SELECT * FROM study_captures WHERE id = :id LIMIT 1")
    suspend fun getCaptureById(id: Long): StudyCaptureEntity?

    @Query("DELETE FROM study_captures WHERE id = :id")
    suspend fun deleteCapture(id: Long)
}

@Dao
interface FocusSignalsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppEvent(event: AppEventEntity): Long

    @Query("SELECT * FROM app_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getAppEvents(startTime: Long, endTime: Long): List<AppEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationEvent(event: NotificationEventEntity): Long

    @Query("SELECT * FROM notification_events WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getNotificationEvents(startTime: Long, endTime: Long): List<NotificationEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudySession(session: StudySessionEntity): Long

    @Query("SELECT * FROM study_sessions ORDER BY startTime DESC LIMIT :limit")
    fun getRecentStudySessions(limit: Int = 20): Flow<List<StudySessionEntity>>
}

// ----------------------------------------------------
// Database
// ----------------------------------------------------

@Database(
    entities = [
        StudyCaptureEntity::class,
        AppEventEntity::class,
        NotificationEventEntity::class,
        StudySessionEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studyCaptureDao(): StudyCaptureDao
    abstract fun focusSignalsDao(): FocusSignalsDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "studylens_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
