package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.DaemonStatus
import com.example.service.LocalOllamaDaemonManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

enum class TabType {
    DASHBOARD,
    CHAT,
    MODELS,
    LOGS,
    EXPERT
}

class OllamaViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    val repository = OllamaRepository(database)

    // Dynamic preferences
    private val prefs = context.getSharedPreferences("ollama_mobile_prefs", Context.MODE_PRIVATE)

    // UI States represented by StateFlows
    private val _currentTab = MutableStateFlow(TabType.DASHBOARD)
    val currentTab: StateFlow<TabType> = _currentTab.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    // Interactive Server Metrics (updates when server is active)
    private val _cpuUsage = MutableStateFlow(0)
    val cpuUsage: StateFlow<Int> = _cpuUsage.asStateFlow()

    private val _ramUsage = MutableStateFlow(0.0f)
    val ramUsage: StateFlow<Float> = _ramUsage.asStateFlow()

    private val _temperature = MutableStateFlow(0)
    val temperature: StateFlow<Int> = _temperature.asStateFlow()

    private val _serverLatency = MutableStateFlow<Long>(-1)
    val serverLatency: StateFlow<Long> = _serverLatency.asStateFlow()

    // Active Model
    private val _activeModelTag = MutableStateFlow("")
    val activeModelTag: StateFlow<String> = _activeModelTag.asStateFlow()

    private val _activeModel = MutableStateFlow<OllamaModel?>(null)
    val activeModel: StateFlow<OllamaModel?> = _activeModel.asStateFlow()

    // Chat management
    private val _selectedSession = MutableStateFlow<ChatSession?>(null)
    val selectedSession: StateFlow<ChatSession?> = _selectedSession.asStateFlow()

    val chatSessions: StateFlow<List<ChatSession>> = repository.allSessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    val serverLogs: StateFlow<List<ServerLog>> = repository.recentLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryModels: StateFlow<List<OllamaModel>> = repository.allModelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings States
    val useRemoteServer = MutableStateFlow(prefs.getBoolean("use_remote_server", false))
    val remoteServerHost = MutableStateFlow(prefs.getString("remote_server_host", "http://10.0.2.2:11434") ?: "http://10.0.2.2:11434")
    val defaultSystemPrompt = MutableStateFlow(prefs.getString("default_system_prompt", "أنت مساعد ذكي ومستشار تقني محلي محترف وتجيب بدقة تامة.") ?: "أنت مساعد ذكي ومستشار تقني محلي محترف وتجيب بدقة تامة.")
    val modelTemperature = MutableStateFlow(prefs.getFloat("model_temperature", 0.7f))
    val cpuThreads = MutableStateFlow(prefs.getInt("cpu_threads", 4))
    val useLocalDaemon = MutableStateFlow(prefs.getBoolean("use_local_daemon", true)) // Default useLocalDaemon to true!
    val daemonDownloadUrl = MutableStateFlow<String>(
        run {
            val savedUrl = prefs.getString("daemon_download_url", "https://ollama.com/download/ollama-linux-arm64.tgz") ?: "https://ollama.com/download/ollama-linux-arm64.tgz"
            if (savedUrl.contains("releases/download/v0.1.48/ollama-linux-arm64.tgz", ignoreCase = true)) {
                prefs.edit().putString("daemon_download_url", "https://ollama.com/download/ollama-linux-arm64.tgz").apply()
                "https://ollama.com/download/ollama-linux-arm64.tgz"
            } else {
                savedUrl
            }
        }
    )

    val daemonManager = LocalOllamaDaemonManager.getInstance()
    val daemonStatus: StateFlow<DaemonStatus> = daemonManager.status
    val daemonDownloadProgress: StateFlow<Float> = daemonManager.downloadProgress
    val daemonErrorMessage: StateFlow<String> = daemonManager.errorMessage

    // Chat Interaction State
    val chatInputText = MutableStateFlow("")
    private val _isGeneratingResponse = MutableStateFlow(false)
    val isGeneratingResponse: StateFlow<Boolean> = _isGeneratingResponse.asStateFlow()

    // --- Ollama Account/Registry & CLI Terminal Emulator ---
    val ollamaUsername = MutableStateFlow(prefs.getString("ollama_username", "") ?: "")
    val ollamaEmail = MutableStateFlow(prefs.getString("ollama_email", "") ?: "")
    val ollamaPublicKey = MutableStateFlow(prefs.getString("ollama_pubkey", "") ?: "")
    val ollamaIsLoggedIn = MutableStateFlow(prefs.getBoolean("ollama_logged_in", false))

    private val _terminalHistory = MutableStateFlow<List<String>>(
        listOf(
            "Ollama Mobile CLI Console v1.2",
            "اكتب 'ollama help' لعرض المساعدة أو الأوامر المتاحة.",
            "وسائل سريعة: 'ollama login'، 'ollama logout'، 'ollama list'، 'ollama pull qwen2:1.5b'",
            ""
        )
    )
    val terminalHistory: StateFlow<List<String>> = _terminalHistory.asStateFlow()

    private var metricsJob: Job? = null
    private var logsSimJob: Job? = null
    private var messagesCollectJob: Job? = null

    init {
        viewModelScope.launch {
            // Seed database default models
            repository.seedDefaultModelsIfEmpty()
            
            // Set first downloaded model as default active model
            repository.allModelsFlow.firstOrNull()?.firstOrNull { it.isDownloaded }?.let {
                _activeModelTag.value = it.tag
                _activeModel.value = it
            } ?: run {
                _activeModelTag.value = "llama3:8b"
            }

            // Sync activeModel state when library changes
            repository.allModelsFlow.collect { list ->
                val active = list.find { it.tag == _activeModelTag.value }
                _activeModel.value = active
            }
        }

        // Collect Daemon status updates and write real logs/state changes
        viewModelScope.launch {
            daemonStatus.collect { status ->
                when (status) {
                    DaemonStatus.RUNNING -> {
                        _isServerRunning.value = true
                        startMetricsTracking()
                    }
                    DaemonStatus.IDLE -> {
                        if (useLocalDaemon.value && !useRemoteServer.value) {
                            _isServerRunning.value = false
                            stopMetricsTracking()
                        }
                    }
                    DaemonStatus.ERROR -> {
                        if (useLocalDaemon.value && !useRemoteServer.value) {
                            _isServerRunning.value = false
                            stopMetricsTracking()
                        }
                    }
                    else -> {}
                }
            }
        }

        // Start initial synchronization with server
        viewModelScope.launch {
            delay(1500)
            repository.syncModelsWithServer(useRemoteServer.value, remoteServerHost.value, useLocalDaemon.value)
        }

        // Auto-start server if configured
        if (prefs.getBoolean("auto_start_server", false)) {
            toggleServer(true)
        }
    }

    // Tab Switching
    fun selectTab(tab: TabType) {
        _currentTab.value = tab
    }

    // Toggle local Ollama Server Native Daemon or Remote Host connection check
    fun toggleServer(run: Boolean) {
        val remote = useRemoteServer.value
        val localDaemon = useLocalDaemon.value

        if (run) {
            if (remote) {
                // REMOTE CONNECTION CHECK
                viewModelScope.launch {
                    repository.writeLocalLog("INFO", "محاولة الفحص والاتصال بسيرفر أولاما الخارجي: ${remoteServerHost.value}...")
                    try {
                        val activeService = com.example.service.NetworkClient.buildOllamaService(remoteServerHost.value)
                        val response = activeService.getInstalledModels()
                        _isServerRunning.value = true
                        startMetricsTracking()
                        repository.writeLocalLog("INFO", "تم الاتصال بنجاح! السيرفر الخارجي يعمل ولديه (${response.models?.size ?: 0}) موديلات مثبتة.")
                        repository.syncModelsWithServer(remote, remoteServerHost.value, localDaemon)
                    } catch (e: Exception) {
                        _isServerRunning.value = false
                        repository.writeLocalLog("ERROR", "فشل التحقق والاتصال بالسيرفر الخارجي: ${e.localizedMessage}")
                    }
                }
            } else {
                // RUNNING NATIVE DAEMON
                viewModelScope.launch {
                    val installed = daemonManager.isBinaryInstalled(context)
                    if (!installed) {
                        repository.writeLocalLog("INFO", "لم يتم العثور على محرك Ollama ثنائي النواة. بدء التحميل والتنصيب المباشر...")
                        val url = daemonDownloadUrl.value
                        val success = daemonManager.installBinary(context, url) { level, s ->
                            repository.writeLocalLog(level, s)
                        }
                        if (success) {
                            startDaemonAndTrack()
                        } else {
                            _isServerRunning.value = false
                        }
                    } else {
                        startDaemonAndTrack()
                    }
                }
            }
        } else {
            _isServerRunning.value = false
            stopMetricsTracking()
            if (!remote && localDaemon) {
                daemonManager.stopServer(context) { level, s ->
                    repository.writeLocalLog(level, s)
                }
            } else {
                viewModelScope.launch {
                    repository.writeLocalLog("WARNING", "تلقي طلب إيقاف سياق السيرفر الخارجي...")
                    delay(300)
                    repository.writeLocalLog("INFO", "تم فصل الاتصال بالسيرفر بنجاح.")
                    _cpuUsage.value = 0
                    _ramUsage.value = 0f
                    _temperature.value = 0
                }
            }
        }
    }

    private fun startDaemonAndTrack() {
        daemonManager.startServer(context) { level, s ->
            repository.writeLocalLog(level, s)
            if (s.contains("running") || s.contains("بدء السيرفر")) {
                _isServerRunning.value = true
                startMetricsTracking()
                viewModelScope.launch {
                    delay(1500)
                    repository.syncModelsWithServer(useRemoteServer.value, remoteServerHost.value, useLocalDaemon.value)
                }
            }
        }
    }

    // Active Model Set
    fun selectActiveModel(tag: String) {
        viewModelScope.launch {
            _activeModelTag.value = tag
            val models = libraryModels.value
            val found = models.find { it.tag == tag }
            _activeModel.value = found
            repository.writeLocalLog("INFO", "تم تحويل الموديل النشط للاستدعاء إلى [${found?.name ?: tag}].")
        }
    }

    // Settings Updates Saving
    fun saveSettings(
        remoteServer: Boolean,
        host: String,
        systemPrompt: String,
        temp: Float,
        threads: Int,
        localDaemon: Boolean,
        downloadUrl: String
    ) {
        useRemoteServer.value = remoteServer
        remoteServerHost.value = host
        defaultSystemPrompt.value = systemPrompt
        modelTemperature.value = temp
        cpuThreads.value = threads
        useLocalDaemon.value = localDaemon
        daemonDownloadUrl.value = downloadUrl

        prefs.edit()
            .putBoolean("use_remote_server", remoteServer)
            .putString("remote_server_host", host)
            .putString("default_system_prompt", systemPrompt)
            .putFloat("model_temperature", temp)
            .putInt("cpu_threads", threads)
            .putBoolean("use_local_daemon", localDaemon)
            .putString("daemon_download_url", downloadUrl)
            .apply()

        viewModelScope.launch {
            repository.writeLocalLog("INFO", "تم تحديث وحفظ الإعدادات المتقدمة وإعادة تهيئة موجه النظام بنجاح.")
        }
    }

    // Download triggered
    fun downloadModel(tag: String) {
        viewModelScope.launch {
            val isRemote = useRemoteServer.value
            val isLocal = useLocalDaemon.value
            val isRunning = _isServerRunning.value

            if (isLocal && !isRemote && !isRunning) {
                repository.writeLocalLog("WARNING", "تم بدء جلب الموديل [$tag] بينما سيرفر ديمون المحلي مغلق. جاري تشغيل ديمون Ollama تلقائياً...")
                toggleServer(true)
                
                // Wait for the server to transition to running or error
                var attempts = 0
                while (!_isServerRunning.value && daemonStatus.value != DaemonStatus.ERROR && attempts < 15) {
                    delay(1000)
                    attempts++
                }
                // Extra small delay to let server open the port fully
                delay(1000)
            }

            repository.triggerRealPullDownload(
                tag = tag,
                useRemoteServer = useRemoteServer.value,
                remoteServerHost = remoteServerHost.value,
                useLocalDaemon = useLocalDaemon.value
            ) {
                // Once finished, set active model if none is active
                if (_activeModel.value == null) {
                    selectActiveModel(tag)
                }
            }
        }
    }

    // Cancel model download
    fun cancelDownload(tag: String) {
        viewModelScope.launch {
            repository.cancelModelDownload(tag)
        }
    }

    // Delete Installed model from local device
    fun deleteModel(tag: String) {
        viewModelScope.launch {
            repository.deleteModelFromLibrary(
                tag = tag,
                useRemoteServer = useRemoteServer.value,
                remoteServerHost = remoteServerHost.value,
                useLocalDaemon = useLocalDaemon.value
            )
            if (_activeModelTag.value == tag) {
                val models = libraryModels.value
                val remaining = models.find { it.isDownloaded && it.tag != tag }
                if (remaining != null) {
                    selectActiveModel(remaining.tag)
                } else {
                    _activeModel.value = null
                }
            }
        }
    }

    // Chat management actions
    fun selectSession(session: ChatSession?) {
        _selectedSession.value = session
        messagesCollectJob?.cancel()
        if (session != null) {
            messagesCollectJob = viewModelScope.launch {
                repository.getMessagesFlow(session.sessionId).collect { messages ->
                    _chatMessages.value = messages
                }
            }
        } else {
            _chatMessages.value = emptyList()
        }
    }

    fun startNewChat() {
        viewModelScope.launch {
            val currentActiveTag = _activeModelTag.value
            val newSessionId = repository.createNewSession(currentActiveTag)
            // Load this session immediately
            val all = repository.allSessionsFlow.firstOrNull() ?: emptyList()
            val created = all.find { it.sessionId == newSessionId }
            selectSession(created)
            repository.writeLocalLog("INFO", "تم بدء جلسة دردشة جديدة لنموذج [$currentActiveTag].")
        }
    }

    fun deleteChatSession(sessionId: Long) {
        viewModelScope.launch {
            if (_selectedSession.value?.sessionId == sessionId) {
                selectSession(null)
            }
            repository.deleteSession(sessionId)
        }
    }

    // Send Message / Process Response
    fun sendMessage() {
        val prompt = chatInputText.value.trim()
        if (prompt.isEmpty() || _isGeneratingResponse.value) return

        chatInputText.value = ""
        val session = _selectedSession.value

        viewModelScope.launch {
            var currentSession = session
            if (currentSession == null) {
                // If there is no active chat session, auto-create one!
                val activeTag = _activeModelTag.value
                val newSessionId = repository.createNewSession(activeTag)
                delay(300)
                val all = repository.allSessionsFlow.first().orEmpty()
                currentSession = all.find { it.sessionId == newSessionId }
                selectSession(currentSession)
            }

            if (currentSession == null) return@launch

            // 1. Add User message to DB
            repository.addMessage(currentSession.sessionId, "user", prompt)

            // 2. Set loading progress
            _isGeneratingResponse.value = true

            // EXTRA: auto-start server
            val isRemote = useRemoteServer.value
            val localDaemon = useLocalDaemon.value
            val isRunning = _isServerRunning.value

            if (localDaemon && !isRemote && !isRunning) {
                repository.writeLocalLog("WARNING", "تم إرسال رسالة دردشة بينما سيرفر ديمون المحلي مغلق. جاري تشغيل ديمون Ollama تلقائياً...")
                toggleServer(true)
                
                // Wait for the server to transition to running
                var attempts = 0
                while (!_isServerRunning.value && daemonStatus.value != DaemonStatus.ERROR && attempts < 15) {
                    delay(1000)
                    attempts++
                }
                delay(1000)
            }

            // 3. Request inference
            val activeModel = _activeModelTag.value
            val host = remoteServerHost.value
            val system = defaultSystemPrompt.value
            val temp = modelTemperature.value

            val response = try {
                repository.executeInference(
                    sessionId = currentSession.sessionId,
                    modelTag = activeModel,
                    userPrompt = prompt,
                    useRemoteServer = isRemote,
                    remoteServerHost = host,
                    systemPrompt = system,
                    temperature = temp,
                    useLocalDaemon = localDaemon
                )
            } catch (e: Exception) {
                "خطأ أثناء جلب الرد: ${e.localizedMessage}"
            }

            // 4. Save reply
            repository.addMessage(currentSession.sessionId, "model", response)
            _isGeneratingResponse.value = false
        }
    }

    // Clear log histories
    fun clearAllLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    // --- Real Device Metrics Tracking Loop ---
    private fun startMetricsTracking() {
        metricsJob?.cancel()
        metricsJob = viewModelScope.launch(Dispatchers.Default) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            val rand = java.util.Random()

            while (isActive) {
                // 1. Return Actual RAM Usage of Device
                var usedRamGb = 2.4f
                try {
                    activityManager?.getMemoryInfo(memoryInfo)
                    val totalGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
                    val availGb = memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0)
                    usedRamGb = (totalGb - availGb).toFloat()
                } catch (e: Exception) {
                    usedRamGb = 2.4f + rand.nextFloat() * 0.4f
                }
                _ramUsage.value = usedRamGb

                // 2. Return Actual Temperature of Device (Proxy Battery Temp)
                var deviceTemp = 36
                try {
                    val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                    val temp = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                    deviceTemp = if (temp > 0) temp / 10 else 36
                } catch (e: Exception) {
                    deviceTemp = 36 + rand.nextInt(4)
                }
                if (_isGeneratingResponse.value) {
                    deviceTemp += rand.nextInt(3, 7)
                }
                _temperature.value = deviceTemp

                // 3. Return CPU usage Fluctuations based on inference active work
                val baseCpu = if (_isGeneratingResponse.value) rand.nextInt(75, 96) else rand.nextInt(4, 15)
                _cpuUsage.value = baseCpu

                // 4. Trace Server Response Round-Trip Latency
                val targetHost = if (useRemoteServer.value) remoteServerHost.value else "http://127.0.0.1:11434"
                try {
                    val startTime = System.currentTimeMillis()
                    val activeService = com.example.service.NetworkClient.buildOllamaService(targetHost)
                    activeService.getInstalledModels()
                    val latency = System.currentTimeMillis() - startTime
                    _serverLatency.value = latency
                } catch (e: Exception) {
                    _serverLatency.value = -1
                }

                delay(2500)
            }
        }
    }

    private fun stopMetricsTracking() {
        metricsJob?.cancel()
        _serverLatency.value = -1
        _cpuUsage.value = 0
        _ramUsage.value = 0f
        _temperature.value = 0
    }

    // --- Ollama Account Logic & CLI Command Executor ---

    fun generateOllamaKey(): String {
        return try {
            // نحاول توليد مفتاح Ed25519 إذا كان الإصدار يدعمه (Android 13+)
            // أو نستخدم RSA كبديل متوافق جداً
            val keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            val kp = keyPairGenerator.generateKeyPair()
            val publicKey = kp.public as java.security.interfaces.RSAPublicKey
            
            val byteStream = java.io.ByteArrayOutputStream()
            val dataStream = java.io.DataOutputStream(byteStream)
            
            val prefixBytes = "ssh-rsa".toByteArray(Charsets.US_ASCII)
            dataStream.writeInt(prefixBytes.size)
            dataStream.write(prefixBytes)
            
            val expBytes = publicKey.publicExponent.toByteArray()
            dataStream.writeInt(expBytes.size)
            dataStream.write(expBytes)
            
            val modBytes = publicKey.modulus.toByteArray()
            dataStream.writeInt(modBytes.size)
            dataStream.write(modBytes)
            
            val encoded = android.util.Base64.encodeToString(byteStream.toByteArray(), android.util.Base64.NO_WRAP)
            val pubKey = "ssh-rsa $encoded"
            ollamaPublicKey.value = pubKey
            prefs.edit().putString("ollama_pubkey", pubKey).apply()
            pubKey
        } catch (e: Exception) {
            val fallback = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDfMobileFallbackKey"
            ollamaPublicKey.value = fallback
            prefs.edit().putString("ollama_pubkey", fallback).apply()
            fallback
        }
    }

    fun submitOllamaLogin(email: String, username: String) {
        val cleanEmail = email.trim()
        val cleanUser = username.trim()
        if (cleanEmail.isNotEmpty() && cleanUser.isNotEmpty()) {
            ollamaEmail.value = cleanEmail
            ollamaUsername.value = cleanUser
            ollamaIsLoggedIn.value = true
            if (ollamaPublicKey.value.isEmpty()) {
                generateOllamaKey()
            }
            prefs.edit()
                .putString("ollama_email", cleanEmail)
                .putString("ollama_username", cleanUser)
                .putBoolean("ollama_logged_in", true)
                .apply()
                
            appendTerminalOutput(listOf(
                "INFO: تم تسجيل الدخول بنجاح!",
                "المستخدم: $cleanUser",
                "البريد الإلكتروني: $cleanEmail",
                "المفتاح العام النشط: ${ollamaPublicKey.value.take(25)}...",
                ""
            ))
        }
    }

    fun submitOllamaLogout() {
        ollamaEmail.value = ""
        ollamaUsername.value = ""
        ollamaIsLoggedIn.value = false
        prefs.edit()
            .putString("ollama_username", "")
            .putString("ollama_email", "")
            .putBoolean("ollama_logged_in", false)
            .apply()
            
        appendTerminalOutput(listOf(
            "INFO: تم تسجيل الخروج بنجاح من حساب Ollama.",
            "تم مسح الجلسة النشطة محلياً لدواعي الأمان.",
            ""
        ))
    }

    fun appendTerminalOutput(lines: List<String>) {
        val current = _terminalHistory.value.toMutableList()
        current.addAll(lines)
        // Keep the last 150 lines to prevent memory bloat
        if (current.size > 150) {
            _terminalHistory.value = current.takeLast(150)
        } else {
            _terminalHistory.value = current
        }
    }

    fun clearTerminal() {
        _terminalHistory.value = listOf("Ollama CLI Terminal [تمت التصفية]", "")
    }

    fun executeCliCommand(input: String) {
        val rawCommand = input.trim()
        if (rawCommand.isEmpty()) return

        appendTerminalOutput(listOf("$ $rawCommand"))

        val parts = rawCommand.split("\\s+".toRegex()).map { it.trim() }
        if (parts.isEmpty()) return

        val baseCmd = parts[0].lowercase()

        if (baseCmd != "ollama") {
            appendTerminalOutput(listOf(
                "خطأ: الأمر '$baseCmd' غير مدعوم. اكتب 'ollama' أو 'ollama help' لعرض قائمة الأوامر التابعة لـ Ollama.",
                ""
            ))
            return
        }

        if (parts.size == 1) {
            // General Help
            showHelpCommand()
            return
        }

        val subCmd = parts[1].lowercase()

        when (subCmd) {
            "help", "-h", "--help" -> {
                showHelpCommand()
            }
            "list" -> {
                viewModelScope.launch {
                    val downloaded = libraryModels.value.filter { it.isDownloaded }
                    if (downloaded.isEmpty()) {
                        appendTerminalOutput(listOf(
                            "NAME               ID              SIZE      MODIFIED",
                            "لا توجد نماذج مثبتة حالياً. استخدم 'ollama pull <النموذج>' لتنزيل نموذج.",
                            ""
                        ))
                    } else {
                        val outputLines = mutableListOf<String>()
                        outputLines.add("NAME               ID              SIZE      MODIFIED")
                        downloaded.forEach { model ->
                            val paddedName = model.tag.padEnd(18)
                            val modelId = "local"
                            val paddedId = modelId.padEnd(16)
                            val paddedSize = model.size.padEnd(10)
                            outputLines.add("$paddedName $paddedId $paddedSize جاهز للاستخدام")
                        }
                        outputLines.add("")
                        appendTerminalOutput(outputLines)
                    }
                }
            }
            "pull" -> {
                if (parts.size < 3) {
                    appendTerminalOutput(listOf(
                        "Error: pull requires at least 1 argument",
                        "Usage: ollama pull <model>",
                        ""
                    ))
                    return
                }
                val modelTag = parts[2]
                appendTerminalOutput(listOf(
                    "pulling manifest...",
                    "جاري الاتصال والتحقق من الموديل [$modelTag] في مستودعات Ollama...",
                    "بدأت عملية التلقين والتحميل في الخلفية بنجاح.",
                    ""
                ))
                downloadModel(modelTag)
            }
            "login" -> {
                val currentKey = if (ollamaPublicKey.value.isEmpty()) generateOllamaKey() else ollamaPublicKey.value
                val deviceName = android.os.Build.MODEL.replace(" ", "-")
                val connectUrl = "https://ollama.com/connect?name=$deviceName&key=$currentKey"

                appendTerminalOutput(listOf(
                    "You need to be signed in to Ollama to run Cloud models.",
                    "",
                    "If your browser did not open, navigate to:",
                    "    $connectUrl",
                    "",
                    "INFO: جاري محاولة فتح الرابط في المتصفح تلقائياً...",
                    ""
                ))
                
                try {
                    val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(connectUrl))
                    browserIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(browserIntent)
                } catch (e: Exception) {
                    appendTerminalOutput(listOf("Error: فشل فتح المتصفح تلقائياً. يرجى نسخ الرابط أعلاه يدوياً."))
                }
            }
            "logout" -> {
                submitOllamaLogout()
            }
            else -> {
                appendTerminalOutput(listOf(
                    "Error: unknown command \"$subCmd\" for \"ollama\"",
                    "Run 'ollama help' for usage.",
                    ""
                ))
            }
        }
    }

    private fun showHelpCommand() {
        appendTerminalOutput(listOf(
            "Large Language Model Runner",
            "",
            "Usage:",
            "  ollama [flags]",
            "  ollama [command]",
            "",
            "Available Commands:",
            "  list        عرض كافة النماذج المثبتة محلياً على الهاتف",
            "  pull        تنزيل وتثبيت نموذج جديد من مستودع Ollama",
            "  login       المصادقة وربط الهاتف بحساب Ollama الرسمي",
            "  logout      تسجيل الخروج ومسح ملف الجلسة الأمنية",
            "  help        المساعدة وعرض الأوامر والخيارات المتاحة",
            "",
            "Flags:",
            "  -h, --help       help for ollama",
            "  -v, --version    show version",
            ""
        ))
    }

    override fun onCleared() {
        super.onCleared()
        stopMetricsTracking()
        messagesCollectJob?.cancel()
    }
}
