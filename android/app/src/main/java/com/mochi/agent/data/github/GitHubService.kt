package com.mochi.agent.data.github

import com.mochi.agent.core.SecretConfig
import com.mochi.agent.core.security.TokenStore
import com.mochi.agent.data.github.CreateIssueRequest
import com.mochi.agent.data.github.CreatePullRequestRequest
import com.mochi.agent.data.github.GitHubIssue
import com.mochi.agent.data.github.GitHubPullRequest
import com.mochi.agent.data.github.GitHubRepository
import com.mochi.agent.data.github.GitHubUser
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin typed wrapper around the GitHub REST API used by Mochi to list issues,
 * inspect repositories and open pull requests. The same token is used by the
 * PRoot shell to configure git credentials, so behaviour is consistent.
 */
class GitHubService(
    tokenStore: TokenStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(GitHubAuthInterceptor(tokenStore))
        .build()

    private val base = SecretConfig.GITHUB_API_BASE

    suspend fun getAuthenticatedUser(): Result<GitHubUser> =
        execute(
            method = "GET",
            path = "user",
            parse = { json.decodeFromString(GitHubUser.serializer(), it) },
        )

    suspend fun getRepository(name: String): Result<GitHubRepository> =
        execute(
            method = "GET",
            path = "repos/$name",
            parse = { json.decodeFromString(GitHubRepository.serializer(), it) },
        )

    suspend fun listIssues(
        repo: String,
        state: String = "open",
        limit: Int = 30,
    ): Result<List<GitHubIssue>> =
        execute(
            method = "GET",
            path = "repos/$repo/issues?state=$state&per_page=$limit",
            parse = { json.decodeFromString(ListSerializer(GitHubIssue.serializer()), it) },
        )

    suspend fun createPullRequest(
        repo: String,
        requestBody: CreatePullRequestRequest,
    ): Result<GitHubPullRequest> =
        execute(
            method = "POST",
            path = "repos/$repo/pulls",
            body = json.encodeToString(CreatePullRequestRequest.serializer(), requestBody),
            parse = { json.decodeFromString(GitHubPullRequest.serializer(), it) },
        )

    suspend fun createIssue(
        repo: String,
        requestBody: CreateIssueRequest,
    ): Result<GitHubIssue> =
        execute(
            method = "POST",
            path = "repos/$repo/issues",
            body = json.encodeToString(CreateIssueRequest.serializer(), requestBody),
            parse = { json.decodeFromString(GitHubIssue.serializer(), it) },
        )

    /** Performs the request and decodes the success body with [parse]. */
    private suspend fun <T> execute(
        method: String,
        path: String,
        body: String? = null,
        parse: (String) -> T,
    ): Result<T> = runCatching {
        val builder = Request.Builder().url("$base$path")
        val request = if (method == "POST") {
            builder
                .post(body!!.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        } else {
            builder.get().build()
        }
        val responseBody = client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("GitHub ${response.code}: $raw")
            }
            raw
        }
        parse(responseBody)
    }
}