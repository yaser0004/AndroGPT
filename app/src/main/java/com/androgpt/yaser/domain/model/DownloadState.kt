package com.androgpt.yaser.domain.model

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Paused(val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
    object Cancelled : DownloadState()
}
