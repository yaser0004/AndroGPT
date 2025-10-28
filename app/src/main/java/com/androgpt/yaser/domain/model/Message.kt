package com.androgpt.yaser.domain.model

data class Message(
    val id: Long = 0,
    val conversationId: Long,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenCount: Int = 0
)
