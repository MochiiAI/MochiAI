package com.mochi.agent.core

import com.mochi.agent.BuildConfig

/**
 * Centralised secrets for Mochi.
 *
 * [DEFAULT_GITHUB_TOKEN] is the fallback used when the user has not saved
 * their own GitHub Personal Access Token. It belongs to the MochiiAI service
 * account and gives the agent read-write access to GitHub repositories
 * (clone / commit / push / issues / pull requests).
 *
 * The value is injected at build time from the Gradle property
 * `MOCHI_DEFAULT_GITHUB_TOKEN` (see `app/build.gradle.kts` and
 * `android/local.properties.example`) so the repository itself never contains
 * a live credential — GitHub push protection rejects hardcoded PATs. When the
 * property is unset the fallback is empty and the UI prompts for a token.
 *
 * NOTE: Shipping a production credential in an APK is insecure by design; this
 * default exists only so the agent works out-of-the-box. Users are strongly
 * encouraged to configure their own PAT which is then stored in
 * [androidx.security.crypto.EncryptedSharedPreferences].
 */
object SecretConfig {

    /** Fallback GitHub PAT for the MochiiAI service account (build-time). */
    val DEFAULT_GITHUB_TOKEN: String
        get() = BuildConfig.MOCHI_DEFAULT_GITHUB_TOKEN

    /** GitHub REST API base URL. */
    const val GITHUB_API_BASE = "https://api.github.com/"

    const val GITHUB_USER_AGENT = "MochiAI-Android/0.1.0"

    /** Cloud backend (FastAPI + WebSockets). Overridden per build type. */
    const val CLOUD_HTTP_BASE = BuildConfig.MOCHI_CLOUD_HTTP_URL

    const val CLOUD_WS_URL = BuildConfig.MOCHI_CLOUD_WS_URL

    /** Timeout (ms) for cloud API calls. */
    const val CLOUD_CONNECT_TIMEOUT_MS = 15_000L

    const val CLOUD_READ_TIMEOUT_MS = 120_000L
}