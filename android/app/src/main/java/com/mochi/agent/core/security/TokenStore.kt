package com.mochi.agent.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.mochi.agent.core.SecretConfig

/**
 * Secure key-value persistence backed by [EncryptedSharedPreferences].
 *
 * Stores the user-supplied GitHub Personal Access Token and the Mochi cloud
 * session token. When no user token has been configured, callers fall back to
 * [SecretConfig.DEFAULT_GITHUB_TOKEN] so the agent works out-of-the-box.
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ---- GitHub PAT ---------------------------------------------------------

    val hasUserGitHubToken: Boolean get() = userGitHubToken != null

    /** The user's own token if one has been saved, otherwise the fallback. */
    val effectiveGitHubToken: String
        get() = userGitHubToken ?: SecretConfig.DEFAULT_GITHUB_TOKEN

    var userGitHubToken: String?
        get() = prefs.getString(KEY_USER_GITHUB_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_USER_GITHUB_TOKEN) else putString(KEY_USER_GITHUB_TOKEN, value)
            }.apply()
        }

    // ---- Mochi cloud session ------------------------------------------------

    var cloudToken: String?
        get() = prefs.getString(KEY_CLOUD_TOKEN, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_CLOUD_TOKEN) else putString(KEY_CLOUD_TOKEN, value)
            }.apply()
        }

    var cloudTokenExpiresAtMillis: Long
        get() = prefs.getLong(KEY_CLOUD_TOKEN_EXPIRES, 0L)
        set(value) = prefs.edit().putLong(KEY_CLOUD_TOKEN_EXPIRES, value).apply()

    /** The authoritative PAT used for git + GitHub API operations. */
    val activeGitHubToken: String get() = effectiveGitHubToken

    fun clear() {
        prefs.edit()
            .remove(KEY_USER_GITHUB_TOKEN)
            .remove(KEY_CLOUD_TOKEN)
            .remove(KEY_CLOUD_TOKEN_EXPIRES)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "mochi_secure_prefs"
        const val KEY_USER_GITHUB_TOKEN = "user_github_token"
        const val KEY_CLOUD_TOKEN = "cloud_token"
        const val KEY_CLOUD_TOKEN_EXPIRES = "cloud_token_expires_at"
    }
}