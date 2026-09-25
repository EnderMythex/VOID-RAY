package win.enderr.voidray.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import win.enderr.voidray.core.Brand
import win.enderr.voidray.core.Format
import win.enderr.voidray.i18n.L
import win.enderr.voidray.vpn.VpnState
import win.enderr.voidray.vpn.VpnStatus

/** Root: theme, ambient background, top bar, gate or dashboard, toast. */
@Composable
fun VoidRayApp(vm: MainViewModel, systemDark: Boolean, status: VpnStatus, onToggleVpn: () -> Unit) {
    val light = when (vm.themeMode) { "light" -> true; "dark" -> false; else -> !systemDark }
    val p = palette(light, vm.accent)

    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        key(vm.langTick) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.bg)
                    .drawBehind {
                        // Ambient glows, as on the page.
                        drawCircle(
                            Brush.radialGradient(
                                listOf(p.accent2.copy(alpha = p.ambient), Color.Transparent),
                                center = Offset(size.width * .05f, size.height * .02f), radius = size.maxDimension * .6f,
                            ),
                            radius = size.maxDimension * .6f, center = Offset(size.width * .05f, size.height * .02f),
                        )
                        drawCircle(
                            Brush.radialGradient(
                                listOf(p.accent.copy(alpha = p.ambient * .8f), Color.Transparent),
                                center = Offset(size.width * .95f, size.height * .98f), radius = size.maxDimension * .55f,
                            ),
                            radius = size.maxDimension * .55f, center = Offset(size.width * .95f, size.height * .98f),
                        )
                    },
            ) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    TopBar(vm)
                    if (vm.authenticated) Dashboard(vm, status, onToggleVpn) else Gate(vm)
                }
                ToastHost(vm, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp))
            }
        }
    }
}

// ==================================================================== top bar

