package win.enderr.voidray.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import win.enderr.voidray.MainActivity
import win.enderr.voidray.R
import win.enderr.voidray.core.Format
import win.enderr.voidray.i18n.L
import java.io.File

/**
 * The VPN itself: Android hands us a TUN interface, and Xray reads it directly
 * through its "tun" inbound (libv2ray StartLoop(config, tunFd)). The app is
 * excluded from the VPN so that Xray's own connections to the server do not
 * loop back into the tunnel.
 */
class VoidRayVpnService : VpnService() {

    companion object {
        private const val TAG = "VoidRayVpn"
        const val ACTION_START = "win.enderr.voidray.START"
        const val ACTION_STOP = "win.enderr.voidray.STOP"
        const val EXTRA_SERVER_NAME = "serverName"
        private const val CHANNEL_ID = "vpn"
        private const val NOTIFICATION_ID = 1

        fun configFile(context: Context) = File(context.filesDir, "config.json")

        fun start(context: Context, serverName: String?) {
            val intent = Intent(context, VoidRayVpnService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SERVER_NAME, serverName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, VoidRayVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tun: ParcelFileDescriptor? = null
    private var controller: CoreController? = null
    private var traffic: Job? = null
    private var currentServer: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> scope.launch { stopVpn() }
            else -> {
                val name = intent?.getStringExtra(EXTRA_SERVER_NAME) ?: VpnState.serverName.value
                goForeground(name)
                scope.launch { startVpn(name) }
            }
        }
        return START_NOT_STICKY
    }

    @Synchronized
    private fun startVpn(serverName: String?) {
        if (controller != null) stopCore()
        VpnState.status.value = VpnStatus.Connecting
        VpnState.serverName.value = serverName
        try {
            val config = configFile(this).readText()

            val builder = Builder()
                .setSession("VOID-RAY")
                .setMtu(1500)
                .addAddress("10.10.14.1", 30)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd66:766f:6964::1", 126)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDisallowedApplication(packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
            val fd = builder.establish() ?: throw IllegalStateException("VPN permission missing")
            tun = fd

            Libv2ray.initCoreEnv(filesDir.absolutePath, "")
            val core = Libv2ray.newCoreController(object : CoreCallbackHandler {
                override fun startup(): Long = 0
                override fun shutdown(): Long = 0
                override fun onEmitStatus(p0: Long, p1: String?): Long {
                    Log.d(TAG, "core: $p1")
                    return 0
                }
            })
            controller = core
            core.startLoop(config, fd.fd)

            VpnState.connectedAt = System.currentTimeMillis()
            VpnState.status.value = VpnStatus.Connected
            startTrafficUpdates()
        } catch (e: Throwable) {
            Log.e(TAG, "start failed", e)
            VpnState.error.value = e.message ?: e.javaClass.simpleName
            stopVpn()
        }
    }

    private fun stopCore() {
        traffic?.cancel()
        traffic = null
        try {
            controller?.stopLoop()
        } catch (e: Throwable) {
            Log.w(TAG, "stopLoop", e)
        }
        controller = null
        try {
            tun?.close()
        } catch (e: Throwable) {
            Log.w(TAG, "close tun", e)
        }
        tun = null
    }

    @Synchronized
    private fun stopVpn() {
        if (VpnState.status.value != VpnStatus.Disconnected) VpnState.status.value = VpnStatus.Disconnecting
        stopCore()
        VpnState.status.value = VpnStatus.Disconnected
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // Another VPN took over, or the user revoked the permission.
        scope.launch { stopVpn() }
    }

    override fun onDestroy() {
        stopCore()
        if (VpnState.status.value != VpnStatus.Disconnected) VpnState.status.value = VpnStatus.Disconnected
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ notification

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, L.t("notifChannel"), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
    }

    /** The VPN notification; `speed` / `totals` show the live upload and download once connected. */
    private fun buildNotification(serverName: String?, speed: String? = null, totals: String? = null): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VoidRayVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val since = VpnState.connectedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_voidray)
            .setContentTitle(L.t("notifTitle"))
            .setContentText(speed ?: serverName ?: "")
            .setSubText(serverName.takeIf { speed != null })
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(since)
            .setUsesChronometer(true)
            .addAction(Notification.Action.Builder(null, L.t("notifDisconnect"), stop).build())
        if (speed != null && totals != null) {
            builder.setStyle(Notification.BigTextStyle().bigText("$speed\n$totals"))
        }
        return builder.build()
    }

    private fun goForeground(serverName: String?) {
        ensureChannel()
        currentServer = serverName
        val notification = buildNotification(serverName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Xray runs inside this app, which is excluded from the VPN: everything the
     * app's uid sends and receives is the tunnel's traffic. Sampled every second.
     */
    private fun startTrafficUpdates() {
        traffic?.cancel()
        val uid = Process.myUid()
        val upStart = TrafficStats.getUidTxBytes(uid)
        val downStart = TrafficStats.getUidRxBytes(uid)
        if (upStart < 0 || downStart < 0) return // not supported on this device
        VpnState.upload.value = 0
        VpnState.download.value = 0
        val nm = getSystemService(NotificationManager::class.java)
        traffic = scope.launch {
            var lastUp = upStart
            var lastDown = downStart
            var lastAt = System.currentTimeMillis()
            while (isActive && VpnState.status.value == VpnStatus.Connected) {
                delay(1000)
                val up = TrafficStats.getUidTxBytes(uid)
                val down = TrafficStats.getUidRxBytes(uid)
                val now = System.currentTimeMillis()
                val secs = ((now - lastAt).coerceAtLeast(1)) / 1000.0
                val upRate = ((up - lastUp).coerceAtLeast(0) / secs).toLong()
                val downRate = ((down - lastDown).coerceAtLeast(0) / secs).toLong()
                lastUp = up; lastDown = down; lastAt = now
                VpnState.upload.value = (up - upStart).coerceAtLeast(0)
                VpnState.download.value = (down - downStart).coerceAtLeast(0)
                val speed = "↑ ${Format.bytes(upRate)}/s   ↓ ${Format.bytes(downRate)}/s"
                val totals = L.t("notifTotals", Format.bytes(VpnState.upload.value), Format.bytes(VpnState.download.value))
                nm.notify(NOTIFICATION_ID, buildNotification(currentServer, speed, totals))
            }
        }
    }
}
