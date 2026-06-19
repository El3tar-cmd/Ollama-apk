package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.OllamaViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: OllamaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Temporary states to hold form edits until "SAVE" is clicked
    var selectedMode by remember {
        mutableStateOf(
            if (viewModel.useRemoteServer.value) 2
            else 1
        )
    }
    var hostUrl by remember { mutableStateOf(viewModel.remoteServerHost.value) }
    var systemPrompt by remember { mutableStateOf(viewModel.defaultSystemPrompt.value) }
    var tempVal by remember { mutableStateOf(viewModel.modelTemperature.value) }
    var threadsVal by remember { mutableStateOf(viewModel.cpuThreads.value.toFloat()) }
    var downloadUrl by remember { mutableStateOf(viewModel.daemonDownloadUrl.value) }
    var autoStart by remember { 
        mutableStateOf(context.getSharedPreferences("ollama_mobile_prefs", android.content.Context.MODE_PRIVATE).getBoolean("auto_start_server", false)) 
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 40.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "تهيئة الإعدادات الاحترافية المتقدمة",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 1. Connection Mode Settings
        SettingsGroupSection(title = "طريقة الاتصال والاستدعاء (Inference Routing)") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Mode 1: Local Daemon Mode
                Surface(
                    onClick = { selectedMode = 1 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedMode == 1) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (selectedMode == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = (selectedMode == 1),
                            onClick = { selectedMode = 1 }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "تشغيل ديمون أولاما الثنائي الأصلي (Local Go Binary)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "تنزيل وتشغيل أرشيف أولاما الثنائي arm64 حقيقي 100% كعملية خلفية (Daemon) معزولة داخل الهاتف لاستيراد وتشغيل النماذج محلياً بالبورت 11434.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Mode 2: Remote Connection Mode
                Surface(
                    onClick = { selectedMode = 2 },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedMode == 2) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (selectedMode == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = (selectedMode == 2),
                            onClick = { selectedMode = 2 }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الاتصال بسيرفر Ollama خارجي (Remote Network Host)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "توجيه كافة طلبات استعلم وشات الذكاء الاصطناعي لعنوان سيرفر خارجي يعمل على الحاسوب الشخصي أو الشبكة المحلية للبورت 11434.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            // Sub-configurations based on selectedMode
            if (selectedMode == 1) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = downloadUrl,
                    onValueChange = { downloadUrl = it },
                    label = { Text("رابط تثبيت أولاما أندرويد (CDN Tarball URL)") },
                    singleLine = true,
                    leadingIcon = { Icon(imageVector = Icons.Default.Build, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "يقوم النظام بتحميل الأرشيف الثنائي arm64 لـ Linux/Android واستخراجه ذاتياً داخل مساحة التخزين العازلة للتطبيق عند نقر زر التشغيل بالسيرفر.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            if (selectedMode == 2) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = hostUrl,
                    onValueChange = { hostUrl = it },
                    label = { Text("عنوان سيرفر أولاما (Host URL)") },
                    placeholder = { Text("http://192.168.1.50:11434") },
                    singleLine = true,
                    leadingIcon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remote_host_url_field")
                )
                Text(
                    text = "يرجى التأكد من تفعيل CORS في إعدادات السيرفر الخارجي (عن طريق تعيين OLLAMA_ORIGINS=\"*\") لتتم عملية الاتصال بنجاح من الموبايل.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }

        // 2. Models Hyperparameters
        SettingsGroupSection(title = "محددات التوليد الرقمي (Hyperparameters)") {
            // Default System Prompt
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("موجّه النظام والتعليمات الأساسية (System Prompt)") },
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_prompt_field")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Temperature Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "درجة العشوائية والتغيير (Temperature)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = String.format("%.2f", tempVal), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = tempVal,
                    onValueChange = { tempVal = it },
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Thread constraints
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "عدد خيوط المعالجة المدعومة (CPU Threads)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "${threadsVal.toInt()} Threads", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = threadsVal,
                    onValueChange = { threadsVal = it },
                    valueRange = 1f..8f,
                    steps = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 3. App Settings
        SettingsGroupSection(title = "خصائص التطبيق العامة") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "التشغيل التلقائي عند فتح التطبيق",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "تشغيل السيرفر والمراقبة محلياً فور إقلاع واجهة التطبيق مباشرة.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Switch(
                    checked = autoStart,
                    onCheckedChange = { autoStart = it }
                )
            }
        }

        // 4. Save Button
        Button(
            onClick = {
                viewModel.saveSettings(
                    remoteServer = (selectedMode == 2),
                    host = hostUrl,
                    systemPrompt = systemPrompt,
                    temp = tempVal,
                    threads = threadsVal.toInt(),
                    localDaemon = (selectedMode == 1),
                    downloadUrl = downloadUrl
                )
                // Persist autostart parameter
                context.getSharedPreferences("ollama_mobile_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("auto_start_server", autoStart)
                    .apply()
            },
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("save_settings_button")
        ) {
            Icon(imageVector = Icons.Default.Done, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "حفظ الإعدادات المطبقة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun SettingsGroupSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 1.dp)
            content()
        }
    }
}
