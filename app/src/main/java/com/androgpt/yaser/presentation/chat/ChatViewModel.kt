package com.androgpt.yaser.presentation.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androgpt.yaser.domain.model.GenerationState
import com.androgpt.yaser.domain.model.Message
import com.androgpt.yaser.domain.model.ModelConfig
import com.androgpt.yaser.domain.repository.ChatRepository
import com.androgpt.yaser.domain.repository.InferenceRepository
import com.androgpt.yaser.domain.repository.ModelRepository
import com.androgpt.yaser.domain.usecase.SendMessageUseCase
import com.androgpt.yaser.domain.util.ChatNameGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val inferenceRepository: InferenceRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val generationPreferences: com.androgpt.yaser.data.local.GenerationPreferences
) : ViewModel() {
    
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId = _currentConversationId.asStateFlow()
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()
    
    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState = _generationState.asStateFlow()
    
    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()
    
    val loadedModel = modelRepository.getLoadedModel()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    private val _conversations = MutableStateFlow<List<com.androgpt.yaser.domain.model.Conversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private var messagesJob: Job? = null
    
    // Generation parameters - loaded from preferences
    private val _generationSettings = MutableStateFlow(
        com.androgpt.yaser.data.local.GenerationPreferences.GenerationSettings()
    )
    val generationSettings = _generationSettings.asStateFlow()
    
    init {
        // Load saved settings
        viewModelScope.launch {
            generationPreferences.getSettings().collect { settings ->
                _generationSettings.value = settings
            }
        }

        loadAllConversations()

        viewModelScope.launch {
            initializeConversation()
        }
    }
    
    private fun loadAllConversations() {
        viewModelScope.launch {
            chatRepository.getAllConversations()
                .collect { convos ->
                    _conversations.value = convos
                }
        }
    }

    private suspend fun initializeConversation() {
        val conversationsSnapshot = try {
            chatRepository.getAllConversations().first()
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to load conversations during init", e)
            startNewConversation()
            return
        }

        if (conversationsSnapshot.isEmpty()) {
            Log.d("ChatViewModel", "No existing conversations found, creating a fresh one")
            startNewConversation()
            return
        }

        val latestConversation = conversationsSnapshot.maxByOrNull { it.updatedAt }
        if (latestConversation == null) {
            Log.d("ChatViewModel", "Unable to resolve latest conversation, creating a fresh one")
            startNewConversation()
            return
        }

        val hasMessages = try {
            chatRepository.getMessagesForConversation(latestConversation.id).first().isNotEmpty()
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to inspect messages for conversation ${latestConversation.id}", e)
            startNewConversation(modelName = latestConversation.modelName)
            return
        }

        if (hasMessages) {
            Log.d(
                "ChatViewModel",
                "Latest conversation (${latestConversation.id}) has messages, creating a new chat for fresh start"
            )
            startNewConversation(modelName = latestConversation.modelName)
        } else {
            Log.d(
                "ChatViewModel",
                "Reusing existing empty conversation with ID: ${latestConversation.id}"
            )
            _currentConversationId.value = latestConversation.id
            _messages.value = emptyList()
            _generationState.value = GenerationState.Idle
            loadMessages(latestConversation.id)
        }
    }
    
    fun setInputText(text: String) {
        _inputText.value = text
    }
    
    fun startNewConversation(modelName: String = "") {
        viewModelScope.launch {
            // First, ensure any ongoing generation is stopped
            stopGeneration()
            
            val title = "New Chat ${System.currentTimeMillis()}"
            val conversationId = chatRepository.createConversation(title, modelName)
            _currentConversationId.value = conversationId
            _messages.value = emptyList() // Clear messages immediately
            
            // Reset generation state
            _generationState.value = GenerationState.Idle
            
            Log.d("ChatViewModel", "Started new conversation with ID: $conversationId")
            loadMessages(conversationId)
        }
    }
    
    fun loadConversation(conversationId: Long) {
        viewModelScope.launch {
            // Stop any ongoing generation when switching conversations
            stopGeneration()
            
            _currentConversationId.value = conversationId
            _generationState.value = GenerationState.Idle
            
            Log.d("ChatViewModel", "Loading conversation: $conversationId")
            loadMessages(conversationId)
        }
    }
    
    private fun loadMessages(conversationId: Long) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessagesForConversation(conversationId)
                .collect { messages ->
                    _messages.value = messages
                }
        }
    }
    
    fun sendMessage() {
        val text = _inputText.value.trim()
        Log.d("ChatViewModel", "=== SEND MESSAGE START ===")
        Log.d("ChatViewModel", "Input text: '$text'")
        Log.d("ChatViewModel", "Text empty: ${text.isEmpty()}")
        
        if (text.isEmpty()) {
            Log.d("ChatViewModel", "Text is empty, returning early")
            return
        }
        
        // Prevent sending if already generating
        val currentState = _generationState.value
        Log.d("ChatViewModel", "Current generation state: $currentState")
        Log.d("ChatViewModel", "State type: ${currentState.javaClass.simpleName}")
        Log.d("ChatViewModel", "Is Generating: ${currentState is GenerationState.Generating}")
        Log.d("ChatViewModel", "Is Loading: ${currentState is GenerationState.Loading}")
        
        if (currentState is GenerationState.Generating || currentState is GenerationState.Loading) {
            Log.d("ChatViewModel", "BLOCKED: Already generating, ignoring request")
            return
        }
        
        Log.d("ChatViewModel", "State check passed, proceeding...")
        
        Log.d("ChatViewModel", "State check passed, proceeding...")
        
        // Check if a model is loaded
        val model = loadedModel.value
        Log.d("ChatViewModel", "Loaded model: $model")
        Log.d("ChatViewModel", "Model is null: ${model == null}")
        
        if (model == null) {
            Log.d("ChatViewModel", "BLOCKED: No model loaded")
            viewModelScope.launch {
                _generationState.value = GenerationState.Error("No model loaded. Please load a model from the Models tab first.")
                kotlinx.coroutines.delay(3000)
                _generationState.value = GenerationState.Idle
            }
            return
        }
        
        Log.d("ChatViewModel", "Model check passed, launching coroutine")
        
        viewModelScope.launch {
            // Ensure we have a conversation ID
            var conversationId = _currentConversationId.value
            Log.d("ChatViewModel", "Current conversation ID: $conversationId")
            
            // Track if this is a new conversation (for auto-naming)
            var isFirstMessage = false
            
            if (conversationId == null) {
                Log.d("ChatViewModel", "Creating new conversation...")
                val modelName = loadedModel.value?.name ?: "Unknown"
                val title = "New Chat ${System.currentTimeMillis()}"
                conversationId = chatRepository.createConversation(title, modelName)
                Log.d("ChatViewModel", "New conversation created with ID: $conversationId")
                _currentConversationId.value = conversationId
                loadMessages(conversationId)
                isFirstMessage = true
                // Small delay to ensure conversation is created
                kotlinx.coroutines.delay(100)
            } else {
                // Check if this conversation has any messages
                val currentMessages = _messages.value
                isFirstMessage = currentMessages.isEmpty()
            }
            
            Log.d("ChatViewModel", "Final conversation ID: $conversationId, isFirstMessage: $isFirstMessage")
            
            // Store the user text for auto-naming
            val userText = text
            
            // Clear input immediately
            _inputText.value = ""
            Log.d("ChatViewModel", "Input text cleared")
            
            // Use current settings
            val settings = _generationSettings.value
            Log.d("ChatViewModel", "Generation settings: temp=${settings.temperature}, maxTokens=${settings.maxTokens}")
            
            Log.d("ChatViewModel", "Calling sendMessageUseCase for conversation: $conversationId")
            
            try {
                sendMessageUseCase(
                    conversationId = conversationId,
                    userMessage = userText,
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens,
                    topP = settings.topP,
                    topK = settings.topK,
                    systemPrompt = settings.systemPrompt
                ).collect { state ->
                    Log.d("ChatViewModel", "Received generation state: $state (${state.javaClass.simpleName})")
                    _generationState.value = state
                    
                    // Reset to idle after completion or error
                    when (state) {
                        is GenerationState.Complete -> {
                            Log.d("ChatViewModel", "Generation complete with text: '${state.text}'")
                            
                            // Auto-generate chat name from first message
                            if (isFirstMessage) {
                                updateConversationTitle(conversationId, userText)
                            }
                            
                            Log.d("ChatViewModel", "Resetting to Idle after 100ms delay")
                            kotlinx.coroutines.delay(100)
                            _generationState.value = GenerationState.Idle
                            Log.d("ChatViewModel", "State reset to Idle complete")
                        }
                        is GenerationState.Error -> {
                            Log.d("ChatViewModel", "Generation error: ${state.message}")
                            Log.d("ChatViewModel", "Resetting to Idle after 3000ms delay")
                            kotlinx.coroutines.delay(3000)
                            _generationState.value = GenerationState.Idle
                            Log.d("ChatViewModel", "State reset to Idle complete")
                        }
                        else -> {
                            Log.d("ChatViewModel", "Keeping state as is: $state")
                        }
                    }
                }
                Log.d("ChatViewModel", "sendMessageUseCase flow collection completed")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "EXCEPTION during generation", e)
                Log.e("ChatViewModel", "Exception type: ${e.javaClass.name}")
                Log.e("ChatViewModel", "Exception message: ${e.message}")
                e.printStackTrace()
                _generationState.value = GenerationState.Error("Error: ${e.message}")
                kotlinx.coroutines.delay(3000)
                _generationState.value = GenerationState.Idle
            }
            
            Log.d("ChatViewModel", "=== SEND MESSAGE END ===")
        }
    }
    
    fun stopGeneration() {
        Log.d("ChatViewModel", "Stopping generation")
        viewModelScope.launch {
            inferenceRepository.stopGeneration()
            // Always reset to Idle state
            _generationState.value = GenerationState.Idle
        }
    }
    
    private fun updateConversationTitle(conversationId: Long, userMessage: String) {
        viewModelScope.launch {
            try {
                val conversation = chatRepository.getConversation(conversationId)
                if (conversation != null) {
                    val newTitle = ChatNameGenerator.generateSmartTitle(userMessage)
                    val updated = conversation.copy(
                        title = newTitle,
                        updatedAt = System.currentTimeMillis()
                    )
                    chatRepository.updateConversation(updated)
                    Log.d("ChatViewModel", "Updated conversation title to: $newTitle")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to update conversation title", e)
            }
        }
    }
    
    fun updateGenerationParams(
        temp: Float? = null,
        tokens: Int? = null,
        p: Float? = null,
        k: Int? = null
    ) {
        viewModelScope.launch {
            val currentSettings = _generationSettings.value
            val newSettings = currentSettings.copy(
                temperature = temp ?: currentSettings.temperature,
                maxTokens = tokens ?: currentSettings.maxTokens,
                topP = p ?: currentSettings.topP,
                topK = k ?: currentSettings.topK
            )
            _generationSettings.value = newSettings
            generationPreferences.saveSettings(newSettings)
        }
    }
    
    fun clearAllHistory() {
        viewModelScope.launch {
            chatRepository.clearAllHistory()
            // Start a new conversation after clearing
            startNewConversation()
        }
    }
    
    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            chatRepository.deleteConversation(conversationId)
            
            // If we deleted the current conversation, create a new one
            if (_currentConversationId.value == conversationId) {
                startNewConversation()
            }
        }
    }
}
