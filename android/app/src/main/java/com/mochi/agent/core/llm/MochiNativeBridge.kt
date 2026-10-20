package com.mochi.agent.core.llm

import android.util.Log
import java.io.File

/**
 * JNI bridge to the on-device LLM (llama.cpp / mlc-llm).
 *
 * The native side exposes a fixed C ABI (see ``cpp/llama_jni.h``). The default
 * build ships a stub .so; to enable real inference, build with
 * ``MOCHI_USE_REAL_BACKEND=ON`` and drop a model file (e.g. ``qwen2.5-1.5b``
 * GGUF) under ``app/src/main/assets/llm/``.
 *
 * This object is thread-confined: nativeGenerate must not be called from more
 * than one thread at a time.
 */
object MochiNativeBridge {

    private const val TAG = "MochiNativeBridge"

    init {
        System.loadLibrary("mochi_llm")
    }

    @Volatile
    private var loadedModelPath: String? = null

    /** True when the bridge links a real backend (not the stub). */
    val isAvailable: Boolean
        get() = nativeIsAvailable()

    /** Path to the extracted GGUF used by nativeGenerate, or null. */
    val activeModelPath: String? get() = loadedModelPath

    /**
     * Loads [modelFile] into the backend. Returns null on success, otherwise a
     * human-readable error.
     */
    fun loadModel(modelFile: File): String? {
        if (nativeIsAvailable().not()) {
            Log.w(TAG, "Stub backend — no on-device inference available.")
            return "On-device LLM backend is not available in this build (stub .so)."
        }
        val error = nativeInit(modelFile.absolutePath)
        if (error == null) {
            loadedModelPath = modelFile.absolutePath
            Log.i(TAG, "local model loaded: ${modelFile.name}")
        }
        return error
    }

    /** Synchronously generate up to [maxTokens] tokens for [prompt]. */
    fun generate(prompt: String, maxTokens: Int = 512): Result<String> = runCatching {
        check(nativeIsAvailable()) { "local LLM backend not available" }
        checkNotNull(loadedModelPath) { "no model loaded" }
        val output = nativeGenerate(prompt, maxTokens)
        checkNotNull(output) { "nativeGenerate returned null" }
        output
    }

    fun unload() {
        nativeFree()
        loadedModelPath = null
    }

    private external fun nativeInit(modelPath: String): String?

    private external fun nativeGenerate(prompt: String, maxTokens: Int): String?

    private external fun nativeFree()

    private external fun nativeIsAvailable(): Boolean
}