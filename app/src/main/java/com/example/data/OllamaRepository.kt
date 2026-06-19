package com.example.data

import com.example.BuildConfig
import com.example.service.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class OllamaRepository(private val database: AppDatabase) {

    private val modelDao = database.modelDao
    private val sessionDao = database.chatSessionDao
    private val messageDao = database.chatMessageDao
    private val logDao = database.serverLogDao

    // Reactive database streams
    val allModelsFlow: Flow<List<OllamaModel>> = modelDao.getAllModelsFlow()
    val allSessionsFlow: Flow<List<ChatSession>> = sessionDao.getAllSessionsFlow()
    val recentLogsFlow: Flow<List<ServerLog>> = logDao.getRecentLogsFlow()

    fun getMessagesFlow(sessionId: Long): Flow<List<ChatMessage>> {
        return messageDao.getMessagesForSessionFlow(sessionId)
    }

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Initializer to seed default models if database is empty
    suspend fun seedDefaultModelsIfEmpty() = withContext(Dispatchers.IO) {
        val count = database.openHelper.writableDatabase.compileStatement("SELECT COUNT(*) FROM ollama_models").simpleQueryForLong()
        if (count == 0L) {
            val defaultModels = listOf(
                OllamaModel(
                    tag = "llama3:8b",
                    name = "Llama-3 (8B)",
                    size = "4.7 GB",
                    sizeBytes = 4700000000L,
                    version = "v3.0.2",
                    description = "الموديل الأساسي القوي من Meta لمهام التفكير المتعددة واللغة والبرمجة.",
                    isDownloaded = false // NOT pre-installed! Requires real Pullman / Pulling!
                ),
                OllamaModel(
                    tag = "phi3:mini",
                    name = "Phi-3 Mini (3.8B)",
                    size = "2.3 GB",
                    sizeBytes = 2300000000L,
                    version = "v1.4.0",
                    description = "نموذج خفيف وذكي للغاية وممتاز للأجهزة المستقلة من Microsoft.",
                    isDownloaded = false
                ),
                OllamaModel(
                    tag = "mistral:7b",
                    name = "Mistral (7B)",
                    size = "4.1 GB",
                    sizeBytes = 4100000000L,
                    version = "v0.2.0",
                    description = "موديل فرنسي رائد مفتوح المصدر يتميز بالسرعة الفائقة وصياغة النصوص الممتازة.",
                    isDownloaded = false
                ),
                OllamaModel(
                    tag = "gemma2:2b",
                    name = "Gemma-2 (2B)",
                    size = "1.6 GB",
                    sizeBytes = 1600000000L,
                    version = "v2.0.0",
                    description = "جما 2 الموديل الخفيف المتصدر من Google المحسن للعمل بذكاء فائق.",
                    isDownloaded = false
                ),
                OllamaModel(
                    tag = "qwen2:1.5b",
                    name = "Qwen-2 (1.5B)",
                    size = "900 MB",
                    sizeBytes = 900000000L,
                    version = "v2.0.0",
                    description = "أصغر موديل سريع يدعم اللغات الشرقية والعربية بجودة ممتازة وسرعة معالجة مذهلة.",
                    isDownloaded = false
                )
            )
            for (model in defaultModels) {
                modelDao.insertOrUpdateModel(model)
            }
            writeLocalLog("INFO", "تمت تهيئة قاعدة بيانات الموديلات المقترحة الجاهزة للتنزيل.")
        }
    }

    // Logging helpers
    suspend fun writeLocalLog(level: String, message: String) = withContext(Dispatchers.IO) {
        val timeString = dateFormat.format(Date())
        logDao.insertLog(ServerLog(timestamp = timeString, level = level, message = message))
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        logDao.clearLogs()
    }

    // Chat management
    suspend fun createNewSession(modelTag: String): Long = withContext(Dispatchers.IO) {
        val cleanTag = modelTag.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val sessionTitle = "محادثة $cleanTag - ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())}"
        sessionDao.insertSession(ChatSession(title = sessionTitle, modelTag = modelTag))
    }

    suspend fun addMessage(sessionId: Long, sender: String, text: String): Long = withContext(Dispatchers.IO) {
        if (getMessagesFlow(sessionId).first().isEmpty() && sender == "user") {
            // Update title of the chat dynamically based on the first prompt
            val title = if (text.length > 25) text.take(25) + "..." else text
            val currentSession = sessionDao.getAllSessionsFlow().first().find { it.sessionId == sessionId }
            if (currentSession != null) {
                sessionDao.insertSession(currentSession.copy(title = title))
            }
        }
        messageDao.insertMessage(ChatMessage(sessionId = sessionId, sender = sender, text = text))
    }

    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        sessionDao.deleteSessionById(sessionId)
        messageDao.deleteMessagesForSession(sessionId)
    }

    // Dynamic Server Model Synchronizer (Real OLLAMA list sync)
    suspend fun syncModelsWithServer(
        useRemoteServer: Boolean,
        remoteServerHost: String,
        useLocalDaemon: Boolean
    ) = withContext(Dispatchers.IO) {
        val targetHost = if (useRemoteServer) remoteServerHost else "http://127.0.0.1:11434"
        try {
            val api = NetworkClient.buildOllamaService(targetHost)
            val response = api.getInstalledModels()
            val installedTags = response.models?.map { it.name } ?: emptyList()
            val normalizedInstalledTags = installedTags.map { it.lowercase().trim() }
            
            val dbModels = modelDao.getAllModelsFlow().first()
            
            for (dbModel in dbModels) {
                if (dbModel.isDownloading) continue

                val isInstalled = normalizedInstalledTags.any {
                    it == dbModel.tag.lowercase() || 
                    it == "${dbModel.tag.lowercase()}:latest" ||
                    dbModel.tag.lowercase() == "${it}:latest" ||
                    it.replace(":latest", "") == dbModel.tag.lowercase()
                }

                var sizeStr = dbModel.size
                var sizeB = dbModel.sizeBytes
                val matchingTag = installedTags.find {
                    it.lowercase() == dbModel.tag.lowercase() ||
                    it.lowercase() == "${dbModel.tag.lowercase()}:latest" ||
                    dbModel.tag.lowercase() == "${it.lowercase()}:latest" ||
                    it.lowercase().replace(":latest", "") == dbModel.tag.lowercase()
                }
                if (matchingTag != null) {
                    val actualModel = response.models?.find { it.name == matchingTag }
                    if (actualModel != null) {
                        sizeB = actualModel.size
                        sizeStr = formatBytesToSize(sizeB)
                    }
                }

                if (dbModel.isDownloaded != isInstalled || dbModel.sizeBytes != sizeB) {
                    modelDao.insertOrUpdateModel(
                        dbModel.copy(
                            isDownloaded = isInstalled,
                            size = sizeStr,
                            sizeBytes = sizeB,
                            downloadProgress = if (isInstalled) 1.0f else 0.0f
                        )
                    )
                }
            }

            // Auto-Discovery of Models Pulled on Server externally
            for (tag in installedTags) {
                val cleanTag = tag.lowercase().trim()
                val matchInDb = dbModels.any {
                    it.tag.lowercase() == cleanTag ||
                    "${it.tag.lowercase()}:latest" == cleanTag ||
                    it.tag.lowercase() == "$cleanTag:latest" ||
                    cleanTag.replace(":latest", "") == it.tag.lowercase()
                }
                if (!matchInDb) {
                    val actualModel = response.models?.find { it.name == tag }
                    val sizeB = actualModel?.size ?: 0L
                    val sizeStr = formatBytesToSize(sizeB)
                    val baseName = tag.substringBefore(":").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    val tagAfter = tag.substringAfter(":", "").uppercase()
                    val displayName = if (tagAfter.isNotEmpty()) "$baseName ($tagAfter)" else baseName
                    modelDao.insertOrUpdateModel(
                        OllamaModel(
                            tag = tag,
                            name = displayName,
                            size = sizeStr,
                            sizeBytes = sizeB,
                            version = "v1.0",
                            description = "نموذج مستورد ومكتشف تلقائياً من خادم أولاما.",
                            isDownloaded = true
                        )
                    )
                }
            }
            writeLocalLog("INFO", "تمت مزامنة قائمة الموديلات والتحقق من النماذج المثبتة بالسيرفر بنجاح.")
        } catch (e: Exception) {
            writeLocalLog("DEBUG", "فشل المزامنة مع السيرفر: ${e.localizedMessage}")
        }
    }

    private fun formatBytesToSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    // Real API-based delete model
    suspend fun deleteModelFromLibrary(
        tag: String,
        useRemoteServer: Boolean,
        remoteServerHost: String,
        useLocalDaemon: Boolean
    ) = withContext(Dispatchers.IO) {
        val model = modelDao.getModelByTag(tag)
        val targetHost = if (useRemoteServer) remoteServerHost else "http://127.0.0.1:11434"
        writeLocalLog("INFO", "طلب إزالة وحذف الموديل [$tag] من السيرفر [$targetHost]...")
        try {
            val activeService = NetworkClient.buildOllamaService(targetHost)
            val response = activeService.deleteModel(OllamaDeleteRequest(name = tag))
            if (response.isSuccessful) {
                writeLocalLog("INFO", "تم حذف الموديل [$tag] بنجاح من خادم أولاما.")
                if (model != null) {
                    modelDao.insertOrUpdateModel(
                        model.copy(
                            isDownloaded = false,
                            downloadProgress = 0f,
                            currentBytesDownloaded = 0L,
                            estimatedSpeed = ""
                        )
                    )
                } else {
                    modelDao.deleteByTag(tag)
                }
            } else {
                writeLocalLog("ERROR", "فشل طلب الحذف من خادم أولاما: كود الحالة ${response.code()}")
            }
        } catch (e: Exception) {
            writeLocalLog("ERROR", "فشل الاتصال لتنفيذ الحذف الرقمي: ${e.localizedMessage}")
        }
        syncModelsWithServer(useRemoteServer, remoteServerHost, useLocalDaemon)
    }

    // Real Streaming "ollama pull" download task
    fun triggerRealPullDownload(
        tag: String,
        useRemoteServer: Boolean,
        remoteServerHost: String,
        useLocalDaemon: Boolean,
        onFinished: () -> Unit = {}
    ) = CoroutineScope(Dispatchers.IO).launch {
        writeLocalLog("INFO", "تهيئة طلب جلب وتحميل الموديل [$tag] من مستودع Ollama Registry الحقيقي...")
        
        var model = modelDao.getModelByTag(tag)
        if (model == null) {
            val baseName = tag.substringBefore(":").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            val tagAfter = tag.substringAfter(":", "").uppercase()
            val displayName = if (tagAfter.isNotEmpty()) "$baseName ($tagAfter)" else baseName
            model = OllamaModel(
                tag = tag,
                name = displayName,
                size = "جار الفحص...",
                sizeBytes = 0L,
                version = "v1.0",
                description = "نموذج مستورد ومحمل عبر منفذ pull.",
                isDownloaded = false,
                isDownloading = true
            )
            modelDao.insertOrUpdateModel(model)
        } else {
            modelDao.insertOrUpdateModel(model.copy(isDownloading = true, downloadProgress = 0f, estimatedSpeed = "تجهيز الاتصال..."))
        }

        val targetHost = if (useRemoteServer) remoteServerHost else "http://127.0.0.1:11434"
        try {
            val activeService = NetworkClient.buildOllamaService(targetHost)
            writeLocalLog("DEBUG", "الاتصال بـ $targetHost/api/pull لبدء سحب الموديل [$tag]...")
            
            val responseBody = activeService.pullModel(OllamaDownloadRequest(name = tag, stream = true))
            val reader = responseBody.charStream().buffered()
            var line: String? = null
            
            val moshi = com.squareup.moshi.Moshi.Builder().build()
            val adapter = moshi.adapter(OllamaPullResponse::class.java)

            var lastUpdateMillis = 0L
            val startTime = System.currentTimeMillis()

            while (isActive && reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty()) continue

                try {
                    val pullUpdate = adapter.fromJson(currentLine)
                    if (pullUpdate != null) {
                        val status = pullUpdate.status ?: "جلب البيانات..."
                        val completed = pullUpdate.completed ?: 0L
                        val total = pullUpdate.total ?: 0L
                        
                        var progress = 0f
                        var speedString = ""
                        var sizeText = model.size

                        if (total > 0) {
                            progress = completed.toFloat() / total.toFloat()
                            sizeText = formatBytesToSize(total)
                            
                            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                            if (elapsedSeconds > 0.5 && completed > 0) {
                                val bytesPerSecond = completed / elapsedSeconds
                                speedString = formatBytesToSize(bytesPerSecond.toLong()) + "/s"
                            }
                        }

                        val now = System.currentTimeMillis()
                        val isFinal = progress >= 0.999f || status.lowercase() == "success"
                        
                        if (now - lastUpdateMillis > 1500 || isFinal) {
                            lastUpdateMillis = now
                            
                            val currentModelInDb = modelDao.getModelByTag(tag)
                            if (currentModelInDb == null || !currentModelInDb.isDownloading) {
                                writeLocalLog("WARNING", "تم إيقاف أو إلغاء تنزيل الموديل [$tag]")
                                break
                            }

                            modelDao.insertOrUpdateModel(
                                currentModelInDb.copy(
                                    isDownloading = !isFinal,
                                    isDownloaded = isFinal,
                                    downloadProgress = progress,
                                    currentBytesDownloaded = completed,
                                    size = sizeText,
                                    sizeBytes = total,
                                    estimatedSpeed = speedString
                                )
                            )

                            if (isFinal) {
                                writeLocalLog("INFO", "نجاح! تم جلب وتثبيت الموديل [$tag] بالكامل على السيرفر.")
                                break
                            } else {
                                val percentage = (progress * 100).toInt()
                                writeLocalLog("DEBUG", "سحب الموديل [$tag]: $status ($percentage%) بسرعة $speedString")
                            }
                        }
                    }
                } catch (e: Exception) {
                    writeLocalLog("DEBUG", "سطر الرد غير منسق: ${e.localizedMessage}")
                }
            }

            syncModelsWithServer(useRemoteServer, remoteServerHost, useLocalDaemon)
            onFinished()

        } catch (e: Exception) {
            writeLocalLog("ERROR", "فشل سحب وتحميل الموديل [$tag]: ${e.localizedMessage}")
            val currentModelInDb = modelDao.getModelByTag(tag)
            if (currentModelInDb != null) {
                modelDao.insertOrUpdateModel(
                    currentModelInDb.copy(
                        isDownloading = false,
                        isDownloaded = false,
                        downloadProgress = 0f,
                        estimatedSpeed = ""
                    )
                )
            }
        }
    }

    // Cancel model download setup
    suspend fun cancelModelDownload(tag: String) = withContext(Dispatchers.IO) {
        val model = modelDao.getModelByTag(tag)
        if (model != null && model.isDownloading) {
            modelDao.insertOrUpdateModel(
                model.copy(
                    isDownloading = false,
                    downloadProgress = 0f,
                    currentBytesDownloaded = 0L,
                    estimatedSpeed = ""
                )
            )
            writeLocalLog("ERROR", "ألغيت عملية تنزيل الموديل [${model.name}] بنجاح.")
        }
    }

    // Direct Inference Router - Handles remote network server OR real local background daemon. Complete removal of Mock simulator + Gemini.
    suspend fun executeInference(
        sessionId: Long,
        modelTag: String,
        userPrompt: String,
        useRemoteServer: Boolean,
        remoteServerHost: String,
        systemPrompt: String,
        temperature: Float,
        useLocalDaemon: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val daemonManager = LocalOllamaDaemonManager.getInstance()
        val isDaemonRunning = daemonManager.status.value == DaemonStatus.RUNNING

        val targetHost = if (useRemoteServer) remoteServerHost else "http://127.0.0.1:11434"
        val serverKindDescription = if (useRemoteServer) "الخارجي ($remoteServerHost)" else "المحلي الداخلي (127.0.0.1:11434)"
        
        writeLocalLog("DEBUG", "إرسال طلب استدلال حقيقي إلى سيرفر أولاما [${targetHost}]...")
        try {
            val messages = messageDao.getMessagesForSession(sessionId).map {
                OllamaMessage(
                    role = if (it.sender == "user") "user" else "assistant",
                    content = it.text
                )
            }
            val ollamaApi = NetworkClient.buildOllamaService(targetHost)
            val response = ollamaApi.chat(OllamaChatRequest(model = modelTag, messages = messages, stream = false))
            writeLocalLog("INFO", "استجابة ناجحة من السيرفر أولاما لنموذج [$modelTag]")
            response.message?.content ?: "لم يتم استرجاع أي محتوى من نموذج أولاما"
        } catch (e: Exception) {
            writeLocalLog("ERROR", "فشل استدعاء سيرفر أولاما: ${e.localizedMessage}")
            
            val solutionTip = if (useLocalDaemon) {
                "تأكد أولاً من تفعيل زر تشغيل السيرفر من الواجهة الرئيسية وتحميل الموديل [$modelTag] بشكل حقيقي عبر تبويب الموديلات."
            } else {
                "تأكد من إمكانية الاتصال برابط السيرفر الخارجي $remoteServerHost، وتفعيل إعدادات الـ CORS فيه (تشغيل السيرفر بالمتغير OLLAMA_ORIGINS=*)."
            }
            "فشل الاتصال بسيرفر أولاما $serverKindDescription.\n\n$solutionTip\n\nتفاصيل الخطأ: ${e.localizedMessage}"
        }
    }
}
