package com.androgpt.yaser.di

import android.content.Context
import androidx.room.Room
import com.androgpt.yaser.data.local.ChatDatabase
import com.androgpt.yaser.data.local.GenerationPreferences
import com.androgpt.yaser.data.local.ModelPreferences
import com.androgpt.yaser.data.local.dao.ConversationDao
import com.androgpt.yaser.data.local.dao.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideChatDatabase(
        @ApplicationContext context: Context
    ): ChatDatabase {
        return Room.databaseBuilder(
            context,
            ChatDatabase::class.java,
            "androgpt_chat_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    @Singleton
    fun provideConversationDao(database: ChatDatabase): ConversationDao {
        return database.conversationDao()
    }
    
    @Provides
    @Singleton
    fun provideMessageDao(database: ChatDatabase): MessageDao {
        return database.messageDao()
    }
    
    @Provides
    @Singleton
    fun provideModelPreferences(
        @ApplicationContext context: Context
    ): ModelPreferences {
        return ModelPreferences(context)
    }
    
    @Provides
    @Singleton
    fun provideGenerationPreferences(
        @ApplicationContext context: Context
    ): GenerationPreferences {
        return GenerationPreferences(context)
    }
}
