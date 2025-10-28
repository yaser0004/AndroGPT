package com.androgpt.yaser.domain.usecase

import android.util.Log
import com.androgpt.yaser.domain.model.GenerationState
import com.androgpt.yaser.domain.model.Message
import com.androgpt.yaser.domain.repository.ChatRepository
import com.androgpt.yaser.domain.repository.InferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val inferenceRepository: InferenceRepository
) {
    
    companion object {
        private const val TAG = "SendMessageUseCase"
        
        // Phi-3 special tokens that should be removed from responses
        private val STOP_TOKENS = listOf(
            "<|end|>",
            "<|end|",     // Partial token (without closing >)
            "<|user|>",
            "<|user|",    // Partial token
            "<|assistant|>",
            "<|assistant|", // Partial token
            "<|system|>",
            "<|system|"   // Partial token
        )
    }
    
    private fun cleanResponse(text: String): String {
        var cleaned = text
        
        // Remove complete stop tokens first
        for (token in STOP_TOKENS) {
            cleaned = cleaned.replace(token, "")
        }
        
        // Remove any Phi-3 token pattern (complete or incomplete)
        // Matches: <|...>, <|..., <|, etc.
        cleaned = cleaned.replace(Regex("<\\|[^>]*\\|?>?"), "")
        
        // Remove any remaining partial tokens at the end of the string
        cleaned = cleaned.replace(Regex("<\\|[^>]*$"), "")
        cleaned = cleaned.replace(Regex("<$"), "")
        
        // Remove repeated whitespace
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        
        // Trim
        cleaned = cleaned.trim()
        
        return cleaned
    }
    
    private fun buildPrompt(messages: List<Message>): String {
        // Microsoft Phi-3 uses ChatML format with specific tokens
        // Format: <|system|>system_message<|end|><|user|>user_message<|end|><|assistant|>
        val promptBuilder = StringBuilder()
        
        // System prompt
        promptBuilder.append("<|system|>")
        promptBuilder.append("You are a helpful AI assistant.")
        promptBuilder.append("<|end|>\n")
        
        // Add conversation history - exclude the last message as it's the current query
        val history = if (messages.size > 1) {
            messages.dropLast(1).takeLast(10) // Keep last 10 messages for context
        } else {
            emptyList()
        }
        
        for (message in history) {
            if (message.isUser) {
                promptBuilder.append("<|user|>")
                promptBuilder.append(message.content)
                promptBuilder.append("<|end|>\n")
            } else {
                promptBuilder.append("<|assistant|>")
                promptBuilder.append(message.content)
                promptBuilder.append("<|end|>\n")
            }
        }
        
        // Add current user message
        val currentMessage = messages.lastOrNull()
        if (currentMessage != null && currentMessage.isUser) {
            promptBuilder.append("<|user|>")
            promptBuilder.append(currentMessage.content)
            promptBuilder.append("<|end|>\n")
        }
        
        // Prompt for assistant response
        promptBuilder.append("<|assistant|>")
        
        return promptBuilder.toString()
    }
    
    operator fun invoke(
        conversationId: Long,
        userMessage: String,
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int
    ): Flow<GenerationState> = flow {
        
        Log.d(TAG, "=== SEND MESSAGE USE CASE START ===")
        Log.d(TAG, "Conversation ID: $conversationId")
        Log.d(TAG, "User message: '$userMessage'")
        Log.d(TAG, "Params: temp=$temperature, maxTokens=$maxTokens, topP=$topP, topK=$topK")
        
        // Save user message
        val userMsg = Message(
            conversationId = conversationId,
            content = userMessage,
            isUser = true
        )
        Log.d(TAG, "Inserting user message to database...")
        chatRepository.insertMessage(userMsg)
        Log.d(TAG, "User message inserted")
        
        Log.d(TAG, "Emitting Loading state")
        emit(GenerationState.Loading)
        
        // Get conversation history for context
        Log.d(TAG, "Fetching conversation history...")
        val messages = chatRepository.getMessagesForConversation(conversationId).firstOrNull() ?: emptyList()
        Log.d(TAG, "Retrieved ${messages.size} messages from history")
        
        // Format prompt with TinyLlama chat template
        val formattedPrompt = buildPrompt(messages)
        
        // Log the prompt for debugging
        Log.d(TAG, "Formatted prompt (${formattedPrompt.length} chars):\n$formattedPrompt")
        Log.d(TAG, "Message count: ${messages.size}")
        
        // Generate response
        Log.d(TAG, "Calling inferenceRepository.generateStream...")
        val responseFlow = inferenceRepository.generateStream(
            prompt = formattedPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
            topP = topP,
            topK = topK
        )
        
        var fullResponse = ""
        var tokenCount = 0
        
        Log.d(TAG, "Starting to collect from response flow...")
        responseFlow.collect { state ->
            tokenCount++
            Log.d(TAG, "Collected state #$tokenCount: ${state.javaClass.simpleName}")
            
            when (state) {
                is GenerationState.Generating -> {
                    // Clean the response text during generation
                    fullResponse = cleanResponse(state.currentText)
                    Log.v(TAG, "Generating - cleaned text length: ${fullResponse.length}")
                    emit(GenerationState.Generating(fullResponse))
                }
                is GenerationState.Complete -> {
                    // Clean the final response text
                    fullResponse = cleanResponse(state.text)
                    
                    Log.d(TAG, "Complete - final cleaned response (${fullResponse.length} chars): $fullResponse")
                    
                    // Save assistant message
                    val assistantMsg = Message(
                        conversationId = conversationId,
                        content = fullResponse,
                        isUser = false
                    )
                    Log.d(TAG, "Inserting assistant message to database...")
                    chatRepository.insertMessage(assistantMsg)
                    Log.d(TAG, "Assistant message inserted")
                    
                    Log.d(TAG, "Emitting Complete state")
                    emit(GenerationState.Complete(fullResponse))
                }
                is GenerationState.Error -> {
                    Log.e(TAG, "Error state received: ${state.message}")
                    emit(state)
                }
                else -> {
                    Log.d(TAG, "Other state: $state")
                    emit(state)
                }
            }
        }
        
        Log.d(TAG, "=== SEND MESSAGE USE CASE END (collected $tokenCount states) ===")
    }
}
