#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include <memory>
#include <mutex>
#include <atomic>
#include <cstring>

// llama.cpp headers
#include "llama.h"
#include "common.h"
#include "sampling.h"

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * Map common emoji codepoints to text-based symbolic representations
 * Returns nullptr if not a recognized emoji
 */
static const char* emojiToSymbolic(uint32_t codepoint) {
    switch (codepoint) {
        // Smileys
        case 0x1F600: return ":D";        // 😀 grinning face
        case 0x1F601: return ":D";        // 😁 beaming face
        case 0x1F602: return ":'D";       // 😂 tears of joy
        case 0x1F603: return ":D";        // 😃 grinning face with big eyes
        case 0x1F604: return ":)";        // 😄 grinning face with smiling eyes
        case 0x1F605: return "^^";        // 😅 grinning face with sweat
        case 0x1F606: return "XD";        // 😆 grinning squinting face
        case 0x1F607: return "O:)";       // 😇 smiling face with halo
        case 0x1F609: return ";)";        // 😉 winking face
        case 0x1F60A: return ":)";        // 😊 smiling face with smiling eyes
        case 0x1F60B: return ":P";        // 😋 face savoring food
        case 0x1F60D: return "<3";        // 😍 smiling face with heart-eyes
        case 0x1F60E: return "B)";        // 😎 smiling face with sunglasses
        case 0x1F60F: return ";)";        // 😏 smirking face
        case 0x1F610: return ":|";        // 😐 neutral face
        case 0x1F612: return ":/";        // 😒 unamused face
        case 0x1F613: return "^^'";       // 😓 downcast face with sweat
        case 0x1F614: return "-_-";       // 😔 pensive face
        case 0x1F618: return ":*";        // 😘 face blowing a kiss
        case 0x1F61A: return ":*";        // 😚 kissing face
        case 0x1F61C: return ";P";        // 😜 winking face with tongue
        case 0x1F61D: return "XP";        // 😝 squinting face with tongue
        case 0x1F620: return ">:(";       // 😠 angry face
        case 0x1F621: return ">:O";       // 😡 pouting face
        case 0x1F622: return ":'(";       // 😢 crying face
        case 0x1F62D: return "T_T";       // 😭 loudly crying face
        case 0x1F631: return "O_O";       // 😱 face screaming in fear
        case 0x1F633: return "O.O";       // 😳 flushed face
        case 0x1F642: return ":)";        // 🙂 slightly smiling face
        case 0x1F643: return "(:";        // 🙃 upside-down face (changed from "(:" to "(")
        case 0x1F644: return "-_-";       // 🙄 face with rolling eyes
        
        // Hearts & Symbols
        case 0x2764:  return "<3";        // ❤ red heart
        case 0x1F495: return "<3<3";      // 💕 two hearts
        case 0x1F496: return "<3*";       // 💖 sparkling heart
        case 0x1F497: return "<3~";       // 💗 growing heart
        case 0x1F498: return "<3!";       // 💘 heart with arrow
        case 0x1F499: return "<3";        // 💙 blue heart
        case 0x1F49A: return "<3";        // 💚 green heart
        case 0x1F49B: return "<3";        // 💛 yellow heart
        case 0x1F49C: return "<3";        // 💜 purple heart
        case 0x1F49D: return "<3";        // 💝 heart with ribbon
        case 0x1F49E: return "<3";        // 💞 revolving hearts
        case 0x1F49F: return "</3";       // 💟 heart decoration
        case 0x1F494: return "</3";       // 💔 broken heart
        
        // Hands & Gestures
        case 0x1F44D: return "(y)";       // 👍 thumbs up
        case 0x1F44E: return "(n)";       // 👎 thumbs down
        case 0x1F44C: return "OK";        // 👌 OK hand
        case 0x1F44F: return "*clap*";    // 👏 clapping hands
        case 0x1F64F: return "*pray*";    // 🙏 folded hands
        case 0x270C:  return "V";         // ✌ victory hand
        case 0x1F44B: return "*wave*";    // 👋 waving hand
        case 0x1F91D: return "*shake*";   // 🤝 handshake
        
        // Common symbols
        case 0x2705:  return "[OK]";      // ✅ check mark
        case 0x274C:  return "[X]";       // ❌ cross mark
        case 0x2B50:  return "*";         // ⭐ star
        case 0x1F525: return "*fire*";    // 🔥 fire
        case 0x1F4AF: return "100";       // 💯 hundred points
        case 0x1F389: return "*party*";   // 🎉 party popper
        
        default: return nullptr;
    }
}

