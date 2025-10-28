package com.androgpt.yaser.data.repository

import android.util.Log
import com.androgpt.yaser.data.inference.LlamaEngine
import com.androgpt.yaser.data.inference.ModelManager
import com.androgpt.yaser.data.local.ModelPreferences
import com.androgpt.yaser.domain.model.ModelConfig
import com.androgpt.yaser.domain.model.ModelInfo
import com.androgpt.yaser.domain.repository.ModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepositoryImpl @Inject constructor(
    private val llamaEngine: LlamaEngine,
    private val modelManager: ModelManager,
    private val modelPreferences: ModelPreferences
) : ModelRepository {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _loadedModel = MutableStateFlow<ModelInfo?>(null)
    
    init {
        // Restore previously loaded model on startup
        scope.launch {
            try {
                val savedModel = modelPreferences.getLoadedModel().firstOrNull()
                if (savedModel != null) {
                    // Verify the model file still exists
                    val file = File(savedModel.filePath)
                    if (file.exists()) {
                        Log.d("ModelRepository", "Restoring model: ${savedModel.name}")
                        // Load the model with default config
                        val config = ModelConfig(
                            name = savedModel.name,
                            filePath = savedModel.filePath,
                            size = savedModel.size
                        )
                        loadModel(config)
                    } else {
                        Log.w("ModelRepository", "Saved model file not found: ${savedModel.filePath}")
                        modelPreferences.clearLoadedModel()
                    }
                }
            } catch (e: Exception) {
                Log.e("ModelRepository", "Error restoring model", e)
            }
        }
    }
    
    override suspend fun loadModel(config: ModelConfig): Result<Unit> {
        val result = llamaEngine.loadModel(
            modelPath = config.filePath,
            nThreads = config.nThreads,
            nGpuLayers = config.nGpuLayers,
            contextSize = config.contextLength
        )
        
        if (result.isSuccess) {
            val modelInfo = ModelInfo(
                name = config.name,
                filePath = config.filePath,
                size = config.size,
                isLoaded = true,
                metadata = llamaEngine.getModelInfo()
            )
            _loadedModel.value = modelInfo
            
            // Persist the loaded model info
            try {
                modelPreferences.saveLoadedModel(
                    name = config.name,
                    filePath = config.filePath,
                    size = config.size
                )
                Log.d("ModelRepository", "Saved model to preferences: ${config.name}")
            } catch (e: Exception) {
                Log.e("ModelRepository", "Error saving model to preferences", e)
            }
        }
        
        return result
    }
    
    override suspend fun unloadModel() {
        llamaEngine.unloadModel()
        _loadedModel.value = null
        
        // Clear the persisted model info
        try {
            modelPreferences.clearLoadedModel()
            Log.d("ModelRepository", "Cleared model from preferences")
        } catch (e: Exception) {
            Log.e("ModelRepository", "Error clearing model from preferences", e)
        }
    }
    
    override fun getLoadedModel(): Flow<ModelInfo?> {
        return _loadedModel.asStateFlow()
    }
    
    override suspend fun getAvailableModels(): List<ModelInfo> {
        return modelManager.getAvailableModels()
    }
    
    override suspend fun deleteModel(filePath: String): Result<Unit> {
        // Unload if this is the loaded model
        if (_loadedModel.value?.filePath == filePath) {
            unloadModel()
        }
        return modelManager.deleteModel(filePath)
    }
    
    override suspend fun getModelInfo(filePath: String): ModelInfo? {
        return modelManager.getModelInfo(filePath)
    }
}
