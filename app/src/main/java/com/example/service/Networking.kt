package com.example.service

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ==========================================
// 1. Ollama Network Integration Models
// ==========================================

@JsonClass(generateAdapter = true)
data class OllamaMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class OllamaChatRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<OllamaMessage>,
    @Json(name = "stream") val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class OllamaChatResponse(
    @Json(name = "model") val model: String,
    @Json(name = "message") val message: OllamaMessage?,
    @Json(name = "done") val done: Boolean
)

@JsonClass(generateAdapter = true)
data class OllamaTagModel(
    @Json(name = "name") val name: String,
    @Json(name = "size") val size: Long
)

@JsonClass(generateAdapter = true)
data class OllamaTagsResponse(
    @Json(name = "models") val models: List<OllamaTagModel>?
)

@JsonClass(generateAdapter = true)
data class OllamaDownloadRequest(
    @Json(name = "name") val name: String,
    @Json(name = "stream") val stream: Boolean = true
)

@JsonClass(generateAdapter = true)
data class OllamaDeleteRequest(
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class OllamaPullResponse(
    @Json(name = "status") val status: String? = null,
    @Json(name = "digest") val digest: String? = null,
    @Json(name = "total") val total: Long? = null,
    @Json(name = "completed") val completed: Long? = null
)

interface OllamaApi {
    @POST("api/chat")
    suspend fun chat(@Body request: OllamaChatRequest): OllamaChatResponse

    @GET("api/tags")
    suspend fun getInstalledModels(): OllamaTagsResponse

    @Streaming
    @POST("api/pull")
    suspend fun pullModel(@Body request: OllamaDownloadRequest): ResponseBody

    @HTTP(method = "DELETE", path = "api/delete", hasBody = true)
    suspend fun deleteModel(@Body request: OllamaDeleteRequest): Response<Unit>
}

// ==========================================
// 2. Gemini Sandbox AI API Models (REST fallback - Kept to prevent compile errors in any unused parts, cleaned of active uses)
// ==========================================

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>?
)

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): GeminiResponse
}

// ==========================================
// 3. Dynamic HTTP Clients & Builders
// ==========================================

object NetworkClient {
    private val baseOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES) // Increased timeout for heavy model pulls
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    // Dynamically builds helper Retrofit instance for standard Ollama services
    fun buildOllamaService(baseUrl: String): OllamaApi {
        // Enforce valid trailing slash
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return try {
            Retrofit.Builder()
                .baseUrl(url)
                .client(baseOkHttpClient)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(OllamaApi::class.java)
        } catch (e: Exception) {
            // Safe fallback dummy client to prevent instantiation crashes
            object : OllamaApi {
                override suspend fun chat(request: OllamaChatRequest) = 
                    OllamaChatResponse("", OllamaMessage("assistant", "خطأ في الاتصال بالسيرفر: ${e.localizedMessage}"), true)
                override suspend fun getInstalledModels() = OllamaTagsResponse(emptyList())
                override suspend fun pullModel(request: OllamaDownloadRequest): ResponseBody = throw e
                override suspend fun deleteModel(request: OllamaDeleteRequest): Response<Unit> = throw e
            }
        }
    }

    // Direct Gemini build singleton
    val geminiService: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(baseOkHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(GeminiApi::class.java)
    }
}
