package com.example.service

import android.content.Context
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

enum class DaemonStatus {
    IDLE,
    DOWNLOADING,
    EXTRACTING,
    STARTING,
    RUNNING,
    ERROR
}

class LocalOllamaDaemonManager private constructor() {

    companion object {
        @Volatile
        private var instance: LocalOllamaDaemonManager? = null

        fun getInstance(): LocalOllamaDaemonManager {
            return instance ?: synchronized(this) {
                instance ?: LocalOllamaDaemonManager().also { instance = it }
            }
        }
    }

    private val _status = MutableStateFlow(DaemonStatus.IDLE)
    val status: StateFlow<DaemonStatus> = _status.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private var process: Process? = null
    private var logJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun isBinaryInstalled(context: Context): Boolean {
        val binFile = File(getBinFolder(context), "bin/ollama")
        return binFile.exists() && binFile.canExecute()
    }

    private fun getBinFolder(context: Context): File {
        return File(context.filesDir, "ollama-core").apply {
            if (!exists()) mkdirs()
        }
    }

    fun deleteBinary(context: Context) {
        val binFolder = getBinFolder(context)
        if (binFolder.exists()) {
            binFolder.deleteRecursively()
        }
        _status.value = DaemonStatus.IDLE
    }

    suspend fun installBinary(
        context: Context,
        downloadUrl: String,
        onLog: suspend (String, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        _status.value = DaemonStatus.DOWNLOADING
        _downloadProgress.value = 0f
        
        onLog("INFO", "بدء عملية تحضير محرك Ollama المدمج...")

        val destFolder = getBinFolder(context)
        val binDir = File(destFolder, "bin")
        if (!binDir.exists()) binDir.mkdirs()
        val binFile = File(binDir, "ollama")

        val abi = when (Build.SUPPORTED_ABIS.firstOrNull()) {
            "arm64-v8a" -> "arm64-v8a"
            "armeabi-v7a" -> "armeabi-v7a"
            else -> "arm64-v8a"
        }

        // 1. Try Assets First
        val assetPath = "$abi/ollama"
        var assetExists = false
        try {
            context.assets.open(assetPath).close()
            assetExists = true
        } catch (e: Exception) {
            onLog("WARNING", "تنبيه: ملف Ollama غير موجود في أصول التطبيق (Assets). سيتم محاولة التحميل من السيرفر...")
        }

        if (assetExists) {
            onLog("INFO", "جاري استخراج Ollama من أصول التطبيق...")
            return@withContext tryExtractFromAssets(context, assetPath, binFile, abi, destFolder, onLog)
        } else {
            // 2. Fallback to Download
            onLog("INFO", "بدء تحميل المحرك من: $downloadUrl")
            return@withContext tryDownloadAndInstall(context, downloadUrl, destFolder, binFile, onLog)
        }
    }

    private suspend fun tryExtractFromAssets(
        context: Context,
        assetPath: String,
        binFile: File,
        abi: String,
        destFolder: File,
        onLog: suspend (String, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Copy libc++_shared.so from assets if present
            val libDir = File(destFolder, "lib")
            if (!libDir.exists()) libDir.mkdirs()
            val libFile = File(libDir, "libc++_shared.so")
            val libcxxAssetPath = "$abi/libc++_shared.so"
            try {
                context.assets.open(libcxxAssetPath).use { input ->
                    FileOutputStream(libFile).use { output ->
                        input.copyTo(output)
                    }
                }
                libFile.setReadable(true, false)
                onLog("INFO", "تم استخراج مكتبة الربط C++ بنجاح.")
            } catch (e: Exception) {
                onLog("WARNING", "تنبيه: libc++_shared.so مفقود، سيتم استخدام مكتبات النظام.")
            }

            _status.value = DaemonStatus.EXTRACTING
            context.assets.open(assetPath).use { input ->
                val totalLength = input.available().toLong()
                FileOutputStream(binFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (totalLength > 0) _downloadProgress.value = total.toFloat() / totalLength.toFloat()
                    }
                }
            }
            binFile.setExecutable(true, false)
            onLog("INFO", "تم تثبيت النسخة المدمجة بنجاح.")
            _status.value = DaemonStatus.IDLE
            true
        } catch (e: Exception) {
            onLog("ERROR", "فشل الاستخراج من الأصول: ${e.message}")
            false
        }
    }

