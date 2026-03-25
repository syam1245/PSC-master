package com.example.pscmaster.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pscmaster.ui.viewmodel.InputViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: InputViewModel,
    onNavigateToInput: () -> Unit,
    onNavigateToQuiz: () -> Unit,
    onNavigateToAnalytics: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importDatabase(it)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "PSC MASTER", 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    ) 
                },
                actions = {
                    IconButton(onClick = { 
                        viewModel.updateStorageInfo()
                        showSettings = true 
                    }) {
                        Icon(
                            Icons.Rounded.Settings, 
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Hero Section ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp), // Reduced from 180dp to fit more content
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(120.dp)
                            .offset(x = 30.dp, y = (-30).dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.1f)
                    ) {}
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp), // Reduced padding
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Welcome back!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "PSC Master",
                                style = MaterialTheme.typography.headlineSmall, // Reduced size
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.15f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatItem(
                                icon = Icons.AutoMirrored.Rounded.MenuBook,
                                value = "${uiState.totalQuestionsCount}",
                                label = "Questions",
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                            StatItem(
                                icon = Icons.Rounded.Category,
                                value = "${uiState.availableSubjects.size}",
                                label = "Subjects",
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            Text(
                text = "Quick Actions",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            HomeMenuButton(
                title = "Contributor Mode",
                subtitle = "Expand your database",
                icon = Icons.Rounded.AddCircleOutline,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onNavigateToInput
            )

            HomeMenuButton(
                title = "Learner Mode",
                subtitle = "Start practice",
                icon = Icons.Rounded.PlayCircleOutline,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onNavigateToQuiz
            )

            HomeMenuButton(
                title = "Performance",
                subtitle = "AI analytics",
                icon = Icons.Rounded.BarChart,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onNavigateToAnalytics
            )

            HomeMenuButton(
                title = if (uiState.isLoggedIn) "Cloud Sync" else "Login to Sync",
                subtitle = if (uiState.isSyncing) "Syncing..." else if (uiState.isLoggedIn) "Data is safe" else "Backup data",
                icon = if (uiState.isSyncing) Icons.Rounded.Sync else if (uiState.isLoggedIn) Icons.Rounded.CloudSync else Icons.Rounded.CloudQueue,
                containerColor = if (uiState.isLoggedIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = if (uiState.isLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                isSyncing = uiState.isSyncing,
                onClick = {
                    if (uiState.isLoggedIn) {
                        viewModel.syncToFirebase()
                    } else {
                        viewModel.signInAnonymously()
                    }
                }
            )

            // Messages
            LaunchedEffect(uiState.errorMessage) {
                uiState.errorMessage?.let {
                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
            LaunchedEffect(uiState.infoMessage) {
                uiState.infoMessage?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearInfo()
                }
            }

            if (showSettings) {
                SettingsDialog(
                    uiState = uiState,
                    onDismiss = { showSettings = false },
                    onExport = { viewModel.onExportRequested(context) { uri ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Export Backup"))
                    }},
                    onImport = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) }
                )
            }
        }
    }
}

@Composable
fun StatItem(icon: ImageVector, value: String, label: String, contentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = contentColor.copy(alpha = 0.8f))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold, color = contentColor)
            Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun HomeMenuButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    isSyncing: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp), // Reduced from 90dp to fit all cards
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(contentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = contentColor
                    )
                } else {
                    Icon(
                        icon, 
                        contentDescription = null, 
                        modifier = Modifier.size(24.dp),
                        tint = contentColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, 
                    style = MaterialTheme.typography.bodyLarge, 
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle, 
                    style = MaterialTheme.typography.labelSmall, 
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForwardIos, 
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun SettingsDialog(
    uiState: com.example.pscmaster.ui.viewmodel.InputUiState,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Storage & Backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StorageInfoItem("Database Size", uiState.dbSize)
                StorageInfoItem("Available Space", uiState.availableSpace)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onExport, modifier = Modifier.weight(1f)) {
                        Text("Export")
                    }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                        Text("Import")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun StorageInfoItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