/**
 * Safely create a Java string from UTF-8 bytes with emoji to symbolic conversion
 * Converts emojis to text symbols like :) <3 etc. to avoid JNI Modified UTF-8 crashes
 */
static jstring safeNewStringUTF(JNIEnv* env, const char* bytes) {
    if (bytes == nullptr || bytes[0] == '\0') {
        LOGW("safeNewStringUTF: empty input");
        return env->NewStringUTF("");
    }
    
    LOGI("safeNewStringUTF: input length=%zu", strlen(bytes));
    
    // Convert UTF-8 to safe ASCII representation, replacing emojis with symbolic text
    std::string result;
    const unsigned char* ptr = (const unsigned char*)bytes;
    
    while (*ptr) {
        unsigned char c = *ptr;
        
        if (c <= 0x7F) {
            // 1-byte sequence (ASCII) - keep as is
            result += (char)c;
            ptr++;
        } else if ((c & 0xE0) == 0xC0) {
            // 2-byte sequence - keep as is (safe for Modified UTF-8)
            if (ptr[1] && (ptr[1] & 0xC0) == 0x80) {
                result += (char)ptr[0];
                result += (char)ptr[1];
                ptr += 2;
            } else {
                LOGW("Invalid 2-byte sequence at position %zu", ptr - (const unsigned char*)bytes);
                ptr++; // Invalid, skip
            }
        } else if ((c & 0xF0) == 0xE0) {
            // 3-byte sequence - keep as is (safe for Modified UTF-8)
            if (ptr[1] && ptr[2] && (ptr[1] & 0xC0) == 0x80 && (ptr[2] & 0xC0) == 0x80) {
                result += (char)ptr[0];
                result += (char)ptr[1];
                result += (char)ptr[2];
                ptr += 3;
            } else {
                LOGW("Invalid 3-byte sequence at position %zu", ptr - (const unsigned char*)bytes);
                ptr++; // Invalid, skip
            }
        } else if ((c & 0xF8) == 0xF0) {
            // 4-byte sequence (emoji!) - convert to symbolic representation
            if (ptr[1] && ptr[2] && ptr[3] && 
                (ptr[1] & 0xC0) == 0x80 && (ptr[2] & 0xC0) == 0x80 && (ptr[3] & 0xC0) == 0x80) {
                
                // Decode the codepoint
                uint32_t codepoint = ((c & 0x07) << 18) | 
                                    ((ptr[1] & 0x3F) << 12) | 
                                    ((ptr[2] & 0x3F) << 6) | 
                                    (ptr[3] & 0x3F);
                
                LOGI("Found emoji: codepoint=0x%X", codepoint);
                
                // Try to map to symbolic emoji
                const char* symbolic = emojiToSymbolic(codepoint);
                if (symbolic != nullptr) {
                    LOGI("Converted to symbolic: %s", symbolic);
                    result += symbolic;
                } else {
                    // Unknown emoji - use generic representation
                    LOGW("Unknown emoji codepoint: 0x%X, using [emoji]", codepoint);
                    result += "[emoji]";
                }
                
                ptr += 4;
            } else {
                LOGW("Invalid 4-byte sequence at position %zu", ptr - (const unsigned char*)bytes);
                ptr++; // Invalid, skip
            }
        } else {
            // Invalid UTF-8 start byte (like 0x8a) - skip it
            LOGW("Invalid UTF-8 start byte: 0x%02X", c);
            ptr++;
        }
    }
    
    LOGI("safeNewStringUTF: result length=%zu, content=%s", result.length(), result.c_str());
    
    // Use regular NewStringUTF since we've sanitized the string
    if (result.empty()) {
        return env->NewStringUTF("");
    }
    
    return env->NewStringUTF(result.c_str());
}

