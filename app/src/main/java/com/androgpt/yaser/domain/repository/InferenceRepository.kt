package com.androgpt.yaser.domain.repository

import com.androgpt.yaser.domain.model.GenerationState
import kotlinx.coroutines.flow.Flow

interface InferenceRepository {
    
    suspend fun generate(
        prompt: String,
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int
    ): Result<String>
    
    fun generateStream(
        prompt: String,
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int
    ): Flow<GenerationState>
    
    suspend fun stopGeneration()
}
