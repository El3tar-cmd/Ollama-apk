package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OllamaModel
import com.example.data.ServerLog
import com.example.service.DaemonStatus
import com.example.viewmodel.OllamaViewModel
import com.example.viewmodel.TabType

@Composable
fun DashboardScreen(
    viewModel: OllamaViewModel,
    modifier: Modifier = Modifier
) {
    val isRunning by viewModel.isServerRunning.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    
    val cpu by viewModel.cpuUsage.collectAsState()
    val ram by viewModel.ramUsage.collectAsState()
    val temp by viewModel.temperature.collectAsState()
    
    val models by viewModel.libraryModels.collectAsState()
    val logs by viewModel.serverLogs.collectAsState()
    
    val downloadingModels = remember(models) {
        models.filter { it.isDownloading }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        // 1. Server Control Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server_control_card")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVE MODEL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeModel?.name ?: "No Active Model",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (activeModel != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (viewModel.useRemoteServer.collectAsState().value) "Remote Host: ${viewModel.remoteServerHost.collectAsState().value}" else "Local Port: 11434",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val isRemote by viewModel.useRemoteServer.collectAsState()
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isRemote) Color(0xFFE8F5E9) else Color(0xFFE0F2F1),
                                border = BorderStroke(1.dp, if (isRemote) Color(0xFF81C784) else Color(0xFF80CBC4)),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isRemote) Color(0xFF4CAF50) else Color(0xFF009688), shape = CircleShape)
                                    )
                                    Text(
                                        text = if (isRemote) "الاتصال بسيرفر Ollama الخارجي" else "محرك أولاما المحلي المستقل",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRemote) Color(0xFF2E7D32) else Color(0xFF00695C)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleServer(!isRunning) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                            modifier = Modifier
                                .testTag("toggle_server_button")
                                .align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = if (isRunning) "STOP" else "START",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    val daemonStatus by viewModel.daemonStatus.collectAsState()
                    val downloadProgress by viewModel.daemonDownloadProgress.collectAsState()

                    if (daemonStatus == DaemonStatus.DOWNLOADING || daemonStatus == DaemonStatus.EXTRACTING) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (daemonStatus == DaemonStatus.DOWNLOADING) "تحميل ديمون أولاما المباشر..." else "استخراج الملفات الثنائية...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(downloadProgress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

                    // Hardware Monitor Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricItem(
                            label = "CPU",
                            value = if (isRunning) "$cpu%" else "--",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                .align(Alignment.CenterVertically)
                        )
                        MetricItem(
                            label = "RAM",
                            value = if (isRunning) String.format("%.1f GB", ram) else "--",
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                .align(Alignment.CenterVertically)
                        )
                        MetricItem(
                            label = "TEMP",
                            value = if (isRunning) "$temp°C" else "--",
                            color = if (temp > 65) Color(0xFFE53935) else Color(0xFF43A047),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Active Downloading Panel
        if (downloadingModels.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "جارٍ التحميل والتهيئة في الخلفية",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    downloadingModels.forEach { model ->
                        DownloadProgressCard(model = model, onCancel = { viewModel.cancelDownload(model.tag) })
                    }
                }
            }
        }

        // 3. Library Quick Access Summary
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مكتبة النماذج المحلية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = { viewModel.selectTab(TabType.MODELS) },
                    modifier = Modifier.testTag("dashboard_view_all_models_btn")
                ) {
                    Text(
                        text = "عرض الكل",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        val downloadedModels = models.filter { it.isDownloaded }
        if (downloadedModels.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "لا توجد نماذج محملة بعد محلياً.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(downloadedModels.take(2), key = { it.tag }) { model ->
                ModelDashboardItem(
                    model = model,
                    isActive = activeModel?.tag == model.tag,
                    onClick = { viewModel.selectActiveModel(model.tag) }
                )
            }
        }

        // 4. Ollama CLI Terminal & Account Center
        item {
            var activeDashboardTab by remember { mutableStateOf(0) } // 0: CLI, 1: Account, 2: Server Logs
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "مركز تحكم وأوامر Ollama",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Tab Headers
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TabButton(
                            text = "موجه الأوامر (CLI)",
                            isActive = activeDashboardTab == 0,
                            onClick = { activeDashboardTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        TabButton(
                            text = "حساب Ollama",
                            isActive = activeDashboardTab == 1,
                            onClick = { activeDashboardTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                        TabButton(
                            text = "سجل السيرفر",
                            isActive = activeDashboardTab == 2,
                            onClick = { activeDashboardTab = 2 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Tab Contents
                when (activeDashboardTab) {
                    0 -> {
                        OllamaCliConsole(viewModel = viewModel)
                    }
                    1 -> {
                        OllamaAccountPanel(viewModel = viewModel)
                    }
                    2 -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1A1C1E))
                                .border(1.dp, Color(0xFF2C2F33), RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "OLLAMA ENGINE LOGS",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                    TextButton(
                                        onClick = { viewModel.selectTab(TabType.LOGS) },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("كامل السجلات", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                if (logs.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "لا توجد سجلات حالية. السيرفر متوقف أو خامد.",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF8A8D90),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(logs.take(6)) { log ->
                                            val textColor = when (log.level) {
                                                "ERROR" -> Color(0xFFEF5350)
                                                "WARNING" -> Color(0xFFFFB74D)
                                                "DEBUG" -> Color(0xFF64B5F6)
                                                else -> Color(0xFF4CFA72)
                                            }
                                            Text(
                                                text = "[${log.timestamp}] ${log.level}: ${log.message}",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun DownloadProgressCard(
    model: OllamaModel,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF1FF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "تحميل",
                        tint = Color(0xFF0061A4)
                    )
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF001D36)
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إلغاء التنزيل",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = model.downloadProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(100.dp)),
                color = Color(0xFF0061A4),
                trackColor = Color.White.copy(alpha = 0.6f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "سرعة التحميل: ${model.estimatedSpeed}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF001D36).copy(alpha = 0.7f)
                )
                Text(
                    text = "${(model.downloadProgress * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0061A4)
                )
            }
        }
    }
}

@Composable
fun ModelDashboardItem(
    model: OllamaModel,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${model.version} • ${model.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "نشط حالياً",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }
    }
}

@Composable
fun TabButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun OllamaCliConsole(
    viewModel: OllamaViewModel
) {
    val terminalLines by viewModel.terminalHistory.collectAsState()
    var inputBuffer by remember { mutableStateOf("") }
    val terminalListState = rememberLazyListState()

    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            terminalListState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Output Window
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F1011))
                .border(2.dp, Color(0xFF232529), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            LazyColumn(
                state = terminalListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(terminalLines) { line ->
                    val color = when {
                        line.startsWith("$") -> Color(0xFF64B5F6)
                        line.startsWith("Error") || line.startsWith("خطأ") -> Color(0xFFEF5350)
                        line.startsWith("INFO") || line.startsWith("نجاح") -> Color(0xFF81C784)
                        else -> Color(0xFFE0E0E0)
                    }
                    Text(
                        text = line,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = color
                    )
                }
            }
        }

        // Input row + Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputBuffer,
                onValueChange = { inputBuffer = it },
                placeholder = { Text("مثال: ollama list", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                modifier = Modifier.weight(1f)
            )

            // Clear Button
            IconButton(
                onClick = { viewModel.clearTerminal() },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "تفريغ الشاشة",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Execute Button
            IconButton(
                onClick = {
                    val cmd = inputBuffer.trim()
                    if (cmd.isNotEmpty()) {
                        viewModel.executeCliCommand(cmd)
                        inputBuffer = ""
                    }
                },
                enabled = inputBuffer.isNotBlank(),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (inputBuffer.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "تنفيذ الأمر",
                    tint = if (inputBuffer.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                )
            }
        }

        // Quick Command chips
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "أوامر سريعة:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Pair("ollama list", "عرض النماذج"),
                    Pair("ollama help", "المساعدة"),
                    Pair("ollama login", "تسجيل الدخول"),
                    Pair("ollama pull qwen2:1.5b", "سحب qwen")
                ).forEach { (command, label) ->
                    SuggestionChip(
                        onClick = {
                            viewModel.executeCliCommand(command)
                        },
                        label = { Text(label, fontSize = 10.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OllamaAccountPanel(
    viewModel: OllamaViewModel
) {
    val isLoggedIn by viewModel.ollamaIsLoggedIn.collectAsState()
    val username by viewModel.ollamaUsername.collectAsState()
    val email by viewModel.ollamaEmail.collectAsState()
    val publicKey by viewModel.ollamaPublicKey.collectAsState()

    var inputEmail by remember { mutableStateOf("") }
    var inputUser by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoggedIn) {
                // Logged In Status View
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "تم تسجيل الدخول",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "متصل بـ Ollama Registry",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "المستخدم: $username • $email",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.submitOllamaLogout() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("خروج", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                // Login Form View
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "تسجيل الدخول وربط الحساب محلياً",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputUser,
                            onValueChange = { inputUser = it },
                            label = { Text("اسم المستخدم", fontSize = 10.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = inputEmail,
                            onValueChange = { inputEmail = it },
                            label = { Text("البريد الإلكتروني", fontSize = 10.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            if (inputEmail.isNotBlank() && inputUser.isNotBlank()) {
                                viewModel.submitOllamaLogin(inputEmail, inputUser)
                                inputEmail = ""
                                inputUser = ""
                            }
                        },
                        enabled = inputEmail.isNotBlank() && inputUser.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("توليد المفتاح الأمني وبدء الجلسة", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Public SSH Key card display
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "مفتاح الربط العام الفردي للجهاز (Active SSH Public Key):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E2022))
                        .border(1.dp, Color(0xFF2C2E33), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayKey = if (publicKey.isEmpty()) "أول استخدام: سيتم توليد مفتاح SSH آلياً لك" else publicKey
                    Text(
                        text = if (displayKey.length > 35) displayKey.take(35) + "..." else displayKey,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFC5C6C7),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            var currentKey = publicKey
                            if (currentKey.isEmpty()) {
                                currentKey = viewModel.generateOllamaKey()
                            }
                            clipboardManager.setText(AnnotatedString(currentKey))
                            android.widget.Toast.makeText(context, "تم نسخ مفتاح SSH بنجاح!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "نسخ المفتاح",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "انسخ هذا المفتاح وضعه بـ Ollama.com لتتمكن من مصادقة الأوامر.",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )

                    TextButton(
                        onClick = {
                            try {
                                val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://ollama.com/settings/keys"))
                                browserIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {}
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("صفحة المفاتيح الرسمية", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
