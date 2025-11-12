package com.androgpt.yaser.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androgpt.yaser.data.local.GenerationPreferences
import com.androgpt.yaser.domain.model.ModelConfig
import com.androgpt.yaser.domain.repository.ChatRepository
import com.androgpt.yaser.domain.repository.ModelRepository
import com.androgpt.yaser.domain.usecase.LoadModelUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val chatRepository: ChatRepository,
    private val loadModelUseCase: LoadModelUseCase,
    private val generationPreferences: GenerationPreferences
) : ViewModel() {
    
    val loadedModel = modelRepository.getLoadedModel()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    // Use preferences for generation settings
    private val _generationSettings = MutableStateFlow(GenerationPreferences.GenerationSettings())
    val temperature = _generationSettings.map { it.temperature }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.7f)
    
    val maxTokens = _generationSettings.map { it.maxTokens }
        .stateIn(viewModelScope, SharingStarted.Lazily, 512)
    
    val topP = _generationSettings.map { it.topP }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.9f)
    
    val topK = _generationSettings.map { it.topK }
        .stateIn(viewModelScope, SharingStarted.Lazily, 40)
    
    val repeatPenalty = _generationSettings.map { it.repeatPenalty }
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.1f)
    
    val repeatLastN = _generationSettings.map { it.repeatLastN }
        .stateIn(viewModelScope, SharingStarted.Lazily, 64)
    
    val minP = _generationSettings.map { it.minP }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.05f)
    
    val tfsZ = _generationSettings.map { it.tfsZ }
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)
    
    val typicalP = _generationSettings.map { it.typicalP }
        .stateIn(viewModelScope, SharingStarted.Lazily, 1.0f)
    
    val mirostat = _generationSettings.map { it.mirostat }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)
    
    val mirostatTau = _generationSettings.map { it.mirostatTau }
        .stateIn(viewModelScope, SharingStarted.Lazily, 5.0f)
    
    val mirostatEta = _generationSettings.map { it.mirostatEta }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.1f)

    private val _systemPrompt = MutableStateFlow(GenerationPreferences.DEFAULT_SYSTEM_PROMPT)
    val systemPrompt = _systemPrompt.asStateFlow()

    init {
        // Load settings from preferences
        viewModelScope.launch {
            generationPreferences.getSettings().collect { settings ->
                _generationSettings.value = settings
                if (_systemPrompt.value != settings.systemPrompt) {
                    _systemPrompt.value = settings.systemPrompt
                }
            }
        }
    }

    private val _contextLength = MutableStateFlow(2048)
    val contextLength = _contextLength.asStateFlow()
    
    private val _cpuThreads = MutableStateFlow(detectCpuThreads())
    val cpuThreads = _cpuThreads.asStateFlow()
    
    private val _gpuLayers = MutableStateFlow(0)
    val gpuLayers = _gpuLayers.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    
    fun setTemperature(value: Float) {
        val coerced = value.coerceIn(0f, 2f)
        viewModelScope.launch {
            generationPreferences.saveTemperature(coerced)
        }
    }
    
    fun setMaxTokens(value: Int) {
        val coerced = value.coerceIn(64, 1024)
        viewModelScope.launch {
            generationPreferences.saveMaxTokens(coerced)
        }
    }
    
    fun setTopP(value: Float) {
        val coerced = value.coerceIn(0f, 1f)
        viewModelScope.launch {
            generationPreferences.saveTopP(coerced)
        }
    }
    
    fun setTopK(value: Int) {
        val coerced = value.coerceIn(1, 100)
        viewModelScope.launch {
            generationPreferences.saveTopK(coerced)
        }
    }
    
    fun setRepeatPenalty(value: Float) {
        val coerced = value.coerceIn(1.0f, 2.0f)
        viewModelScope.launch {
            generationPreferences.saveRepeatPenalty(coerced)
        }
    }
    
    fun setRepeatLastN(value: Int) {
        val coerced = value.coerceIn(0, 256)
        viewModelScope.launch {
            generationPreferences.saveRepeatLastN(coerced)
        }
    }
    
    fun setMinP(value: Float) {
        val coerced = value.coerceIn(0.0f, 1.0f)
        viewModelScope.launch {
            generationPreferences.saveMinP(coerced)
        }
    }
    
    fun setTfsZ(value: Float) {
        val coerced = value.coerceIn(0.0f, 1.0f)
        viewModelScope.launch {
            generationPreferences.saveTfsZ(coerced)
        }
    }
    
    fun setTypicalP(value: Float) {
        val coerced = value.coerceIn(0.0f, 1.0f)
        viewModelScope.launch {
            generationPreferences.saveTypicalP(coerced)
        }
    }
    
    fun setMirostat(value: Int) {
        val coerced = value.coerceIn(0, 2)
        viewModelScope.launch {
            generationPreferences.saveMirostat(coerced)
        }
    }
    
    fun setMirostatTau(value: Float) {
        val coerced = value.coerceIn(0.0f, 10.0f)
        viewModelScope.launch {
            generationPreferences.saveMirostatTau(coerced)
        }
    }
    
    fun setMirostatEta(value: Float) {
        val coerced = value.coerceIn(0.001f, 1.0f)
        viewModelScope.launch {
            generationPreferences.saveMirostatEta(coerced)
        }
    }
    
    fun setContextLength(value: Int) {
        _contextLength.value = value.coerceIn(128, 8192)
    }
    
    fun setCpuThreads(value: Int) {
        _cpuThreads.value = value.coerceIn(1, 16)
    }
    
    fun setGpuLayers(value: Int) {
        _gpuLayers.value = value.coerceIn(0, 100)
    }

    private fun detectCpuThreads(): Int {
        val available = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        if (available <= 4) return available

        val reservedForSystem = (available * 0.25f).roundToInt().coerceAtLeast(1)
        val target = available - reservedForSystem
        return target.coerceIn(4, 8)
    }
    
    fun setSystemPrompt(value: String) {
        _systemPrompt.value = value
    }

    fun saveSystemPrompt() {
        viewModelScope.launch {
            val sanitized = _systemPrompt.value.trim()
            generationPreferences.saveSystemPrompt(sanitized)
            _generationSettings.value = _generationSettings.value.copy(systemPrompt = sanitized)
            _systemPrompt.value = sanitized
            _message.value = if (sanitized.isEmpty()) {
                "System prompt cleared"
            } else {
                "System prompt saved"
            }
        }
    }
    
    fun loadModel(modelPath: String, modelName: String, modelSize: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            
            val currentSettings = _generationSettings.value
            val config = ModelConfig(
                name = modelName,
                filePath = modelPath,
                size = modelSize,
                contextLength = _contextLength.value,
                temperature = currentSettings.temperature,
                maxTokens = currentSettings.maxTokens,
                nThreads = _cpuThreads.value,
                nGpuLayers = _gpuLayers.value,
                systemPrompt = _systemPrompt.value
            )
            
            loadModelUseCase(config)
                .onSuccess {
                    _message.value = "Model loaded successfully"
                }
                .onFailure {
                    _message.value = "Failed to load model: ${it.message}"
                }
            
            _isLoading.value = false
        }
    }
    
    fun unloadModel() {
        viewModelScope.launch {
            modelRepository.unloadModel()
            _message.value = "Model unloaded"
        }
    }
    
    fun clearChatHistory() {
        viewModelScope.launch {
            chatRepository.clearAllHistory()
            _message.value = "Chat history cleared"
        }
    }
    
    fun clearMessage() {
        _message.value = null
    }
    
    fun applyPreset(preset: String) {
        viewModelScope.launch {
            val newSettings = when (preset) {
                "precise" -> GenerationPreferences.GenerationSettings(
                    temperature = 0.2f,
                    maxTokens = 256,
                    topP = 0.7f,
                    topK = 20,
                    repeatPenalty = 1.15f,
                    repeatLastN = 64,
                    minP = 0.05f,
                    tfsZ = 1.0f,
                    typicalP = 1.0f,
                    mirostat = 0,
                    mirostatTau = 5.0f,
                    mirostatEta = 0.1f,
                    systemPrompt = _systemPrompt.value
                )
                "balanced" -> GenerationPreferences.GenerationSettings(
                    temperature = 0.7f,
                    maxTokens = 512,
                    topP = 0.9f,
                    topK = 40,
                    repeatPenalty = 1.1f,
                    repeatLastN = 64,
                    minP = 0.05f,
                    tfsZ = 1.0f,
                    typicalP = 1.0f,
                    mirostat = 0,
                    mirostatTau = 5.0f,
                    mirostatEta = 0.1f,
                    systemPrompt = _systemPrompt.value
                )
                "creative" -> GenerationPreferences.GenerationSettings(
                    temperature = 1.2f,
                    maxTokens = 512,
                    topP = 0.95f,
                    topK = 60,
                    repeatPenalty = 1.05f,
                    repeatLastN = 128,
                    minP = 0.0f,
                    tfsZ = 1.0f,
                    typicalP = 1.0f,
                    mirostat = 0,
                    mirostatTau = 5.0f,
                    mirostatEta = 0.1f,
                    systemPrompt = _systemPrompt.value
                )
                "focused" -> GenerationPreferences.GenerationSettings(
                    temperature = 0.7f,
                    maxTokens = 256,
                    topP = 0.9f,
                    topK = 40,
                    repeatPenalty = 1.1f,
                    repeatLastN = 64,
                    minP = 0.05f,
                    tfsZ = 1.0f,
                    typicalP = 1.0f,
                    mirostat = 2,
                    mirostatTau = 5.0f,
                    mirostatEta = 0.1f,
                    systemPrompt = _systemPrompt.value
                )
                else -> _generationSettings.value
            }
            generationPreferences.saveSettings(newSettings)
            _message.value = "Applied $preset preset"
        }
    }
}
