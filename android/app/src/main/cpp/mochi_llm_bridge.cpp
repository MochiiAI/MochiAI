/*
 * mochi_llm_bridge.cpp - JNI bridge between MochiNativeBridge (Kotlin) and the
 * on-device LLM (llama.cpp / mlc-llm through its C wrapper).
 *
 * Java surface (see com.mochi.agent.core.llm.MochiNativeBridge):
 *   native String nativeInit(String modelPath)          -> error, null on success
 *   native String nativeGenerate(String prompt, int maxTokens)
 *   native void   nativeFree()
 *   native boolean nativeIsAvailable()
 *
 * All exported symbols use a fixed C ABI so swapping the inference backend
 * never requires a Kotlin change.
 */

#include "llama_jni.h"

#include <android/log.h>
#include <jni.h>

#include <cstring>
#include <string>
#include <vector>

#define LOG_TAG "mochi-llm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// ---- backend hook defaults (stub mode) ------------------------------------
// When ""MOCHI_USE_REAL_BACKEND"" is OFF these are what actually run. A real
// backend replaces the bodies of these six functions; the JNI layer below is
// deliberately agnostic.

mochi_model *g_model = nullptr;

int backend_init(mochi_model **model, const char *model_path) {
    (void)model;
    (void)model_path;
    return -1; // stub: model loading is not wired up in a default build
}

void backend_free(mochi_model *model) {
    (void)model; // stub
}

int backend_available(void) { return 0; }

int backend_generate(mochi_model *model, const char *prompt, char *out, size_t out_cap,
                     unsigned int max_tokens) {
    (void)model;
    (void)prompt;
    (void)out;
    (void)out_cap;
    (void)max_tokens;
    return -1;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_mochi_agent_core_llm_MochiNativeBridge_nativeInit(JNIEnv *env, jobject /*thiz*/,
                                                           jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    mochi_model *model = nullptr;
    int rc = mochi_backend_init(&model, path);
    env->ReleaseStringUTFChars(modelPath, path);
    if (rc == 0) {
        g_model = model;
        LOGI("model loaded: %s", "ok");
        return nullptr;
    }
    LOGE("failed to load model (rc=%d)", rc);
    jstring err = env->NewStringUTF(
        "Failed to load local model; the default build ships without a bundled "
        "llama.cpp backend. Drop model weights under app/src/main/assets/llm/.");
    return err;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mochi_agent_core_llm_MochiNativeBridge_nativeGenerate(JNIEnv *env, jobject /*thiz*/,
                                                               jstring prompt, jint maxTokens) {
    if (g_model == nullptr) {
        return env->NewStringUTF("local model is not initialised");
    }
    const char *utf = env->GetStringUTFChars(prompt, nullptr);
    std::vector<char> buffer(64 * 1024);
    int rc = mochi_backend_generate(g_model, utf, buffer.data(), buffer.size(),
                                    static_cast<unsigned int>(maxTokens));
    env->ReleaseStringUTFChars(prompt, utf);
    if (rc != 0) {
        return env->NewStringUTF("local generation failed");
    }
    return env->NewStringUTF(buffer.data());
}

extern "C" JNIEXPORT void JNICALL
Java_com_mochi_agent_core_llm_MochiNativeBridge_nativeFree(JNIEnv * /*env*/, jobject /*thiz*/) {
    mochi_backend_free(g_model);
    g_model = nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mochi_agent_core_llm_MochiNativeBridge_nativeIsAvailable(JNIEnv * /*env*/,
                                                                  jobject /*thiz*/) {
    return mochi_backend_available() != 0 ? JNI_TRUE : JNI_FALSE;
}

// ---------------------------------------------------------------------------
// default backend hook implementations (used when MOCHI_USE_REAL_BACKEND=OFF)
// A real backend should #define MOCHI_BACKEND_OVERRIDDEN or simply provide
// these symbols from its own TU.
#ifndef MOCHI_BACKEND_OVERRIDDEN
int mochi_backend_init(mochi_model **model, const char *model_path) {
    return backend_init(model, model_path);
}
void mochi_backend_free(mochi_model *model) { backend_free(model); }
int mochi_backend_available(void) { return backend_available(); }
int mochi_backend_generate(mochi_model *model, const char *prompt, char *out, size_t out_cap,
                           unsigned int max_tokens) {
    return backend_generate(model, prompt, out, out_cap, max_tokens);
}
#endif