package com.androgpt.yaser.data.local.dao

import androidx.room.*
import com.androgpt.yaser.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long
    
    @Update
    suspend fun update(message: MessageEntity)
    
    @Delete
    suspend fun delete(message: MessageEntity)
    
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getByConversationId(conversationId: Long): Flow<List<MessageEntity>>
    
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversationId(conversationId: Long)
    
    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
