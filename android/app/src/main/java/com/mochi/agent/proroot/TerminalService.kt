package com.mochi.agent.proroot

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.mochi.agent.core.llm.state.AgentState
import com.mochi.agent.state.AgentStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Long-lived background service hosting the PRoot terminal session. The UI
 * binds to it to receive the output flow and to inject keystrokes.
 */
class TerminalService : Service() {

    companion object {
        const val ACTION_START = "com.mochi.agent.terminal.START"
        const val ACTION_STOP = "com.mochi.agent.terminal.STOP"
        const val ACTION_WRITE = "com.mochi.agent.terminal.WRITE"
        const val ACTION_WRITE_NAKED = "com.mochi.agent.terminal.WRITE_NAKED"
        const val EXTRA_INPUT = "input"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var terminal: TerminalSession

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        terminal = TerminalSession {
            AndroidPrRootManager(this, com.mochi.agent.core.security.TokenStore(this)).let { manager ->
                manager.provisionGitCredentials()
                manager.buildCommand()
            }
        }
        scope.launch {
            terminal.output.collect { line ->
                TerminalBus.emit(line)
                AgentStateFlow.update(AgentState.CODING, "Running terminal command")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!terminal.isRunning) terminal.start()
            }
            ACTION_STOP -> {
                terminal.destroy()
                AgentStateFlow.update(AgentState.IDLE)
            }
            ACTION_WRITE -> terminal.write(intent.getStringExtra(EXTRA_INPUT).orEmpty())
            ACTION_WRITE_NAKED -> terminal.writeNaked(intent.getStringExtra(EXTRA_INPUT).orEmpty())
        }
        return START_STICKY
    }

    override fun onDestroy() {
        terminal.destroy()
        scope.cancel()
        super.onDestroy()
    }
}