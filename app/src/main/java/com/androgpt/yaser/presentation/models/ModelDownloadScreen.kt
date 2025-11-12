package com.androgpt.yaser.presentation.models

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.androgpt.yaser.domain.model.DownloadState
import com.androgpt.yaser.domain.model.DownloadableModel

@Composable
fun ModelDownloadScreen(
    viewModel: ModelDownloadViewModel = hiltViewModel(),
    onModelDownloaded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val availableModels by viewModel.availableModels.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()
    val downloadedModels by viewModel.downloadedModels.collectAsState()
    val completionMessage by viewModel.completionMessage.collectAsState()
    
    var showDeleteDialog by remember { mutableStateOf<DownloadableModel?>(null) }
    var expandedCreators by remember { mutableStateOf(setOf<String>()) }
    
    // Group models by creator
    val groupedModels = remember(availableModels) {
        availableModels.groupBy { it.creator }
            .toSortedMap() // Sort by creator name
    }
    
    // Refresh downloaded models when screen appears
    LaunchedEffect(Unit) {
        viewModel.refreshDownloadedModels()
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Available Models",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Download language models to run locally on your device. Choose based on your device capacity and needs:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 Tap the info icon (ⓘ) next to each model for detailed capabilities and recommendations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Grouped models by creator
            groupedModels.forEach { (creator, models) ->
                item(key = "header_$creator") {
                    CreatorGroupHeader(
                        creator = creator,
                        modelCount = models.size,
                        isExpanded = expandedCreators.contains(creator),
                        onToggle = {
                            expandedCreators = if (expandedCreators.contains(creator)) {
                                expandedCreators - creator
                            } else {
                                expandedCreators + creator
                            }
                        }
                    )
                }
                
                if (expandedCreators.contains(creator)) {
                    items(models, key = { it.id }) { model ->
                        DownloadableModelItem(
                            model = model,
                            downloadState = downloadStates[model.id] ?: DownloadState.Idle,
                            isDownloaded = downloadedModels.contains(model.id),
                            onDownload = { viewModel.downloadModel(model, onSuccess = onModelDownloaded) },
                            onCancel = { viewModel.cancelDownload(model) },
                            onDelete = { showDeleteDialog = model },
                            onClearError = { viewModel.clearDownloadState(model) }
                        )
                    }
                }
            }
        }
        
        // Show completion message as a snackbar at the bottom
        completionMessage?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.dismissCompletionMessage() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    showDeleteDialog?.let { model ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Model") },
            text = { Text("Are you sure you want to delete ${model.name}? You'll need to download it again to use it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteModel(model)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
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
fun CreatorGroupHeader(
    creator: String,
    modelCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = creator,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "$modelCount ${if (modelCount == 1) "model" else "models"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = if (isExpanded) 
                    Icons.Default.KeyboardArrowUp 
                else 
                    Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
fun DownloadableModelItem(
    model: DownloadableModel,
    downloadState: DownloadState,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onClearError: () -> Unit
) {
    var showModelInfo by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDownloaded -> MaterialTheme.colorScheme.primaryContainer
                downloadState is DownloadState.Error -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Title row with info button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDownloaded) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { showModelInfo = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Model Info",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Description and size
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Size: ${formatFileSize(model.fileSize)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (downloadState) {
                    is DownloadState.Idle -> {
                        if (isDownloaded) {
                            Button(
                                onClick = onDelete,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete")
                            }
                        } else {
                            Button(onClick = onDownload) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download")
                            }
                        }
                    }
                    is DownloadState.Downloading -> {
                        OutlinedButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel Download")
                        }
                    }
                    is DownloadState.Success -> {
                        Button(
                            onClick = onDelete,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }
                    is DownloadState.Error -> {
                        Button(onClick = onClearError) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                    is DownloadState.Cancelled -> {
                        Button(onClick = onDownload) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download")
                        }
                    }
                    else -> {}
                }
            }
            
            // Download progress
            when (downloadState) {
                is DownloadState.Downloading -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        LinearProgressIndicator(
                            progress = downloadState.progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${(downloadState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${formatFileSize(downloadState.downloadedBytes)} / ${formatFileSize(downloadState.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is DownloadState.Paused -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column {
                        val progress = downloadState.downloadedBytes.toFloat() / downloadState.totalBytes.toFloat()
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Paused at ${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                            Text(
                                text = "${formatFileSize(downloadState.downloadedBytes)} / ${formatFileSize(downloadState.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is DownloadState.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: ${downloadState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        }
    }
    
    // Model Info Dialog
    if (showModelInfo) {
        val listState = rememberLazyListState()
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { showModelInfo = false },
            title = { Text(model.name) },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item("description") {
                        Text(
                            text = model.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (model.capabilities.isNotEmpty()) {
                        item("capabilities") {
                            Divider()
                            Text(
                                text = "Capabilities:",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = model.capabilities,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (model.recommendedFor.isNotEmpty()) {
                        item("recommended") {
                            Divider()
                            Text(
                                text = model.recommendedFor,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    item("file_size") {
                        Divider()
                        Text(
                            text = "File Size: ${formatFileSize(model.fileSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Quantization: ${model.quantization}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item("download_url") {
                        Divider()
                        Text(
                            text = "Download URL:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        val annotated = remember(model.downloadUrl) {
                            AnnotatedString(model.downloadUrl)
                        }
                        ClickableText(
                            text = annotated,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            ),
                            onClick = { uriHandler.openUri(model.downloadUrl) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { uriHandler.openUri(model.downloadUrl) }) {
                    Text("Open Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showModelInfo = false }) {
                    Text("Close")
                }
            }
        )
    }
}
