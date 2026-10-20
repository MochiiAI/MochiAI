package com.mochi.agent.data.cloud

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- REST DTOs (mirror backend/app/schemas.py) -----------------------------

@Serializable
data class DeviceRegisterRequest(
    @SerialName("device_name") val deviceName: String,
    @SerialName("public_key_digest") val publicKeyDigest: String,
)

@Serializable
data class TokenResponse(
    val token: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("device_id") val deviceId: String,
)

@Serializable
data class ProjectResponse(
    val id: String,
    @SerialName("repository_full_name") val repositoryFullName: String,
    @SerialName("checkout_path") val checkoutPath: String? = null,
    val branch: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class ProjectCreate(
    @SerialName("repository_full_name") val repositoryFullName: String,
    @SerialName("checkout_path") val checkoutPath: String? = null,
    val branch: String? = null,
)

@Serializable
data class TaskResponse(
    val id: String,
    val title: String,
    val prompt: String,
    val status: String,
    val result: String? = null,
    val error: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("project_id") val projectId: String? = null,
)

@Serializable
data class TaskCreate(
    val title: String,
    val prompt: String,
    @SerialName("project_id") val projectId: String? = null,
)

@Serializable
data class LlmMessage(
    val role: String,
    val content: String,
)

@Serializable
data class LlmChatRequest(
    val messages: List<LlmMessage>,
    val temperature: Double = 0.4,
    @SerialName("max_tokens") val maxTokens: Int? = 4096,
    val stream: Boolean = false,
    val provider: String? = null,
)

@Serializable
data class LlmChatResponse(
    val provider: String,
    val model: String,
    val content: String,
    val usage: Map<String, Int> = emptyMap(),
)

// ---- WebSocket wire protocol -----------------------------------------------

@Serializable
data class AgentStateUpdate(
    val state: String,
    @SerialName("action_name") val actionName: String? = null,
    @SerialName("action_detail") val actionDetail: String? = null,
    @SerialName("repo_full_name") val repoFullName: String? = null,
    val message: String? = null,
)

@Serializable
data class WsEnvelope(
    val type: String,
    val state: String? = null,
    @SerialName("action_name") val actionName: String? = null,
    @SerialName("action_detail") val actionDetail: String? = null,
    val message: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    val event: String? = null,
    val data: kotlinx.serialization.json.JsonObject? = null,
    val detail: String? = null,
)

@Serializable
data class WsPing(val type: String = "ping")

@Serializable
data class WsStateGet(val type: String = "state.get")

@Serializable
data class WsAgentStateSend(
    val type: String = "agent.state",
    val state: String,
    @SerialName("action_name") val actionName: String? = null,
    @SerialName("action_detail") val actionDetail: String? = null,
    val message: String? = null,
)

@Serializable
data class WsProjectEvent(
    val type: String = "project.state",
    @SerialName("repo_full_name") val repoFullName: String,
    val state: String = "IDLE",
)