package com.androgpt.yaser.di

import com.androgpt.yaser.data.inference.LlamaEngine
import com.androgpt.yaser.data.inference.ModelManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InferenceModule {
    
    @Provides
    @Singleton
    fun provideLlamaEngine(): LlamaEngine {
        return LlamaEngine()
    }
}
