package com.mochi.agent.proroot

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide bridge between [TerminalService] (producer) and the UI layer
 * (consumer). Keeps terminal output flowing even when no binding exists.
 */
object TerminalBus {
    private val _lines = MutableSharedFlow<String>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val lines: SharedFlow<String> = _lines.asSharedFlow()

    fun emit(line: String) {
        _lines.tryEmit(line)
    }
}