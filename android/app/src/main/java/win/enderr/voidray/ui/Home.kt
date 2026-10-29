package win.enderr.voidray.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import win.enderr.voidray.vpn.VpnStatus
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Surfaces of the rounded mobile layout, derived from the palette. */
val Palette.surface: Color get() = if (isLight) Color(0xFFF0F1F5) else Color.White.copy(alpha = .065f)
val Palette.sheet: Color get() = if (isLight) Color.White else Color(0xFF0E1118)
/** Strong "ink" fill for primary buttons, with its content colour. */
val Palette.solid: Color get() = if (isLight) Color(0xFF0B0D12) else Color(0xFFF2F4F8)
val Palette.onSolid: Color get() = if (isLight) Color.White else Color(0xFF0B0D12)

/**
 * The black hole: an event horizon wrapped in bent light, a tilted accretion
 * disk (half behind, half in front) and the ray crossing it. Dim while idle,
 * it spins up while connecting and blazes with a halo once protected.
 */
@Composable
fun Orb(status: VpnStatus, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val connected = status == VpnStatus.Connected
    val busy = status == VpnStatus.Connecting || status == VpnStatus.Disconnecting
    val vivid by animateFloatAsState(if (connected) 1f else if (busy) .75f else 0f, tween(1000), label = "vivid")
    val t = rememberInfiniteTransition(label = "hole")
    val slow by t.animateFloat(0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "slow")
    val mid by t.animateFloat(0f, 360f, infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "mid")
    val fast by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "fast")
    val ringSpin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "ring")
    val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3500), RepeatMode.Reverse), label = "breathe")
    val spin = if (busy) fast else if (connected) mid else slow

    val warm = Color(0xFFFFAE6B)
    val grey = if (p.isLight) Color(0xFF9EA2B4) else Color(0xFF5A6070)
    fun tone(c: Color) = lerp(lerp(c, grey, .55f), c, vivid)
    val accent = tone(p.accent)
    val accent2 = tone(p.accent2)
    val hot = tone(Color(0xFFF4FFFC))
    val ember = tone(warm)
    val scale = 1f + .02f * breathe

    Canvas(modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        val hole = r * .40f

        // halo
        drawCircle(
            Brush.radialGradient(
                listOf(accent2.copy(alpha = .22f + .28f * vivid), accent.copy(alpha = .10f + .12f * vivid), Color.Transparent),
                c, r * 1.05f,
            ),
            r * 1.05f, c,
        )

        // the ray, behind
        rotate(-45f, c) {
            val len = r * 1.25f
            val beam = Brush.horizontalGradient(
                listOf(Color.Transparent, accent, hot, accent2, Color.Transparent), c.x - len, c.x + len,
            )
            drawLine(beam, Offset(c.x - len, c.y), Offset(c.x + len, c.y), strokeWidth = 9.dp.toPx(), alpha = .18f + .2f * vivid, cap = StrokeCap.Round)
            drawLine(beam, Offset(c.x - len, c.y), Offset(c.x + len, c.y), strokeWidth = 2.2.dp.toPx(), alpha = .55f + .45f * vivid, cap = StrokeCap.Round)
        }

        // The disk's colours flow around it; its shape stays put.
        val disk = turningSweep(listOf(accent, hot, accent2, accent2.copy(alpha = .3f), ember, accent), spin / 360f, c)
        val diskW = r * .92f
        val diskH = r * .30f
        fun drawDisk(front: Boolean) {
            rotate(-14f, c) {
                clipRect(top = if (front) c.y else 0f, bottom = if (front) size.height else c.y) {
                    val tl = Offset(c.x - diskW, c.y - diskH)
                    val sz = Size(diskW * 2, diskH * 2)
                    drawOval(disk, tl, sz, style = Stroke(r * .16f), alpha = (if (front) .28f else .18f) * (.6f + .4f * vivid))
                    drawOval(disk, tl, sz, style = Stroke(r * .07f), alpha = if (front) 1f else .75f)
                }
            }
        }

        // far half of the disk
        drawDisk(front = false)

        // bent light around the horizon
        rotate(-spin * 1.4f, c) {
            val lens = Brush.sweepGradient(listOf(hot, accent2, accent, ember, accent2, hot), c)
            drawCircle(lens, hole * 1.22f, c, style = Stroke(hole * .42f), alpha = .25f + .2f * vivid)
            drawCircle(lens, hole * 1.14f, c, style = Stroke(hole * .16f))
        }

        // event horizon
        drawCircle(Brush.radialGradient(listOf(Color(0xFF0B0D16), Color.Black), Offset(c.x - hole * .1f, c.y - hole * .15f), hole), hole, c)
        drawCircle(lerp(hot, accent2, .4f), hole, c, style = Stroke(1.5.dp.toPx()), alpha = .8f)

        // near half, in front of the hole
        drawDisk(front = true)

        if (busy) {
            rotate(ringSpin, c) {
                drawArc(
                    p.accent, -90f, 50f, useCenter = false,
                    topLeft = Offset(c.x - r * .98f, c.y - r * .98f), size = Size(r * 1.96f, r * 1.96f),
                    style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** A sweep gradient whose colours are turned by `turn` (0..1) around `center`. */
private fun turningSweep(loop: List<Color>, turn: Float, center: Offset): Brush {
    val n = loop.size - 1
    fun at(x: Float): Color {
        val f = (((x % 1f) + 1f) % 1f) * n
        val i = f.toInt().coerceAtMost(n - 1)
        return lerp(loop[i], loop[i + 1], f - i)
    }
    val steps = 24
    val stops = Array(steps + 1) { k -> (k.toFloat() / steps) to at(k.toFloat() / steps - turn) }
    return Brush.sweepGradient(*stops, center = center)
}

/**
 * Slide to connect: drag the knob to the end to turn the VPN on, back to the
 * start to turn it off. The knob follows the real state when it changes.
 */
@Composable
fun SlideToConnect(status: VpnStatus, label: String, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val knob = 64.dp
    val connected = status == VpnStatus.Connected
    val busy = status == VpnStatus.Connecting || status == VpnStatus.Disconnecting
    val atEnd = status == VpnStatus.Connected || status == VpnStatus.Connecting

    BoxWithConstraints(modifier.height(knob)) {
        val density = LocalDensity.current
        val max = with(density) { (maxWidth - knob).toPx() }.coerceAtLeast(1f)
        val offset = remember { Animatable(if (atEnd) max else 0f) }
        LaunchedEffect(atEnd, max) {
            offset.animateTo(if (atEnd) max else 0f, spring(dampingRatio = .8f, stiffness = 400f))
        }
        val progress = (offset.value / max).coerceIn(0f, 1f)
        val drag = rememberDraggableState { delta ->
            scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, max)) }
        }

        // track, filling up behind the knob
        Box(Modifier.fillMaxSize().clip(CircleShape).background(p.surface)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(with(density) { (offset.value).toDp() } + knob)
                    .clip(CircleShape)
                    .background(p.accent.copy(alpha = if (connected) .28f else .10f + .18f * progress)),
            )
            Text(
                label,
                color = p.ink.copy(alpha = .85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = if (atEnd) 0.dp else knob * .6f, end = if (atEnd) knob * .6f else 0.dp)
                    .alpha(if (busy) 1f else (1f - 1.6f * (if (atEnd) 1f - progress else progress)).coerceIn(.15f, 1f)),
            )
        }
        // knob
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(knob)
                .shadow(10.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(p.solid)
                .draggable(
                    state = drag,
                    orientation = Orientation.Horizontal,
                    enabled = !busy,
                    onDragStopped = {
                        val turnOn = !atEnd && offset.value > max * .82f
                        val turnOff = atEnd && offset.value < max * .18f
                        if (turnOn || turnOff) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        } else {
                            offset.animateTo(if (atEnd) max else 0f, spring(dampingRatio = .7f, stiffness = 500f))
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                busy -> Spinner(p.onSolid, Modifier.size(24.dp))
                connected -> VIcon(Ico.arrowLeft, tint = p.onSolid, size = 24.dp)
                else -> VIcon(Ico.arrowRight, tint = p.onSolid, size = 24.dp)
            }
        }
    }
}

@Composable
fun Spinner(color: Color, modifier: Modifier = Modifier) {
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a",
    )
    Canvas(modifier) {
        rotate(spin) {
            drawArc(color, 0f, 270f, useCenter = false, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Round solid button (settings). */
@Composable
fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 64.dp) {
    val p = LocalPalette.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(p.solid)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        VIcon(icon, tint = p.onSolid, size = size * .4f)
    }
}

/** Bottom sheet over a scrim; closes on scrim tap or back. */
@Composable
fun Sheet(visible: Boolean, title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    if (visible) BackHandler(onBack = onDismiss)

    // Swipe down to close: from the header, or from the content once it is scrolled to the top.
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    var dragY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible) { if (visible) dragY = 0f }
    val settle: (Float) -> Unit = { velocity ->
        if (dragY > threshold || velocity > 1600f) {
            onDismiss()
        } else {
            scope.launch { animate(dragY, 0f, animationSpec = spring(dampingRatio = .8f, stiffness = 500f)) { v, _ -> dragY = v } }
        }
    }
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && dragY > 0f) {
                    val used = maxOf(available.y, -dragY)
                    dragY += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    dragY += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragY > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val headerDrag = rememberDraggableState { delta -> dragY = (dragY + delta).coerceAtLeast(0f) }

    AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = .42f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
        ) {
            BoxWithConstraints {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * .88f)
                        .graphicsLayer { translationY = dragY }
                        .nestedScroll(nested)
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .background(p.sheet)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                        .navigationBarsPadding(),
                ) {
                    Column(
                        Modifier.fillMaxWidth().draggable(
                            state = headerDrag,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity -> settle(velocity) },
                        ),
                    ) {
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(CircleShape).background(p.ink.copy(alpha = .18f)))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = p.ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(p.surface).clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) { VIcon(Ico.close, tint = p.ink, size = 18.dp) }
                    }
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

/** Rounded grouped card used inside sheets. */
@Composable
fun Group(modifier: Modifier = Modifier, padding: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(p.surface).padding(padding), content = content)
}

/** Small caption above a group. */
@Composable
fun GroupTitle(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp))
}

/** Segmented choice, e.g. Auto / Dark / Light. */
@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(p.surface).padding(4.dp)) {
        for ((value, label) in options) {
            val on = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (on) p.solid else Color.Transparent)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) p.onSolid else p.ink2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Two-line stat inside a group. */
@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        Text(label, color = p.muted, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = p.ink, fontFamily = Mono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