// Global state
static std::mutex g_mutex;
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::atomic<bool> g_should_stop{false};
static common_params g_params;

extern "C" {

/**
 * Initialize the LLM library
 */
JNIEXPORT jboolean JNICALL
Java_com_androgpt_yaser_data_inference_LlamaEngine_nativeInit(
        JNIEnv* env,
        jobject /* this */) {
    LOGI("Initializing Llama native library");
    
    // Initialize llama backend
    llama_backend_init();
    llama_numa_init(GGML_NUMA_STRATEGY_DISABLED);
    
    LOGI("Llama backend initialized successfully");
    return JNI_TRUE;
}

/**
 * Load a model from file path
 */
JNIEXPORT jboolean JNICALL
Java_com_androgpt_yaser_data_inference_LlamaEngine_nativeLoadModel(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath,
        jint nThreads,
        jint nGpuLayers,
        jint contextSize) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);
    LOGI("Threads: %d, GPU Layers: %d, Context: %d", nThreads, nGpuLayers, contextSize);
    
    // Free existing model if any
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    
    // Set up model parameters
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;
    
    // Load model using new API
    g_model = llama_model_load_from_file(path, model_params);
    if (!g_model) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }
    
    // Set up context parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = contextSize;
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;
    
    // Create context using new API
    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }
    
    // Initialize default params
    g_params = common_params();
    g_params.model.path = path;
    g_params.n_ctx = contextSize;
    g_params.cpuparams.n_threads = nThreads;
    
    env->ReleaseStringUTFChars(modelPath, path);
    
    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

/**
 * Unload the current model
 */
JNIEXPORT void JNICALL
Java_com_androgpt_yaser_data_inference_LlamaEngine_nativeUnloadModel(
        JNIEnv* env,
        jobject /* this */) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    LOGI("Unloading model");
    
    // Free llama.cpp resources
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    
    LOGI("Model unloaded successfully");
}

/**
 * Generate text completion
 */
JNIEXPORT jstring JNICALL
Java_com_androgpt_yaser_data_inference_LlamaEngine_nativeGenerate(
        JNIEnv* env,
        jobject /* this */,
        jstring prompt,
        jint maxTokens,
        jfloat temperature,
        jfloat topP,
        jint topK) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (!g_model || !g_ctx) {
        LOGE("Model not loaded");
        return safeNewStringUTF(env, "");
    }
    
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Generating with prompt: %s", promptStr);
    LOGI("Max tokens: %d, Temperature: %.2f", maxTokens, temperature);
    
    // Tokenize prompt using common_tokenize
    std::vector<llama_token> tokens_list = common_tokenize(g_ctx, std::string(promptStr), true);
    env->ReleaseStringUTFChars(prompt, promptStr);
    
    const int n_ctx = llama_n_ctx(g_ctx);
    const int n_predict = maxTokens;
    
    // Prepare sampling
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = false;
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    
    // Create batch
    llama_batch batch = llama_batch_init(n_ctx, 0, 1);
    
    // Get vocab for token operations
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    
    // Add prompt tokens to batch
    for (size_t i = 0; i < tokens_list.size(); i++) {
        common_batch_add(batch, tokens_list[i], i, {0}, false);
    }
    
    // Prepare for generation
    batch.logits[batch.n_tokens - 1] = true;
    
    if (llama_decode(g_ctx, batch) != 0) {
        LOGE("Failed to decode");
        llama_batch_free(batch);
        llama_sampler_free(smpl);
        return safeNewStringUTF(env, "");
    }
    
    int n_cur = batch.n_tokens;
    int n_decode = 0;
    std::string result;
    
    // Generation loop
    while (n_cur <= n_ctx && n_decode < n_predict) {
        // Sample next token
        const llama_token new_token_id = llama_sampler_sample(smpl, g_ctx, -1);
        
        // Check for EOS
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }
        
        // Convert token to text
        std::string piece = common_token_to_piece(g_ctx, new_token_id);
        result.append(piece);
        
        // Check for Phi-3 stop sequences
        if (result.find("<|end|>") != std::string::npos ||
            result.find("<|user|>") != std::string::npos ||
            result.find("<|assistant|>") != std::string::npos ||
            result.find("<|system|>") != std::string::npos) {
            LOGI("Stop sequence detected, ending generation");
            break;
        }
        
        // Prepare next batch
        common_batch_clear(batch);
        common_batch_add(batch, new_token_id, n_cur, {0}, true);
        
        n_decode++;
        n_cur++;
        
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Failed to decode at position %d", n_cur);
            break;
        }
    }
    
    llama_batch_free(batch);
    llama_sampler_free(smpl);
    
    LOGI("Generated %d tokens", n_decode);
    return safeNewStringUTF(env, result.c_str());
}

