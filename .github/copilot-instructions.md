# AndroGPT - AI Coding Agent Instructions

## Project Overview
**AndroGPT** is a production-ready Android application for running Large Language Models (LLMs) locally on Android devices using llama.cpp. Built with modern Android development practices: Kotlin, Jetpack Compose Material 3, Clean Architecture (MVVM), Hilt DI, and JNI for native C++ integration.

**Package**: `com.androgpt.yaser`  
**Current Models**: Microsoft Phi-3 Mini (1B/3.8B), Meta Llama 3.2 (1B/3B), SmolLM2 (1.7B)  
**Min SDK**: 29 (Android 10+)  
**Build**: Gradle 8.2 + AGP 8.2.1 + NDK 26.1

## Architecture Overview

### 3-Layer Clean Architecture
```
Presentation → Domain → Data → Native (C++)
```

**Key Pattern**: Repository interfaces live in `domain/repository/`, implementations in `data/repository/`. Use cases (`domain/usecase/`) orchestrate business logic between layers.

**Critical**: Native code changes require rebuilding C++ (`.\gradlew.bat assembleDebug`), not just Kotlin compilation.

### Data Flow for AI Inference
1. User input → `ChatViewModel` → `SendMessageUseCase`
2. `SendMessageUseCase` builds **Phi-3 prompt template** (ChatML format) with conversation history
3. → `InferenceRepository` → `LlamaEngine` (Kotlin/JNI) → `llama_jni.cpp` (native)
4. Native streaming callbacks → Kotlin Flow → UI (real-time token display)
5. Response saved to Room database with stop token cleanup

**Critical Prompt Format** (Phi-3 Mini requires this exact format):
```kotlin
<|system|>system_message<|end|>
<|user|>user_message<|end|>
<|assistant|>response<|end|>
```
See `SendMessageUseCase.kt` for full implementation. Wrong format = nonsensical responses.

## Build & Development Commands

### Essential Commands (PowerShell)
```powershell
# Build APK (includes native C++ compilation)
.\gradlew.bat assembleDebug

# Install to connected device
.\gradlew.bat installDebug

# Clean build (use when native code changes don't apply)
.\gradlew.bat clean assembleDebug

# Refresh CMake after native changes
# Build → Refresh Linked C++ Projects (in Android Studio)
```

### Debugging Native Code
```powershell
# View logcat with filtering
adb logcat | Select-String "LlamaJNI|LlamaEngine|ChatViewModel"

# Check native library load
adb logcat | Select-String "LlamaJNI|LlamaEngine|AndroGPT"
```

**APK Location**: `app\build\outputs\apk\debug\app-debug.apk`

## Critical Patterns & Conventions

### 1. Native Code Integration (JNI Bridge)
- **JNI Methods**: Declared in `LlamaEngine.kt` as `external fun`, implemented in `llama_jni.cpp`
- **Threading**: Native generation runs on `Dispatchers.IO`, uses mutex locks (`g_mutex`)
- **State Management**: `isGenerating` flag prevents concurrent inference, **must** be reset in all code paths
- **Stop Sequences**: Native code checks for Phi-3 tokens (`<|end|>`, `<|user|>`, etc.) in generation loop

**Example Bug Pattern**: If `isGenerating` stays true after error, follow-up queries fail silently. Always reset in catch blocks.

### 2. Model Download System
- **Architecture**: Foreground Service (`ModelDownloadService`) with notification support
- **HTTP Client**: OkHttp with 30s timeouts, resume support via HTTP Range headers
- **Storage**: `/data/data/com.androgpt.yaser/files/models/` (app private storage)
- **Progress Tracking**: Polling system (500ms interval) reads temp file size, updates UI via StateFlow
- **Cancellation**: `activeDownloads` map tracks jobs, checks cancellation before each buffer write
- **Completion Notification**: Shows success message in UI for 5 seconds after download completes

**Critical Fix History**:
- **Oct 18**: Added `onModelDownloaded` callback to refresh UI immediately after download
- **Oct 21**: Fixed duplicate download flows (ViewModel + Service both downloading)
- **Oct 21**: Fixed service stopping immediately (race condition with `activeDownloads` map)
- **Oct 21**: Added Android 13+ notification permission request (`POST_NOTIFICATIONS`)
- **Oct 22**: Fixed file size metadata mismatches causing 110% progress (updated actual server sizes)
- **Oct 22**: Removed pause/resume functionality, simplified to Download/Cancel only
- **Oct 22**: Implemented polling system (replaced failed BroadcastReceiver approach)

**Available Models** (see `DownloadableModel.kt`):
- Phi-3 Mini 4K Q4 (2.2GB), 128K Q4 (7.6GB)
- Llama 3.2 1B Q8 (1.32GB), 3B Q6_K (2.64GB)
- SmolLM2 1.7B Q8 (1.82GB)

**Download Flow**:
1. User clicks Download → `ModelDownloadViewModel.downloadModel()`
2. Start foreground service with model metadata
3. Service creates download job, adds to `activeDownloads`
4. ViewModel starts polling temp file size every 500ms
5. On completion: polling detects final file, shows completion message, refreshes list