@Composable
private fun TopBar(vm: MainViewModel) {
    val p = LocalPalette.current
    var paletteOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo(Modifier.size(30.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(vm.title, color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (vm.authenticated) vm.sid else "by EnderrVPN",
                color = p.muted, fontFamily = Mono, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconBtn(Ico.palette, { paletteOpen = !paletteOpen })
            if (paletteOpen) {
                Popup(alignment = Alignment.TopEnd, offset = androidx.compose.ui.unit.IntOffset(0, 130), onDismissRequest = { paletteOpen = false }) {
                    Row(Modifier.background(p.bg).border(1.dp, p.line).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Accents.forEachIndexed { i, a ->
                            val selected = i == vm.accent
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .border(1.dp, if (selected) p.ink else Color.Transparent)
                                    .padding(3.dp)
                                    .background(if (p.isLight) a.light else a.dark)
                                    .clickable { vm.setAccentIndex(i); paletteOpen = false },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier.size(38.dp).background(p.field).border(1.dp, p.line).clickable { vm.cycleLanguage() },
            contentAlignment = Alignment.Center,
        ) {
            Text(vm.langBadge, color = p.ink2, fontFamily = Mono, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier.size(38.dp).background(p.field).border(1.dp, p.line).clickable { vm.cycleTheme() },
            contentAlignment = Alignment.Center,
        ) {
            when (vm.themeMode) {
                "dark" -> VIcon(Ico.moon)
                "light" -> VIcon(Ico.sun)
                else -> Box { VIcon(Ico.autoRing); VIcon(Ico.autoHalf) }
            }
        }
    }
}

@Composable
fun Logo(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    androidx.compose.foundation.Canvas(modifier) {
        val s = size.width / 32f
        val grad = Brush.linearGradient(listOf(p.accent2, p.accent), Offset(5 * s, 5 * s), Offset(27 * s, 27 * s))
        drawCircle(grad, 11 * s, Offset(16 * s, 16 * s), style = androidx.compose.ui.graphics.drawscope.Stroke(3 * s))
        drawCircle(grad, 4 * s, Offset(16 * s, 16 * s))
        drawLine(p.accent, Offset(1 * s, 31 * s), Offset(31 * s, 1 * s), strokeWidth = 2 * s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

// ==================================================================== gate

@Composable
private fun Gate(vm: MainViewModel) {
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Panel(Modifier.widthIn(max = 480.dp).align(Alignment.CenterHorizontally), padding = 24.dp) {
            Logo(Modifier.size(52.dp))
            Spacer(Modifier.height(18.dp))
            Text(L.t("gateTitle"), color = p.ink, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(L.t("gateText"), color = p.muted, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(22.dp))
            SectionHead(L.t("gateLabel"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(44.dp).background(p.field).border(1.dp, if (vm.gateError != null) p.alertLine else p.line)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (vm.gateUrl.isEmpty()) Text(Brand.LINK_EXAMPLE, color = p.muted, fontFamily = Mono, fontSize = 12.sp, maxLines = 1)
                    BasicTextField(
                        value = vm.gateUrl,
                        onValueChange = { vm.gateUrl = it },
                        singleLine = true,
                        textStyle = TextStyle(color = p.ink, fontFamily = Mono, fontSize = 12.sp),
                        cursorBrush = SolidColor(p.accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { vm.signIn() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(6.dp))
                IconBtn(Ico.paste, { clipboard.getText()?.text?.let { vm.gateUrl = it.trim() } }, size = 44.dp)
            }
            vm.gateError?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(
                    err, color = p.alert, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(p.alertSoft).border(1.dp, p.alertLine).padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Btn({ vm.signIn() }, Modifier.fillMaxWidth(), kind = BtnKind.Accent, enabled = !vm.validating, minHeight = 44.dp) {
                BtnText(L.t(if (vm.validating) "gateChecking" else "gateButton"), size = 13.sp)
                Spacer(Modifier.width(8.dp))
                VIcon(Ico.arrowRight, tint = p.ink, size = 15.dp)
            }
            Spacer(Modifier.height(18.dp))
            Row {
                Text(L.t("gateHelp"), color = p.muted, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(L.t("gateContact"), color = p.ink2, fontSize = 12.sp, modifier = Modifier.clickable { vm.openUrl(Brand.DEFAULT_SUPPORT_URL) })
            }
        }
    }
}

// ==================================================================== dashboard

@Composable
private fun Dashboard(vm: MainViewModel, status: VpnStatus, onToggleVpn: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ConnectionCard(vm, status, onToggleVpn)
        UsageCard(vm)
        LinkCard(vm)
        ConfigsCard(vm)
        DetailsCard(vm)
        Footer(vm)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ConnectionCard(vm: MainViewModel, status: VpnStatus, onToggleVpn: () -> Unit) {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) {
        while (status == VpnStatus.Connected) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val connected = status == VpnStatus.Connected
    Panel {
        SectionHead(L.t("connection"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(CircleShape).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    enabled = status == VpnStatus.Connected || status == VpnStatus.Disconnected,
                ) { onToggleVpn() },
            ) { PowerButton(status) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    L.t(when (status) {
                        VpnStatus.Connecting -> "stateConnecting"
                        VpnStatus.Connected -> "stateOn"
                        VpnStatus.Disconnecting -> "stateDisconnecting"
                        VpnStatus.Disconnected -> "stateOff"
                    }),
                    color = if (connected) p.accent else p.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    L.t(when (status) { VpnStatus.Connected -> "hintOn"; VpnStatus.Disconnected -> "hintOff"; else -> "hintWait" }),
                    color = p.muted, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                )
                Row(
                    Modifier.background(p.field).border(1.dp, p.line2).padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (connected) p.accent else p.muted))
                    Spacer(Modifier.width(8.dp))
                    Text(vm.selected?.name ?: L.t("noServer"), color = p.ink, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val elapsed = if (connected) Format.duration((now - VpnState.connectedAt).coerceAtLeast(0) / 1000) else "00:00"
            Flow(L.t("duration"), elapsed, Modifier.weight(1f))
            Flow(L.t("realDelay"), vm.realDelay ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Flow(L.t("publicIp"), vm.publicIp ?: "—", Modifier.weight(1f))
            Flow(L.t("location"), vm.exitCountry ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun UsageCard(vm: MainViewModel) {
    val p = LocalPalette.current
    Panel(padding = 0.dp) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = 250.dp, height = 178.dp)) {
                TickGauge(
                    ratio = vm.ratio, unlimited = vm.unlimited, live = vm.live,
                    tone = toneColor(vm.gaugeTone), idle = p.muted,
                    modifier = Modifier.size(width = 250.dp, height = 190.dp),
                )
                Column(Modifier.align(Alignment.TopCenter).padding(top = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val (value, unit) = vm.gaugeFigure
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value, color = p.ink, fontFamily = Mono, fontSize = 30.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.04).em)
                        Text(unit, color = p.muted, fontFamily = Mono, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    }
                    Label(vm.gaugeCaption, size = 9.5.sp, spacing = 0.14.em)
                }
            }
            val (statusLabel, statusTone) = vm.status
            Row(
                Modifier.background(p.field).border(1.dp, p.line).padding(start = 9.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(toneColor(statusTone, p.accent)))
                Spacer(Modifier.width(7.dp))
                Text(statusLabel, color = toneColor(statusTone, p.accent), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Flow(L.t("upload"), Format.bytes(vm.sub?.upload ?: 0), Modifier.weight(1f), Ico.up, p.accent2)
                Flow(L.t("download"), Format.bytes(vm.sub?.download ?: 0), Modifier.weight(1f), Ico.down, p.accent)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line2))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 13.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                Label(L.t("history"))
                Spacer(Modifier.weight(1f))
                Text(vm.historyAverage, color = p.ink2, fontFamily = Mono, fontSize = 11.sp)
            }
            if (vm.history.any { it != null }) {
                UsageBars(vm.history, bar = p.accent.copy(alpha = .58f), void = p.line, modifier = Modifier.fillMaxWidth().height(48.dp))
            } else {
                Text(L.t("histWait"), color = p.muted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LinkCard(vm: MainViewModel) {
    val p = LocalPalette.current
    Panel {
        SectionHead(L.t("link"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(40.dp).background(p.field).border(1.dp, p.line2).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(vm.subscriptionUrl, color = p.ink2, fontFamily = Mono, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
            IconBtn(Ico.refresh, { vm.refreshNow() }, size = 40.dp)
            Spacer(Modifier.width(6.dp))
            IconBtn(Ico.exit, { vm.changeLink() }, size = 40.dp)
        }
        Spacer(Modifier.height(8.dp))
        Btn({ vm.copy(vm.subscriptionUrl, L.t("lblLink")) }, Modifier.fillMaxWidth(), kind = BtnKind.Accent) {
            VIcon(Ico.copy, tint = p.ink, size = 14.dp)
            Spacer(Modifier.width(7.dp))
            BtnText(L.t("copy"))
        }
    }
}

@Composable
private fun ConfigsCard(vm: MainViewModel) {
    val p = LocalPalette.current
    Panel(padding = 14.dp) {
        SectionHead(L.t("configs")) {
            Text("${vm.servers.size}", color = p.muted, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(end = 10.dp))
            Btn({ vm.pingAll() }, minHeight = 30.dp) {
                VIcon(Ico.bolt, tint = p.accent, size = 12.dp)
                Spacer(Modifier.width(6.dp))
                BtnText(L.t("testPing"), size = 11.5.sp)
            }
        }
        if (vm.servers.isEmpty()) {
            Text(L.t("noConfigs"), color = p.muted, fontSize = 12.5.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            for (item in vm.servers) {
                val selected = item.profile.key == vm.selectedKey
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) p.accentSoft else p.field)
                        .border(1.dp, if (selected) p.accent else p.line2)
                        .clickable { vm.select(item) }
                        .padding(start = 11.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row {
                            Tag(item.protocolTag, TagKind.Proto)
                            Tag(item.networkTag)
                            item.securityTag?.let { Tag(it, TagKind.Sec) }
                            if (item.profile.isBypassPatched) Tag("BYPASS", TagKind.Warn)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(item.name, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        item.pingText,
                        color = when (item.quality) {
                            PingQuality.Good -> p.accent
                            PingQuality.Medium -> p.warn
                            PingQuality.Bad, PingQuality.Timeout -> p.alert
                            else -> p.muted
                        },
                        fontFamily = Mono, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconBtn(Ico.copy, { vm.copy(item.profile.rawLink, L.t("lblConfig")) }, size = 32.dp)
                }
            }
        }
    }
}

@Composable
private fun DetailsCard(vm: MainViewModel) {
    val p = LocalPalette.current
    Panel(padding = 0.dp) {
        SectionHead(L.t("details"), Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))
        vm.detailRows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(p.line2))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.key, color = p.muted, fontSize = 12.sp, modifier = Modifier.width(130.dp))
                Spacer(Modifier.weight(1f))
                if (row.chip) {
                    Chip(row.value, row.tone)
                } else {
                    Text(
                        row.value,
                        color = toneColor(row.tone),
                        fontFamily = if (row.mono) Mono else null,
                        fontSize = if (row.mono) 12.sp else 13.sp,
                        fontWeight = if (row.mono) FontWeight.Normal else FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                        modifier = Modifier.weight(3f, fill = false),
                    )
                    row.hint?.let { Text(" · $it", color = p.muted, fontSize = 12.sp, maxLines = 1) }
                }
            }
        }
        Btn({ vm.copy(vm.sid, L.t("lblId")) }, Modifier.fillMaxWidth().padding(16.dp)) {
            VIcon(Ico.copy, tint = p.ink, size = 14.dp)
            Spacer(Modifier.width(7.dp))
            BtnText(L.t("copyId"))
        }
    }
}

@Composable
private fun Footer(vm: MainViewModel) {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vm.accounts, color = p.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text("  ·  ", color = p.muted.copy(alpha = .5f), fontSize = 11.sp)
            Text(L.t("support"), color = p.ink2, fontSize = 11.sp, modifier = Modifier.clickable { vm.openUrl(vm.supportUrl) })
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(p.accent.copy(alpha = if (vm.refreshing) 1f else .55f)))
            Spacer(Modifier.width(6.dp))
            Text(vm.freshText(now), color = p.muted, fontFamily = Mono, fontSize = 10.5.sp)
        }
    }
}

// ==================================================================== toast

@Composable
private fun ToastHost(vm: MainViewModel, modifier: Modifier) {
    val p = LocalPalette.current
    val msg = vm.toast ?: return
    LaunchedEffect(msg.id) {
        delay(3500)
        vm.dismissToast(msg)
    }
    Box(modifier.padding(horizontal = 24.dp)) {
        Text(
            msg.text,
            color = p.ink,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(p.bg)
                .border(1.dp, if (msg.error) p.alertLine else p.accentLine)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