/**
 * Generate text with streaming callback
 */
JNIEXPORT void JNICALL
Java_com_androgpt_yaser_data_inference_LlamaEngine_nativeGenerateStream(
        JNIEnv* env,
        jobject thiz,
        jstring prompt,
        jint maxTokens,
        jfloat temperature,
        jfloat topP,
        jint topK,
        jobject callback) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (!g_model || !g_ctx) {
        LOGE("Model not loaded");
        return;
    }
    
    g_should_stop.store(false);
    
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Streaming generation with prompt: %s", promptStr);
    
    // **CRITICAL FIX: Clear KV cache before each generation**
    llama_memory_t mem = llama_get_memory(g_ctx);
    llama_memory_clear(mem, false);  // Clear metadata but keep data buffers
    LOGI("KV cache cleared");
    
    // Get callback methods
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "()V");
    
    // Tokenize prompt using common_tokenize
    std::vector<llama_token> tokens_list = common_tokenize(g_ctx, std::string(promptStr), true);
    env->ReleaseStringUTFChars(prompt, promptStr);
    
    const int n_ctx = llama_n_ctx(g_ctx);
    const int n_predict = maxTokens;
    
    // Get vocab for token operations
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    
    // Prepare sampling
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = false;
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    
    // Create batch
    llama_batch batch = llama_batch_init(n_ctx, 0, 1);
    
    // Add prompt tokens to batch
    for (size_t i = 0; i < tokens_list.size(); i++) {
        common_batch_add(batch, tokens_list[i], i, {0}, false);
    }
    
    // Prepare for generation
    batch.logits[batch.n_tokens - 1] = true;
    
    LOGI("Decoding initial batch with %d tokens...", batch.n_tokens);
    int decode_result = llama_decode(g_ctx, batch);
    if (decode_result != 0) {
        LOGE("Failed to decode initial batch, error code: %d", decode_result);
        LOGE("Context size: %d, Batch tokens: %d", n_ctx, batch.n_tokens);
        llama_batch_free(batch);
        llama_sampler_free(smpl);
        
        // Call error callback
        jstring errorMsg = safeNewStringUTF(env, "Failed to decode prompt");
        jclass callbackClass = env->GetObjectClass(callback);
        // Try to call onComplete to prevent hanging
        jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "()V");
        env->CallVoidMethod(callback, onCompleteMethod);
        env->DeleteLocalRef(errorMsg);
        return;
    }
    LOGI("Initial batch decoded successfully");
    
    int n_cur = batch.n_tokens;
    int n_decode = 0;
    std::string accumulated_text;
    
    // Generation loop with streaming
    while (n_cur <= n_ctx && n_decode < n_predict && !g_should_stop.load()) {
        // Sample next token
        const llama_token new_token_id = llama_sampler_sample(smpl, g_ctx, -1);
        
        // Check for EOS
        if (llama_vocab_is_eog(vocab, new_token_id)) {
            break;
        }
        
        // Convert token to text
        std::string token_str = common_token_to_piece(g_ctx, new_token_id);
        accumulated_text += token_str;
        
        // Check for Phi-3 stop sequences
        if (accumulated_text.find("<|end|>") != std::string::npos ||
            accumulated_text.find("<|user|>") != std::string::npos ||
            accumulated_text.find("<|assistant|>") != std::string::npos ||
            accumulated_text.find("<|system|>") != std::string::npos) {
            LOGI("Stop sequence detected, ending generation");
            break;
        }
        
        // Stream the token if not empty - use safe string conversion
        if (!token_str.empty()) {
            LOGI("Token before conversion: length=%zu, first_byte=0x%02X", token_str.length(), (unsigned char)token_str[0]);
            jstring jtoken = safeNewStringUTF(env, token_str.c_str());
            if (jtoken != nullptr) {
                // Log the Java string length to verify conversion
                jsize jlen = env->GetStringUTFLength(jtoken);
                LOGI("Java string length after conversion: %d", jlen);
                
                env->CallVoidMethod(callback, onTokenMethod, jtoken);
                // Check for exceptions in Java callback
                if (env->ExceptionCheck()) {
                    LOGE("Exception in onToken callback, clearing and continuing");
                    env->ExceptionClear();
                }
                env->DeleteLocalRef(jtoken);
            } else {
                LOGE("safeNewStringUTF returned nullptr for token");
            }
        }
        
        // Prepare next batch
        common_batch_clear(batch);
        common_batch_add(batch, new_token_id, n_cur, {0}, true);
        
        n_decode++;
        n_cur++;
        
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Failed to decode at position %d", n_cur);
            break;
        }
    }
    
    llama_batch_free(batch);
    llama_sampler_free(smpl);
    
    // Call completion callback
    env->CallVoidMethod(callback, onCompleteMethod);
    
    LOGI("Streaming complete. Generated %d tokens", n_decode);
}