### 3. State Management Patterns
- **ViewModels**: Use `StateFlow` for UI state, `MutableStateFlow` for internal state
- **Generation States**: `Idle → Loading → Generating → Complete/Error → Idle`
- **Persistence**: `GenerationPreferences` (DataStore) for settings, `ChatDatabase` (Room) for messages
- **Critical**: Always set state back to `Idle` after `Complete`/`Error` with delay (100ms/3s)

### 4. Response Cleaning Pipeline
1. Native code stops on Phi-3 tokens (see `llama_jni.cpp` stop sequence detection)
2. Kotlin `cleanResponse()` removes remaining tokens from streamed text using regex
3. **Aggressive Token Cleaning** (Oct 23 update):
   - Removes complete tokens: `<|end|>`, `<|user|>`, `<|assistant|>`, `<|system|>`
   - Removes partial tokens: `<|end|`, `<|end`, `<|`, `<`
   - Regex pattern: `<\|[^>]*\|?>?` matches any Phi-3 token (complete or incomplete)
   - Handles token-by-token generation where `<|end|>` appears as separate tokens
4. Both streaming (`Generating`) and final (`Complete`) states get cleaned
5. Saved to database only after cleaning

### 5. UI Features
- **Chat Screen**: Real-time streaming with copy buttons below each AI response
- **Copy Button** (Oct 22): 
  - Appears below every AI message (not user messages)
  - Uses `LocalClipboardManager` to copy text
  - Shows "Copied!" feedback for 2 seconds
  - Works on both streaming and completed messages
- **Models Screen**: Tabbed interface (Local Models / Download)
  - Download tab shows grouped models by creator
  - Progress bars with percentage and bytes
  - Completion message overlay shows after successful download
- **Settings Screen**: Temperature, max tokens, top-P, top-K, system prompt

## Common Issues & Solutions

### Issue: "App crashes when LLM generates emojis or invalid UTF-8"
**Root Cause**: JNI's `NewStringUTF()` uses Modified UTF-8 which rejects 4-byte UTF-8 sequences (emojis) AND invalid UTF-8 bytes (like 0x8a). JVM aborts process immediately BEFORE exception handling can run.
**Fix**: Use symbolic emoji conversion system implemented as of Oct 26, 2025:
- `emojiToSymbolic()` function maps 50+ emoji codepoints to text symbols (😀→`:)`, ❤️→`<3`, 🔥→`*fire*`)
- `safeNewStringUTF()` sanitizes UTF-8, converts emojis to symbols, skips invalid bytes
- System prompt instructs model to use text emoticons instead of emojis
- **Critical**: Cannot use exception handling after `NewStringUTF()` - process crashes before check runs
- **Critical**: Must sanitize BEFORE calling `NewStringUTF()`, not after

### Issue: "App crashes when sending emojis or non-English text"
**Root Cause**: JNI's `NewStringUTF()` fails on certain multi-byte UTF-8 characters, returns NULL causing native crash
**Fix**: Use `safeNewStringUTF()` wrapper function that sanitizes UTF-8 and converts emojis to symbolic text. Already implemented as of Oct 26, 2025

### Issue: "Follow-up queries not working"
**Root Cause**: `isGenerating` flag stuck true or state not reset to `Idle`
**Fix**: Check `LlamaEngine.generateStream()` resets flag in all paths, verify `ChatViewModel` sets `_generationState` to `Idle` after completion

### Issue: "Model outputs special tokens like `<|assistant|>` or `<|end|`"
**Root Cause**: Stop sequence detection missing or response cleaning skipped
**Fix**: Verify `llama_jni.cpp` checks `accumulated_text.find("<|end|>")` etc., ensure `SendMessageUseCase` calls `cleanResponse()` with updated regex pattern

### Issue: "Native changes not applying"
**Solution**: Run `.\gradlew.bat clean`, then `Build → Refresh Linked C++ Projects`, then `assembleDebug`

### Issue: "Model responses nonsensical"
**Root Cause**: Wrong prompt template or missing conversation history
**Fix**: Verify `SendMessageUseCase.buildPrompt()` uses exact Phi-3 ChatML format with `<|system|>`, `<|user|>`, `<|assistant|>`, `<|end|>` tokens

### Issue: "Download stuck at 0% or wrong percentage"
**Root Cause**: Incorrect file size metadata in `DownloadableModel.kt`
**Fix**: Verify `fileSize` matches actual server file size (use `curl -I` to check Content-Length header)

### Issue: "Downloaded model not appearing"
**Root Cause**: UI not refreshing after download completes
**Fix**: Check `ModelDownloadViewModel` polling detects completion and calls `refreshDownloadedModels()`

### Issue: "Download service stops immediately"
**Root Cause**: Race condition - job removed from `activeDownloads` before being added
**Fix**: Ensure job added to map BEFORE launching coroutine that can complete quickly

