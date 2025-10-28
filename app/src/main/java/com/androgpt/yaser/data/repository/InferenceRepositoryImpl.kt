package com.androgpt.yaser.data.repository

import com.androgpt.yaser.data.inference.LlamaEngine
import com.androgpt.yaser.domain.model.GenerationState
import com.androgpt.yaser.domain.repository.InferenceRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InferenceRepositoryImpl @Inject constructor(
    private val llamaEngine: LlamaEngine
) : InferenceRepository {
    
    override suspend fun generate(
        prompt: String,
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int
    ): Result<String> {
        return llamaEngine.generate(
            prompt = prompt,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            topK = topK
        )
    }
    
    override fun generateStream(
        prompt: String,
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int
    ): Flow<GenerationState> = callbackFlow {
        
        trySend(GenerationState.Loading)
        
        val fullText = StringBuilder()
        
        try {
            llamaEngine.generateStream(
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = topP,
                topK = topK,
                onToken = { token ->
                    fullText.append(token)
                    trySend(GenerationState.Generating(fullText.toString()))
                },
                onComplete = {
                    trySend(GenerationState.Complete(fullText.toString()))
                    close()
                }
            )
        } catch (e: Exception) {
            trySend(GenerationState.Error(e.message ?: "Unknown error"))
            close(e)
        }
        
        awaitClose {
            // Cleanup if needed
        }
    }
    
    override suspend fun stopGeneration() {
        llamaEngine.stopGeneration()
    }
}
