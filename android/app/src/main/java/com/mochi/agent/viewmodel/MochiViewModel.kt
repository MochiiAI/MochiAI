package com.mochi.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochi.agent.MochiApplication
import com.mochi.agent.core.llm.state.AgentState
import com.mochi.agent.data.cloud.LlmMessage
import com.mochi.agent.data.cloud.WsEnvelope
import com.mochi.agent.proroot.AndroidPrRootManager
import com.mochi.agent.state.AgentStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Single source of truth for everything the UI shows: agent state (avatar),
 * a chat transcript and the live terminal log.
 */
class MochiViewModel(private val app: MochiApplication) : ViewModel() {

    val agentState: StateFlow<AgentState> = AgentStateFlow.state
    val actionName: StateFlow<String?> = AgentStateFlow.actionName
    val actionMessage: StateFlow<String?> = AgentStateFlow.message

    private val _chat = MutableStateFlow<List<ChatItem>>(emptyList())
    val chat: StateFlow<List<ChatItem>> = _chat.asStateFlow()

    private val _terminalLines = MutableStateFlow<List<String>>(emptyList())
    val terminalLines: StateFlow<List<String>> = _terminalLines.asStateFlow()

    private val _githubToken = MutableStateFlow("")
    val githubToken: StateFlow<String> = _githubToken.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        _githubToken.value = app.tokenStore.userGitHubToken.orEmpty()
        viewModelScope.launch {
            app.cloudWebSocket.incoming
                .onEach { envelope -> handleCloudEvent(envelope) }
                .collect { }
        }
        viewModelScope.launch {
            com.mochi.agent.proroot.TerminalBus.lines
                .onEach { line -> _terminalLines.update { (it + line).takeLast(MAX_TERMINAL_LINES) } }
                .collect { }
        }
        viewModelScope.launch {
            AgentStateFlow.state.collect { state ->
                if (state != AgentState.IDLE) {
                    app.cloudWebSocket.sendState(state)
                }
            }
        }
    }

    private fun handleCloudEvent(envelope: WsEnvelope) {
        when (envelope.event) {
            "github.pr_created" -> {
                val url = envelope.data?.get("url")?.toString()?.removeSurrounding("\"")
                appendChat(ChatItem.agent("Opened PR: $url".ifBlank { "PR created" }))
            }
            "github.issue_created" -> {
                val url = envelope.data?.get("url")?.toString()?.removeSurrounding("\"")
                appendChat(ChatItem.agent("Created issue: $url".ifBlank { "issue created" }))
            }
        }
    }

    // ---- user actions -------------------------------------------------------

    fun sendChat(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty() || _busy.value) return
        appendChat(ChatItem.user(text))
        _busy.value = true
        AgentStateFlow.update(AgentState.THINKING)
        viewModelScope.launch(Dispatchers.IO) {
            val backend = app.cloudApi.chat(
                messages = listOf(
                    LlmMessage(role = "user", content = text),
                ),
            )
            _busy.value = false
            backend.onSuccess { response ->
                appendChat(ChatItem.agent(response.content))
                AgentStateFlow.update(AgentState.IDLE)
            }.onFailure { e ->
                // Fall back to local LLM when cloud is unreachable.
                val local = runCatching {
                    com.mochi.agent.core.llm.MochiNativeBridge.generate(text, 512).getOrThrow()
                }.getOrNull()
                appendChat(ChatItem.agent(local ?: "Cloud unavailable: ${e.message}"))
                AgentStateFlow.update(AgentState.IDLE)
            }
        }
    }

    fun runTerminalCommand(command: String) {
        if (command.isBlank()) return
        _terminalLines.update { it + "» $command" }
        AgentStateFlow.update(AgentState.CODING, "Running terminal command")
        val startIntent = android.content.Intent(app, com.mochi.agent.proroot.TerminalService::class.java)
            .setAction(com.mochi.agent.proroot.TerminalService.ACTION_START)
        app.startService(startIntent)
        val writeIntent = android.content.Intent(app, com.mochi.agent.proroot.TerminalService::class.java)
            .setAction(com.mochi.agent.proroot.TerminalService.ACTION_WRITE)
            .putExtra(com.mochi.agent.proroot.TerminalService.EXTRA_INPUT, command)
        app.startService(writeIntent)
    }

    fun saveGitHubToken(token: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            app.tokenStore.userGitHubToken = token.trim().ifEmpty { null }
            _githubToken.value = token.trim()
            // Refresh the PRoot credentials with the new token.
            AndroidPrRootManager(app, app.tokenStore)
                .provisionGitCredentials()
            onSaved()
        }
    }

    // ---- helpers ------------------------------------------------------------

    private fun appendChat(item: ChatItem) {
        _chat.update { (it + item).takeLast(MAX_CHAT) }
    }

    data class ChatItem(
        val role: Role,
        val text: String,
    ) {
        enum class Role { USER, AGENT }
        companion object {
            fun user(text: String) = ChatItem(Role.USER, text)
            fun agent(text: String) = ChatItem(Role.AGENT, text)
        }
    }

    private companion object {
        const val MAX_CHAT = 200
        const val MAX_TERMINAL_LINES = 2000
    }
}