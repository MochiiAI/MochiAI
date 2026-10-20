package com.mochi.agent.data.cloud

import com.mochi.agent.core.SecretConfig
import com.mochi.agent.core.llm.state.AgentState
import com.mochi.agent.core.security.TokenStore
import com.mochi.agent.state.AgentStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * WebSocket channel to the Mochi cloud backend.
 *
 * Keeps the device connected, replays the last [AgentState] locally and
 * forwards inbound ``agent.state``/``event``/``heartbeat`` frames into
 * [AgentStateFlow] so the UI avatar animates. Automatically reconnects with
 * exponential backoff and refreshes the session token via the REST API.
 *
 * Outbound frames the app may send:
 *   ```json
 *   {"type":"ping"}
 *   {"type":"agent.state","state":"THINKING","action_name":null,"message":null}
 *   {"type":"state.get"}
 *   ```
 */
class CloudWebSocketClient(
    private val tokenStore: TokenStore,
    private val cloudApi: CloudApi,
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _connectionState = MutableSharedFlow<ConnectionState>(
        extraBufferCapacity = 4,
    )
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    private val _incoming = MutableSharedFlow<WsEnvelope>(
        extraBufferCapacity = 32,
    )
    val incoming: SharedFlow<WsEnvelope> = _incoming.asSharedFlow()

    @Volatile
    private var socket: WebSocket? = null
    @Volatile
    private var closing = false
    @Volatile
    private var attempts = 0

    enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, FAILED }

    @Synchronized
    fun connect() {
        if (socket != null && closing.not()) return
        closing = false
        appScope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        while (true) {
            if (closing || !appScope.isActive) return
            _connectionState.tryEmit(ConnectionState.CONNECTING)
            // Token may have expired; refresh before (re)connecting.
            tokenStore.cloudToken ?: cloudApi.refreshToken().getOrNull()
            val token = tokenStore.cloudToken ?: cloudApi.registerDevice().getOrNull()?.also {
                tokenStore.cloudToken = it.token
            }?.token ?: run {
                _connectionState.tryEmit(ConnectionState.FAILED)
                return
            }

            val request = Request.Builder()
                .url(buildWsUrl(token))
                .build()
            val listener = createListener()
            socket = HTTP_CLIENT.newWebSocket(request, listener)

            // Wait until the socket is dead (listener updates socket==null) or
            // we are told to close.
            delay(WAIT_STEP)
            while (socket != null && !closing) {
                delay(WAIT_STEP)
            }
            if (closing) return

            attempts++
            val backoff = (BASE_DELAY_MS shl attempts.coerceAtMost(MAX_EXPONENT)).coerceAtMost(MAX_DELAY_MS)
            _connectionState.tryEmit(ConnectionState.DISCONNECTED)
            delay(backoff)
        }
    }

    private fun buildWsUrl(token: String): String {
        val base = SecretConfig.CLOUD_WS_URL
        val separator = if ('?' in base) "&" else "?"
        return "$base${separator}token=$token"
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempts = 0
            _connectionState.tryEmit(ConnectionState.CONNECTED)
            // On (re)connect, publish the app's current state + request server state.
            socket = webSocket
            sendState(AgentStateFlow.current)
            webSocket.send("""{"type":"state.get"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val envelope = json.decodeFromString(WsEnvelope.serializer(), text)
                handle(envelope)
            } catch (e: Exception) {
                // Ignore malformed frames; the server validates too.
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = Unit

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            socket = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            socket = null
        }
    }

    private fun handle(envelope: WsEnvelope) {
        when (envelope.type) {
            "hello",
            "heartbeat" -> {
                val state = AgentState.fromWire(envelope.state.orEmpty())
                if (envelope.state != null) {
                    AgentStateFlow.update(state, envelope.actionName, envelope.message)
                }
            }
            "agent.state" -> {
                val state = AgentState.fromWire(envelope.state.orEmpty())
                AgentStateFlow.update(state, envelope.actionName, envelope.message)
            }
            "state" -> {
                AgentStateFlow.update(AgentState.fromWire(envelope.state.orEmpty()))
            }
            "event" -> {
                _incoming.tryEmit(envelope)
            }
            "error" -> {
                _connectionState.tryEmit(ConnectionState.FAILED)
            }
        }
        _incoming.tryEmit(envelope)
    }

    /** Push a state change to the cloud (used after local LLM/terminal jobs). */
    fun sendState(state: AgentState, actionName: String? = null, message: String? = null) {
        val ws = socket ?: return
        if (closing) return
        val frame = json.encodeToString(
            WsAgentStateSend.serializer(),
            WsAgentStateSend(
                state = state.wire,
                actionName = actionName,
                message = message,
            ),
        )
        runCatching { ws.send(frame) }
    }

    fun sendPing() {
        runCatching { socket?.send("""{"type":"ping"}""") }
    }

    @Synchronized
    fun close() {
        closing = true
        runCatching { socket?.close(1000, "client closing") }
        socket = null
    }

    private companion object {
        val HTTP_CLIENT = OkHttpClient.Builder()
            .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        const val WAIT_STEP = 1_000L
        const val BASE_DELAY_MS = 1_000L
        const val MAX_DELAY_MS = 30_000L
        const val MAX_EXPONENT = 5
    }
}