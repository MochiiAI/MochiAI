# Mochi — R8/ProGuard rules.
# Keep Kotlinx serialization model classes — they are referenced reflectively.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep JNI entry points; names are matched by JavaVM.
-keepclasseswithmembers class * {
    native <methods>;
}
-keep class com.mochi.agent.core.llm.MochiNativeBridge { *; }

# AndroidX Security (Tink) references Error Prone annotations that are only
# present at compile time. They are CLASS-retention annotations; safe to skip.
-dontwarn com.google.errorprone.annotations.**

# Retrofit / OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Rive / Lottie keep their own consumer rules; nothing to add.