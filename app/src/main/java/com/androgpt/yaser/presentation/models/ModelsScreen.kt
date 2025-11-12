package com.androgpt.yaser.presentation.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.androgpt.yaser.domain.model.ModelInfo
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel(),
    onLoadModel: (ModelInfo) -> Unit
) {
    val availableModels by viewModel.availableModels.collectAsState()
    val loadedModel by viewModel.loadedModel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf<ModelInfo?>(null) }
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Models") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Local Models") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Download") }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> LocalModelsTab(
                availableModels = availableModels,
                loadedModel = loadedModel,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onLoadModel = onLoadModel,
                onDeleteModel = { showDeleteDialog = it },
                onClearError = { viewModel.clearError() },
                modifier = Modifier.padding(paddingValues)
            )
            1 -> ModelDownloadScreen(
                onModelDownloaded = {
                    viewModel.refreshModels()
                    selectedTab = 0
                },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { model ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Model") },
            text = { Text("Are you sure you want to delete ${model.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteModel(model.filePath)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LocalModelsTab(
    availableModels: List<ModelInfo>,
    loadedModel: ModelInfo?,
    isLoading: Boolean,
    errorMessage: String?,
    onLoadModel: (ModelInfo) -> Unit,
    onDeleteModel: (ModelInfo) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadViewModel: ModelDownloadViewModel = hiltViewModel()
    val allModels = com.androgpt.yaser.domain.model.AvailableModels.getAllModels()
    
    // Create a map of file names to downloadable models for additional info
    val modelInfoMap = remember(allModels) {
        allModels.associateBy { it.fileName }
    }
    
    var showInfoDialog by remember { mutableStateOf<Pair<ModelInfo, com.androgpt.yaser.domain.model.DownloadableModel?>?>(null) }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        if (errorMessage != null) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = onClearError) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(errorMessage)
            }
        }
        
        if (availableModels.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "No models found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Download models from the Download tab",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(availableModels, key = { it.filePath }) { model ->
                    // Extract filename from path
                    val fileName = model.filePath.substringAfterLast("/")
                    val downloadableModel = modelInfoMap[fileName]
                    
                    EnhancedModelItem(
                        model = model,
                        downloadableModel = downloadableModel,
                        isLoaded = loadedModel?.filePath == model.filePath,
                        onLoad = { onLoadModel(model) },
                        onDelete = { onDeleteModel(model) },
                        onShowInfo = { showInfoDialog = Pair(model, downloadableModel) }
                    )
                }
            }
        }
    }
    
    // Enhanced Info Dialog
    showInfoDialog?.let { (modelInfo, downloadableModel) ->
    val listState = rememberLazyListState()
        val uriHandler = LocalUriHandler.current
        val userFacingPath = remember(modelInfo.filePath) {
            val fileName = File(modelInfo.filePath).name
            val packageName = modelInfo.filePath
                .substringAfter("/data/user/0/")
                .substringBefore("/")
                .takeIf { it.isNotEmpty() }
                ?: "com.androgpt.yaser"
            "/data/data/$packageName/files/models/$fileName"
        }
        val packageName = remember(modelInfo.filePath) {
            modelInfo.filePath
                .substringAfter("/data/user/0/")
                .substringBefore("/")
                .takeIf { it.isNotEmpty() }
                ?: "com.androgpt.yaser"
        }
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
            title = { Text(modelInfo.name) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    downloadableModel?.let { dlModel ->
                        item("description") {
                            Text(dlModel.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        item("capabilities_header") {
                            Divider()
                            Text("Capabilities:", style = MaterialTheme.typography.titleSmall)
                        }
                        item("capabilities") {
                            Text(dlModel.capabilities, style = MaterialTheme.typography.bodySmall)
                        }
                        item("recommended_header") {
                            Divider()
                            Text("Recommended For:", style = MaterialTheme.typography.titleSmall)
                        }
                        item("recommended") {
                            Text(dlModel.recommendedFor, style = MaterialTheme.typography.bodySmall)
                        }
                        item("technical_header") {
                            Divider()
                            Text("Technical Details:", style = MaterialTheme.typography.titleSmall)
                        }
                        item("technical") {
                            Text("Creator: ${dlModel.creator}", style = MaterialTheme.typography.bodySmall)
                            Text("Quantization: ${dlModel.quantization}", style = MaterialTheme.typography.bodySmall)
                        }
                        item("download_url") {
                            Divider()
                            Text("Download URL:", style = MaterialTheme.typography.titleSmall)
                            ClickableText(
                                text = AnnotatedString(dlModel.downloadUrl),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                ),
                                onClick = { uriHandler.openUri(dlModel.downloadUrl) }
                            )
                        }
                    }
                    item("file_size") {
                        Text("File Size: ${formatFileSize(modelInfo.size)}", style = MaterialTheme.typography.bodySmall)
                    }
                    item("stored_at") {
                        Text(
                            text = "Stored at: $userFacingPath",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    item("access_note") {
                        Text(
                            text = "Note: App-private storage requires root or 'adb shell run-as $packageName' to access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ModelItem(
    model: ModelInfo,
    isLoaded: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (isLoaded) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Loaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Size: ${formatFileSize(model.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isLoaded) {
                    Button(onClick = onLoad) {
                        Text("Load")
                    }
                }
                
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedModelItem(
    model: ModelInfo,
    downloadableModel: com.androgpt.yaser.domain.model.DownloadableModel?,
    isLoaded: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onShowInfo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isLoaded) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Loaded",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    downloadableModel?.let {
                        Text(
                            text = it.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                IconButton(onClick = onShowInfo) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Model Info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Model metadata chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                downloadableModel?.let { dlModel ->
                    AssistChip(
                        onClick = { },
                        label = { Text(dlModel.creator, style = MaterialTheme.typography.labelSmall) },
                        enabled = false
                    )
                    AssistChip(
                        onClick = { },
                        label = { Text(dlModel.quantization, style = MaterialTheme.typography.labelSmall) },
                        enabled = false
                    )
                }
                AssistChip(
                    onClick = { },
                    label = { Text(formatFileSize(model.size), style = MaterialTheme.typography.labelSmall) },
                    enabled = false
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoaded) {
                    Button(
                        onClick = { },
                        modifier = Modifier.weight(1f),
                        enabled = false,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Loaded")
                    }
                } else {
                    Button(
                        onClick = onLoad,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Load Model")
                    }
                }
                
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !isLoaded
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

internal fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.2f MB".format(mb)
        else -> "%.2f KB".format(kb)
    }
}
