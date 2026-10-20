package com.mochi.agent.state

import com.mochi.agent.core.llm.state.AgentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide source of truth for Mochi's agent state. The UI, local LLM
 * service, PRoot terminal and cloud WebSocket all publish and observe this
 * single flow, so the avatar always reflects the active intent.
 */
object AgentStateFlow {

    private val _state = MutableStateFlow(AgentState.IDLE)
    val state: StateFlow<AgentState> = _state.asStateFlow()

    private val _actionName = MutableStateFlow<String?>(null)
    val actionName: StateFlow<String?> = _actionName.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    @Synchronized
    fun update(newState: AgentState, action: String? = null, message: String? = null) {
        _state.value = newState
        _actionName.value = action
        _message.value = message
    }

    val current: AgentState get() = _state.value
}