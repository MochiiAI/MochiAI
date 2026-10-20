package com.mochi.agent.core.llm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mochi.agent.core.llm.state.AgentState
import com.mochi.agent.state.AgentStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that owns the on-device LLM lifecycle.
 *
 * The UI talks to this service via Kotlin Flows ([responses] flows each result;
 * [agentState] mirrors the last published state). The JNI bridge performs
 * inference on a background dispatcher so the main thread is never blocked.
 */
class LocalLlmService : Service() {

    companion object {
        private const val CHANNEL_ID = "mochi_llm"
        private const val NOTIFICATION_ID = 42
        const val ACTION_LOAD = "com.mochi.agent.llm.LOAD"
        const val EXTRA_MODEL_PATH = "model_path"
        const val ACTION_GENERATE = "com.mochi.agent.llm.GENERATE"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_JOB_ID = "job_id"
        const val ACTION_UNLOAD = "com.mochi.agent.llm.UNLOAD"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobCounter = java.util.concurrent.atomic.AtomicInteger(0)

    private val _responses = MutableStateFlow<LlmResponse?>(null)
    val responses: StateFlow<LlmResponse?> = _responses.asStateFlow()

    private val _loaded = MutableStateFlow<String?>(null)
    val loadedModelPath: StateFlow<String?> = _loaded.asStateFlow()

    private val _agentState = MutableStateFlow(AgentStateFlow.current.value)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOAD -> {
                val path = intent.getStringExtra(EXTRA_MODEL_PATH)
                if (path != null) loadModel(File(path))
            }
            ACTION_GENERATE -> {
                val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: return START_STICKY
                val jobId = intent.getIntExtra(EXTRA_JOB_ID, jobCounter.incrementAndGet())
                generate(prompt, jobId)
            }
            ACTION_UNLOAD -> {
                _agentState.value = AgentState.IDLE
                MochiNativeBridge.unload()
                _loaded.value = null
            }
        }
        return START_STICKY
    }

    private fun loadModel(modelFile: File) {
        _agentState.value = AgentState.THINKING
        scope.launch {
            val error = MochiNativeBridge.loadModel(modelFile)
            if (error == null) {
                _loaded.value = modelFile.absolutePath
                _responses.value = LlmResponse.Loaded(modelFile.absolutePath)
            } else {
                _responses.value = LlmResponse.Error(error)
            }
            _agentState.value = AgentState.IDLE
        }
    }

    private fun generate(prompt: String, jobId: Int) {
        _agentState.value = AgentState.THINKING
        scope.launch {
            val result: LlmResponse = MochiNativeBridge.generate(prompt).fold(
                onSuccess = { LlmResponse.Text(jobId, it) },
                onFailure = { LlmResponse.Error(it.message ?: "generation failed") },
            )
            _responses.value = result
            _agentState.value = AgentState.IDLE
        }
    }

    override fun onDestroy() {
        MochiNativeBridge.unload()
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = baseNotification("Mochi is idle")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun baseNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mochi")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_mochi)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mochi local inference",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }
}

sealed interface LlmResponse {
    data class Loaded(val modelPath: String) : LlmResponse
    data class Text(val jobId: Int, val text: String) : LlmResponse
    data class Error(val message: String) : LlmResponse
}