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
 * Safely create a Java string from UTF-8 bytes by decoding into UTF-16.
 * Handles surrogate pairs so 4-byte emoji sequences survive without crashing JNI.
 */
static jstring safeNewStringUTF(JNIEnv* env, const char* bytes) {
    static const jchar EMPTY_STR[] = {0};

    if (bytes == nullptr || bytes[0] == '\0') {
        LOGW("safeNewStringUTF: empty input");
        return env->NewString(EMPTY_STR, 0);
    }

    const unsigned char* ptr = reinterpret_cast<const unsigned char*>(bytes);
    std::vector<jchar> utf16;
    utf16.reserve(strlen(bytes));

    while (*ptr) {
        uint32_t codepoint = 0;
        unsigned char c = *ptr;

        if (c <= 0x7F) {
            codepoint = c;
            ptr++;
        } else if ((c & 0xE0) == 0xC0) {
            if ((ptr[1] & 0xC0) != 0x80) {
                LOGW("safeNewStringUTF: invalid 2-byte sequence start 0x%02X", c);
                ptr++;
                continue;
            }
            codepoint = ((c & 0x1F) << 6) | (ptr[1] & 0x3F);
            ptr += 2;
            if (codepoint < 0x80) {
                LOGW("safeNewStringUTF: overlong 2-byte sequence");
                continue;
            }
        } else if ((c & 0xF0) == 0xE0) {
            if ((ptr[1] & 0xC0) != 0x80 || (ptr[2] & 0xC0) != 0x80) {
                LOGW("safeNewStringUTF: invalid 3-byte sequence start 0x%02X", c);
                ptr++;
                continue;
            }
            codepoint = ((c & 0x0F) << 12) | ((ptr[1] & 0x3F) << 6) | (ptr[2] & 0x3F);
            ptr += 3;
            if (codepoint < 0x800 || (codepoint >= 0xD800 && codepoint <= 0xDFFF)) {
                LOGW("safeNewStringUTF: invalid 3-byte codepoint 0x%X", codepoint);
                continue;
            }
        } else if ((c & 0xF8) == 0xF0) {
            if ((ptr[1] & 0xC0) != 0x80 || (ptr[2] & 0xC0) != 0x80 || (ptr[3] & 0xC0) != 0x80) {
                LOGW("safeNewStringUTF: invalid 4-byte sequence start 0x%02X", c);
                ptr++;
                continue;
            }
            codepoint = ((c & 0x07) << 18) | ((ptr[1] & 0x3F) << 12) |
                        ((ptr[2] & 0x3F) << 6) | (ptr[3] & 0x3F);
            ptr += 4;
            if (codepoint < 0x10000 || codepoint > 0x10FFFF) {
                LOGW("safeNewStringUTF: invalid 4-byte codepoint 0x%X", codepoint);
                continue;
            }
        } else {
            LOGW("safeNewStringUTF: invalid UTF-8 start byte 0x%02X", c);
            ptr++;
            continue;
        }

        if (codepoint <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(codepoint));
        } else {
            codepoint -= 0x10000;
            jchar high = static_cast<jchar>(0xD800 + (codepoint >> 10));
            jchar low = static_cast<jchar>(0xDC00 + (codepoint & 0x3FF));
            utf16.push_back(high);
            utf16.push_back(low);
        }
    }

    if (utf16.empty()) {
        return env->NewString(EMPTY_STR, 0);
    }

    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

/**
 * Convert UTF-8 chunks to UTF-16 while carrying incomplete multi-byte sequences across calls.
 */