## File Structure Map
```
app/src/main/
├── cpp/                              # Native C++ code
│   ├── llama_jni.cpp                # JNI bridge, inference loop with stop tokens
│   ├── llama-cpp/                   # llama.cpp submodule (53 source files)
│   └── CMakeLists.txt               # Build config, ARM optimizations
├── java/.../yaser/                   # Package: com.androgpt.yaser
│   ├── data/
│   │   ├── inference/
│   │   │   └── LlamaEngine.kt       # JNI interface, isGenerating flag
│   │   ├── local/
│   │   │   ├── ChatDatabase.kt      # Room: conversations & messages
│   │   │   └── GenerationPreferences.kt  # DataStore: temp, max_tokens, topP/K
│   │   ├── repository/              # Impl: ChatRepositoryImpl, ModelDownloadRepository
│   │   └── service/
│   │       └── ModelDownloadService.kt  # Foreground service for downloads
│   ├── domain/
│   │   ├── model/                   # Data classes: Message, GenerationState, DownloadableModel
│   │   ├── repository/              # Interfaces: ChatRepository, InferenceRepository
│   │   └── usecase/
│   │       └── SendMessageUseCase.kt # **Prompt building + response cleaning**
│   ├── presentation/
│   │   ├── chat/
│   │   │   ├── ChatViewModel.kt     # State management, send logic, generation flow
│   │   │   └── ChatScreen.kt        # Compose UI with copy buttons
│   │   ├── models/
│   │   │   ├── ModelDownloadViewModel.kt  # Polling system, completion messages
│   │   │   └── ModelDownloadScreen.kt     # Download UI with progress tracking
│   │   └── settings/                # Generation params UI
│   └── di/                          # Hilt modules: DatabaseModule, RepositoryModule
└── docs/                            # Documentation (gitignored)
```

## Adding New Features Checklist

### For New Generation Parameters (e.g., repetition_penalty)
1. Add to `GenerationPreferences.kt` DataStore
2. Update `ChatViewModel._generationSettings` StateFlow
3. Pass through `SendMessageUseCase` → `InferenceRepository.generateStream()`
4. Add to `llama_jni.cpp` native function signature
5. Apply in sampler chain setup (see `llama_sampler_chain_add()` calls)
6. Update `SettingsScreen.kt` UI

### For New Models
1. Add entry to `DownloadableModel.kt` → `AvailableModels` object
2. Update `ModelDownloadScreen.kt` descriptions
3. If different prompt format, update `SendMessageUseCase.buildPrompt()` and native stop tokens

### For Database Changes
1. Update entity in `data/local/`
2. Increment `ChatDatabase.VERSION`
3. Add migration or set `.fallbackToDestructiveMigration()`

## Testing Workflows
- **Quick iteration**: Kotlin-only changes use hot reload/build
- **Native changes**: Full `clean → assembleDebug → installDebug` cycle (~30s)
- **Generation testing**: Monitor logcat for prompt format, check `isGenerating` resets
- **Model testing**: Download Phi-3 Mini 4K Q4 (2.2GB), smallest variant

## Documentation Guidelines

### Creating New Documentation
- **All markdown files MUST be placed in the `docs/` folder** (which is gitignored)
- Do NOT create markdown files in the root directory (except `README.md`)
- The `docs/` folder structure:
  - Bug fixes: `docs/BUG_FIXES_*.md`
  - Feature documentation: `docs/FEATURE_NAME.md`
  - Setup guides: `docs/SETUP_*.md`
  - Progress tracking: `docs/BUILD_PROGRESS.md`, `docs/INTEGRATION_*.md`

### Updating This File
- **CRITICAL**: With every change made to the project, this `copilot-instructions.md` file MUST be updated
- When to update:
  - New features added (update relevant sections + add to file structure)
  - Bugs fixed (add to "Common Issues & Solutions")
  - Architecture changes (update patterns & conventions)
  - New models added (update available models list)
  - Dependencies changed (update build commands if needed)
  - UI changes (update UI Features section)
- Always update the "Last Updated" section at the bottom with:
  - Current date
  - List of changes made
  - Any new patterns or conventions introduced
- **CRITICAL**: Also update the "Project Changelog" section with detailed entries for each change

## Project Changelog

**Purpose**: This log serves as a complete history of the project for continuity across GitHub accounts, team changes, or AI assistant sessions. Every change must be logged here with date, category, description, files modified, and technical details.

### October 2025

#### **Oct 25, 2025** - Fixed App Crashes with Emojis and Non-English Characters
- **Category**: Bug Fix
- **Description**: Fixed app crashes when sending messages containing emojis, non-ASCII characters, or non-English text
- **Files Modified**:
  - `app/src/main/cpp/llama_jni.cpp`
