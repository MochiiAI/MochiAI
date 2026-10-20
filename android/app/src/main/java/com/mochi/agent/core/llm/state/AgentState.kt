package com.mochi.agent.core.llm.state

/**
 * Mirror of the backend's AgentState so the app and cloud agree on the wire
 * protocol. Values are serialised verbatim over the WebSocket.
 */
enum class AgentState(val wire: String) {
    IDLE("IDLE"),
    THINKING("THINKING"),
    ACTION("ACTION"),
    CODING("CODING"),
    OFFLINE("OFFLINE"),
    ERROR("ERROR");

    companion object {
        fun fromWire(value: String): AgentState =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: IDLE
    }
}