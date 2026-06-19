package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.OllamaViewModel
import com.example.viewmodel.TabType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainLayout()
            }
        }
    }
}

@Composable
fun MainLayout(
    viewModel: OllamaViewModel = viewModel()
) {
    val activeTab by viewModel.currentTab.collectAsState()
    val isRunning by viewModel.isServerRunning.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_main_container"),
        topBar = {
            OllamaHeaderBar(
                isRunning = isRunning,
                onSettingsClick = { viewModel.selectTab(TabType.EXPERT) }
            )
        },
        bottomBar = {
            OllamaBottomNavigation(
                activeTab = activeTab,
                onTabSelect = { viewModel.selectTab(it) }
            )
        }
    ) { innerPadding ->
        // Animated Crossfade for premium screen transition feel
        Crossfade(
            targetState = activeTab,
            label = "screen_transition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { tab ->
            when (tab) {
                TabType.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                TabType.CHAT -> ChatScreen(viewModel = viewModel)
                TabType.MODELS -> ModelsLibraryScreen(viewModel = viewModel)
                TabType.LOGS -> LogsScreen(viewModel = viewModel)
                TabType.EXPERT -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun OllamaHeaderBar(
    isRunning: Boolean,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Code Terminal Logo
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Ollama Mobile Logo",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Title and Status Indicators
                Column {
                    Text(
                        text = "Ollama Mobile",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Status LED Glow dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) Color(0xFF4CAF50) else Color.Gray)
                        )
                        Text(
                            text = if (isRunning) "SERVER RUNNING" else "SERVER STOPPED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) Color(0xFF4CAF50) else Color.Gray,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Quick-Settings click
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("appbar_settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "الاعدادات",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun OllamaBottomNavigation(
    activeTab: TabType,
    onTabSelect: (TabType) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Chat Tab
            BottomNavItem(
                icon = Icons.Default.Send,
                selectedIcon = Icons.Default.Send,
                label = "الدردشة",
                isSelected = activeTab == TabType.CHAT,
                onClick = { onTabSelect(TabType.CHAT) },
                modifier = Modifier.testTag("nav_chat_tab")
            )

            // 2. Models Library Tab
            BottomNavItem(
                icon = Icons.Default.List,
                selectedIcon = Icons.Default.List,
                label = "الموديلات",
                isSelected = activeTab == TabType.MODELS,
                onClick = { onTabSelect(TabType.MODELS) },
                modifier = Modifier.testTag("nav_models_tab")
            )

            // 3. Central Dashboard Icon (High Highlighted Pill)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clickable { onTabSelect(TabType.DASHBOARD) }
                    .padding(horizontal = 4.dp)
                    .testTag("nav_dashboard_tab")
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (activeTab == TabType.DASHBOARD) MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "لوحة التحكم",
                        tint = if (activeTab == TabType.DASHBOARD) MaterialTheme.colorScheme.onPrimaryContainer 
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "سيرفر",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (activeTab == TabType.DASHBOARD) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // 4. Logs Tab
            BottomNavItem(
                icon = Icons.Default.Info,
                selectedIcon = Icons.Default.Info,
                label = "السجلات",
                isSelected = activeTab == TabType.LOGS,
                onClick = { onTabSelect(TabType.LOGS) },
                modifier = Modifier.testTag("nav_logs_tab")
            )

            // 5. Advanced Settings Tab
            BottomNavItem(
                icon = Icons.Default.Settings,
                selectedIcon = Icons.Default.Settings,
                label = "الخبير",
                isSelected = activeTab == TabType.EXPERT,
                onClick = { onTabSelect(TabType.EXPERT) },
                modifier = Modifier.testTag("nav_expert_tab")
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (isSelected) selectedIcon else icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