- **Technical Details**:
  - **Problem**: App crashed when generating responses to messages with emojis (🔥, 😊, etc.) or non-English characters (Chinese, Arabic, etc.)
  - **Root Cause**: JNI's `NewStringUTF()` function fails and returns NULL when encountering invalid UTF-8 byte sequences or certain multi-byte characters. When NULL is returned without checking, subsequent operations cause native crashes
  - **Solution**: Added comprehensive UTF-8 error handling:
    1. **New Helper Function**: `safeNewStringUTF()` wraps `NewStringUTF()` with proper error checking
    2. **Exception Detection**: Checks `env->ExceptionCheck()` after string creation and clears exceptions gracefully
    3. **NULL Handling**: Returns empty string instead of NULL to prevent crashes
    4. **Callback Exception Handling**: Added exception checking after `CallVoidMethod()` to handle Java-side errors
  - **Implementation**:
    ```cpp
    static jstring safeNewStringUTF(JNIEnv* env, const char* bytes) {
        if (bytes == nullptr) return env->NewStringUTF("");
        jstring result = env->NewStringUTF(bytes);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return env->NewStringUTF("");
        }
        if (result == nullptr) return env->NewStringUTF("");
        return result;
    }
    ```
  - **Updated All String Creation Sites**: Replaced all `env->NewStringUTF()` calls with `safeNewStringUTF()` in:
    - Streaming generation (`nativeGenerateStream`)
    - Synchronous generation (`nativeGenerate`)
    - Model info retrieval (`nativeGetModelInfo`)
    - Error messages
  - **Token Streaming Safety**: Added exception check after streaming each token to Java callback
- **Testing**: Should test with messages containing: emojis (🔥😊✨), Chinese characters (你好), Arabic text (مرحبا), other Unicode symbols
- **Reason**: User reported app crashes when sending emojis or non-English text - generation would start then app would crash immediately

### October 2025

#### **Oct 26, 2025** - Implemented Symbolic Emoji Conversion System
- **Category**: Feature / Bug Fix
- **Description**: Replaced emoji-to-UTF-16 conversion with symbolic text emoticon system to avoid JNI crashes and improve compatibility
- **Files Modified**:
  - `app/src/main/cpp/llama_jni.cpp`
  - `app/src/main/java/com/androgpt/yaser/presentation/settings/SettingsViewModel.kt`
  - `app/src/main/java/com/androgpt/yaser/domain/model/ModelConfig.kt`
- **Technical Details**:
  - **Problem Evolution**:
    1. UTF-16 conversion approach caused blank responses (complex, fragile)
    2. Fast-path optimization still crashed on invalid UTF-8 bytes like `0x8a`
    3. User requested symbolic emoji replacements instead of actual emojis
  - **Final Solution - Symbolic Emoji Mapping**:
    - Added `emojiToSymbolic()` function with 50+ emoji mappings
    - Common mappings: 😀😊→`:)`, ❤️→`<3`, 👍→`(y)`, 🔥→`*fire*`, 😂→`:'D`, 😭→`T_T`
    - Unknown emojis fallback to `[emoji]`
  - **Implementation**:
    ```cpp
    static const char* emojiToSymbolic(uint32_t codepoint) {
        switch (codepoint) {
            case 0x1F600: return ":D";   // 😀 grinning face
            case 0x2764:  return "<3";   // ❤ red heart
            case 0x1F44D: return "(y)";  // 👍 thumbs up
            case 0x1F525: return "*fire*"; // 🔥 fire
            // ... 50+ more mappings
            default: return nullptr;
        }
    }
    ```
  - **UTF-8 Sanitization**: Decodes 4-byte sequences to codepoints, maps to symbolic text, skips invalid bytes
  - **System Prompt Update**: Changed default from "You are a helpful AI assistant." to "You are a helpful AI assistant. Do not use emoji characters in your responses - use text-based emoticons like :) or <3 instead."
  - **Updated Files**:
    - `SettingsViewModel.kt`: Updated `_systemPrompt` default value
    - `ModelConfig.kt`: Updated `systemPrompt` default parameter
  - **Behavior**:
    - Model discouraged from generating emojis via system prompt
    - If emojis generated, automatically converted to text symbols
    - No crashes on invalid UTF-8 bytes (silently skipped)
    - User-friendly text emoticons display correctly
- **Testing**: Verified no crashes, symbolic emoticons display properly, model follows new system prompt
- **Reason**: User wanted emoji support without crashes, preferred text-based emoticons over complex UTF-16 handling

#### **Oct 26, 2025** - Fixed Multiple UTF-8/JNI Crash Attempts (Emoji Support)
- **Category**: Bug Fix (Multiple Iterations)
- **Description**: Fixed app crashes when LLM generates emoji tokens or invalid UTF-8 sequences through multiple solution attempts
- **Files Modified**:
  - `app/src/main/cpp/llama_jni.cpp`
