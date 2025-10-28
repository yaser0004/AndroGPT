# AndroGPT - Local LLM on Android 🤖

A well-polished Android application for running **Large Language Models (LLMs)** locally on your device using **llama.cpp** for efficient inference. Built with modern Android development practices using Kotlin, Jetpack Compose, and Clean Architecture.

## 🌟 Features

- **Local AI Inference**: Run LLMs completely offline on your Android device
- **llama.cpp Integration**: Native C++ backend for optimal performance
- **Modern UI**: Material 3 Design with Jetpack Compose
- **Chat Interface**: Real-time streaming responses with chat history
- **Model Management**: Load, unload, and manage multiple GGUF models
- **Customizable Settings**: Fine-tune generation parameters (temperature, tokens, context, etc.)
- **GPU Acceleration**: Optional GPU layer offloading for faster inference
- **Clean Architecture**: MVVM + Domain layer for maintainability

## 📱 Screenshots

*(Add screenshots here once the app is running)*

## 🏗️ Architecture

The app follows **Clean Architecture** principles with three main layers:

```
📦 AndroGPT
├── 🎨 Presentation Layer (UI)
│   ├── Jetpack Compose screens
│   ├── ViewModels
│   └── Material 3 theme
│
├── 💼 Domain Layer (Business Logic)
│   ├── Models (Message, Conversation, ModelConfig)
│   ├── Repositories (Interfaces)
│   └── Use Cases (SendMessage, LoadModel)
│
├── 📊 Data Layer
│   ├── Local Database (Room)
│   ├── Inference Engine (LlamaEngine - JNI)
│   ├── Model Manager
│   └── Repository Implementations
│
└── 🔧 Native Layer (C++)
    ├── llama.cpp integration
    └── JNI bridge
```

## 🛠️ Tech Stack

### Android/Kotlin
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt/Dagger
- **Async**: Kotlin Coroutines + Flow
- **Database**: Room
- **Navigation**: Jetpack Navigation

### Native/C++
- **Backend**: llama.cpp
- **Build**: CMake + NDK
- **Language**: C++17

## 📋 Prerequisites

### Development Environment
- **Android Studio**: Hedgehog (2023.1.1) or newer
- **JDK**: 17 or higher
- **Android SDK**: API 34 (Android 14)
- **Android NDK**: 26.1.10909125
- **CMake**: 3.22.1 or higher

### Device Requirements
- **Minimum**: Android 10 (API 29), 6GB RAM
- **Recommended**: Android 12+, 8GB+ RAM, GPU with Vulkan support
- **Storage**: 2-8GB free space for models

