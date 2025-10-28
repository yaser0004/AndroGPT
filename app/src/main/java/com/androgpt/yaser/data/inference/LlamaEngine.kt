package com.androgpt.yaser.data.inference

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlamaEngine @Inject constructor() {
    
    companion object {
        private const val TAG = "LlamaEngine"
        
        init {
            try {
                System.loadLibrary("androgpt")
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    @Volatile
    private var isModelLoaded = false
    
    @Volatile
    private var isGenerating = false
    
    // Native method declarations
    private external fun nativeInit(): Boolean
    
    private external fun nativeLoadModel(
        modelPath: String,
        nThreads: Int,
        nGpuLayers: Int,
        contextSize: Int
    ): Boolean
    
    private external fun nativeUnloadModel()
    
    private external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int
    ): String
    
    private external fun nativeGenerateStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        callback: StreamCallback
    )
    
    private external fun nativeStopGeneration()
    
    private external fun nativeGetModelInfo(): String
    
    private external fun nativeCleanup()
    
    init {
        nativeInit()
    }
    
    suspend fun loadModel(
        modelPath: String,
        nThreads: Int = 4,
        nGpuLayers: Int = 0,
        contextSize: Int = 2048
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(modelPath)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Model file not found: $modelPath"))
            }
            
            if (isModelLoaded) {
                unloadModel()
            }
            
            val success = nativeLoadModel(modelPath, nThreads, nGpuLayers, contextSize)
            
            if (success) {
                isModelLoaded = true
                Log.i(TAG, "Model loaded successfully: $modelPath")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to load model"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            Result.failure(e)
        }
    }
    
    fun unloadModel() {
        if (isModelLoaded) {
            nativeUnloadModel()
            isModelLoaded = false
            Log.i(TAG, "Model unloaded")
        }
    }
    
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isModelLoaded) {
                return@withContext Result.failure(Exception("No model loaded"))
            }
            
            isGenerating = true
            val response = nativeGenerate(prompt, maxTokens, temperature, topP, topK)
            isGenerating = false
            
            Result.success(response)
        } catch (e: Exception) {
            isGenerating = false
            Log.e(TAG, "Generation error", e)
            Result.failure(e)
        }
    }
    
    suspend fun generateStream(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40,
        onToken: (String) -> Unit,
        onComplete: () -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== GENERATE STREAM START ===")
            Log.d(TAG, "isModelLoaded: $isModelLoaded")
            Log.d(TAG, "isGenerating (before): $isGenerating")
            Log.d(TAG, "maxTokens: $maxTokens, temperature: $temperature")
            Log.d(TAG, "Prompt length: ${prompt.length} chars")
            
            if (!isModelLoaded) {
                Log.e(TAG, "BLOCKED: No model loaded")
                throw Exception("No model loaded")
            }
            
            // Always reset flag at start to prevent blocking
            if (isGenerating) {
                Log.w(TAG, "WARNING: Flag was stuck at true, resetting to allow generation")
            }
            isGenerating = false
            
            isGenerating = true
            Log.d(TAG, "Set isGenerating = true, starting native generation")
            
            val callback = object : StreamCallback {
                override fun onToken(token: String) {
                    Log.v(TAG, "Token callback: '$token'")
                    onToken(token)
                }
                
                override fun onComplete() {
                    Log.d(TAG, "Complete callback received, resetting isGenerating flag")
                    isGenerating = false
                    onComplete()
                }
            }
            
            try {
                Log.d(TAG, "Calling nativeGenerateStream...")
                nativeGenerateStream(prompt, maxTokens, temperature, topP, topK, callback)
                Log.d(TAG, "nativeGenerateStream returned")
            } catch (e: Exception) {
                Log.e(TAG, "Native generation threw exception", e)
                isGenerating = false
                throw e
            }
            
            Log.d(TAG, "=== GENERATE STREAM END ===")
        } catch (e: Exception) {
            Log.e(TAG, "Streaming generation error", e)
            isGenerating = false
            throw e
        }
    }
    
    fun stopGeneration() {
        Log.d(TAG, "stopGeneration called, resetting flag")
        if (isGenerating) {
            nativeStopGeneration()
        }
        // Always reset the flag regardless of previous state
        isGenerating = false
    }
    
    fun getModelInfo(): String {
        return if (isModelLoaded) {
            nativeGetModelInfo()
        } else {
            "No model loaded"
        }
    }
    
    fun isLoaded(): Boolean = isModelLoaded
    
    fun cleanup() {
        unloadModel()
        nativeCleanup()
    }
    
    interface StreamCallback {
        fun onToken(token: String)
        fun onComplete()
    }
}