- **Technical Details**:
  - **Original Problem**: App crashed with error "JNI DETECTED ERROR: input is not valid Modified UTF-8: illegal continuation byte 0xf0"
  - **Root Cause**: JNI's `NewStringUTF()` uses Modified UTF-8 which rejects 4-byte UTF-8 sequences (emoji characters starting with 0xF0-0xF7)
  
  - **First Attempt (FAILED)**: Exception-based wrapper
    - Created `safeNewStringUTF()` that called `NewStringUTF()` then checked `env->ExceptionCheck()`
    - **Fatal Flaw**: JVM aborts process IMMEDIATELY when `NewStringUTF()` encounters invalid Modified UTF-8, BEFORE exception check runs
    - Exception handling code never executes
  
  - **Second Attempt (FAILED)**: Pre-validation with `[emoji]` placeholder
    - Added `isValidModifiedUTF8()` to detect 4-byte sequences BEFORE calling `NewStringUTF()`
    - Added `filterInvalidUTF8()` to replace 4-byte sequences with "[emoji]" text
    - **Issue**: Worked but user reported responses showing "[emoji]" text instead of actual emojis
  
  - **Third Attempt (FAILED)**: UTF-16 conversion for emoji preservation
    - Converted UTF-8 to UTF-16 surrogate pairs: `codepoint -= 0x10000; high=(0xD800+(codepoint>>10)); low=(0xDC00+(codepoint&0x3FF))`
    - Used `env->NewString(utf16.data(), utf16.size())` instead of `NewStringUTF()`
    - **Issue**: Responses showed as completely blank (conversion logic bug or null handling issue)
  
  - **Fourth Attempt (FAILED)**: Fast-path optimization
    - Added emoji detection: if no 4-byte sequences detected, use `NewStringUTF()` directly (faster)
    - Only use UTF-16 conversion when emojis present
    - **Critical Bug**: Crashed on `0x8a` (invalid UTF-8 start byte) - emoji detection only checked for 4-byte sequences, not all invalid bytes
    - Error: "input is not valid Modified UTF-8: illegal start byte 0x8a"
  
  - **Fifth Attempt (PARTIAL SUCCESS)**: Always UTF-16 conversion
    - Removed fast-path optimization
    - ALWAYS use UTF-16 conversion to handle ALL edge cases (emojis, invalid bytes, etc.)
    - Skip invalid bytes instead of crashing
    - **Issue**: Responses still showed blank (UTF-16 approach fundamentally problematic)
  
  - **Final Solution**: Symbolic emoji conversion (see separate changelog entry above)

- **Build Issues Encountered**:
  - Missing `LOGW` macro definition (added alongside `LOGI` and `LOGE`)
  - Syntax error: `return "(:"` needed escaping, changed to `return "("`
  - Duplicate case value for waving hand emoji (0x1F44B listed twice)
  - Missing closing brace on `safeNewStringUTF()` function
  - All resolved during iterative fixes

- **Key Learnings**:
  - Cannot rely on JNI exception handling for `NewStringUTF()` - it aborts process immediately
  - MUST validate or sanitize encoding BEFORE calling `NewStringUTF()`
  - UTF-16 conversion complex and error-prone for this use case
  - Symbolic text emoticons simpler, more reliable, and user-friendly
  - Modified UTF-8 limitations affect not just emojis but any invalid UTF-8 byte sequence

- **Testing History**:
  - First fix: Crashed immediately (exception check too late)
  - Second fix: Worked but showed "[emoji]" text
  - Third fix: Responses completely blank
  - Fourth fix: Crashed on 0x8a byte
  - Fifth fix: Responses still blank
  - Final fix: Works perfectly with symbolic emoticons

- **Reason**: User sent message with emoji, app crashed repeatedly. After multiple fix attempts with varying issues, settled on symbolic emoji conversion as most reliable solution.

#### **Oct 25, 2025** - Added Project Changelog & Account Continuity System
- **Category**: Documentation
- **Description**: Added comprehensive changelog section to track all project changes for GitHub account switches and team continuity
- **Files Modified**: 
  - `.github/copilot-instructions.md` (added "Project Changelog" section)
- **Technical Details**:
  - Changelog organized by month and date (newest first)
  - Each entry includes: Date, Category, Description, Files Modified, Technical Details
  - Categories: Feature, Bug Fix, Enhancement, Architecture, Documentation, Build System
  - Ensures new GitHub accounts/developers can quickly understand project history
  - Integrated with existing documentation guidelines (must update after every change)
- **Reason**: Enable seamless transitions between GitHub accounts and maintain complete project knowledge continuity

#### **Oct 23, 2025** - Documentation Organization & Guidelines
- **Category**: Documentation
- **Description**: Organized all markdown documentation into `docs/` folder and established documentation maintenance guidelines
- **Files Modified**:
  - `.gitignore` (added `docs/` exclusion)
  - `.github/copilot-instructions.md` (consolidated all documentation, added guidelines)
  - Created `docs/` folder and moved 17 markdown files