    private suspend fun tryDownloadAndInstall(
        context: Context,
        url: String,
        destFolder: File,
        binFile: File,
        onLog: suspend (String, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val downloadFile = File(context.cacheDir, "ollama-temp.tgz")
            if (downloadFile.exists()) downloadFile.delete()

            val connection = URL(url).openConnection() as HttpURLConnection
            // إضافة User-Agent لتبدو العملية كطلب من متصفح حقيقي لتجنب الرفض 403
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
            connection.connect()
            if (connection.responseCode != 200) throw Exception("HTTP ${connection.responseCode} while downloading")

            val totalLength = connection.contentLength.toLong()
            connection.inputStream.use { input ->
                FileOutputStream(downloadFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    var lastUpdate = 0L
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (totalLength > 0) {
                            val progress = total.toFloat() / totalLength.toFloat()
                            _downloadProgress.value = progress
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                onLog("DEBUG", "تحميل المحرك: ${(progress * 100).toInt()}%...")
                                lastUpdate = now
                            }
                        }
                    }
                }
            }

            onLog("INFO", "تم التحميل بنجاح. جاري فك الضغط (Targz)...")
            _status.value = DaemonStatus.EXTRACTING
            extractTarGzippedFile(downloadFile, destFolder, onLog)
            
            // Check if binary was extracted to standard location or root of extraction
            // Ollama tgz usually has 'bin/ollama' or just 'ollama'
            val possibleBin = File(destFolder, "ollama")
            if (possibleBin.exists()) {
                val finalBinDir = File(destFolder, "bin")
                if (!finalBinDir.exists()) finalBinDir.mkdirs()
                possibleBin.renameTo(binFile)
            }

