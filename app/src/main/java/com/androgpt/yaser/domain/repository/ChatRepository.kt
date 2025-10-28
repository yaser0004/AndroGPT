package com.androgpt.yaser.domain.repository

import com.androgpt.yaser.domain.model.Conversation
import com.androgpt.yaser.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    
    suspend fun createConversation(title: String, modelName: String): Long
    
    suspend fun getConversation(id: Long): Conversation?
    
    fun getAllConversations(): Flow<List<Conversation>>
    
    suspend fun updateConversation(conversation: Conversation)
    
    suspend fun deleteConversation(id: Long)
    
    suspend fun insertMessage(message: Message): Long
    
    fun getMessagesForConversation(conversationId: Long): Flow<List<Message>>
    
    suspend fun deleteAllMessages(conversationId: Long)
    
    suspend fun clearAllHistory()
}
