package com.mochi.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochi.agent.core.llm.state.AgentState
import com.mochi.agent.ui.components.MochiAvatarView
import com.mochi.agent.ui.theme.MochiBlack
import com.mochi.agent.ui.theme.MochiBlue
import com.mochi.agent.ui.theme.MochiWhite

/**
 * Landing screen: the animated Mochi avatar driven by the agent state, with a
 * state caption underneath.
 */
@Composable
fun HomeScreen(
    state: AgentState,
    actionName: String?,
    actionMessage: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MochiBlack),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            MochiAvatarView(state = state, modifier = Modifier, size = 200)

            Text(
                text = stateCaption(state, actionName, actionMessage),
                color = MochiBlue,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = "Hybrid AI Coding Agent",
                color = MochiWhite.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
        }
    }
}

private fun stateCaption(state: AgentState, actionName: String?, actionMessage: String?): String {
    return when (state) {
        AgentState.THINKING -> "Mochi is thinking…"
        AgentState.ACTION -> "Mochi is doing ${actionName ?: "something"}"
        AgentState.CODING -> "Mochi is coding${actionMessage?.let { " — $it" } ?: ""}"
        AgentState.OFFLINE -> "Mochi is offline"
        AgentState.ERROR -> "Mochi hit an error"
        AgentState.IDLE -> "Mochi is idle"
    }
}