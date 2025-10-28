package com.androgpt.yaser.presentation.models

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androgpt.yaser.data.repository.ModelDownloadRepository
import com.androgpt.yaser.data.service.ModelDownloadService
import com.androgpt.yaser.domain.model.AvailableModels
import com.androgpt.yaser.domain.model.DownloadState
import com.androgpt.yaser.domain.model.DownloadableModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelDownloadViewModel @Inject constructor(
    private val downloadRepository: ModelDownloadRepository,
    application: Application
) : AndroidViewModel(application) {
    
    private val context = application.applicationContext
    
    private val _availableModels = MutableStateFlow<List<DownloadableModel>>(emptyList())
    val availableModels: StateFlow<List<DownloadableModel>> = _availableModels.asStateFlow()
    
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()
    
    private val _downloadedModels = MutableStateFlow<Set<String>>(emptySet())
    val downloadedModels: StateFlow<Set<String>> = _downloadedModels.asStateFlow()
    
    private val _completionMessage = MutableStateFlow<String?>(null)
    val completionMessage: StateFlow<String?> = _completionMessage.asStateFlow()
    
    private val activePollingJobs = mutableMapOf<String, Job>()
    
    init {
        loadAvailableModels()
        refreshDownloadedModels()
        android.util.Log.i("ModelDownloadViewModel", "ViewModel initialized")
    }
    
    private fun loadAvailableModels() {
        _availableModels.value = AvailableModels.getAllModels()
    }
    
    fun refreshDownloadedModels() {
        android.util.Log.d("ModelDownloadViewModel", "Refreshing downloaded models list")
        val downloaded = _availableModels.value
            .filter { downloadRepository.isModelDownloaded(it) }
            .map { it.id }
            .toSet()
        
        android.util.Log.d("ModelDownloadViewModel", "Downloaded models: ${downloaded.size} - $downloaded")
        _downloadedModels.value = downloaded
        
        // Clear success states for downloaded models
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            downloaded.forEach { modelId ->
                if (get(modelId) is DownloadState.Success) {
                    remove(modelId)
                }
            }
        }
    }
    
    /**
     * Start polling the temp file to track download progress
     */
    private fun startProgressPolling(model: DownloadableModel) {
        // Cancel existing polling for this model
        activePollingJobs[model.id]?.cancel()
        
        val job = viewModelScope.launch {
            android.util.Log.i("ModelDownloadViewModel", "Started progress polling for ${model.name}")
            
            // Give service time to start
            delay(100)
            
            val tempFile = java.io.File(context.filesDir, "models/${model.fileName}.tmp")
            val finalFile = java.io.File(context.filesDir, "models/${model.fileName}")
            
            while (isActive) {
                // Check if download is complete
                if (finalFile.exists() && finalFile.length() == model.fileSize) {
                    android.util.Log.i("ModelDownloadViewModel", "Download complete detected for ${model.name}")
                    _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                        put(model.id, DownloadState.Success)
                    }
                    // Show completion message
                    _completionMessage.value = "✓ ${model.name} downloaded successfully!"
                    // Clear message after 5 seconds
                    delay(5000)
                    _completionMessage.value = null
                    
                    refreshDownloadedModels()
                    break
                }
                
                // Update progress from temp file
                if (tempFile.exists()) {
                    val downloadedBytes = tempFile.length()
                    val progress = if (model.fileSize > 0) {
                        (downloadedBytes.toFloat() / model.fileSize.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    
                    _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                        put(model.id, DownloadState.Downloading(progress, downloadedBytes, model.fileSize))
                    }
                } else {
                    // Temp file doesn't exist yet, show 0% progress
                    _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                        put(model.id, DownloadState.Downloading(0f, 0L, model.fileSize))
                    }
                }
                
                delay(500) // Poll every 500ms
            }
            
            android.util.Log.i("ModelDownloadViewModel", "Stopped progress polling for ${model.name}")
            activePollingJobs.remove(model.id)
        }
        
        activePollingJobs[model.id] = job
    }
    
    fun downloadModel(model: DownloadableModel, onSuccess: () -> Unit = {}) {
        android.util.Log.i("ModelDownloadViewModel", "Starting download for ${model.name}")
        
        // Start progress polling immediately
        startProgressPolling(model)
        
        // Start the foreground service
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, model.name)
            putExtra(ModelDownloadService.EXTRA_MODEL_DESCRIPTION, model.description)
            putExtra(ModelDownloadService.EXTRA_MODEL_URL, model.downloadUrl)
            putExtra(ModelDownloadService.EXTRA_MODEL_SIZE, model.fileSize)
            putExtra(ModelDownloadService.EXTRA_MODEL_FILENAME, model.fileName)
            putExtra(ModelDownloadService.EXTRA_MODEL_QUANTIZATION, model.quantization)
            putExtra(ModelDownloadService.EXTRA_MODEL_CREATOR, model.creator)
            putExtra(ModelDownloadService.EXTRA_MODEL_CAPABILITIES, model.capabilities)
            putExtra(ModelDownloadService.EXTRA_MODEL_RECOMMENDED, model.recommendedFor)
        }
        
        try {
            context.startForegroundService(intent)
            android.util.Log.i("ModelDownloadViewModel", "Started download service for ${model.name}")
        } catch (e: Exception) {
            android.util.Log.e("ModelDownloadViewModel", "Failed to start service", e)
            _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                put(model.id, DownloadState.Error(e.message ?: "Failed to start download"))
            }
            activePollingJobs[model.id]?.cancel()
            activePollingJobs.remove(model.id)
        }
    }
    
    fun cancelDownload(model: DownloadableModel) {
        android.util.Log.i("ModelDownloadViewModel", "Cancelling download for ${model.name}")
        
        // Stop polling
        activePollingJobs[model.id]?.cancel()
        activePollingJobs.remove(model.id)
        
        // Cancel in service
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
        }
        
        try {
            context.startService(intent)
        } catch (e: Exception) {
            android.util.Log.e("ModelDownloadViewModel", "Failed to cancel service", e)
        }
        
        // Cancel locally
        downloadRepository.cancelDownload(model)
        
        // Update UI
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(model.id, DownloadState.Cancelled)
        }
    }
    
    fun deleteModel(model: DownloadableModel) {
        android.util.Log.i("ModelDownloadViewModel", "Deleting model: ${model.name}")
        if (downloadRepository.deleteDownloadedModel(model)) {
            _downloadedModels.value = _downloadedModels.value - model.id
            _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                remove(model.id)
            }
            android.util.Log.i("ModelDownloadViewModel", "Model deleted successfully")
        } else {
            android.util.Log.w("ModelDownloadViewModel", "Failed to delete model")
        }
    }
    
    fun clearDownloadState(model: DownloadableModel) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            remove(model.id)
        }
    }
    
    fun dismissCompletionMessage() {
        _completionMessage.value = null
    }
    
    fun getModelFilePath(model: DownloadableModel): String? {
        return if (downloadRepository.isModelDownloaded(model)) {
            downloadRepository.getModelFilePath(model)
        } else {
            null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Cancel all polling jobs
        activePollingJobs.values.forEach { it.cancel() }
        activePollingJobs.clear()
        android.util.Log.i("ModelDownloadViewModel", "ViewModel cleared, all polling stopped")
    }
}