- **Technical Details**:
  - **Documentation Structure**: All project docs now in gitignored `docs/` folder
  - **Files Moved**: BUG_FIXES_OCT18.md, BUILD_PROGRESS.md, DEVELOPMENT.md, DOWNLOAD_FIXES_OCT21.md, FOLLOWUP_FIXES_OCT18.md, INDEX.md, INTEGRATION_SUMMARY.md, INTEGRATION_VERIFICATION.md, JAVA_21_UPGRADE.md, LLAMA_INTEGRATION.md, MODEL_DOWNLOAD_FEATURE.md, PROJECT_SUMMARY.md, QUICKSTART.md, SETTINGS_ENHANCEMENTS.md, SETTINGS_QUICK_REFERENCE.md, SETUP.md, UI_FIXES_OCT21.md
  - **Consolidated Knowledge**: Read and integrated content from PROJECT_SUMMARY.md (360 lines), LLAMA_INTEGRATION.md (315 lines), BUG_FIXES_OCT18.md, MODEL_DOWNLOAD_FEATURE.md, DOWNLOAD_FIXES_OCT21.md
  - **Maintenance Rules**: 
    - All new markdown files go in `docs/` folder
    - Update copilot-instructions.md with every project change
    - Keep "Last Updated" section current
- **Reason**: Clean project structure, single source of truth for AI assistants, maintainable documentation system

#### **Oct 23, 2025** - Enhanced Token Cleaning (Aggressive Regex)
- **Category**: Bug Fix
- **Description**: Fixed `<|end|` partial tokens appearing in AI responses during streaming
- **Files Modified**:
  - `app/src/main/java/com/androgpt/yaser/domain/usecase/SendMessageUseCase.kt`
- **Technical Details**:
  - **Problem**: Phi-3 special tokens (`<|end|>`, `<|user|>`) appearing in responses during token-by-token streaming
  - **Root Cause**: Streaming generates tokens incrementally, so `<|end|>` appears as separate tokens: `<`, `|`, `end`, `|`, `>`
  - **Solution**: Added aggressive regex cleaning to `cleanResponse()`:
    ```kotlin
    // Remove complete tokens
    for (token in STOP_TOKENS) {
        cleaned = cleaned.replace(token, "")
    }
    // Remove any Phi-3 token pattern (complete or incomplete)
    cleaned = cleaned.replace(Regex("<\\|[^>]*\\|?>?"), "")
    // Remove partial tokens at end
    cleaned = cleaned.replace(Regex("<\\|[^>]*$"), "")
    cleaned = cleaned.replace(Regex("<$"), "")
    ```
  - **STOP_TOKENS Updated**: Added partial tokens: `<|end|`, `<|end`, `<|user|`, `<|user`, `<|assistant|`, `<|system|`
  - **Regex Pattern**: `<\|[^>]*\|?>?` matches any Phi-3 token (complete or incomplete)
  - Cleans both streaming (`Generating` state) and final (`Complete` state) text
- **Testing**: Verified responses no longer contain `<|end|` or partial tokens
- **Reason**: User reported `<|end|` appearing at end of some AI responses

#### **Oct 22, 2025** - Download Completion UI Notifications
- **Category**: Feature
- **Description**: Added visual notification in Models screen when model download completes successfully
- **Files Modified**:
  - `app/src/main/java/com/androgpt/yaser/presentation/models/ModelDownloadViewModel.kt`
  - `app/src/main/java/com/androgpt/yaser/presentation/models/ModelDownloadScreen.kt`
- **Technical Details**:
  - **ViewModel Changes**:
    - Added `_completionMessage = MutableStateFlow<String?>(null)`
    - Modified `startProgressPolling()` to detect download completion:
    ```kotlin
    if (finalFile.exists() && finalFile.length() == model.fileSize) {
        _downloadStates.value = ... put(model.id, DownloadState.Success)
        _completionMessage.value = "✓ ${model.name} downloaded successfully!"
        delay(5000)
        _completionMessage.value = null
        refreshDownloadedModels()
    }
    ```
    - Added `dismissCompletionMessage()` for manual dismissal
  - **UI Changes**:
    - Wrapped LazyColumn in Box
    - Added completion message Card overlay at bottom:
    ```kotlin
    completionMessage?.let { message ->
        Card(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row {
                Text(text = message, ...)
                IconButton(onClick = { viewModel.dismissCompletionMessage() }) {
                    Icon(Icons.Default.Close, ...)
                }
            }
        }
    }
    ```
  - **Behavior**: Message shows for 5 seconds, auto-dismisses, manual dismiss available
- **Testing**: Verified completion message shows after successful download and refreshes model list
- **Reason**: User requested visual feedback when "Service destroyed" logcat message occurs

#### **Oct 22, 2025** - Copy Button for AI Responses
- **Category**: Feature
- **Description**: Added copy buttons below all AI responses with clipboard functionality and feedback
- **Files Modified**:
  - `app/src/main/java/com/androgpt/yaser/presentation/chat/ChatScreen.kt`
- **Technical Details**:
  - **Imports Added**: `LocalClipboardManager`, `AnnotatedString`, `delay`
  - **MessageBubble Changes**: Wrapped content in Column, added copy button below AI messages only
  - **StreamingMessageBubble Changes**: Same copy button pattern for streaming responses
  - **Implementation**:
    ```kotlin
    var showCopiedSnackbar by remember { mutableStateOf(false) }
    FilledTonalButton(
        onClick = {
            clipboardManager.setText(AnnotatedString(message.content))
            showCopiedSnackbar = true
        }
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = if (showCopiedSnackbar) "Copied!" else "Copy")
    }
    LaunchedEffect(showCopiedSnackbar) {
        if (showCopiedSnackbar) {
            delay(2000)
            showCopiedSnackbar = false
        }
    }
    ```
  - **Behavior**: Button shows "Copy", changes to "Copied!" for 2 seconds after click
  - **Scope**: Only AI messages have copy buttons (not user messages)
  - Works on both completed and streaming messages
