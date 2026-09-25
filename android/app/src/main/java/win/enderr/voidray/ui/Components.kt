package win.enderr.voidray.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Mono = FontFamily.Monospace

/** Square glass card, like `.card` on the page. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(p.panelTop, p.panelBottom)))
            .border(1.dp, p.line)
            .padding(padding),
        content = content,
    )
}

/** Section header: LABEL ────── trailing */
@Composable
fun SectionHead(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(title, size = 10.5.sp, spacing = 0.16.em)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 9.dp)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(p.line, Color.Transparent))),
        )
        trailing?.invoke(this)
    }
}

/** Small uppercase label. */
@Composable
fun Label(text: String, size: TextUnit = 9.5.sp, spacing: TextUnit = 0.12.em, color: Color = LocalPalette.current.muted) {
    Text(
        text.uppercase(),
        color = color,
        fontSize = size,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = spacing,
        maxLines = 1,
    )
}

@Composable
fun VIcon(icon: ImageVector, tint: Color = LocalPalette.current.ink2, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(size))
}

@Composable
private fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return this
        .scale(if (pressed) 0.96f else 1f)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

enum class BtnKind { Normal, Accent }

@Composable
fun Btn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Normal,
    enabled: Boolean = true,
    minHeight: Dp = 40.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val p = LocalPalette.current
    val bg = if (kind == BtnKind.Accent) p.accentSoft else p.field
    val border = if (kind == BtnKind.Accent) p.accentLine else p.line
    Row(
        modifier
            .pressable(onClick, enabled)
            .alpha(if (enabled) 1f else .5f)
            .background(bg)
            .border(1.dp, border)
            .defaultMinSize(minHeight = minHeight, minWidth = minHeight)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 38.dp, tint: Color? = null) {
    val p = LocalPalette.current
    Box(
        modifier
            .pressable(onClick, true)
            .size(size)
            .background(p.field)
            .border(1.dp, p.line),
        contentAlignment = Alignment.Center,
    ) {
        VIcon(icon, tint = tint ?: p.ink2, size = 16.dp)
    }
}

@Composable
fun BtnText(text: String, color: Color = LocalPalette.current.ink, size: TextUnit = 12.sp) {
    Text(text, color = color, fontSize = size, fontWeight = FontWeight.SemiBold, maxLines = 1)
}

enum class TagKind { Plain, Proto, Sec, Warn }

@Composable
fun Tag(text: String, kind: TagKind = TagKind.Plain) {
    val p = LocalPalette.current
    val (bg, border, fg) = when (kind) {
        TagKind.Proto -> Triple(p.accentSoft, p.accentLine, p.accentText)
        TagKind.Sec -> Triple(p.accent2Soft, p.accent2Line, p.accent2Text)
        TagKind.Warn -> Triple(p.warnSoft, p.warnLine, p.warn)
        TagKind.Plain -> Triple(p.fieldHover, p.line, p.ink2)
    }
    Text(
        text.uppercase(),
        modifier = Modifier.padding(end = 3.dp).background(bg).border(1.dp, border).padding(horizontal = 5.dp, vertical = 2.dp),
        color = fg,
        fontFamily = Mono,
        fontSize = 8.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.06.em,
        maxLines = 1,
    )
}

enum class Tone { Normal, Ok, Warn, Alert }

@Composable
fun toneColor(tone: Tone, normal: Color = LocalPalette.current.ink): Color {
    val p = LocalPalette.current
    return when (tone) {
        Tone.Ok -> p.accent
        Tone.Warn -> p.warn
        Tone.Alert -> p.alert
        Tone.Normal -> normal
    }
}

@Composable
fun Chip(text: String, tone: Tone) {
    val p = LocalPalette.current
    val (bg, border, fg) = when (tone) {
        Tone.Warn -> Triple(p.warnSoft, p.warnLine, p.warn)
        Tone.Alert -> Triple(p.alertSoft, p.alertLine, p.alert)
        else -> Triple(p.accentSoft, p.accentLine, p.accentText)
    }
    Text(
        text,
        modifier = Modifier.background(bg).border(1.dp, border).padding(horizontal = 8.dp, vertical = 2.dp),
        color = fg,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Small tile: LABEL + mono value (upload/download, connection stats). */
@Composable
fun Flow(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, iconTint: Color? = null) {
    val p = LocalPalette.current
    Row(
        modifier.background(p.field).border(1.dp, p.line2).padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            VIcon(icon, tint = iconTint ?: p.accent, size = 14.dp)
            Box(Modifier.width(9.dp))
        }
        Column {
            Label(label)
            Text(
                value,
                color = p.ink,
                fontFamily = Mono,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(letterSpacing = (-0.02).em),
            )
        }
    }
}
