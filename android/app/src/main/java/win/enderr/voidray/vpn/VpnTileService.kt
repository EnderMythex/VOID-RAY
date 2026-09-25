package win.enderr.voidray.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import win.enderr.voidray.MainActivity

/** Quick-settings tile: connect / disconnect without opening the app. */
class VpnTileService : TileService() {
    private var watch: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        watch = CoroutineScope(Dispatchers.Main).launch {
            VpnState.status.collect { update(it) }
        }
    }

    override fun onStopListening() {
        watch?.cancel()
        watch = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (VpnState.status.value) {
            VpnStatus.Connected, VpnStatus.Connecting -> VoidRayVpnService.stop(this)
            VpnStatus.Disconnected -> {
                val ready = VpnService.prepare(this) == null && VoidRayVpnService.configFile(this).exists()
                if (ready) VoidRayVpnService.start(this, VpnState.serverName.value) else openApp()
            }
            VpnStatus.Disconnecting -> Unit
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun update(status: VpnStatus) {
        val tile = qsTile ?: return
        tile.state = when (status) {
            VpnStatus.Connected -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = VpnState.serverName.value
        tile.updateTile()
    }
}
