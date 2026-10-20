package com.mochi.agent.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.mochi.agent.core.llm.state.AgentState
import com.mochi.agent.ui.theme.MochiBlue
import com.mochi.agent.ui.theme.MochiBlueSoft
import com.mochi.agent.ui.theme.MochiPanel

/**
 * Maps backend [AgentState] to an animated Mochi avatar.
 *
 * Lottie/Rive files live under ``res/raw`` and are optional; when an asset is
 * missing the composable falls back to a Compose-drawn head so the app always
 * renders something meaningful.
 *
 *   IDLE     -> floating, blinking head
 *   THINKING -> pulsing head + thinking ring
 *   ACTION   -> head with action badge shimmer
 *   CODING   -> full-body mochi walking a keyboard (Rive `mochi_coding.riv`)
 */
@Composable
fun MochiAvatarView(
    state: AgentState,
    modifier: Modifier = Modifier,
    size: Int = 140,
) {
    when (state) {
        AgentState.CODING -> CodingMochi(modifier = modifier, size = size)
        else -> HeadMochi(state = state, modifier = modifier, size = size)
    }
}

@Composable
private fun HeadMochi(
    state: AgentState,
    modifier: Modifier,
    size: Int,
) {
    val composition = RememberedLottieByState(state)
    if (composition != null) {
        val progress by animateLottieCompositionAsState(composition)
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = modifier.size(size.dp),
        )
    } else {
        DrawnHead(
            state = state,
            modifier = modifier,
            size = size,
        )
    }
}

@Composable
private fun RememberedLottieByState(state: AgentState): LottieComposition? {
    val context = LocalContext.current
    val resId = when (state) {
        AgentState.THINKING -> rawRes(context, "mochi_thinking")
        AgentState.ACTION -> rawRes(context, "mochi_action")
        AgentState.CODING -> rawRes(context, "mochi_coding")
        else -> rawRes(context, "mochi_idle")
    } ?: return null
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    return composition
}

private fun rawRes(context: android.content.Context, name: String): Int? =
    context.resources.getIdentifier(name, "raw", context.packageName).takeIf { it != 0 }

@Composable
private fun DrawnHead(
    state: AgentState,
    modifier: Modifier,
    size: Int,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = remember(size, density) { with(density) { size.dp.toPx() } }
    val transition = rememberInfiniteTransition(label = "mochi-head")
    val float by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "float",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "breathe",
    )
    val ring by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "ring",
    )
    val glow = when (state) {
        AgentState.THINKING -> MochiBlue
        AgentState.ACTION -> MochiBlueSoft
        else -> Color(0xFF2A2F45)
    }
    val floatPx = with(density) { float.dp.toPx() }

    Box(
        modifier = modifier
            .size(size.dp)
            .offset(y = floatPx),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size.dp)) {
            // Head (all geometry derived from the px size of the box).
            drawCircle(color = glow.copy(alpha = 0.25f), radius = px * 0.44f * breathe)
            drawCircle(color = glow, radius = px * 0.38f * breathe)
            drawCircle(color = MochiBlueDark, radius = px * 0.34f * breathe)
            // Eyes
            drawCircle(Color.White, radius = px * 0.05f, center = center + Offset(-px * 0.12f * breathe, -px * 0.04f))
            drawCircle(Color.White, radius = px * 0.05f, center = center + Offset(px * 0.12f * breathe, -px * 0.04f))
            // Smile
            drawArc(
                color = MochiBlueSoft,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = center + Offset(-px * 0.1f, -px * 0.02f),
                size = Size(px * 0.2f, px * 0.14f),
                style = Stroke(width = px * 0.03f, cap = StrokeCap.Round),
            )
            // Thinking/action ring
            if (state == AgentState.THINKING || state == AgentState.ACTION) {
                drawArc(
                    color = glow.copy(alpha = 0.85f),
                    startAngle = ring * 360f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(px * 0.28f, px * 0.28f),
                    size = Size(px * 0.44f, px * 0.44f),
                    style = Stroke(width = px * 0.03f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
private fun CodingMochi(modifier: Modifier, size: Int) {
    val context = LocalContext.current
    val rivId = rawRes(context, "mochi_coding")
    if (rivId != null) {
        AndroidView(
            modifier = modifier.size(size.dp),
            factory = { ctx ->
                RiveAnimationView(ctx).apply {
                    runCatching { setRiveResource(rivId) }
                }
            },
        )
        return
    }
    DrawnCodingMochi(modifier, size)
}

@Composable
private fun DrawnCodingMochi(modifier: Modifier, size: Int) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = remember(size, density) { with(density) { size.dp.toPx() } }
    val transition = rememberInfiniteTransition(label = "code-walk")
    val walk by transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "walk-phase",
    )
    val bob by transition.animateFloat(
        0f,
        px * 0.06f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "bob",
    )
    Canvas(modifier = modifier.size(size.dp)) {
        val x = (walk - 0.5f) * px * 0.3f
        // Keyboard base
        drawRoundRect(
            color = MochiPanel,
            topLeft = center + Offset(-px * 0.38f, px * 0.18f),
            size = Size(px * 0.76f, px * 0.16f),
            cornerRadius = CornerRadius(px * 0.06f),
        )
        repeat(9) { i ->
            drawRect(
                color = MochiBlue.copy(alpha = 0.6f),
                topLeft = center + Offset(-px * 0.32f + i * px * 0.08f, px * 0.22f),
                size = Size(px * 0.045f, px * 0.05f),
            )
        }
        // Walking mochi body
        val bodyCenter = center + Offset(x, -px * 0.08f + bob)
        drawCircle(color = MochiBlue, radius = px * 0.12f, center = bodyCenter)
        drawCircle(color = MochiBlueDark, radius = px * 0.1f, center = bodyCenter)
        drawCircle(Color.White, radius = px * 0.022f, center = bodyCenter + Offset(-px * 0.04f, -px * 0.02f))
        drawCircle(Color.White, radius = px * 0.022f, center = bodyCenter + Offset(px * 0.04f, -px * 0.02f))
        // Feet (walking)
        val legSwing = if (x > 0) 1f else -1f
        drawCircle(color = MochiBlueSoft, radius = px * 0.03f, center = bodyCenter + Offset(legSwing * px * 0.06f, px * 0.1f))
        drawCircle(color = MochiBlueSoft, radius = px * 0.03f, center = bodyCenter + Offset(-legSwing * px * 0.06f, px * 0.1f))
    }
}

private val MochiBlueDark = Color(0xFF16318F)