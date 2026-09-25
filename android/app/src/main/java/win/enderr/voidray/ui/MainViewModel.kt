package win.enderr.voidray.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import win.enderr.voidray.core.Brand
import win.enderr.voidray.core.FirewallBypass
import win.enderr.voidray.core.Format
import win.enderr.voidray.core.ServerProfile
import win.enderr.voidray.core.SubscriptionFetcher
import win.enderr.voidray.core.SubscriptionInfo
import win.enderr.voidray.core.UsageHistory
import win.enderr.voidray.core.XrayConfig
import win.enderr.voidray.BuildConfig
import win.enderr.voidray.data.Store
import win.enderr.voidray.update.AppUpdater
import win.enderr.voidray.i18n.L
import win.enderr.voidray.vpn.NetTools
import win.enderr.voidray.vpn.VoidRayVpnService
import win.enderr.voidray.vpn.VpnState
import win.enderr.voidray.vpn.VpnStatus
import kotlin.math.ceil

enum class PingQuality { Unknown, Testing, Good, Medium, Bad, Timeout }

class ServerItem(val profile: ServerProfile) {
    /** 3x-ui appends "-email" to every remark: keep the readable part only. */
    val name: String = profile.remark.replace(Regex("""\s*[-–—|]\s*[^\s@]+@[^\s@]+\.[A-Za-z]{2,}\s*$"""), "").trim()
        .ifEmpty { profile.remark }
    val protocolTag = if (profile.protocol == "shadowsocks") "SS" else profile.protocol.uppercase()
    val networkTag = when (val n = profile.network.lowercase()) { "raw" -> "TCP"; "splithttp" -> "XHTTP"; else -> n.uppercase() }
    val securityTag = profile.security.takeUnless { it.isBlank() || it == "none" }?.uppercase()

    var ping by mutableStateOf<Int?>(null)
    var quality by mutableStateOf(PingQuality.Unknown)

    val pingText: String get() = when (quality) {
        PingQuality.Unknown -> "—"
        PingQuality.Testing -> "···"
        PingQuality.Timeout -> "timeout"
        else -> "$ping ms"
    }

    fun setResult(ms: Int?) {
        ping = ms
        quality = when {
            ms == null -> PingQuality.Timeout
            ms < 150 -> PingQuality.Good
            ms < 350 -> PingQuality.Medium
            else -> PingQuality.Bad
        }
    }
}

class DetailRow(val key: String, val value: String, val hint: String? = null, val mono: Boolean = false,
                val tone: Tone = Tone.Normal, val chip: Boolean = false)

