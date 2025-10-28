package com.androgpt.yaser.data.repository

import android.content.Context
import android.util.Log
import com.androgpt.yaser.data.notification.DownloadNotificationManager
import com.androgpt.yaser.domain.model.DownloadState
import com.androgpt.yaser.domain.model.DownloadableModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: DownloadNotificationManager
) {
    companion object {
        private const val TAG = "ModelDownloadRepo"
        private const val TEMP_SUFFIX = ".tmp"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val modelsDir = File(context.filesDir, "models").apply {
        if (!exists()) mkdirs()
    }
    
    private val activeDownloads = mutableMapOf<String, DownloadControl>()
    
    data class DownloadControl(
        var isActive: Boolean = true,
        var isPaused: Boolean = false
    )

    
    fun downloadModel(model: DownloadableModel): Flow<DownloadState> = flow {
        try {
            val control = DownloadControl()
            activeDownloads[model.id] = control
            
            val destinationFile = File(modelsDir, model.fileName)
            val tempFile = File(modelsDir, model.fileName + TEMP_SUFFIX)
            
            // Check if final file already exists and is complete
            if (destinationFile.exists() && destinationFile.length() == model.fileSize) {
                Log.i(TAG, "Model already downloaded: ${model.name}")
                emit(DownloadState.Success)
                activeDownloads.remove(model.id)
                return@flow
            }
            
            // Get existing bytes if resuming, but validate temp file isn't corrupted
            var existingBytes = if (tempFile.exists()) tempFile.length() else 0L
            
            // If temp file is larger than expected, it's corrupted - delete it
            if (existingBytes > model.fileSize) {
                Log.w(TAG, "Temp file corrupted (${existingBytes} > ${model.fileSize}), deleting and restarting")
                tempFile.delete()
                existingBytes = 0L
            }
            
            Log.i(TAG, "Starting download for ${model.name}, resuming from $existingBytes bytes")
            
            emit(DownloadState.Downloading(
                existingBytes.toFloat() / model.fileSize.toFloat(),
                existingBytes,
                model.fileSize
            ))
            
            // Build request with Range header for resume support
            val requestBuilder = Request.Builder().url(model.downloadUrl)
            if (existingBytes > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
                Log.i(TAG, "Resuming download with Range: bytes=$existingBytes-")
            }
            
            val call = client.newCall(requestBuilder.build())
            val response = call.execute()
            
            // Handle HTTP 416 (Range Not Satisfiable) - temp file is already complete or corrupted
            if (response.code == 416) {
                Log.w(TAG, "HTTP 416: Range not satisfiable. Temp file size: $existingBytes, Expected: ${model.fileSize}")
                response.close()
                
                // Check if temp file size matches expected
                if (existingBytes >= model.fileSize) {
                    // File is complete, just rename it
                    if (tempFile.renameTo(destinationFile)) {
                        Log.i(TAG, "Temp file was already complete, renamed to final")
                        emit(DownloadState.Success)
                    } else {
                        emit(DownloadState.Error("Failed to finalize complete download"))
                    }
                } else {
                    // Temp file is corrupted, delete and restart
                    Log.w(TAG, "Deleting corrupted temp file and restarting download")
                    tempFile.delete()
                    emit(DownloadState.Error("Download corrupted, please retry"))
                }
                activeDownloads.remove(model.id)
                return@flow
            }
            
            // Accept both 200 (full content) and 206 (partial content)
            if (!response.isSuccessful && response.code != 206) {
                emit(DownloadState.Error("Download failed: ${response.code}"))
                activeDownloads.remove(model.id)
                return@flow
            }
            
            val body = response.body ?: run {
                emit(DownloadState.Error("Empty response body"))
                activeDownloads.remove(model.id)
                return@flow
            }
            
            val inputStream = body.byteStream()
            // Use RandomAccessFile for append support
            val outputFile = RandomAccessFile(tempFile, "rw")
            if (existingBytes > 0 && response.code == 206) {
                // Resume: seek to end of existing file
                outputFile.seek(existingBytes)
            } else if (response.code == 200) {
                // Server doesn't support resume, start fresh
                outputFile.setLength(0)
                outputFile.seek(0)
            }
            
            val buffer = ByteArray(8192)
            var downloadedBytes = existingBytes
            var bytesRead: Int
            var lastNotificationTime = 0L
            
            try {
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    // Check if download was cancelled
                    if (!control.isActive) {
                        outputFile.close()
                        inputStream.close()
                        tempFile.delete()
                        notificationManager.cancelNotification(model.id)
                        Log.i(TAG, "Download cancelled: ${model.name}")
                        emit(DownloadState.Cancelled)
                        activeDownloads.remove(model.id)
                        return@flow
                    }
                    
                    // Check if download was paused
                    while (control.isPaused && control.isActive) {
                        Thread.sleep(100)
                    }
                    
                    // If cancelled while paused
                    if (!control.isActive) {
                        outputFile.close()
                        inputStream.close()
                        notificationManager.cancelNotification(model.id)
                        Log.i(TAG, "Download cancelled while paused: ${model.name}")
                        emit(DownloadState.Paused(downloadedBytes, model.fileSize))
                        return@flow
                    }
                    
                    outputFile.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    val progress = downloadedBytes.toFloat() / model.fileSize.toFloat()
                    
                    // Update notification and emit state every 500ms to avoid too frequent updates
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastNotificationTime > 500) {
                        notificationManager.showDownloadNotification(
                            model.id,
                            model.name,
                            progress,
                            downloadedBytes,
                            model.fileSize
                        )
                        emit(DownloadState.Downloading(progress, downloadedBytes, model.fileSize))
                        lastNotificationTime = currentTime
                    }
                }
                
                outputFile.close()
                inputStream.close()
                
                // Verify download completed successfully
                // Check total bytes downloaded (not temp file size which may be from resume)
                Log.i(TAG, "Download finished. Total downloaded: $downloadedBytes, Expected: ${model.fileSize}, Temp file size: ${tempFile.length()}")
                
                if (downloadedBytes >= model.fileSize || tempFile.length() >= model.fileSize) {
                    // Rename temp file to final file
                    if (tempFile.renameTo(destinationFile)) {
                        Log.i(TAG, "Download completed successfully: ${model.name}")
                        notificationManager.showDownloadCompleteNotification(model.id, model.name)
                        emit(DownloadState.Success)
                    } else {
                        Log.e(TAG, "Failed to rename temp file to final: ${tempFile.absolutePath} -> ${destinationFile.absolutePath}")
                        emit(DownloadState.Error("Failed to finalize download"))
                    }
                } else {
                    Log.w(TAG, "Download incomplete: downloaded=$downloadedBytes, tempFileSize=${tempFile.length()}, expected=${model.fileSize}")
                    emit(DownloadState.Error("Download incomplete: $downloadedBytes / ${model.fileSize} bytes"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error for ${model.name}", e)
                outputFile.close()
                inputStream.close()
                // Keep temp file for resume
                notificationManager.showDownloadErrorNotification(
                    model.id,
                    model.name,
                    e.message ?: "Unknown error"
                )
                emit(DownloadState.Error("Download interrupted: ${e.message}"))
            } finally {
                activeDownloads.remove(model.id)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${model.name}", e)
            notificationManager.showDownloadErrorNotification(
                model.id,
                model.name,
                e.message ?: "Unknown error"
            )
            emit(DownloadState.Error("Download failed: ${e.message}"))
            activeDownloads.remove(model.id)
        }
    }.flowOn(Dispatchers.IO)
    
    fun cancelDownload(model: DownloadableModel) {
        activeDownloads[model.id]?.isActive = false
        activeDownloads.remove(model.id)
        notificationManager.cancelNotification(model.id)
        // Delete temp file on cancel
        val tempFile = File(modelsDir, model.fileName + TEMP_SUFFIX)
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }
    
    fun cancelDownload(modelId: String) {
        activeDownloads[modelId]?.isActive = false
        activeDownloads.remove(modelId)
        Log.i(TAG, "Cancelled download: $modelId")
    }
    
    fun pauseDownload(model: DownloadableModel) {
        activeDownloads[model.id]?.isPaused = true
        Log.i(TAG, "Paused download: ${model.name}")
    }
    
    fun pauseDownload(modelId: String) {
        activeDownloads[modelId]?.isPaused = true
        Log.i(TAG, "Paused download: $modelId")
    }
    
    fun resumeDownload(model: DownloadableModel) {
        activeDownloads[model.id]?.isPaused = false
        Log.i(TAG, "Resumed download: ${model.name}")
    }
    
    fun resumeDownload(modelId: String) {
        activeDownloads[modelId]?.isPaused = false
        Log.i(TAG, "Resumed download: $modelId")
    }
    
    fun isDownloadActive(model: DownloadableModel): Boolean {
        return activeDownloads.containsKey(model.id)
    }
    
    fun isDownloadPaused(model: DownloadableModel): Boolean {
        return activeDownloads[model.id]?.isPaused == true
    }
    
    fun getPartialDownloadSize(model: DownloadableModel): Long {
        val tempFile = File(modelsDir, model.fileName + TEMP_SUFFIX)
        return if (tempFile.exists()) tempFile.length() else 0L
    }
    
    fun isModelDownloaded(model: DownloadableModel): Boolean {
        val file = File(modelsDir, model.fileName)
        // Only consider complete downloads
        return file.exists() && file.length() == model.fileSize
    }
    
    fun deleteDownloadedModel(model: DownloadableModel): Boolean {
        val file = File(modelsDir, model.fileName)
        val tempFile = File(modelsDir, model.fileName + TEMP_SUFFIX)
        tempFile.delete() // Also delete temp file if exists
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
    
    fun getModelFilePath(model: DownloadableModel): String {
        return File(modelsDir, model.fileName).absolutePath
    }
}
