package com.androgpt.yaser.domain.model

data class ModelConfig(
    val name: String,
    val filePath: String,
    val size: Long,
    val contextLength: Int = 2048,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 512,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val nThreads: Int = 4,
    val nGpuLayers: Int = 0,
    val systemPrompt: String = "You are a helpful AI assistant. Do not use emoji characters in your responses - use text-based emoticons like :) or <3 instead."
)