class ToastMsg(val text: String, val error: Boolean, val id: Long = System.nanoTime())

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val fetcher = SubscriptionFetcher()

    // ---- appearance
    var themeMode by mutableStateOf(store.theme); private set
    var accent by mutableIntStateOf(store.accent); private set
    /** Bumped on language change so the whole UI recomposes with the new strings. */
    var langTick by mutableIntStateOf(0); private set

    // ---- gate
    var authenticated by mutableStateOf(false); private set
    var gateUrl by mutableStateOf("")
    var gateError by mutableStateOf<String?>(null); private set
    var validating by mutableStateOf(false); private set

    // ---- subscription
    var sub by mutableStateOf<SubscriptionInfo?>(null); private set
    var refreshing by mutableStateOf(false); private set
    var refreshFailed by mutableStateOf(false); private set
    var lastRefresh by mutableLongStateOf(System.currentTimeMillis()); private set
    var history by mutableStateOf<List<Long?>>(emptyList()); private set
    val servers = mutableStateListOf<ServerItem>()
    var selectedKey by mutableStateOf(store.selectedKey); private set

    // ---- connection
    var publicIp by mutableStateOf<String?>(null); private set
    var exitCountry by mutableStateOf<String?>(null); private set
    var realDelay by mutableStateOf<String?>(null); private set

    var toast by mutableStateOf<ToastMsg?>(null); private set

    // ---- updates
    val currentVersion: String = "v" + BuildConfig.VERSION_NAME
    var checkingUpdate by mutableStateOf(false); private set
    var availableUpdate by mutableStateOf<AppUpdater.Release?>(null); private set
    /** Shown as a dialog when the user asked for the check and a newer version exists. */
    var updatePrompt by mutableStateOf<AppUpdater.Release?>(null); private set

    private var refreshLoop: Job? = null

    init {
        L.setMode(store.language)
        val link = Brand.normalizeLink(store.subscriptionUrl)
        if (link != null) {
            store.cachedSubscription?.let { apply(it, rebuild = true) }
            authenticated = true
            startRefreshLoop()
        }
        viewModelScope.launch {
            VpnState.status.collect { status ->
                if (status == VpnStatus.Connected) checkExit()
                if (status == VpnStatus.Disconnected) { publicIp = null; exitCountry = null; realDelay = null }
            }
        }
        checkUpdates(manual = false)
        viewModelScope.launch {
            VpnState.error.collect { err ->
                if (err != null) {
                    showToast(L.t("errConnect", err), error = true)
                    VpnState.error.value = null
                }
            }
        }
    }

    // ================================================================ appearance

    fun cycleTheme() {
        themeMode = when (themeMode) { "auto" -> "dark"; "dark" -> "light"; else -> "auto" }
        store.theme = themeMode
    }

    fun setAccentIndex(i: Int) {
        accent = i
        store.accent = i
    }

    fun cycleLanguage() {
        store.language = when (store.language) { "auto" -> "en"; "en" -> "fr"; else -> "auto" }
        L.setMode(store.language)
        langTick++
    }

    val langBadge: String get() = if (L.mode == "auto") "A/" + L.lang.uppercase() else L.lang.uppercase()

    // ================================================================ gate

    fun signIn() {
        val link = Brand.normalizeLink(gateUrl)
        if (link == null) {
            gateError = L.t("errFormat")
            return
        }
        validating = true
        gateError = null
        viewModelScope.launch {
            try {
                val info = fetch(link.first)
                if (info.servers.isEmpty()) {
                    gateError = L.t("errEmpty")
                    return@launch
                }
                store.subscriptionUrl = link.first
                apply(info, rebuild = true)
                authenticated = true
                gateUrl = ""
                val msg = L.t("loaded", info.servers.size)
                showToast(if (info.servers.any { it.isBypassPatched }) msg + " · " + L.t("bypassApplied") else msg)
                pingAll()
                startRefreshLoop()
            } catch (e: SubscriptionFetcher.NotFoundException) {
                gateError = L.t("errNotFound")
            } catch (e: Exception) {
                gateError = L.t("errNetwork")
            } finally {
                validating = false
            }
        }
    }

    fun changeLink() {
        if (VpnState.status.value != VpnStatus.Disconnected) VoidRayVpnService.stop(getApplication())
        signOut(null)
    }

    private fun signOut(error: String?) {
        refreshLoop?.cancel()
        store.clearSubscription()
        sub = null
        servers.clear()
        selectedKey = null
        authenticated = false
        gateError = error
    }

    // ================================================================ subscription

    private suspend fun fetch(url: String): SubscriptionInfo = withContext(Dispatchers.IO) {
        val info = fetcher.fetch(url)
        if (info.sid == null) info.sid = url.trimEnd('/').substringAfterLast('/')
        FirewallBypass.apply(info.servers)
        info
    }

    private fun startRefreshLoop() {
        refreshLoop?.cancel()
        refreshLoop = viewModelScope.launch {
            refresh(manual = false)
            while (true) {
                delay(120_000)
                refresh(manual = false)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh(manual = true) }
    }

    private suspend fun refresh(manual: Boolean) {
        if (!authenticated || refreshing) return
        refreshing = true
        try {
            val info = fetch(store.subscriptionUrl)
            val changed = sub?.servers?.map { it.key } != info.servers.map { it.key }
            apply(info, rebuild = changed)
            refreshFailed = false
            lastRefresh = System.currentTimeMillis()
            if (manual) showToast(L.t("loaded", info.servers.size))
            if (changed || manual) pingAll()
        } catch (e: SubscriptionFetcher.NotFoundException) {
            if (VpnState.status.value != VpnStatus.Disconnected) VoidRayVpnService.stop(getApplication())
            signOut(L.t("errRevoked"))
        } catch (e: Exception) {
            refreshFailed = true
            if (manual) showToast(L.t("errNetwork"), error = true)
        } finally {
            refreshing = false
        }
    }

    private fun apply(info: SubscriptionInfo, rebuild: Boolean) {
        sub = info
        store.cachedSubscription = info
        if (rebuild || servers.isEmpty()) {
            servers.clear()
            servers.addAll(info.servers.map(::ServerItem))
            if (servers.none { it.profile.key == selectedKey }) select(servers.firstOrNull())
        }
        val id = sid
        val list = store.history(id)
        UsageHistory.record(list, info.used)
        store.saveHistory(id, list)
        history = UsageHistory.daily(list)
    }

    val sid: String get() = sub?.sid ?: store.subscriptionUrl.trimEnd('/').substringAfterLast('/')
    val subscriptionUrl: String get() = store.subscriptionUrl
    val supportUrl: String get() = sub?.supportUrl?.takeIf { it.isNotBlank() } ?: Brand.DEFAULT_SUPPORT_URL
    val title: String get() = if (authenticated) sub?.title ?: Brand.SERVICE_NAME else "VOID-RAY"
    val accounts: String get() = sub?.emails?.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "ID $sid"

    // ---- gauge & status
    val unlimited: Boolean get() = (sub?.total ?: 0) <= 0
    val ratio: Float get() = sub?.let { if (it.total > 0) (it.used.toFloat() / it.total).coerceAtMost(1f) else 0f } ?: 0f
    private val expired: Boolean get() = sub?.expire?.let { it * 1000 < System.currentTimeMillis() } ?: false
    private val drained: Boolean get() = sub?.let { it.total > 0 && it.used >= it.total } ?: false
    val live: Boolean get() = sub?.enabled == true && !expired && !drained
    val gaugeTone: Tone get() = if (!live) Tone.Alert else if (ratio >= .9f) Tone.Warn else Tone.Ok

    val gaugeFigure: Pair<String, String>
        get() {
            val s = sub ?: return "—" to ""
            val text = if (unlimited) Format.bytes(s.used) else Format.bytes(maxOf(0, s.total - s.used))
            return text.substringBefore(' ') to text.substringAfter(' ', "")
        }

    val gaugeCaption: String get() = if (unlimited) L.t("capUsed") else L.t("capLeft", Format.bytes(sub?.total ?: 0))

    val status: Pair<String, Tone>
        get() {
            val s = sub ?: return "—" to Tone.Normal
            return when {
                !s.enabled -> L.t("stDisabled") to Tone.Alert
                expired -> L.t("stExpired") to Tone.Alert
                drained -> L.t("stQuota") to Tone.Alert
                unlimited -> L.t("stUnlimited") to Tone.Ok
                ratio >= .9f -> L.t("stAlmost") to Tone.Warn
                else -> L.t("stActive") to Tone.Ok
            }
        }

    val historyAverage: String
        get() = history.filterNotNull().takeIf { it.isNotEmpty() }?.let { L.t("histAvg", Format.bytes(it.average())) } ?: ""

    val detailRows: List<DetailRow>
        get() {
            val s = sub ?: return emptyList()
            val rows = mutableListOf(DetailRow(L.t("rowId"), sid.ifEmpty { "—" }, mono = true))
            if (s.emails.isNotEmpty()) rows += DetailRow(L.t(if (s.emails.size > 1) "rowAccounts" else "rowAccount"), s.emails.joinToString(" · "))
            val (label, tone) = status
            rows += DetailRow(L.t("rowStatus"), label, tone = tone, chip = true)
            rows += DetailRow(L.t("rowDown"), Format.bytes(s.download), mono = true)
            rows += DetailRow(L.t("rowUp"), Format.bytes(s.upload), mono = true)
            rows += DetailRow(L.t("rowUsed"), Format.bytes(s.used), mono = true)
            rows += DetailRow(L.t("rowQuota"), if (unlimited) "∞" else Format.bytes(s.total), mono = true)
            if (!unlimited) rows += DetailRow(L.t("rowLeft"), Format.bytes(maxOf(0, s.total - s.used)), mono = true)
            rows += s.lastOnline?.let { DetailRow(L.t("rowOnline"), Format.stamp(it), Format.ago(it)) }
                ?: DetailRow(L.t("rowOnline"), L.t("neverSeen"))
            val exp = s.expire
            rows += if (exp == null) {
                DetailRow(L.t("rowExpiry"), L.t("noExpiry"))
            } else {
                val days = ceil((exp * 1000 - System.currentTimeMillis()) / 86_400_000.0).toInt()
                val hint = when {
                    days < 0 -> L.t("expired")
                    days == 0 -> L.t("today")
                    days == 1 -> L.t("inDay", 1)
                    else -> L.t("inDays", days)
                }
                DetailRow(L.t("rowExpiry"), Format.shortDate(exp * 1000), hint,
                    tone = if (days <= 3) Tone.Alert else if (days <= 7) Tone.Warn else Tone.Normal)
            }
            return rows
        }

    fun freshText(now: Long): String {
        if (refreshFailed) return L.t("freshFail")
        val diff = now - lastRefresh
        return when {
            diff < 90_000 -> L.t("freshNow")
            diff < 3_600_000 -> L.t("freshMin", (diff / 60_000.0).toInt().coerceAtLeast(1))
            else -> L.t("freshHour", (diff / 3_600_000.0).toInt())
        }
    }

    // ================================================================ servers

    val selected: ServerItem? get() = servers.firstOrNull { it.profile.key == selectedKey }

    fun select(item: ServerItem?) {
        val changed = item?.profile?.key != selectedKey
        selectedKey = item?.profile?.key
        store.selectedKey = selectedKey
        if (changed && item != null && VpnState.status.value == VpnStatus.Connected) startVpn()
    }

    fun pingAll() {
        val items = servers.toList()
        items.forEach { it.quality = PingQuality.Testing }
        val gate = Semaphore(8)
        items.forEach { item ->
            viewModelScope.launch {
                val ms = gate.withPermit { withContext(Dispatchers.IO) { NetTools.tcpPing(item.profile.address, item.profile.port) } }
                item.setResult(ms)
            }
        }
    }

    // ================================================================ connection

    /** Called once the VPN permission is granted. */
    fun startVpn() {
        val server = selected ?: run {
            showToast(L.t("pickServer"), error = true)
            return
        }
        val ctx = getApplication<Application>()
        try {
            VoidRayVpnService.configFile(ctx).writeText(XrayConfig.build(server.profile))
        } catch (e: Exception) {
            showToast(L.t("errConnect", e.message ?: ""), error = true)
            return
        }
        VpnState.serverName.value = server.name
        VpnState.status.value = VpnStatus.Connecting
        VoidRayVpnService.start(ctx, server.name)
    }

    fun stopVpn() = VoidRayVpnService.stop(getApplication())

    private fun checkExit() {
        viewModelScope.launch {
            delay(600)
            val exit = withContext(Dispatchers.IO) { NetTools.checkExit(XrayConfig.SOCKS_PORT) }
            if (VpnState.status.value != VpnStatus.Connected) return@launch
            if (exit == null) {
                realDelay = L.t("failed")
                showToast(L.t("errNoTraffic"), error = true)
                return@launch
            }
            publicIp = exit.ip
            exitCountry = exit.country?.let { "$it (${exit.countryCode})" }
            realDelay = "${exit.delayMs} ms"
        }
    }

    // ================================================================ updates

    val updateLabel: String
        get() = when {
            checkingUpdate -> L.t("updChecking")
            availableUpdate != null -> L.t("updAvailable", availableUpdate!!.tag)
            else -> L.t("updCheck")
        }

    fun checkUpdates(manual: Boolean) {
        if (checkingUpdate) return
        if (manual && availableUpdate != null) {
            updatePrompt = availableUpdate
            return
        }
        checkingUpdate = true
        viewModelScope.launch {
            val latest = try {
                withContext(Dispatchers.IO) { AppUpdater.latest(BuildConfig.VERSION_NAME) }
            } catch (e: Exception) {
                null
            }
            checkingUpdate = false
            if (latest == null) {
                if (manual) showToast(L.t("updFailed"), error = true)
                return@launch
            }
            val newer = AppUpdater.isNewer(latest.version, AppUpdater.parseVersion(BuildConfig.VERSION_NAME))
            availableUpdate = if (newer) latest else null
            when {
                !newer -> if (manual) showToast(L.t("updLatest", currentVersion))
                manual -> updatePrompt = latest
            }
        }
    }

    fun dismissUpdate() {
        updatePrompt = null
    }

    fun installUpdate() {
        val release = updatePrompt ?: return
        updatePrompt = null
        val url = release.apkUrl
        if (url == null) {
            openUrl(release.page)
            return
        }
        val ctx = getApplication<Application>()
        checkingUpdate = true
        viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    AppUpdater.download(ctx, url) { percent ->
                        viewModelScope.launch { showToast(L.t("updDownloading", percent)) }
                    }
                }
                if (!AppUpdater.install(ctx, apk)) showToast(L.t("updAllow"))
            } catch (e: Exception) {
                showToast(L.t("updInstallFailed", e.message ?: ""), error = true)
            } finally {
                checkingUpdate = false
            }
        }
    }

    // ================================================================ misc

    fun copy(text: String?, label: String) {
        if (text.isNullOrEmpty()) return
        val cm = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        showToast(L.t("copied", label))
    }

    fun openUrl(url: String) {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun showToast(text: String, error: Boolean = false) {
        toast = ToastMsg(text, error)
    }

    fun dismissToast(msg: ToastMsg) {
        if (toast?.id == msg.id) toast = null
    }
}
