package com.androgpt.yaser.data.inference

import android.content.Context
import android.util.Log
import com.androgpt.yaser.domain.model.ModelInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "ModelManager"
        private const val MODELS_DIR = "models"
    }
    
    private val modelsDirectory: File by lazy {
        File(context.filesDir, MODELS_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    suspend fun getAvailableModels(): List<ModelInfo> = withContext(Dispatchers.IO) {
        try {
            val modelFiles = modelsDirectory.listFiles { file ->
                file.isFile && file.extension.equals("gguf", ignoreCase = true)
            } ?: emptyArray()
            
            modelFiles.map { file ->
                ModelInfo(
                    name = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    size = file.length(),
                    isLoaded = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available models", e)
            emptyList()
        }
    }
    
    suspend fun deleteModel(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists() && file.delete()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete model file"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model", e)
            Result.failure(e)
        }
    }
    
    suspend fun getModelInfo(filePath: String): ModelInfo? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                ModelInfo(
                    name = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    size = file.length()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting model info", e)
            null
        }
    }
}
