package com.androgpt.yaser.domain.model

data class ModelInfo(
    val name: String,
    val filePath: String,
    val size: Long,
    val isLoaded: Boolean = false,
    val metadata: String = ""
)