- **Testing**: Verified clipboard copies text correctly and feedback displays properly
- **Reason**: User requested copy functionality for model responses

#### **Oct 22, 2025** - Fixed Model File Size Metadata
- **Category**: Bug Fix
- **Description**: Corrected file sizes for Llama 3.2 models causing incorrect download progress percentages
- **Files Modified**:
  - `app/src/main/java/com/androgpt/yaser/domain/model/DownloadableModel.kt`
- **Technical Details**:
  - **Llama 3.2 1B Q8**: Changed from 1.34GB to 1.32GB (1419752448 bytes)
  - **Llama 3.2 3B Q6_K**: Changed from 2.66GB to 2.64GB (2837923840 bytes)
  - Used `curl -I` to verify actual Content-Length headers from server
  - **Impact**: Fixed progress bars showing >100% or incorrect percentages
- **Testing**: Verified download progress now shows accurate percentages
- **Reason**: Download progress showed 110% for some models due to metadata mismatch

#### **Oct 22, 2025** - Simplified Download System (Removed Pause/Resume)
- **Category**: Enhancement
- **Description**: Removed pause/resume functionality, simplified to Download/Cancel only
- **Files Modified**:
  - `app/src/main/java/com/androgpt/yaser/data/service/ModelDownloadService.kt`
  - `app/src/main/java/com/androgpt/yaser/presentation/models/ModelDownloadViewModel.kt`
  - `app/src/main/java/com/androgpt/yaser/presentation/models/ModelDownloadScreen.kt`
- **Technical Details**:
  - Removed pause state from `DownloadState` enum
  - Removed `pauseDownload()` method from service and ViewModel
  - Updated UI to show only Download/Cancel buttons
  - Simplified state transitions: `NotStarted → Downloading → Success/Cancelled`
- **Reason**: Pause/resume was unreliable and added complexity without significant user benefit

#### **Oct 22, 2025** - Implemented Polling System for Download Progress
- **Category**: Enhancement
- **Description**: Replaced BroadcastReceiver with polling system for more reliable progress updates
- **Files Modified**:
  - `app/src/main/java/com/androgpt/yaser/presentation/models/ModelDownloadViewModel.kt`
- **Technical Details**:
  - **Polling Interval**: 500ms
  - **Mechanism**: Reads temp file size from disk every 500ms
  - **Detection**: Checks if `.tmp` file deleted and final file exists with correct size
  - **Completion Handling**: Shows completion message, refreshes model list
  - **Implementation**:
    ```kotlin
    private fun startProgressPolling(model: DownloadableModel) {
        viewModelScope.launch {
            while (_downloadStates.value[model.id] is DownloadState.Downloading) {
                val tempFile = File(modelDir, "${model.fileName}.tmp")
                val finalFile = File(modelDir, model.fileName)
                
                if (finalFile.exists() && finalFile.length() == model.fileSize) {
                    // Download complete
                    _completionMessage.value = "✓ ${model.name} downloaded successfully!"
                    delay(5000)
                    _completionMessage.value = null
                    refreshDownloadedModels()
                    break
                }
                
                if (tempFile.exists()) {
                    val progress = (tempFile.length().toFloat() / model.fileSize * 100).toInt()
                    _downloadStates.value = ... put(model.id, DownloadState.Downloading(progress))
                }
                delay(500)
            }
        }
    }
    ```
- **Reason**: BroadcastReceiver approach failed to receive broadcasts from service

#### **Oct 21, 2025** - Fixed Download Service Stopping Immediately
- **Category**: Bug Fix
- **Description**: Fixed race condition causing download service to stop immediately after starting
- **Files Modified**:
  - `app/src/main/java/com/androgpt/yaser/data/service/ModelDownloadService.kt`
- **Technical Details**:
  - **Problem**: `stopIfNoActiveDownloads()` called before job added to `activeDownloads` map
  - **Root Cause**: Fast-completing coroutines could finish before map insertion
  - **Solution**: Ensure job added to map BEFORE launching coroutine
  - **Code Pattern**:
    ```kotlin
    // WRONG (job can complete before being added)
    val job = scope.launch { /* download */ }
    activeDownloads[modelId] = job
    
    // CORRECT (add to map first)
    activeDownloads[modelId] = scope.launch { /* download */ }
    ```
- **Testing**: Verified downloads no longer stop immediately
- **Reason**: Logcat showed "Service destroyed" immediately after start

#### **Oct 21, 2025** - Fixed Duplicate Download Flows
- **Category**: Bug Fix
- **Description**: Fixed issue where both ViewModel and Service were attempting downloads, causing conflicts
- **Files Modified**:
  - `app/src/main/java/com/androgpt/yaser/presentation/models/ModelDownloadViewModel.kt`
  - `app/src/main/java/com/androgpt/yaser/data/service/ModelDownloadService.kt`
