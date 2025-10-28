package com.androgpt.yaser.domain.model

data class DownloadableModel(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val fileSize: Long,
    val fileName: String,
    val quantization: String,
    val creator: String = "Unknown",
    val capabilities: String = "",
    val recommendedFor: String = ""
)

// Predefined list of available models for download
object AvailableModels {
    
    // ========== META - LLAMA 3.2 MODELS ==========
    
    val LLAMA_3_2_1B_Q8_0 = DownloadableModel(
        id = "llama-3.2-1b-instruct-q8_0",
        name = "Llama 3.2 1B Q8 (Fast & Accurate)",
        description = "Meta Llama 3.2 1B Instruct, Q8_0 quantization (high quality, compact)",
        downloadUrl = "https://huggingface.co/lmstudio-community/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q8_0.gguf",
        fileSize = 1321079232L, // ~1.32 GB (actual server file size)
        fileName = "Llama-3.2-1B-Instruct-Q8_0.gguf",
        quantization = "Q8_0",
        creator = "Meta",
        capabilities = "• 1 billion parameters\n" +
                "• 128K token context window\n" +
                "• High-quality Q8 quantization\n" +
                "• Fast inference speed\n" +
                "• Excellent for general tasks\n" +
                "• Great instruction following",
        recommendedFor = "Best for: Mid-range devices, general chat, Q&A, coding snippets\n\n" +
                "Recommended settings:\n" +
                "• Context Length: 2048-4096\n" +
                "• Temperature: 0.7\n" +
                "• Works great on 4GB+ RAM devices\n" +
                "⚡ Fast and reliable"
    )
    
    val LLAMA_3_2_3B_Q6_K = DownloadableModel(
        id = "llama-3.2-3b-instruct-q6_k",
        name = "Llama 3.2 3B Q6 (Balanced)",
        description = "Meta Llama 3.2 3B Instruct, Q6_K quantization (excellent quality)",
        downloadUrl = "https://huggingface.co/lmstudio-community/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q6_K.gguf",
        fileSize = 2643853600L, // ~2.64 GB (actual server file size)
        fileName = "Llama-3.2-3B-Instruct-Q6_K.gguf",
        quantization = "Q6_K",
        creator = "Meta",
        capabilities = "• 3 billion parameters\n" +
                "• 128K token context window\n" +
                "• High-quality Q6 quantization\n" +
                "• Balanced speed and quality\n" +
                "• Strong reasoning capabilities\n" +
                "• Advanced instruction following",
        recommendedFor = "Best for: Complex tasks, coding, creative writing, technical Q&A\n\n" +
                "Recommended settings:\n" +
                "• Context Length: 4096-8192\n" +
                "• Temperature: 0.6-0.8\n" +
                "• Requires 6GB+ RAM for best performance\n" +
                "⭐ Excellent quality/size ratio"
    )
    
    // ========== HUGGINGFACE - SMOLLM2 MODELS ==========
    
    val SMOLLM2_1_7B_Q8_0 = DownloadableModel(
        id = "smollm2-1.7b-instruct-q8_0",
        name = "SmolLM2 1.7B Q8 (Efficient)",
        description = "HuggingFace SmolLM2 1.7B Instruct, Q8_0 quantization (optimized for mobile)",
        downloadUrl = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q8_0.gguf",
        fileSize = 1820414944L, // ~1.82 GB (actual server file size)
        fileName = "SmolLM2-1.7B-Instruct-Q8_0.gguf",
        quantization = "Q8_0",
        creator = "HuggingFace",
        capabilities = "• 1.7 billion parameters\n" +
                "• 8K token context window\n" +
                "• Optimized for mobile devices\n" +
                "• Efficient Q8 quantization\n" +
                "• Good general performance\n" +
                "• Compact and fast",
        recommendedFor = "Best for: Mobile devices, general chat, basic coding, quick responses\n\n" +
                "Recommended settings:\n" +
                "• Context Length: 2048-4096\n" +
                "• Temperature: 0.7-0.8\n" +
                "• Ideal for 4-6GB RAM devices\n" +
                "📱 Great mobile performance"
    )
    
    // ========== MICROSOFT - PHI-3 MODELS ==========
    
    val PHI_3_MINI_4K_Q2_K = DownloadableModel(
        id = "phi-3-mini-4k-q2_k",
        name = "Phi-3 Mini 4K Q2 (Ultra Light)",
        description = "Microsoft Phi-3 Mini 3.8B, 4K context, Q2_K quantization (smallest, fastest)",
        downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q2_K.gguf",
        fileSize = 1560000000L, // ~1.56 GB
        fileName = "Phi-3-mini-4k-instruct-q2_K.gguf",
        quantization = "Q2_K",
        creator = "Microsoft",
        capabilities = "• 3.8 billion parameters\n" +
                "• 4,096 token context window\n" +
                "• Smallest file size (~1.6GB)\n" +
                "• Fastest inference speed\n" +
                "• Optimized for low-end devices\n" +
                "• Good for basic conversations",
        recommendedFor = "Best for: Budget phones, quick responses, basic chat, simple Q&A\n\n" +
                "Recommended settings:\n" +
                "• Context Length: 1024-2048\n" +
                "• Temperature: 0.8 (compensate for quantization)\n" +
                "• Ideal for devices with <4GB RAM\n" +
                "⚡ Fastest option - great for testing"
    )
    
