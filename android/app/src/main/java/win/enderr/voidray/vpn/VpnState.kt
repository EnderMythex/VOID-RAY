package win.enderr.voidray.vpn

import kotlinx.coroutines.flow.MutableStateFlow

enum class VpnStatus { Disconnected, Connecting, Connected, Disconnecting }

/** Process-wide VPN state shared by the service, the UI and the quick-settings tile. */
object VpnState {
    val status = MutableStateFlow(VpnStatus.Disconnected)
    /** Last error raised by the service, consumed by the UI. */
    val error = MutableStateFlow<String?>(null)
    val serverName = MutableStateFlow<String?>(null)
    @Volatile var connectedAt: Long = 0L
}