            if (binFile.exists()) {
                binFile.setExecutable(true, false)
                onLog("INFO", "تم تحميل وتثبيت المحرك بنجاح من المصدر الخارجي.")
                downloadFile.delete()
                _status.value = DaemonStatus.IDLE
                true
            } else {
                throw Exception("فشل العثور على الملف الثنائي بعد فك الضغط.")
            }
        } catch (e: Exception) {
            onLog("ERROR", "فشل التحميل أو التثبيت: ${e.message}")
            _status.value = DaemonStatus.ERROR
            false
        }
    }

    private suspend fun extractTarGzippedFile(tarGzFile: File, destDir: File, onLog: suspend (String, String) -> Unit) {
        if (!destDir.exists()) destDir.mkdirs()
        var filesCount = 0

        withContext(Dispatchers.IO) {
            GZIPInputStream(FileInputStream(tarGzFile)).use { gzis ->
                val buffer = ByteArray(8192)
                while (true) {
                    val header = ByteArray(512)
                    var read = 0
                    while (read < 512) {
                        val len = gzis.read(header, read, 512 - read)
                        if (len == -1) break
                        read += len
                    }
                    if (read < 512) break // EOF

                    if (header.all { it == 0.toByte() }) {
                        break // End of archive marker
                    }

                    // Extract file name
                    val nameBytes = header.sliceArray(0 until 100)
                    val nameEnd = nameBytes.indexOf(0)
                    val rawName = String(nameBytes, 0, if (nameEnd == -1) 100 else nameEnd).trim()
                    if (rawName.isEmpty()) continue

                    // Translate path if needed
                    val name = rawName.replace("../", "")

                    // Extract size (octal)
                    val sizeBytes = header.sliceArray(124 until 136)
                    val sizeStr = String(sizeBytes).trim { it <= ' ' || it == '\u0000' }
                    val size = if (sizeStr.isNotEmpty()) sizeStr.toLong(8) else 0L

                    // Extract type
                    val type = header[156]

                    val file = File(destDir, name)
                    if (type == '5'.toByte() || name.endsWith("/")) {
                        file.mkdirs()
                        continue
                    }

                    file.parentFile?.mkdirs()

                    FileOutputStream(file).use { fos ->
                        var remaining = size
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val len = gzis.read(buffer, 0, toRead)
                            if (len == -1) throw Exception("Unexpected end of archive stream.")
                            fos.write(buffer, 0, len)
                            remaining -= len
                        }
                    }

                    filesCount++
                    if (filesCount % 10 == 0) {
                        onLog("DEBUG", "استخراج الملف رقم $filesCount: $name...")
                    }

                    val pad = (512 - (size % 512)) % 512
                    if (pad > 0) {
                        var skipped = 0L
                        while (skipped < pad) {
                            val len = gzis.read(buffer, 0, (pad - skipped).toInt())
                            if (len == -1) break
                            skipped += len
                        }
                    }
                }
            }
        }
        onLog("INFO", "تم استخراج وإدراج $filesCount ملف من الحزمة بنجاح.")
    }

    fun startServer(context: Context, onLog: suspend (String, String) -> Unit): Boolean {
        if (_status.value == DaemonStatus.RUNNING) {
            return true
        }

        val binFolder = getBinFolder(context)
        val binFile = File(binFolder, "bin/ollama")

        if (!binFile.exists()) {
            _status.value = DaemonStatus.ERROR
            _errorMessage.value = "ملف البينري غير مثبت محلياً."
            scope.launch { onLog("ERROR", "فشل تشغيل السيرفر: ملف ollama الثنائي غير دقيق أو مفقود.") }
            return false
        }

        binFile.setExecutable(true, false)
        _status.value = DaemonStatus.STARTING

        try {
            val pb = ProcessBuilder(binFile.absolutePath, "serve")
            // Configure environmental variables for true sandbox compliance
            val env = pb.environment()
            env["HOME"] = context.filesDir.absolutePath
            env["OLLAMA_HOST"] = "127.0.0.1:11434"
            env["LD_LIBRARY_PATH"] = File(binFolder, "lib").absolutePath

            // Start native process
            process = pb.start()
            _status.value = DaemonStatus.RUNNING

            scope.launch { onLog("INFO", "تم تشغيل ديمون Ollama الأصلي بنجاح على نظام التشغيل.") }

            // Launch output log capturing
            logJob = scope.launch(Dispatchers.IO) {
                val reader = process?.inputStream?.bufferedReader()
                val errorReader = process?.errorStream?.bufferedReader()

                // Read output logs in parallel
                launch {
                    try {
                        reader?.forEachLine { line ->
                            val cleanLine = line.trim()
                            if (cleanLine.isNotEmpty()) {
                                launch { onLog("DEBUG", "[Ollama Binary] $cleanLine") }
                            }
                        }
                    } catch (e: Exception) {
                        // Stream closed
                    }
                }

                // Read error logs in parallel
                launch {
                    try {
                        errorReader?.forEachLine { line ->
                            val cleanLine = line.trim()
                            if (cleanLine.isNotEmpty()) {
                                if (cleanLine.contains("error", ignoreCase = true)) {
                                    launch { onLog("ERROR", "[Ollama Error] $cleanLine") }
                                } else {
                                    launch { onLog("INFO", "[Ollama Binary] $cleanLine") }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Stream closed
                    }
                }
            }

            return true
        } catch (e: Exception) {
            _status.value = DaemonStatus.ERROR
            _errorMessage.value = e.localizedMessage ?: "فشل تشغيل عملية البينري"
            scope.launch { onLog("ERROR", "فشل تشغيل سيرفر أولاما الأصلي: ${e.localizedMessage}") }
            stopServer(context) { _, _ -> }
            return false
        }
    }

    fun stopServer(context: Context, onLog: suspend (String, String) -> Unit) {
        scope.launch { onLog("WARNING", "تلقي طلب إغلاق سيرفر أولاما الأصلي...") }
        
        logJob?.cancel()
        logJob = null

        try {
            process?.destroy()
        } catch (e: Exception) {
            // Ignored
        }
        process = null

        killOrphandOllamaProcesses()

        _status.value = DaemonStatus.IDLE
        scope.launch { onLog("INFO", "تم إيقاف ديمون Ollama بنجاح وإخلاء منافذ الشبكة.") }
    }

    private fun killOrphandOllamaProcesses() {
        // Run ps & kill if possible on any lingering ollama instances
        try {
            val runtime = Runtime.getRuntime()
            runtime.exec("pkill ollama")
        } catch (e: Exception) {
            // pkill might not be available, try killall or manual search
            try {
                Runtime.getRuntime().exec("killall ollama")
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }
}
