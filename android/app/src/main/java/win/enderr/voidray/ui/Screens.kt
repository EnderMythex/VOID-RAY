package win.enderr.voidray.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import kotlinx.coroutines.delay
import win.enderr.voidray.core.Brand
import win.enderr.voidray.core.Format
import win.enderr.voidray.i18n.L
import win.enderr.voidray.vpn.VpnState
import win.enderr.voidray.vpn.VpnStatus

private enum class SheetKind { None, Servers, Account, Settings }

/** Root: theme, gate or home, bottom sheets, toast. */
@Composable
fun VoidRayApp(vm: MainViewModel, systemDark: Boolean, status: VpnStatus, onToggleVpn: () -> Unit) {
    val light = when (vm.themeMode) { "light" -> true; "dark" -> false; else -> !systemDark }
    val p = palette(light, vm.accent)
    var sheet by remember { mutableStateOf(SheetKind.None) }

    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        key(vm.langTick) {
            Box(Modifier.fillMaxSize().background(p.bg)) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    Header(vm) { sheet = SheetKind.Account }
                    if (vm.authenticated) {
                        Home(vm, status, onToggleVpn, openServers = { sheet = SheetKind.Servers }, openSettings = { sheet = SheetKind.Settings })
                    } else {
                        Gate(vm, openSettings = { sheet = SheetKind.Settings })
                    }
                }
                val close = { sheet = SheetKind.None }
                Sheet(sheet == SheetKind.Servers, L.t("servers"), close) { ServersContent(vm, status) { sheet = SheetKind.None } }
                Sheet(sheet == SheetKind.Account, L.t("account"), close) { AccountContent(vm) }
                Sheet(sheet == SheetKind.Settings, L.t("settings"), close) { SettingsContent(vm) { sheet = SheetKind.None } }
                ToastHost(vm, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp))
                UpdateDialog(vm)
            }
        }
    }
}

// ==================================================================== header

