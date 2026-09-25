package win.enderr.voidray.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import win.enderr.voidray.vpn.VpnStatus
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The dial of the subscription page: 52 ticks on a 240° arc. With a quota the
 * ticks fill up to the used ratio; without one a glow sweeps the arc.
 */
@Composable
fun TickGauge(ratio: Float, unlimited: Boolean, live: Boolean, tone: Color, idle: Color, modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "sweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        val n = 52
        val scale = minOf(size.width / 200f, size.height / 152f)
        val ox = (size.width - 200f * scale) / 2f
        val lit = (ratio.coerceIn(0f, 1f) * n).roundToInt()
        for (i in 0 until n) {
            val a = Math.toRadians(150.0 + 240.0 * i / (n - 1))
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            val glow = when {
                !unlimited -> if (i < lit) 1f else 0f
                !live -> 0f
                else -> {
                    val ph = (phase + (1f - i / n.toFloat())) % 1f
                    when {
                        ph < 0.05f -> ph / 0.05f
                        ph < 0.13f -> 1f - (ph - 0.05f) / 0.08f
                        else -> 0f
                    }
                }
            }
            val color = lerp(idle, tone, glow).copy(alpha = 0.16f + 0.84f * glow)
            drawLine(
                color,
                Offset(ox + (100 + 66 * c) * scale, (100 + 66 * s) * scale),
                Offset(ox + (100 + 80 * c) * scale, (100 + 80 * s) * scale),
                strokeWidth = 2.9f * scale,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Last 14 days of usage (null = no data that day). */
@Composable
fun UsageBars(values: List<Long?>, bar: Color, void: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (values.size - 1)) / values.size
        val peak = maxOf(1L, values.filterNotNull().maxOrNull() ?: 1L).toFloat()
        values.forEachIndexed { i, v ->
            val x = i * (bw + gap)
            if (v == null) {
                drawRect(void, Offset(x, size.height - 2.dp.toPx()), Size(bw, 2.dp.toPx()))
            } else {
                val h = maxOf(2.dp.toPx(), v / peak * size.height)
                drawRect(bar, Offset(x, size.height - h), Size(bw, h))
            }
        }
    }
}

/** Big round connect button with pulse (connected) and spinner (connecting). */
@Composable
fun PowerButton(status: VpnStatus, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "power")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "pulse")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spin")
    val connected = status == VpnStatus.Connected
    val busy = status == VpnStatus.Connecting || status == VpnStatus.Disconnecting

    Box(modifier.size(138.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(138.dp)) {
            val ring = 59.dp.toPx()
            val center = Offset(size.width / 2, size.height / 2)
            if (connected) {
                drawCircle(p.accent.copy(alpha = 0.7f * (1 - pulse)), ring * (1 + 0.17f * pulse), center, style = Stroke(2.dp.toPx()))
            }
            drawCircle(if (connected) p.accent else p.line, ring, center, style = Stroke((if (connected) 2.5f else 2f) * density))
            if (busy) {
                rotate(spin, center) {
                    drawArc(
                        p.accent, -90f, 90f, useCenter = false,
                        topLeft = Offset(center.x - ring, center.y - ring), size = Size(ring * 2, ring * 2),
                        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            val core = 49.dp.toPx()
            drawCircle(if (connected) p.accentSoft else p.field, core, center)
            drawCircle(if (connected) p.accentLine else p.line2, core, center, style = Stroke(1.dp.toPx()))
        }
        VIcon(Ico.power, tint = if (connected) p.accent else p.muted, size = 40.dp)
    }
}
