package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "ollama_models")
data class OllamaModel(
    @PrimaryKey val tag: String,
    val name: String,
    val size: String,
    val sizeBytes: Long,
    val version: String,
    val description: String,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val currentBytesDownloaded: Long = 0,
    val estimatedSpeed: String = ""
) : Serializable

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val title: String,
    val modelTag: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val messageId: Long = 0,
    val sessionId: Long,
    val sender: String, // "user" or "model" (assistant)
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "server_logs")
data class ServerLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: String,
    val level: String, // "INFO", "DEBUG", "ERROR"
    val message: String
) : Serializable
