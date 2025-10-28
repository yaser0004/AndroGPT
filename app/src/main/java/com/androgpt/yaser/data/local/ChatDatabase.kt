package com.androgpt.yaser.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.androgpt.yaser.data.local.dao.ConversationDao
import com.androgpt.yaser.data.local.dao.MessageDao
import com.androgpt.yaser.data.local.entity.ConversationEntity
import com.androgpt.yaser.data.local.entity.MessageEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
