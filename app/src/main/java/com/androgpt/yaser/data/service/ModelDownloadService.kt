package com.androgpt.yaser.data.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.androgpt.yaser.data.notification.DownloadNotificationManager
import com.androgpt.yaser.data.repository.ModelDownloadRepository
import com.androgpt.yaser.domain.model.DownloadState
import com.androgpt.yaser.domain.model.DownloadableModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class ModelDownloadService : Service() {
    
    @Inject
    lateinit var downloadRepository: ModelDownloadRepository
    
    @Inject
    lateinit var notificationManager: DownloadNotificationManager
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeDownloads = mutableMapOf<String, Job>()
    
    companion object {
        private const val TAG = "ModelDownloadService"
        const val ACTION_START_DOWNLOAD = "com.androgpt.yaser.START_DOWNLOAD"
        const val ACTION_PAUSE_DOWNLOAD = "com.androgpt.yaser.PAUSE_DOWNLOAD"
        const val ACTION_RESUME_DOWNLOAD = "com.androgpt.yaser.RESUME_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.androgpt.yaser.CANCEL_DOWNLOAD"
        const val ACTION_DOWNLOAD_UPDATE = "com.androgpt.yaser.DOWNLOAD_UPDATE"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_MODEL_NAME = "model_name"
        const val EXTRA_MODEL_DESCRIPTION = "model_description"
        const val EXTRA_MODEL_URL = "model_url"
        const val EXTRA_MODEL_SIZE = "model_size"
        const val EXTRA_MODEL_FILENAME = "model_filename"
        const val EXTRA_MODEL_QUANTIZATION = "model_quantization"
        const val EXTRA_MODEL_CREATOR = "model_creator"
        const val EXTRA_MODEL_CAPABILITIES = "model_capabilities"
        const val EXTRA_MODEL_RECOMMENDED = "model_recommended"
        const val EXTRA_DOWNLOAD_STATE = "download_state"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_DOWNLOADED_BYTES = "downloaded_bytes"
        const val EXTRA_TOTAL_BYTES = "total_bytes"
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val model = extractModelFromIntent(intent)
                if (model != null) {
                    startDownload(model)
                }
            }
            ACTION_PAUSE_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
                if (modelId != null) {
                    pauseDownload(modelId)
                }
            }
            ACTION_RESUME_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
                if (modelId != null) {
                    resumeDownload(modelId)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID)
                if (modelId != null) {
                    cancelDownload(modelId)
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun extractModelFromIntent(intent: Intent): DownloadableModel? {
        return try {
            DownloadableModel(
                id = intent.getStringExtra(EXTRA_MODEL_ID) ?: return null,
                name = intent.getStringExtra(EXTRA_MODEL_NAME) ?: return null,
                description = intent.getStringExtra(EXTRA_MODEL_DESCRIPTION) ?: "",
                downloadUrl = intent.getStringExtra(EXTRA_MODEL_URL) ?: return null,
                fileSize = intent.getLongExtra(EXTRA_MODEL_SIZE, 0L),
                fileName = intent.getStringExtra(EXTRA_MODEL_FILENAME) ?: return null,
                quantization = intent.getStringExtra(EXTRA_MODEL_QUANTIZATION) ?: "",
                creator = intent.getStringExtra(EXTRA_MODEL_CREATOR) ?: "Unknown",
                capabilities = intent.getStringExtra(EXTRA_MODEL_CAPABILITIES) ?: "",
                recommendedFor = intent.getStringExtra(EXTRA_MODEL_RECOMMENDED) ?: ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract model from intent", e)
            null
        }
    }
    
    private fun startDownload(model: DownloadableModel) {
        Log.d(TAG, "Starting download for: ${model.name}")
        
        // Cancel existing download for this model if any
        activeDownloads[model.id]?.cancel()
        
        // Start foreground service with notification
        val notificationId = notificationManager.showDownloadNotification(
            model.id,
            model.name,
            0f,
            0L,
            model.fileSize
        )
        
        try {
            ServiceCompat.startForeground(
                this,
                notificationId,
                notificationManager.createDownloadNotification(
                    model.id,
                    model.name,
                    0f,
                    0L,
                    model.fileSize
                ),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
            Log.i(TAG, "Service started in foreground for ${model.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }
        
        val job = serviceScope.launch {
            try {
                Log.i(TAG, "Starting download flow for ${model.name}")
                downloadRepository.downloadModel(model).collect { state ->
                    // Broadcast state update to UI
                    broadcastDownloadState(model.id, state)
                    
                    when (state) {
                        is DownloadState.Downloading -> {
                            notificationManager.showDownloadNotification(
                                model.id,
                                model.name,
                                state.progress,
                                state.downloadedBytes,
                                state.totalBytes
                            )
                        }
                        is DownloadState.Success -> {
                            Log.i(TAG, "Download completed for ${model.name}")
                            notificationManager.showDownloadCompleteNotification(
                                model.id,
                                model.name
                            )
                            activeDownloads.remove(model.id)
                            checkAndStopService()
                        }
                        is DownloadState.Error -> {
                            Log.e(TAG, "Download error for ${model.name}: ${state.message}")
                            notificationManager.showDownloadErrorNotification(
                                model.id,
                                model.name,
                                state.message
                            )
                            activeDownloads.remove(model.id)
                            checkAndStopService()
                        }
                        is DownloadState.Cancelled -> {
                            Log.i(TAG, "Download cancelled for ${model.name}")
                            notificationManager.cancelNotification(model.id)
                            activeDownloads.remove(model.id)
                            checkAndStopService()
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download exception for ${model.name}", e)
                notificationManager.showDownloadErrorNotification(
                    model.id,
                    model.name,
                    e.message ?: "Unknown error"
                )
                activeDownloads.remove(model.id)
                checkAndStopService()
            }
        }
        
        // Add job to map BEFORE it can complete
        activeDownloads[model.id] = job
        Log.i(TAG, "Download job added for ${model.name}, active downloads: ${activeDownloads.size}")
    }
    
    private fun cancelDownload(modelId: String) {
        Log.d(TAG, "Cancelling download for: $modelId")
        activeDownloads[modelId]?.cancel()
        activeDownloads.remove(modelId)
        
        // Also cancel in repository
        downloadRepository.cancelDownload(modelId)
        
        notificationManager.cancelNotification(modelId)
        checkAndStopService()
    }
    
    private fun pauseDownload(modelId: String) {
        Log.i(TAG, "Pausing download for: $modelId")
        downloadRepository.pauseDownload(modelId)
    }
    
    private fun resumeDownload(modelId: String) {
        Log.i(TAG, "Resuming download for: $modelId")
        downloadRepository.resumeDownload(modelId)
    }
    
    private fun checkAndStopService() {
        Log.d(TAG, "checkAndStopService called, active downloads: ${activeDownloads.size}")
        if (activeDownloads.isEmpty()) {
            Log.i(TAG, "No active downloads, stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            Log.i(TAG, "Still have ${activeDownloads.size} active download(s), keeping service running")
        }
    }
    
    private fun broadcastDownloadState(modelId: String, state: DownloadState) {
        val intent = Intent(ACTION_DOWNLOAD_UPDATE).apply {
            putExtra(EXTRA_MODEL_ID, modelId)
            when (state) {
                is DownloadState.Downloading -> {
                    putExtra(EXTRA_DOWNLOAD_STATE, "downloading")
                    putExtra(EXTRA_PROGRESS, state.progress)
                    putExtra(EXTRA_DOWNLOADED_BYTES, state.downloadedBytes)
                    putExtra(EXTRA_TOTAL_BYTES, state.totalBytes)
                }
                is DownloadState.Success -> {
                    putExtra(EXTRA_DOWNLOAD_STATE, "success")
                }
                is DownloadState.Error -> {
                    putExtra(EXTRA_DOWNLOAD_STATE, "error")
                    putExtra(EXTRA_ERROR_MESSAGE, state.message)
                }
                is DownloadState.Cancelled -> {
                    putExtra(EXTRA_DOWNLOAD_STATE, "cancelled")
                }
                is DownloadState.Paused -> {
                    putExtra(EXTRA_DOWNLOAD_STATE, "paused")
                    putExtra(EXTRA_DOWNLOADED_BYTES, state.downloadedBytes)
                    putExtra(EXTRA_TOTAL_BYTES, state.totalBytes)
                }
                else -> {
                    putExtra(EXTRA_DOWNLOAD_STATE, "idle")
                }
            }
        }
        sendBroadcast(intent)
    }
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }
}
