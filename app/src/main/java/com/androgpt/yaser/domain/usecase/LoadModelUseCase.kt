package com.androgpt.yaser.domain.usecase

import com.androgpt.yaser.domain.model.ModelConfig
import com.androgpt.yaser.domain.repository.ModelRepository
import javax.inject.Inject

class LoadModelUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    
    suspend operator fun invoke(config: ModelConfig): Result<Unit> {
        return modelRepository.loadModel(config)
    }
}