@Composable
private fun Header(vm: MainViewModel, openAccount: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Logo(Modifier.size(34.dp))
        Spacer(Modifier.width(10.dp))
        Text("VOID-RAY", color = p.ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em,
            modifier = Modifier.weight(1f), maxLines = 1)
        if (vm.authenticated) {
            // usage pill, opens the subscription sheet
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(p.solid)
                    .clickable(onClick = openAccount)
                    .padding(start = 16.dp, end = 20.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VIcon(Ico.bolt, tint = p.onSolid, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                val (value, unit) = vm.gaugeFigure
                Text("$value $unit".trim(), color = p.onSolid, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
fun Logo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(win.enderr.voidray.R.drawable.voidray_mark),
        contentDescription = null,
        modifier = modifier,
    )
}

// ==================================================================== home

@Composable
private fun Home(vm: MainViewModel, status: VpnStatus, onToggleVpn: () -> Unit, openServers: () -> Unit, openSettings: () -> Unit) {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) {
        while (status == VpnStatus.Connected) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val connected = status == VpnStatus.Connected

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Orb(status, Modifier.size(300.dp))
        }

        // server chip
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .border(1.5.dp, p.ink, RoundedCornerShape(14.dp))
                .clickable(onClick = openServers)
                .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = 34.dp, height = 26.dp).clip(RoundedCornerShape(6.dp)).background(p.solid), contentAlignment = Alignment.Center) {
                VIcon(Ico.globe, tint = p.onSolid, size = 17.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                vm.selected?.name ?: L.t("chooseServer"),
                color = p.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            vm.selected?.pingText?.takeIf { it.isNotBlank() && it != "—" }?.let {
                Spacer(Modifier.width(8.dp))
                Text(it, color = p.muted, fontFamily = Mono, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            L.t(when (status) {
                VpnStatus.Disconnected -> "homeOff"
                VpnStatus.Connecting -> "homeConnecting"
                VpnStatus.Connected -> "homeOn"
                VpnStatus.Disconnecting -> "homeDisconnecting"
            }),
            color = p.ink, fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.025).em,
        )
        Spacer(Modifier.height(10.dp))
        // live details once protected, otherwise the subscription state
        val line = if (connected) {
            val elapsed = Format.duration((now - VpnState.connectedAt).coerceAtLeast(0) / 1000)
            listOfNotNull(elapsed, vm.publicIp, vm.exitCountry, vm.realDelay).joinToString("  ·  ")
        } else {
            vm.status.first.takeIf { it != "—" }?.let { "$it  ·  ${vm.gaugeCaption}" } ?: ""
        }
        Text(line, color = if (connected) p.accentText else p.muted, fontFamily = if (connected) Mono else null, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(20.dp))

        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SlideToConnect(
                status,
                L.t(when (status) {
                    VpnStatus.Disconnected -> "slideOn"
                    VpnStatus.Connected -> "slideOff"
                    else -> "slideWait"
                }),
                onToggle = onToggleVpn,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            RoundButton(Ico.gear, openSettings)
        }
        Spacer(Modifier.height(26.dp))
    }
}

// ==================================================================== gate

@Composable
private fun Gate(vm: MainViewModel, openSettings: () -> Unit) {
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            Orb(VpnStatus.Disconnected, Modifier.size(240.dp))
        }
        Text(L.t("gateHeadline"), color = p.ink, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.025).em)
        Spacer(Modifier.height(10.dp))
        Text(L.t("gateText"), color = p.muted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(22.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(p.surface)
                .border(1.5.dp, if (vm.gateError != null) p.alert else Color.Transparent, RoundedCornerShape(18.dp))
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).height(56.dp), contentAlignment = Alignment.CenterStart) {
                if (vm.gateUrl.isEmpty()) Text(Brand.LINK_EXAMPLE, color = p.muted, fontFamily = Mono, fontSize = 13.sp, maxLines = 1)
                BasicTextField(
                    value = vm.gateUrl,
                    onValueChange = { vm.gateUrl = it },
                    singleLine = true,
                    textStyle = TextStyle(color = p.ink, fontFamily = Mono, fontSize = 13.sp),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { vm.signIn() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(p.sheet).clickable { clipboard.getText()?.text?.let { vm.gateUrl = it.trim() } },
                contentAlignment = Alignment.Center,
            ) { VIcon(Ico.paste, tint = p.ink, size = 18.dp) }
        }
        vm.gateError?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(err, color = p.alert, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(CircleShape)
                    .background(p.solid.copy(alpha = if (vm.validating) .6f else 1f))
                    .clickable(enabled = !vm.validating) { vm.signIn() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (vm.validating) Spinner(p.onSolid, Modifier.size(20.dp)) else VIcon(Ico.arrowRight, tint = p.onSolid, size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Text(L.t(if (vm.validating) "gateChecking" else "gateButton"), color = p.onSolid, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(16.dp))
            RoundButton(Ico.gear, openSettings)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.padding(horizontal = 6.dp)) {
            Text(L.t("gateHelp"), color = p.muted, fontSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            Text(L.t("gateContact"), color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { vm.openUrl(Brand.DEFAULT_SUPPORT_URL) })
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ==================================================================== sheets

@Composable
private fun ServersContent(vm: MainViewModel, status: VpnStatus, done: () -> Unit) {
    val p = LocalPalette.current
    LaunchedEffect(Unit) { if (vm.servers.none { it.ping != null }) vm.pingAll() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)) {
        Text("${vm.servers.size} ${L.t("configs").lowercase()}", color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Row(
            Modifier.clip(CircleShape).background(p.surface).clickable { vm.pingAll() }.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VIcon(Ico.bolt, tint = p.ink, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(L.t("testPing"), color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (vm.servers.isEmpty()) {
        Text(L.t("noConfigs"), color = p.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (item in vm.servers) {
            val selected = item.profile.key == vm.selectedKey
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (selected) p.accentSoft else p.surface)
                    .border(1.5.dp, if (selected) p.accent else Color.Transparent, RoundedCornerShape(18.dp))
                    .clickable(enabled = status == VpnStatus.Disconnected || selected) { vm.select(item); done() }
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Row {
                        Tag(item.protocolTag, TagKind.Proto)
                        Tag(item.networkTag)
                        item.securityTag?.let { Tag(it, TagKind.Sec) }
                        if (item.profile.isBypassPatched) Tag("BYPASS", TagKind.Warn)
                    }
                }
                Text(
                    item.pingText,
                    color = when (item.quality) {
                        PingQuality.Good -> p.accent
                        PingQuality.Medium -> p.warn
                        PingQuality.Bad, PingQuality.Timeout -> p.alert
                        else -> p.muted
                    },
                    fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (selected) VIcon(Ico.check, tint = p.accent, size = 20.dp)
            }
        }
    }
}

@Composable
private fun AccountContent(vm: MainViewModel) {
    val p = LocalPalette.current
    Group(padding = 0.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(width = 250.dp, height = 178.dp)) {
                TickGauge(
                    ratio = vm.ratio, unlimited = vm.unlimited, live = vm.live,
                    tone = toneColor(vm.gaugeTone), idle = p.muted,
                    modifier = Modifier.size(width = 250.dp, height = 190.dp),
                )
                Column(Modifier.align(Alignment.TopCenter).padding(top = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val (value, unit) = vm.gaugeFigure
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value, color = p.ink, fontFamily = Mono, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.04).em)
                        Text(unit, color = p.muted, fontFamily = Mono, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    }
                    Label(vm.gaugeCaption, size = 9.5.sp, spacing = 0.14.em)
                }
            }
            val (statusLabel, statusTone) = vm.status
            Row(
                Modifier.clip(CircleShape).background(p.sheet).padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(toneColor(statusTone, p.accent)))
                Spacer(Modifier.width(8.dp))
                Text(statusLabel, color = toneColor(statusTone, p.accent), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("↑ " + L.t("upload"), Format.bytes(vm.sub?.upload ?: 0), Modifier.weight(1f))
                Stat("↓ " + L.t("download"), Format.bytes(vm.sub?.download ?: 0), Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.ink.copy(alpha = .06f)))
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(L.t("history"), color = p.muted, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(vm.historyAverage, color = p.ink2, fontFamily = Mono, fontSize = 12.sp)
            }
            if (vm.history.any { it != null }) {
                UsageBars(vm.history, bar = p.accent.copy(alpha = .7f), void = p.ink.copy(alpha = .08f), modifier = Modifier.fillMaxWidth().height(52.dp))
            } else {
                Text(L.t("histWait"), color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    GroupTitle(L.t("details"))
    Group(padding = 0.dp) {
        vm.detailRows.forEachIndexed { i, row ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(p.ink.copy(alpha = .06f)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(row.key, color = p.muted, fontSize = 14.sp, modifier = Modifier.width(130.dp))
                Spacer(Modifier.weight(1f))
                if (row.chip) {
                    Chip(row.value, row.tone)
                } else {
                    Text(
                        row.value, color = toneColor(row.tone),
                        fontFamily = if (row.mono) Mono else null,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                        modifier = Modifier.weight(3f, fill = false),
                    )
                    row.hint?.let { Text(" · $it", color = p.muted, fontSize = 13.sp, maxLines = 1) }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    PillButton(L.t("copyId"), Ico.copy) { vm.copy(vm.sid, L.t("lblId")) }
}

@Composable
private fun SettingsContent(vm: MainViewModel, close: () -> Unit) {
    val p = LocalPalette.current
    GroupTitle(L.t("theme"))
    Segmented(listOf("auto" to L.t("auto"), "dark" to L.t("dark"), "light" to L.t("light")), vm.themeMode) { vm.setTheme(it) }

    GroupTitle(L.t("accentLabel"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Accents.forEachIndexed { i, a ->
            val on = i == vm.accent
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (on) p.ink else Color.Transparent, CircleShape)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(if (p.isLight) a.light else a.dark)
                    .clickable { vm.setAccentIndex(i) },
                contentAlignment = Alignment.Center,
            ) { if (on) VIcon(Ico.check, tint = Color.White, size = 18.dp) }
        }
    }

    GroupTitle(L.t("langLabel"))
    Segmented(listOf("auto" to L.t("auto"), "en" to "English", "fr" to "Français"), vm.languageMode) { vm.setLanguage(it) }

    if (vm.authenticated) {
        GroupTitle(L.t("link"))
        Group {
            Text(vm.subscriptionUrl, color = p.ink2, fontFamily = Mono, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallPill(L.t("copy"), Ico.copy, Modifier.weight(1f)) { vm.copy(vm.subscriptionUrl, L.t("lblLink")) }
                SmallPill(L.t("refresh"), Ico.refresh, Modifier.weight(1f)) { vm.refreshNow() }
            }
            Spacer(Modifier.height(8.dp))
            SmallPill(L.t("changeLink"), Ico.exit, Modifier.fillMaxWidth()) { close(); vm.changeLink() }
        }
    }

    GroupTitle(L.t("about"))
    Group(padding = 0.dp) {
        SettingsRow(L.t("updCheck"), vm.updateLabel, accent = vm.availableUpdate != null) { vm.checkUpdates(manual = true) }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(p.ink.copy(alpha = .06f)))
        SettingsRow(L.t("support"), "") { vm.openUrl(vm.supportUrl) }
    }
    Spacer(Modifier.height(14.dp))
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    Text(
        listOfNotNull(vm.currentVersion, if (vm.authenticated) vm.freshText(now) else null).joinToString("  ·  "),
        color = p.muted, fontFamily = Mono, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsRow(title: String, value: String, accent: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = if (accent) p.accent else p.muted, fontSize = 13.sp, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        VIcon(Ico.chevronRight, tint = p.muted, size = 16.dp)
    }
}

@Composable
private fun PillButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(p.solid).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        VIcon(icon, tint = p.onSolid, size = 17.dp)
        Spacer(Modifier.width(9.dp))
        Text(text, color = p.onSolid, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier.height(44.dp).clip(CircleShape).background(p.sheet).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        VIcon(icon, tint = p.ink, size = 15.dp)
        Spacer(Modifier.width(7.dp))
        Text(text, color = p.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ==================================================================== update dialog

@Composable
private fun UpdateDialog(vm: MainViewModel) {
    val p = LocalPalette.current
    val release = vm.updatePrompt ?: return
    androidx.compose.ui.window.Dialog(onDismissRequest = { vm.dismissUpdate() }) {
        Column(Modifier.clip(RoundedCornerShape(28.dp)).background(p.sheet).padding(22.dp)) {
            Text(L.t("updTitle", release.tag), color = p.ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(L.t("updText", vm.currentVersion), color = p.muted, fontSize = 14.sp, lineHeight = 20.sp)
            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    release.notes.trim().take(600), color = p.ink2, fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.surface).padding(12.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            PillButton(L.t("updInstall"), Ico.down) { vm.installUpdate() }
            Spacer(Modifier.height(6.dp))
            Text(
                L.t("updLater"), color = p.ink2, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(CircleShape).clickable { vm.dismissUpdate() }.padding(vertical = 14.dp),
            )
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
        Row(
            Modifier.clip(CircleShape).background(p.solid).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (msg.error) p.alert else p.accent))
            Spacer(Modifier.width(10.dp))
            Text(msg.text, color = p.onSolid, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}
