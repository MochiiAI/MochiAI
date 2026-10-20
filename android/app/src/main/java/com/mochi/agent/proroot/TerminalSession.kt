package com.mochi.agent.proroot

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Backs the terminal UI with a live process emitting stdout lines via a
 * [Flow]. A sibling [write] sends keystrokes/commands to the process.
 *
 * [execute] wraps a command with an echo sentinel so it can capture exactly
 * the output produced by that one command while the shell stays alive.
 */
class TerminalSession(
    private val startCommand: () -> Pair<java.io.File, List<String>>,
) {
    private val _output = MutableSharedFlow<String>(
        replay = 64,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val output: Flow<String> = _output.asSharedFlow()

    private var process: Process? = null
    private var processOutput: OutputStream? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String? = null,
    )

    val isRunning: Boolean get() = running.get()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val (binary, args) = startCommand()
        val pb = ProcessBuilder(listOf(binary.absolutePath) + args)
        pb.directory(binary.parentFile)
        pb.redirectErrorStream(true)
        try {
            val proc = pb.start()
            process = proc
            processOutput = proc.outputStream
            executor.execute {
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                try {
                    while (true) {
                        val line = reader.readLine() ?: break
                        _output.tryEmit(line)
                    }
                } finally {
                    val code = runCatching { proc.waitFor() }.getOrDefault(-1)
                    running.set(false)
                    _output.tryEmit("mochi: terminal exited ($code)")
                }
            }
        } catch (e: Exception) {
            running.set(false)
            _output.tryEmit("mochi: failed to start terminal: ${e.message}")
        }
    }

    /**
     * Wait for a discrete command to finish and return its captured output.
     * The command is wrapped so the end of its output is unambiguously marked
     * even when stderr and stdout are merged.
     */
    suspend fun execute(command: String, timeoutMillis: Long = 60_000L): CommandResult {
        val marker = "MOCHI_CMD_END_${System.nanoTime()}"
        val wrapped = "$command; echo \"$marker \$?\""
        write(wrapped)

        val collected = StringBuilder()
        return try {
            withTimeoutOrNull(timeoutMillis) {
                _output.collect { line ->
                    collected.appendLine(line)
                    val idx = line.indexOf(marker)
                    if (idx >= 0) {
                        val exitPart = line.substring(idx + marker.length).trim()
                        val exitCode = exitPart.split("\\s+".toRegex())
                            .firstOrNull()?.toIntOrNull() ?: 0
                        throw ResultFound(
                            CommandResult(exitCode, collected.toString()),
                            marker,
                        )
                    }
                }
                null
            } ?: CommandResult(-1, "command timed out after ${timeoutMillis}ms")
        } catch (e: ResultFound) {
            if (e.marker != marker) {
                // A result from an older invocation surfaced (missed cleanup);
                // wait for this command's marker instead.
                execute(command, timeoutMillis)
            } else {
                e.payload
            }
        }
    }

    private class ResultFound(
        val payload: CommandResult,
        val marker: String,
    ) : Throwable(null, null, false, false)

    /** Write a line (e.g. a shell command) to the running process. */
    fun write(line: String) {
        val stream = processOutput ?: return
        runCatching {
            stream.write((line + "\n").toByteArray())
            stream.flush()
        }
    }

    fun writeNaked(data: String) {
        val stream = processOutput ?: return
        runCatching {
            stream.write(data.toByteArray())
            stream.flush()
        }
    }

    fun destroy() {
        running.compareAndSet(true, false)
        runCatching { process?.destroy() }
        runCatching { process?.waitFor() }
        process = null
        processOutput = null
    }
}