package com.androgpt.yaser.domain.repository

import com.androgpt.yaser.domain.model.ModelConfig
import com.androgpt.yaser.domain.model.ModelInfo
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    
    suspend fun loadModel(config: ModelConfig): Result<Unit>
    
    suspend fun unloadModel()
    
    fun getLoadedModel(): Flow<ModelInfo?>
    
    suspend fun getAvailableModels(): List<ModelInfo>
    
    suspend fun deleteModel(filePath: String): Result<Unit>
    
    suspend fun getModelInfo(filePath: String): ModelInfo?
}
