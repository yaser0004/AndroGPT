package com.androgpt.yaser.domain.model

sealed class GenerationState {
    object Idle : GenerationState()
    object Loading : GenerationState()
    data class Generating(val currentText: String = "") : GenerationState()
    data class Complete(val text: String) : GenerationState()
    data class Error(val message: String) : GenerationState()
}
