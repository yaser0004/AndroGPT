package com.androgpt.yaser.presentation.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androgpt.yaser.domain.model.ModelInfo
import com.androgpt.yaser.domain.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {
    
    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    val availableModels = _availableModels.asStateFlow()
    
    val loadedModel = modelRepository.getLoadedModel()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    
    init {
        refreshModels()
    }
    
    fun refreshModels() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val models = modelRepository.getAvailableModels()
                _availableModels.value = models
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteModel(filePath: String) {
        viewModelScope.launch {
            modelRepository.deleteModel(filePath).onSuccess {
                refreshModels()
            }.onFailure {
                _errorMessage.value = it.message
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