/**
 * Stop ongoing generation
 */
JNIEXPORT void JNICALL
Java_com_androgpt_yaser_data_inference_LlamaEngine_nativeStopGeneration(
        JNIEnv* env,
        jobject /* this */) {
    LOGI("Stopping generation");
    g_should_stop.store(true);
}

/**
 * Get model information
 */
JNIEXPORT jstring JNICALL
Java_com_androgpt_yaser_data_inference_LlamaEngine_nativeGetModelInfo(
        JNIEnv* env,
        jobject /* this */) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (!g_model) {
        return safeNewStringUTF(env, "No model loaded");
    }
    
    // Get vocab and model metadata
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    const int n_vocab = llama_vocab_n_tokens(vocab);
    const int n_ctx_train = llama_model_n_ctx_train(g_model);
    const int n_embd = llama_model_n_embd(g_model);
    
    char desc[256];
    llama_model_desc(g_model, desc, sizeof(desc));
    
    // Format info string
    std::string info = "Model: ";
    info += desc;
    info += "\nVocab: " + std::to_string(n_vocab);
    info += "\nContext (train): " + std::to_string(n_ctx_train);
    info += "\nEmbedding dim: " + std::to_string(n_embd);
    
    if (g_ctx) {
        const int n_ctx = llama_n_ctx(g_ctx);
        info += "\nContext (current): " + std::to_string(n_ctx);
    }
    
    return safeNewStringUTF(env, info.c_str());
}

/**
 * Cleanup resources
 */
JNIEXPORT void JNICALL
Java_com_androgpt_yaser_data_inference_LlamaEngine_nativeCleanup(
        JNIEnv* env,
        jobject /* this */) {
    
    std::lock_guard<std::mutex> lock(g_mutex);
    LOGI("Cleaning up native resources");
    
    // Cleanup llama.cpp resources
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    
    llama_backend_free();
    
    LOGI("Native cleanup complete");
}

} // extern "C"