## 🚀 Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/androgpt.git
cd androgpt
```

### 2. Add llama.cpp Integration

Navigate to the native code directory and add llama.cpp:

```bash
cd app/src/main/cpp
git submodule add https://github.com/ggerganov/llama.cpp.git llama-cpp
git submodule update --init --recursive
```

**OR** download manually from [llama.cpp releases](https://github.com/ggerganov/llama.cpp) and extract to `app/src/main/cpp/llama-cpp/`

### 3. Update CMakeLists.txt

Uncomment the llama.cpp source files in `app/src/main/cpp/CMakeLists.txt`:

```cmake
add_library(${CMAKE_PROJECT_NAME} SHARED
    llama_jni.cpp
    ${LLAMA_CPP_DIR}/ggml.c
    ${LLAMA_CPP_DIR}/llama.cpp
    ${LLAMA_CPP_DIR}/common/common.cpp
)
```

### 4. Build the Project

Open the project in Android Studio and sync Gradle:

```bash
./gradlew build
```

### 5. Download a Model

Download a quantized GGUF model (recommended: Q4_K_M quantization):

**Recommended Models:**
- **Phi-2 (2.7B)**: ~1.6GB - Great for testing
- **Gemma-2B**: ~1.5GB - Fast and efficient  
- **Llama-3.2-3B**: ~2GB - Better quality
- **TinyLlama-1.1B**: ~600MB - Lightweight option

**Download Sources:**
- [Hugging Face](https://huggingface.co/models?library=gguf)
- [TheBloke's Models](https://huggingface.co/TheBloke)

### 6. Install the Model

There are three ways to install models:

#### Option A: ADB (Recommended for Development)
```bash
adb push model.gguf /sdcard/Android/data/com.androgpt.yaser/files/models/
```

#### Option B: Using Device File Manager
1. Connect device to PC
2. Copy `.gguf` file to: `Android/data/com.androgpt.yaser/files/models/`

#### Option C: In-App (Future Feature)
- Use the Models screen to download directly

### 7. Run the App

1. Connect your Android device or start an emulator
2. Click **Run** in Android Studio
3. Go to **Models** tab → Load your model
4. Navigate to **Chat** tab → Start chatting!

## 📖 Usage Guide

### Loading a Model

1. Open the **Models** tab
2. Tap **Load** on your desired model
3. Wait for confirmation message
4. Navigate to **Chat** to start

### Adjusting Settings

Go to **Settings** tab to customize:

- **Temperature** (0.0-2.0): Controls randomness
  - 0.0 = Deterministic
  - 0.7 = Balanced (default)
  - 1.5+ = Creative

- **Max Tokens** (128-4096): Response length limit

- **Context Length** (512-8192): Model memory window
  - Higher = better context but slower
  - Requires model reload

- **CPU Threads** (1-8): Parallel processing
  - More threads = faster (up to CPU cores)
  - Requires model reload

- **GPU Layers** (0-50): GPU acceleration
  - 0 = CPU only
  - Higher = faster but uses more VRAM
  - Requires model reload

- **System Prompt**: Define AI behavior

### Chat Interface

- **Type & Send**: Enter message and tap send icon
- **Streaming**: See responses in real-time
- **Stop**: Tap stop icon to cancel generation
- **History**: Auto-saved conversations

## 🔧 Configuration

### Build Variants

```kotlin
// Debug build
./gradlew assembleDebug

// Release build (minified)
./gradlew assembleRelease
```

### ProGuard Rules

The app includes ProGuard rules to protect native methods and Room entities in release builds.

## 🐛 Troubleshooting

### Model Not Loading
- ✅ Verify file is `.gguf` format
- ✅ Check file permissions
- ✅ Ensure sufficient RAM (6GB+ recommended)
- ✅ Try reducing context length

### Native Library Error
- ✅ Verify NDK is installed
- ✅ Check CMake configuration
- ✅ Rebuild native library: `Build → Refresh Linked C++ Projects`

### Slow Inference
- ✅ Enable GPU layers (Settings → GPU Layers)
- ✅ Reduce context length
- ✅ Use smaller/quantized models (Q4_K_M)
- ✅ Increase CPU threads

### Out of Memory
- ✅ Use smaller models (< 3B parameters)
- ✅ Reduce context length (512-1024)
- ✅ Close other apps
- ✅ Reduce GPU layers

## 🔐 Permissions

The app requires:
- **INTERNET**: For future model downloads (not for inference)
- **READ/WRITE_EXTERNAL_STORAGE**: Model file access (Android 10)
- **READ_MEDIA_IMAGES**: Model file access (Android 13+)

**Note**: All inference is 100% offline and private.

## 🎯 Roadmap

- [ ] **llama.cpp Integration**: Complete native implementation
- [ ] **In-App Model Downloads**: Direct downloads from Hugging Face
- [ ] **Multi-Modal Support**: Image input for vision models
- [ ] **Voice Input/Output**: TTS and STT integration
- [ ] **Conversation Export**: Share as text/PDF
- [ ] **Model Benchmarking**: Performance metrics
- [ ] **Custom Prompts Library**: Save/load prompt templates
- [ ] **iOS Support**: Flutter or React Native port

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/AmazingFeature`
3. Commit changes: `git commit -m 'Add AmazingFeature'`
4. Push to branch: `git push origin feature/AmazingFeature`
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) - Efficient LLM inference in C++
- [Hugging Face](https://huggingface.co) - Model hosting and community
- [TheBloke](https://huggingface.co/TheBloke) - Quantized model conversions
- Android Jetpack Team - Modern Android development tools

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/androgpt/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/androgpt/discussions)
- **Email**: your.email@example.com

---

**Made with ❤️ for the local AI community**

*Run powerful AI models privately on your Android device!*
