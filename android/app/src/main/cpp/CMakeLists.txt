cmake_minimum_required(VERSION 3.22.1)
project(mochi_llm LANGUAGES C CXX)

# ---------------------------------------------------------------------------
# Mochi local LLM bridge
#
# By default the build links against a *stubbed* llama.cpp interface so the
# project compiles without vendoring llama.cpp / mlc-llm. To enable the real
# backend put llama.cpp (or mlc-llm) under this directory, uncomment the
# corresponding add_subdirectory block, and flip MOCHI_USE_REAL_BACKEND.
#
#   android/app/src/main/cpp/llama.cpp/  -> https://github.com/ggerganov/llama.cpp
#   android/app/src/main/cpp/mlc-llm/    -> https://github.com/mlc-ai/mlc-llm
# ---------------------------------------------------------------------------

set(MOCHI_USE_REAL_BACKEND OFF CACHE BOOL "Link real llama.cpp backend (OFF = stub)")

add_library(mochi_llm SHARED
    mochi_llm_bridge.cpp
)

target_include_directories(mochi_llm PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# JNI header locations provided by the Android SDK.
target_include_directories(mochi_llm PRIVATE
    ${ANDROID_NDK}/toolchains/llvm/prebuilt/*/sysroot/usr/include
)

if(MOCHI_USE_REAL_BACKEND)
    # ---- llama.cpp (CPU) ---------------------------------------------------
    # add_subdirectory(llama.cpp)          # builds the 'llama' static lib
    # add_subdirectory(llama.cpp/ggml)     # ggml dependency
    # target_include_directories(mochi_llm PRIVATE llama.cpp llama.cpp/ggml/include)
    # target_link_libraries(mochi_llm PRIVATE llama ggml # pthread m)
    #
    # ---- MLC LLM Android artifacts ----------------------------------------
    # target_include_directories(mochi_llm PRIVATE mlc-llm)
    # target_link_libraries(mochi_llm PRIVATE tvm-runtime)
endif()

target_compile_features(mochi_llm PRIVATE cxx_std_17)
target_compile_options(mochi_llm PRIVATE -Wall -Wextra)

# These must match the values loaded by MochiNativeBridge.
find_library(log-lib log)
target_link_libraries(mochi_llm PRIVATE ${log-lib})