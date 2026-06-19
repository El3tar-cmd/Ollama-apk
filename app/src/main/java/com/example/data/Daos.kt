package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OllamaModelDao {
    @Query("SELECT * FROM ollama_models")
    fun getAllModelsFlow(): Flow<List<OllamaModel>>

    @Query("SELECT * FROM ollama_models WHERE tag = :tag LIMIT 1")
    suspend fun getModelByTag(tag: String): OllamaModel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateModel(model: OllamaModel)

    @Update
    suspend fun updateModel(model: OllamaModel)

    @Delete
    suspend fun deleteModel(model: OllamaModel)

    @Query("DELETE FROM ollama_models WHERE tag = :tag")
    suspend fun deleteByTag(tag: String)
}

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessionsFlow(): Flow<List<ChatSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Delete
    suspend fun deleteSession(session: ChatSession)

    @Query("DELETE FROM chat_sessions WHERE sessionId = :id")
    suspend fun deleteSessionById(id: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSessionFlow(sessionId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: Long): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)
}

@Dao
interface ServerLogDao {
    @Query("SELECT * FROM server_logs ORDER BY id DESC LIMIT 200")
    fun getRecentLogsFlow(): Flow<List<ServerLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ServerLog)

    @Query("DELETE FROM server_logs")
    suspend fun clearLogs()
}
