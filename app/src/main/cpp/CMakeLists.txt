cmake_minimum_required(VERSION 3.22.1)

project("androgpt")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)

# Add llama.cpp source files (you'll need to add llama.cpp as a submodule or copy the files)
# For now, we'll create a placeholder structure
set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama-cpp)

# Create the native library with JNI wrapper
add_library(${CMAKE_PROJECT_NAME} SHARED
    llama_jni.cpp
    llama_build_info.cpp
    # llama.cpp core files
    ${LLAMA_CPP_DIR}/src/llama.cpp
    ${LLAMA_CPP_DIR}/src/llama-adapter.cpp
    ${LLAMA_CPP_DIR}/src/llama-arch.cpp
    ${LLAMA_CPP_DIR}/src/llama-batch.cpp
    ${LLAMA_CPP_DIR}/src/llama-chat.cpp
    ${LLAMA_CPP_DIR}/src/llama-context.cpp
    ${LLAMA_CPP_DIR}/src/llama-cparams.cpp
    ${LLAMA_CPP_DIR}/src/llama-graph.cpp
    ${LLAMA_CPP_DIR}/src/llama-hparams.cpp
    ${LLAMA_CPP_DIR}/src/llama-impl.cpp
    ${LLAMA_CPP_DIR}/src/llama-io.cpp
    ${LLAMA_CPP_DIR}/src/llama-kv-cache.cpp
    ${LLAMA_CPP_DIR}/src/llama-kv-cache-iswa.cpp
    ${LLAMA_CPP_DIR}/src/llama-memory.cpp
    ${LLAMA_CPP_DIR}/src/llama-memory-hybrid.cpp
    ${LLAMA_CPP_DIR}/src/llama-memory-recurrent.cpp
    ${LLAMA_CPP_DIR}/src/llama-mmap.cpp
    ${LLAMA_CPP_DIR}/src/llama-model-loader.cpp
    ${LLAMA_CPP_DIR}/src/llama-model-saver.cpp
    ${LLAMA_CPP_DIR}/src/llama-model.cpp
    ${LLAMA_CPP_DIR}/src/llama-quant.cpp
    ${LLAMA_CPP_DIR}/src/llama-vocab.cpp
    ${LLAMA_CPP_DIR}/src/llama-grammar.cpp
    ${LLAMA_CPP_DIR}/src/llama-sampling.cpp
    ${LLAMA_CPP_DIR}/src/unicode-data.cpp
    ${LLAMA_CPP_DIR}/src/unicode.cpp
    # ggml core files
    ${LLAMA_CPP_DIR}/ggml/src/ggml.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-opt.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-alloc.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-backend.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-backend-reg.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-quants.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-threading.cpp
    ${LLAMA_CPP_DIR}/ggml/src/gguf.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/ggml-cpu.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/ggml-cpu.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/binary-ops.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/unary-ops.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/ops.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/traits.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/vec.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/quants.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/hbm.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/repack.cpp
    # ARM architecture specific files
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/arch/arm/cpu-feats.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/arch/arm/quants.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/arch/arm/repack.cpp
    # common utilities
    ${LLAMA_CPP_DIR}/common/common.cpp
    ${LLAMA_CPP_DIR}/common/sampling.cpp
    ${LLAMA_CPP_DIR}/common/log.cpp
)

# Include directories
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${LLAMA_CPP_DIR}
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/ggml/src
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu
    ${LLAMA_CPP_DIR}/common
    ${LLAMA_CPP_DIR}/src
)

# Link libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
    ${android-lib}
    c++_shared
)

# Compiler flags for optimization
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
    -O3
    -ffast-math
    -fno-finite-math-only
    -funroll-loops
)

# Add preprocessor definitions
target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
    GGML_USE_CPU=1
    NDEBUG
    GGML_VERSION="0.9.4"
    GGML_COMMIT="unknown"
)
