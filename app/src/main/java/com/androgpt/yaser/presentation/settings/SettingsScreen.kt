package com.androgpt.yaser.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val loadedModel by viewModel.loadedModel.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val maxTokens by viewModel.maxTokens.collectAsState()
    val topP by viewModel.topP.collectAsState()
    val topK by viewModel.topK.collectAsState()
    val repeatPenalty by viewModel.repeatPenalty.collectAsState()
    val repeatLastN by viewModel.repeatLastN.collectAsState()
    val minP by viewModel.minP.collectAsState()
    val tfsZ by viewModel.tfsZ.collectAsState()
    val typicalP by viewModel.typicalP.collectAsState()
    val mirostat by viewModel.mirostat.collectAsState()
    val mirostatTau by viewModel.mirostatTau.collectAsState()
    val mirostatEta by viewModel.mirostatEta.collectAsState()
    val contextLength by viewModel.contextLength.collectAsState()
    val cpuThreads by viewModel.cpuThreads.collectAsState()
    val gpuLayers by viewModel.gpuLayers.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val message by viewModel.message.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Model Info
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current Model",
                            style = MaterialTheme.typography.titleMedium
                        )
                        InfoButton(
                            info = "The AI model currently loaded in memory. Models must be downloaded from the Models tab before use."
                        )
                    }
                    Text(
                        text = loadedModel?.name ?: "No model loaded",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    loadedModel?.let {
                        Button(
                            onClick = { viewModel.unloadModel() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Unload Model")
                        }
                    }
                }
            }
            
            // Core Generation Parameters
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Core Generation Parameters",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    SettingSlider(
                        label = "Temperature",
                        value = temperature,
                        onValueChange = viewModel::setTemperature,
                        valueRange = 0f..2f,
                        steps = 19,
                        valueFormatter = { String.format("%.2f", it) },
                        info = "Controls randomness in text generation.\n\n" +
                                "• 0.0-0.3: Very focused and deterministic (good for factual tasks)\n" +
                                "• 0.4-0.7: Balanced creativity and coherence (recommended)\n" +
                                "• 0.8-1.2: More creative and diverse responses\n" +
                                "• 1.3-2.0: Very creative but may lose coherence"
                    )
                    
                    Divider()
                    
                    SettingSlider(
                        label = "Max Tokens",
                        value = maxTokens.toFloat(),
                        onValueChange = { viewModel.setMaxTokens(it.toInt()) },
                        valueRange = 64f..1024f,
                        steps = 15,
                        valueFormatter = { it.toInt().toString() },
                        info = "Maximum number of tokens (words/subwords) in the response.\n\n" +
                                "• 64-128: Very short, concise answers\n" +
                                "• 256-512: Medium length responses (recommended)\n" +
                                "• 512-1024: Long, detailed responses\n\n" +
                                "Higher values increase generation time and memory usage."
                    )
                    
                    Divider()
                    
                    SettingSlider(
                        label = "Top P (Nucleus Sampling)",
                        value = topP,
                        onValueChange = viewModel::setTopP,
                        valueRange = 0f..1f,
                        steps = 19,
                        valueFormatter = { String.format("%.2f", it) },
                        info = "Cumulative probability cutoff for token selection.\n\n" +
                                "• 0.1-0.5: Very focused, conservative responses\n" +
                                "• 0.6-0.9: Balanced variety (0.9 recommended)\n" +
                                "• 0.9-1.0: Maximum diversity\n\n" +
                                "Lower values make output more deterministic by considering fewer tokens."
                    )
                    
                    Divider()
                    
                    SettingSlider(
                        label = "Top K",
                        value = topK.toFloat(),
                        onValueChange = { viewModel.setTopK(it.toInt()) },
                        valueRange = 1f..100f,
                        steps = 98,
                        valueFormatter = { it.toInt().toString() },
                        info = "Limits token selection to the top K most likely tokens.\n\n" +
                                "• 1-20: Very conservative, repetitive\n" +
                                "• 20-40: Balanced (40 recommended)\n" +
                                "• 40-100: More diverse vocabulary\n\n" +
                                "Works together with Top P to control output variety."
                    )
                }
            }
            
            // Advanced Sampling Parameters
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Advanced Sampling",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    SettingSlider(
                        label = "Repetition Penalty",
                        value = repeatPenalty,
                        onValueChange = viewModel::setRepeatPenalty,
                        valueRange = 1.0f..2.0f,
                        steps = 19,
                        valueFormatter = { String.format("%.2f", it) },
                        info = "Penalizes repeated tokens to reduce repetition.\n\n" +
                                "• 1.0: No penalty (may repeat)\n" +
                                "• 1.1-1.3: Light penalty (recommended)\n" +
                                "• 1.4-2.0: Strong penalty (may become incoherent)\n\n" +
                                "Use with Repeat Last N to control penalty window."
                    )
                    
                    Divider()
                    
                    SettingSlider(
                        label = "Repeat Last N Tokens",
                        value = repeatLastN.toFloat(),
                        onValueChange = { viewModel.setRepeatLastN(it.toInt()) },
                        valueRange = 0f..256f,
                        steps = 15,
                        valueFormatter = { it.toInt().toString() },
                        info = "Number of recent tokens considered for repetition penalty.\n\n" +
                                "• 0: Disabled\n" +
                                "• 32-64: Short-term repetition prevention (recommended)\n" +
                                "• 128-256: Long-term pattern prevention\n\n" +
                                "Larger values prevent repeating phrases from earlier in the response."
                    )
                    
                    Divider()
                    
                    SettingSlider(
                        label = "Min P",
                        value = minP,
                        onValueChange = viewModel::setMinP,
                        valueRange = 0.0f..1.0f,
                        steps = 19,
                        valueFormatter = { String.format("%.3f", it) },
                        info = "Minimum probability threshold for token selection.\n\n" +
                                "• 0.0: Disabled (default)\n" +
                                "• 0.01-0.05: Light filtering (recommended)\n" +
                                "• 0.1+: Strong filtering (very focused)\n\n" +
                                "Removes tokens below this probability ratio compared to the most likely token."
                    )
                    
                    Divider()
                    
                    SettingSlider(
                        label = "TFS Z (Tail Free Sampling)",
                        value = tfsZ,
                        onValueChange = viewModel::setTfsZ,
                        valueRange = 0.0f..1.0f,
                        steps = 19,
                        valueFormatter = { String.format("%.2f", it) },
                        info = "Removes low-probability tokens based on derivative.\n\n" +
                                "• 1.0: Disabled (default)\n" +
                                "• 0.8-0.95: Light filtering\n" +
                                "• 0.5-0.8: Moderate filtering\n\n" +
                                "Helps reduce low-quality tokens while preserving diversity."
                    )
                    
                    Divider()
                    
                    SettingSlider(
                        label = "Typical P",
                        value = typicalP,
                        onValueChange = viewModel::setTypicalP,
                        valueRange = 0.0f..1.0f,
                        steps = 19,
                        valueFormatter = { String.format("%.2f", it) },
                        info = "Locally typical sampling parameter.\n\n" +
                                "• 1.0: Disabled (default)\n" +
                                "• 0.8-0.95: Light effect\n" +
                                "• 0.5-0.8: Moderate effect\n\n" +
                                "Selects tokens with information content close to the expected value."
                    )
                }
            }
            
            // Mirostat Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mirostat Sampling",
                            style = MaterialTheme.typography.titleMedium
                        )
                        InfoButton(
                            info = "Mirostat is an advanced sampling method that dynamically adjusts perplexity during generation.\n\n" +
                                    "When enabled, it overrides Temperature, Top P, and Top K settings.\n\n" +
                                    "Best for maintaining consistent quality in long-form text generation."
                        )
                    }
                    
                    SettingSlider(
                        label = "Mirostat Mode",
                        value = mirostat.toFloat(),
                        onValueChange = { viewModel.setMirostat(it.toInt()) },
                        valueRange = 0f..2f,
                        steps = 1,
                        valueFormatter = { 
                            when(it.toInt()) {
                                0 -> "0 (Disabled)"
                                1 -> "1 (Mirostat 1.0)"
                                2 -> "2 (Mirostat 2.0)"
                                else -> it.toInt().toString()
                            }
                        },
                        info = "Mirostat sampling mode.\n\n" +
                                "• 0: Disabled (use standard sampling)\n" +
                                "• 1: Mirostat 1.0 algorithm\n" +
                                "• 2: Mirostat 2.0 algorithm (recommended if using Mirostat)\n\n" +
                                "When enabled, overrides Temperature, Top P, and Top K."
                    )
                    
                    if (mirostat > 0) {
                        Divider()
                        
                        SettingSlider(
                            label = "Mirostat Tau (Target Entropy)",
                            value = mirostatTau,
                            onValueChange = viewModel::setMirostatTau,
                            valueRange = 0f..10f,
                            steps = 19,
                            valueFormatter = { String.format("%.1f", it) },
                            info = "Target entropy (perplexity) value.\n\n" +
                                    "• 3-5: More focused and coherent (recommended)\n" +
                                    "• 5-7: Balanced\n" +
                                    "• 7-10: More diverse and creative\n\n" +
                                    "Higher values allow more randomness in generation."
                        )
                        
                        Divider()
                        
                        SettingSlider(
                            label = "Mirostat Eta (Learning Rate)",
                            value = mirostatEta,
                            onValueChange = viewModel::setMirostatEta,
                            valueRange = 0.001f..1.0f,
                            steps = 19,
                            valueFormatter = { String.format("%.3f", it) },
                            info = "Learning rate for entropy adjustment.\n\n" +
                                    "• 0.001-0.05: Slow adaptation\n" +
                                    "• 0.05-0.2: Moderate adaptation (0.1 recommended)\n" +
                                    "• 0.2-1.0: Fast adaptation\n\n" +
                                    "Controls how quickly Mirostat adjusts to target entropy."
                        )
                    }
                }
            }
            
            // Hardware Settings
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Hardware Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    SettingSlider(
                        label = "Context Length",
                        value = contextLength.toFloat(),
                        onValueChange = { viewModel.setContextLength(it.toInt()) },
                        valueRange = 512f..8192f,
                        steps = 7,
                        valueFormatter = { it.toInt().toString() },
                        info = "Maximum context window for the model.\n\n" +
                                "• 512-2048: Lower memory usage, shorter conversations\n" +
                                "• 2048-4096: Balanced (recommended for most models)\n" +
                                "• 4096-8192: Long conversations (requires more RAM)\n\n" +
                                "⚠️ Requires model reload to take effect.\n" +
                                "Phi-3 4K models: use 2048-4096\n" +
                                "Phi-3 128K models: can use up to 8192+"
                    )
                    
                    Divider()
                    
                    SettingSlider(
                        label = "CPU Threads",
                        value = cpuThreads.toFloat(),
                        onValueChange = { viewModel.setCpuThreads(it.toInt()) },
                        valueRange = 1f..8f,
                        steps = 6,
                        valueFormatter = { it.toInt().toString() },
                        info = "Number of CPU threads for inference.\n\n" +
                                "• 1-2: Low CPU usage, slower\n" +
                                "• 4: Balanced (recommended)\n" +
                                "• 6-8: Faster inference, higher CPU usage\n\n" +
                                "⚠️ Requires model reload to take effect.\n" +
                                "Match to your device's CPU cores for best performance."
                    )
                    
                    Divider()
                    
                    SettingSlider(
                        label = "GPU Layers",
                        value = gpuLayers.toFloat(),
                        onValueChange = { viewModel.setGpuLayers(it.toInt()) },
                        valueRange = 0f..50f,
                        steps = 49,
                        valueFormatter = { it.toInt().toString() },
                        info = "Number of model layers offloaded to GPU.\n\n" +
                                "• 0: CPU only (slower, less battery drain)\n" +
                                "• 10-20: Partial GPU acceleration\n" +
                                "• 30-50: Maximum GPU acceleration (fastest, more battery drain)\n\n" +
                                "⚠️ Requires model reload to take effect.\n" +
                                "Higher values use more VRAM but increase speed significantly."
                    )
                }
            }
            
            // System Prompt
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "System Prompt",
                            style = MaterialTheme.typography.titleMedium
                        )
                        InfoButton(
                            info = "Instructions that guide the AI's behavior and personality.\n\n" +
                                    "Examples:\n" +
                                    "• 'You are a helpful coding assistant.'\n" +
                                    "• 'You are a creative writing partner.'\n" +
                                    "• 'You respond in a concise, technical manner.'\n\n" +
                                    "The system prompt is included at the start of every conversation."
                        )
                    }
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = viewModel::setSystemPrompt,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter system instructions...") },
                        minLines = 3,
                        maxLines = 6
                    )
                }
            }
            
            // Preset Configurations
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preset Configurations",
                            style = MaterialTheme.typography.titleMedium
                        )
                        InfoButton(
                            info = "Quick presets optimized for different use cases.\n\n" +
                                    "These will override your current generation parameter settings."
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.applyPreset("precise") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Precise", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { viewModel.applyPreset("balanced") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Balanced", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.applyPreset("creative") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Creative", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { viewModel.applyPreset("focused") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Focused", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            // Actions
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Actions",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Button(
                        onClick = { viewModel.clearChatHistory() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear Chat History")
                    }
                }
            }
            
            // App Info
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "AndroGPT v1.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Run Large Language Models locally on Android devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "com.androgpt.yaser",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueFormatter: (Float) -> String,
    info: String
) {
    var showInfo by remember { mutableStateOf(false) }
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$label: ${valueFormatter(value)}")
            IconButton(
                onClick = { showInfo = !showInfo },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info about $label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
        
        AnimatedVisibility(
            visible = showInfo,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = info,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun InfoButton(info: String) {
    var showInfo by remember { mutableStateOf(false) }
    
    Column {
        IconButton(
            onClick = { showInfo = !showInfo },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Information",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        if (showInfo) {
            AlertDialog(
                onDismissRequest = { showInfo = false },
                title = { Text("Information") },
                text = { 
                    Text(
                        text = info,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showInfo = false }) {
                        Text("Got it")
                    }
                }
            )
        }
    }
}
