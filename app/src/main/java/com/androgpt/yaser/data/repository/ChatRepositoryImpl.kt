package com.androgpt.yaser.data.repository

import com.androgpt.yaser.data.local.dao.ConversationDao
import com.androgpt.yaser.data.local.dao.MessageDao
import com.androgpt.yaser.data.local.entity.ConversationEntity
import com.androgpt.yaser.data.local.entity.MessageEntity
import com.androgpt.yaser.domain.model.Conversation
import com.androgpt.yaser.domain.model.Message
import com.androgpt.yaser.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChatRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ChatRepository {
    
    override suspend fun createConversation(title: String, modelName: String): Long {
        val entity = ConversationEntity(
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            modelName = modelName
        )
        return conversationDao.insert(entity)
    }
    
    override suspend fun getConversation(id: Long): Conversation? {
        return conversationDao.getById(id)?.toDomain()
    }
    
    override fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAll().map { list ->
            list.map { it.toDomain() }
        }
    }
    
    override suspend fun updateConversation(conversation: Conversation) {
        conversationDao.update(conversation.toEntity())
    }
    
    override suspend fun deleteConversation(id: Long) {
        conversationDao.deleteById(id)
    }
    
    override suspend fun insertMessage(message: Message): Long {
        return messageDao.insert(message.toEntity())
    }
    
    override fun getMessagesForConversation(conversationId: Long): Flow<List<Message>> {
        return messageDao.getByConversationId(conversationId).map { list ->
            list.map { it.toDomain() }
        }
    }
    
    override suspend fun deleteAllMessages(conversationId: Long) {
        messageDao.deleteByConversationId(conversationId)
    }
    
    override suspend fun clearAllHistory() {
        conversationDao.deleteAll()
        messageDao.deleteAll()
    }
    
    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        modelName = modelName
    )
    
    private fun Conversation.toEntity() = ConversationEntity(
        id = id,
        title = title,
        createdAt = createdAt,
        updatedAt = updatedAt,
        modelName = modelName
    )
    
    private fun MessageEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        content = content,
        isUser = isUser,
        timestamp = timestamp,
        tokenCount = tokenCount
    )
    
    private fun Message.toEntity() = MessageEntity(
        id = id,
        conversationId = conversationId,
        content = content,
        isUser = isUser,
        timestamp = timestamp,
        tokenCount = tokenCount
    )
}