static jstring safeNewStringUTFStreaming(
        JNIEnv* env,
        const std::string& chunk,
        std::string& remainder) {

    std::string combined = remainder;
    combined.append(chunk);

    const unsigned char* ptr = reinterpret_cast<const unsigned char*>(combined.data());
    const size_t total = combined.size();
    std::vector<jchar> utf16;
    utf16.reserve(combined.size());

    size_t index = 0;
    while (index < total) {
        unsigned char c = ptr[index];
        uint32_t codepoint = 0;
        size_t expected = 0;

        if (c <= 0x7F) {
            codepoint = c;
            expected = 1;
        } else if ((c & 0xE0) == 0xC0) {
            expected = 2;
        } else if ((c & 0xF0) == 0xE0) {
            expected = 3;
        } else if ((c & 0xF8) == 0xF0) {
            expected = 4;
        } else {
            LOGW("safeNewStringUTFStreaming: invalid UTF-8 start byte 0x%02X", c);
            index++;
            continue;
        }

        if (index + expected > total) {
            // Incomplete multi-byte sequence, keep in remainder.
            break;
        }

        switch (expected) {
            case 1:
                codepoint = c;
                break;
            case 2: {
                unsigned char c1 = ptr[index + 1];
                if ((c1 & 0xC0) != 0x80) {
                    LOGW("safeNewStringUTFStreaming: invalid continuation byte 0x%02X", c1);
                    index++;
                    continue;
                }
                codepoint = ((c & 0x1F) << 6) | (c1 & 0x3F);
                if (codepoint < 0x80) {
                    LOGW("safeNewStringUTFStreaming: overlong 2-byte sequence");
                    index += 2;
                    continue;
                }
                break;
            }
            case 3: {
                unsigned char c1 = ptr[index + 1];
                unsigned char c2 = ptr[index + 2];
                if ((c1 & 0xC0) != 0x80 || (c2 & 0xC0) != 0x80) {
                    LOGW("safeNewStringUTFStreaming: invalid 3-byte continuation");
                    index++;
                    continue;
                }
                codepoint = ((c & 0x0F) << 12) | ((c1 & 0x3F) << 6) | (c2 & 0x3F);
                if (codepoint < 0x800 || (codepoint >= 0xD800 && codepoint <= 0xDFFF)) {
                    LOGW("safeNewStringUTFStreaming: invalid 3-byte codepoint 0x%X", codepoint);
                    index += 3;
                    continue;
                }
                break;
            }
            case 4: {
                unsigned char c1 = ptr[index + 1];
                unsigned char c2 = ptr[index + 2];
                unsigned char c3 = ptr[index + 3];
                if ((c1 & 0xC0) != 0x80 ||
                    (c2 & 0xC0) != 0x80 ||
                    (c3 & 0xC0) != 0x80) {
                    LOGW("safeNewStringUTFStreaming: invalid 4-byte continuation");
                    index++;
                    continue;
                }
                codepoint = ((c & 0x07) << 18) |
                            ((c1 & 0x3F) << 12) |
                            ((c2 & 0x3F) << 6) |
                            (c3 & 0x3F);
                if (codepoint < 0x10000 || codepoint > 0x10FFFF) {
                    LOGW("safeNewStringUTFStreaming: invalid 4-byte codepoint 0x%X", codepoint);
                    index += 4;
                    continue;
                }
                break;
            }
        }

        if (codepoint <= 0xFFFF) {
            utf16.push_back(static_cast<jchar>(codepoint));
        } else {
            codepoint -= 0x10000;
            jchar high = static_cast<jchar>(0xD800 + (codepoint >> 10));
            jchar low = static_cast<jchar>(0xDC00 + (codepoint & 0x3FF));
            utf16.push_back(high);
            utf16.push_back(low);
        }

        index += expected;
    }

    if (index < total) {
        remainder.assign(reinterpret_cast<const char*>(ptr + index), total - index);
    } else {
        remainder.clear();
    }

    if (utf16.empty()) {
        return env->NewString(nullptr, 0);
    }

    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

/**
 * Convert a Java string (UTF-16) to sanitized UTF-8 suitable for llama.cpp consumption.
 * Preserves emoji code points while skipping malformed surrogate sequences.
 */
static std::string sanitizeInputString(JNIEnv* env, jstring jstr) {
    if (jstr == nullptr) {
        LOGW("sanitizeInputString: null input string");
        return "";
    }

    const jchar* chars = env->GetStringChars(jstr, nullptr);
    if (chars == nullptr) {
        LOGW("sanitizeInputString: failed to acquire chars");
        return "";
    }

    const jsize length = env->GetStringLength(jstr);
    std::string result;
    result.reserve(static_cast<size_t>(length) * 3);

    for (jsize i = 0; i < length; ++i) {
        uint32_t codepoint = chars[i];

        if (codepoint >= 0xD800 && codepoint <= 0xDBFF) {
            if (i + 1 < length) {
                const jchar low = chars[i + 1];
                if (low >= 0xDC00 && low <= 0xDFFF) {
                    codepoint = (((codepoint - 0xD800) << 10) | (low - 0xDC00)) + 0x10000;
                    ++i;
                } else {
                    LOGW("sanitizeInputString: invalid surrogate pair at index %d", i);
                    continue;
                }
            } else {
                LOGW("sanitizeInputString: truncated surrogate at end of string");
                break;
            }
        } else if (codepoint >= 0xDC00 && codepoint <= 0xDFFF) {
            LOGW("sanitizeInputString: unexpected low surrogate 0x%X at index %d", codepoint, i);
            continue;
        }

        if (codepoint <= 0x7F) {
            result.push_back(static_cast<char>(codepoint));
        } else if (codepoint <= 0x7FF) {
            result.push_back(static_cast<char>(0xC0 | (codepoint >> 6)));
            result.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        } else if (codepoint <= 0xFFFF) {
            result.push_back(static_cast<char>(0xE0 | (codepoint >> 12)));
            result.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        } else if (codepoint <= 0x10FFFF) {
            result.push_back(static_cast<char>(0xF0 | (codepoint >> 18)));
            result.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        } else {
            LOGW("sanitizeInputString: invalid codepoint 0x%X", codepoint);
        }
    }

    env->ReleaseStringChars(jstr, chars);
    LOGI("sanitizeInputString: produced %zu bytes from %d code units", result.size(), length);
    return result;
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
    
    const std::string promptStr = sanitizeInputString(env, prompt);
    LOGI("Generating with prompt: %s", promptStr.c_str());
    LOGI("Max tokens: %d, Temperature: %.2f", maxTokens, temperature);
    
    // Tokenize prompt using common_tokenize
    std::vector<llama_token> tokens_list = common_tokenize(g_ctx, promptStr, true);
    
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
    
    const std::string promptStr = sanitizeInputString(env, prompt);
    LOGI("Streaming generation with prompt: %s", promptStr.c_str());
    
    // **CRITICAL FIX: Clear KV cache before each generation**
    llama_memory_t mem = llama_get_memory(g_ctx);
    llama_memory_clear(mem, false);  // Clear metadata but keep data buffers
    LOGI("KV cache cleared");
    
    // Get callback methods
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackClass, "onComplete", "()V");
    
    // Tokenize prompt using common_tokenize
    std::vector<llama_token> tokens_list = common_tokenize(g_ctx, promptStr, true);
    
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
    std::string utf8_remainder;
    
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
            jstring jtoken = safeNewStringUTFStreaming(env, token_str, utf8_remainder);
            if (jtoken != nullptr) {
                jsize jlen = env->GetStringLength(jtoken);
                if (jlen > 0) {
                    env->CallVoidMethod(callback, onTokenMethod, jtoken);
                    if (env->ExceptionCheck()) {
                        LOGE("Exception in onToken callback, clearing and continuing");
                        env->ExceptionClear();
                    }
                }
                env->DeleteLocalRef(jtoken);
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
    
    // Flush any remaining partial sequences
    if (!utf8_remainder.empty()) {
        jstring jflush = safeNewStringUTFStreaming(env, "", utf8_remainder);
        if (jflush != nullptr && env->GetStringLength(jflush) > 0) {
            env->CallVoidMethod(callback, onTokenMethod, jflush);
            if (env->ExceptionCheck()) {
                LOGE("Exception in onToken flush callback, clearing");
                env->ExceptionClear();
            }
        }
        if (jflush != nullptr) {
            env->DeleteLocalRef(jflush);
        }
    }

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
