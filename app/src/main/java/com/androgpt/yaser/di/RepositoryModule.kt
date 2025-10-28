package com.androgpt.yaser.di

import com.androgpt.yaser.data.repository.ChatRepositoryImpl
import com.androgpt.yaser.data.repository.InferenceRepositoryImpl
import com.androgpt.yaser.data.repository.ModelRepositoryImpl
import com.androgpt.yaser.domain.repository.ChatRepository
import com.androgpt.yaser.domain.repository.InferenceRepository
import com.androgpt.yaser.domain.repository.ModelRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository
    
    @Binds
    @Singleton
    abstract fun bindModelRepository(
        modelRepositoryImpl: ModelRepositoryImpl
    ): ModelRepository
    
    @Binds
    @Singleton
    abstract fun bindInferenceRepository(
        inferenceRepositoryImpl: InferenceRepositoryImpl
    ): InferenceRepository
}