- **Technical Details**:
  - **Architecture**: Service is single source for downloads
  - **ViewModel Role**: UI state management only, delegates download to service
  - **Service Role**: Actual HTTP download with OkHttp, progress tracking, notification management
  - **Flow**: ViewModel.downloadModel() → start service with model metadata → service downloads → polling updates UI
- **Reason**: Concurrent download attempts caused HTTP 416 Range Not Satisfiable errors

#### **Oct 21, 2025** - Added Android 13+ Notification Permission Request
- **Category**: Feature
- **Description**: Added runtime permission request for POST_NOTIFICATIONS on Android 13+
- **Files Modified**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/androgpt/yaser/presentation/models/ModelDownloadScreen.kt`
- **Technical Details**:
  - **Manifest**: Added `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
  - **Runtime Request**: Check SDK version, request if API 33+
  - **Accompanist Library**: Used `rememberPermissionState()` for declarative permission handling
  - **Implementation**:
    ```kotlin
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionState = rememberPermissionState(
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        LaunchedEffect(Unit) {
            if (!permissionState.hasPermission) {
                permissionState.launchPermissionRequest()
            }
        }
    }
    ```
- **Reason**: Download notifications not showing on Android 13+ due to missing permission

#### **Oct 18, 2025** - Added Model Download Completion Callback
- **Category**: Enhancement
- **Description**: Added callback to refresh UI immediately after model download completes
- **Files Modified**:
  - `app/src/main/java/com/androgpt/yaser/data/service/ModelDownloadService.kt`
  - `app/src/main/java/com/androgpt/yaser/presentation/models/ModelDownloadViewModel.kt`
- **Technical Details**:
  - Added `onModelDownloaded: () -> Unit` callback parameter to download service
  - Called after successful download completion and temp file rename
  - ViewModel refreshes downloaded models list via `refreshDownloadedModels()`
  - **Ensures**: Downloaded models appear immediately without manual screen refresh
- **Reason**: Users had to leave and return to Models screen to see newly downloaded models

### September 2025

#### **Sep 2025** - Initial Project Setup
- **Category**: Architecture
- **Description**: Created production-ready Android app for local LLM inference using llama.cpp
- **Files Created**: Full project structure (see File Structure Map)
- **Technical Details**:
  - **Architecture**: Clean Architecture (MVVM) with 3 layers (Presentation → Domain → Data → Native)
  - **Tech Stack**: Kotlin, Jetpack Compose Material 3, Hilt DI, Room Database, DataStore Preferences
  - **Native Integration**: llama.cpp via JNI bridge (`LlamaEngine.kt` ↔ `llama_jni.cpp`)
  - **Initial Models**: Phi-3 Mini 4K Q4 (2.2GB), Phi-3 Mini 128K Q4 (7.6GB)
  - **Build System**: Gradle 8.2, AGP 8.2.1, NDK 26.1, CMake for C++ compilation
  - **Min SDK**: Android 10 (API 29)
  - **Key Features**:
    - Real-time streaming inference with token-by-token display
    - Conversation persistence (Room database)
    - Model download system with progress tracking
    - Configurable generation parameters (temperature, top-P/K, max tokens)
    - System prompt support
- **Reason**: Create fully local, privacy-focused LLM app for Android devices

---

### How to Use This Changelog

**For New GitHub Accounts / Developers**:
1. Read this changelog from **oldest to newest** (bottom to top) to understand project evolution
2. Focus on "Technical Details" sections for implementation specifics
3. Check "Reason" fields to understand why decisions were made
4. Cross-reference with "Common Issues & Solutions" for known problems

**When Making Changes**:
1. Make your code changes
2. Add entry to this changelog (under current month, newest first)
3. Include: Date, Category, Description, Files Modified, Technical Details, Reason
4. Update relevant sections in this file (Architecture, UI Features, Common Issues, etc.)
5. Update "Last Updated" section at bottom
6. Commit with descriptive message referencing changelog entry

**Categories**:
- **Feature**: New functionality added to the app
- **Bug Fix**: Fixes for broken or incorrect behavior
- **Enhancement**: Improvements to existing features
- **Architecture**: Structural or design pattern changes
- **Documentation**: Documentation updates or reorganization
- **Build System**: Changes to build configuration, dependencies, or tooling

---
**Last Updated**: October 26, 2025
- **Implemented symbolic emoji conversion system** - replaced complex UTF-16 conversion with text-based emoticons (`:)` `<3` `(y)` etc.)
- **Fixed multiple UTF-8/JNI crash attempts** - tried 5 different approaches before settling on symbolic emoji mapping
- **Updated system prompt** to discourage models from using actual emoji characters
- Added 50+ emoji-to-symbolic mappings in `emojiToSymbolic()` function
- Handles all invalid UTF-8 bytes gracefully (emojis, `0x8a`, etc.) without crashes
- Responses now display user-friendly text emoticons instead of actual emojis or crashing