    val PHI_3_MINI_4K_Q3_K_M = DownloadableModel(
        id = "phi-3-mini-4k-q3_k_m",
        name = "Phi-3 Mini 4K Q3 (Light)",
        description = "Microsoft Phi-3 Mini 3.8B, 4K context, Q3_K_M quantization (light, fast)",
        downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q3_K_M.gguf",
        fileSize = 1860000000L, // ~1.86 GB
        fileName = "Phi-3-mini-4k-instruct-q3_K_M.gguf",
        quantization = "Q3_K_M",
        creator = "Microsoft",
        capabilities = "• 3.8 billion parameters\n" +
                "• 4,096 token context window\n" +
                "• Small file size (~1.9GB)\n" +
                "• Fast inference with good quality\n" +
                "• Better accuracy than Q2\n" +
                "• Good balance for mid-range devices",
        recommendedFor = "Best for: Mid-range phones, general chat, coding, creative writing\n\n" +
                "Recommended settings:\n" +
                "• Context Length: 2048-3072\n" +
                "• Temperature: 0.7-0.8\n" +
                "• Sweet spot for 4-6GB RAM devices\n" +
                "⚡ Great speed/quality balance"
    )
    
    val PHI_3_MINI_4K_Q4_K_M = DownloadableModel(
        id = "phi-3-mini-4k-q4_k_m",
        name = "Phi-3 Mini 4K Q4 (Recommended)",
        description = "Microsoft Phi-3 Mini 3.8B, 4K context, Q4_K_M quantization (recommended)",
        downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
        fileSize = 2200000000L, // ~2.2 GB
        fileName = "Phi-3-mini-4k-instruct-q4.gguf",
        quantization = "Q4_K_M",
        creator = "Microsoft",
        capabilities = "• 3.8 billion parameters\n" +
                "• 4,096 token context window\n" +
                "• Optimized for mobile/edge devices\n" +
                "• Excellent general purpose performance\n" +
                "• Good balance of speed and quality\n" +
                "• Trained on diverse high-quality data",
        recommendedFor = "Best for: General chat, Q&A, coding assistance, summarization\n\n" +
                "Recommended settings:\n" +
                "• Context Length: 2048-4096\n" +
                "• Temperature: 0.7 (balanced)\n" +
                "• GPU Layers: 20-30 for optimal speed\n" +
                "⭐ Most popular choice"
    )
    
    val PHI_3_MINI_4K_Q5_K_M = DownloadableModel(
        id = "phi-3-mini-4k-q5_k_m",
        name = "Phi-3 Mini 4K Q5 (High Quality)",
        description = "Microsoft Phi-3 Mini 3.8B, 4K context, Q5_K_M quantization (high quality)",
        downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q5_K_M.gguf",
        fileSize = 2670000000L, // ~2.67 GB
        fileName = "Phi-3-mini-4k-instruct-q5_K_M.gguf",
        quantization = "Q5_K_M",
        creator = "Microsoft",
        capabilities = "• 3.8 billion parameters\n" +
                "• 4,096 token context window\n" +
                "• Higher quality than Q4\n" +
                "• Better coherence and accuracy\n" +
                "• Good for complex tasks\n" +
                "• Minimal quality loss from FP16",
        recommendedFor = "Best for: Professional use, complex reasoning, coding, technical writing\n\n" +
                "Recommended settings:\n" +
                "• Context Length: 2048-4096\n" +
                "• Temperature: 0.6-0.7\n" +
                "• Requires 6GB+ RAM for best performance\n" +
                "🎯 Best quality/size ratio"
    )
    
    fun getAllModels(): List<DownloadableModel> = listOf(
        // Meta Llama 3.2 models
        LLAMA_3_2_1B_Q8_0,        // ~1.17 GB - Fast & accurate
        LLAMA_3_2_3B_Q6_K,        // ~2.52 GB - Balanced quality
        // HuggingFace SmolLM2 models
        SMOLLM2_1_7B_Q8_0,        // ~1.84 GB - Efficient mobile
        // Microsoft Phi-3 models
        PHI_3_MINI_4K_Q2_K,       // ~1.56 GB - Ultra light
        PHI_3_MINI_4K_Q3_K_M,     // ~1.86 GB - Light
        PHI_3_MINI_4K_Q4_K_M,     // ~2.2 GB - Recommended
        PHI_3_MINI_4K_Q5_K_M      // ~2.67 GB - High quality
    )
    
    fun getModelsByCreator(): Map<String, List<DownloadableModel>> {
        return getAllModels().groupBy { it.creator }
    }
}
