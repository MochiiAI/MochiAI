/*
 * llama_jni.h - minimal C ABI shared between the bridge and a real llama.cpp
 * or mlc-llm backend.
 *
 * The JNI entry points use stable method/field names (see MochiNativeBridge in
 * com.mochi.agent.core.llm) so the Java layer never depends on llama.cpp or
 * tvm headers directly.
 */
#ifndef MOCHI_LLAMA_JNI_H
#define MOCHI_LLAMA_JNI_H

#include <jni.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// Opaque handle used by the backend implementation.
typedef struct mochi_model mochi_model;

/*
 * Forward-declared backend hooks.
 *
 * The current default implementation (mochi_llm_bridge.cpp) provides a stub
 * that loads a model *only* for forward-compatibility documentation:
 *   mochi_backend_is_available() -> 0
 * and every other function is a no-op returning an error string. Real
 * backends must simply provide these symbols; the .so stays ABI-stable from
 * the Java side.
 */
int mochi_backend_init(mochi_model **model, const char *model_path);
void mochi_backend_free(mochi_model *model);
int mochi_backend_available(void);
int mochi_backend_generate(mochi_model *model, const char *prompt, char *out, size_t out_cap,
                           unsigned int max_tokens);

#ifdef __cplusplus
} // extern "C"
#endif

#endif // MOCHI_LLAMA_JNI_H