package com.mochi.agent.data.cloud

import com.mochi.agent.core.SecretConfig
import com.mochi.agent.core.security.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal REST client for the Mochi cloud backend. Kept as direct OkHttp calls
 * (no Retrofit) so the WebSocket + REST share one HTTP stack and auth flow.
 *
 * Endpoints mirror backend/app/routers/\*:
 *   POST /api/auth/register -> [TokenResponse]
 *   POST /api/auth/refresh  -> [TokenResponse]
 *   GET/POST /api/projects  -> [ProjectResponse]
 *   POST /api/tasks         -> [TaskResponse]
 *   POST /api/llm/chat      -> [LlmChatResponse] (cloud LLM proxy)
 */
class CloudApi(
    private val tokenStore: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(SecretConfig.CLOUD_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(SecretConfig.CLOUD_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private val base = SecretConfig.CLOUD_HTTP_BASE

    private val deviceName: String = android.os.Build.MODEL
    private val deviceDigest: String by lazy {
        // Per-install digest: stable across the app's lifetime, unique enough
        // to act as the device public-key identity.
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(deviceName.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun registerDevice(): Result<TokenResponse> = runCatching {
        val body = json.encodeToString(
            DeviceRegisterRequest.serializer(),
            DeviceRegisterRequest(deviceName, deviceDigest),
        )
        val request = Request.Builder()
            .url("$base/api/auth/register")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()
        json.decodeFromString(TokenResponse.serializer(), call(request))
    }

    suspend fun refreshToken(): Result<TokenResponse> = runCatching {
        val request = authedRequest("api/auth/refresh").post("{}".toRequestBody(JSON_MEDIA)).build()
        json.decodeFromString(TokenResponse.serializer(), call(request))
    }

    suspend fun createProject(project: ProjectCreate): Result<ProjectResponse> =
        post(
            "api/projects",
            json.encodeToString(ProjectCreate.serializer(), project),
            ProjectResponse.serializer(),
        )

    suspend fun listProjects(): Result<List<ProjectResponse>> =
        get("api/projects") { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ProjectResponse.serializer()), it) }

    suspend fun createTask(task: TaskCreate): Result<TaskResponse> =
        post(
            "api/tasks",
            json.encodeToString(TaskCreate.serializer(), task),
            TaskResponse.serializer(),
        )

    suspend fun chat(messages: List<LlmMessage>, temperature: Double = 0.4): Result<LlmChatResponse> =
        post(
            "api/llm/chat",
            json.encodeToString(
                LlmChatRequest.serializer(),
                LlmChatRequest(messages = messages, temperature = temperature),
            ),
            LlmChatResponse.serializer(),
        )

    suspend fun uploadAgentState(update: AgentStateUpdate): Result<Unit> = runCatching {
        val body = json.encodeToString(AgentStateUpdate.serializer(), update)
        val request = authedRequest("api/state").put(body.toRequestBody(JSON_MEDIA)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("PUT state: HTTP ${response.code}")
        }
    }

    // ---- low-level helpers ---------------------------------------------------

    /** Cached cloud session token, registering the device on first use. */
    private suspend fun requireToken(): String {
        tokenStore.cloudToken?.takeIf { tokenValid() }?.let { return it }
        val registered = registerDevice().getOrThrow()
        tokenStore.cloudToken = registered.token
        tokenStore.cloudTokenExpiresAtMillis = System.currentTimeMillis()
        return registered.token
    }

    private fun tokenValid(): Boolean =
        tokenStore.cloudTokenExpiresAtMillis == 0L ||
            System.currentTimeMillis() < tokenStore.cloudTokenExpiresAtMillis + 24.hours

    private suspend fun authedRequest(path: String): Request.Builder =
        Request.Builder()
            .url("$base$path")
            .header("Authorization", "Bearer ${requireToken()}")

    private suspend fun <T> post(
        path: String,
        body: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): Result<T> = execute("POST", path, body, serializer)

    private suspend fun <T> get(
        path: String,
        parse: (String) -> T,
    ): Result<T> = runCatching {
        val request = authedRequest(path).get().build()
        parse(call(request))
    }

    private suspend fun <T> execute(
        method: String,
        path: String,
        body: String,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): Result<T> = runCatching {
        val builder = authedRequest(path)
        val request = if (method == "POST") {
            builder.post(body.toRequestBody(JSON_MEDIA)).build()
        } else {
            builder.get().build()
        }
        json.decodeFromString(serializer, call(request))
    }

    private fun call(request: Request): String =
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $raw")
            }
            raw
        }

    private val Int.hours: Long get() = toLong() * 60L * 60L * 1000L

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}