package com.mochi.agent.data.github

import com.mochi.agent.core.SecretConfig
import com.mochi.agent.core.security.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the Bearer GitHub token to every REST request, using the configured
 * interceptor that is refreshed at call time so a user token swap takes effect
 * without rebuilding the client graph.
 */
class GitHubAuthInterceptor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val authenticated = request.newBuilder()
            .header("Authorization", "Bearer ${tokenStore.effectiveGitHubToken}")
            .header("User-Agent", SecretConfig.GITHUB_USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        return chain.proceed(authenticated)
    }
}