package win.enderr.voidray.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import win.enderr.voidray.MainActivity
import win.enderr.voidray.R
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
        } catch (e: Throwable) {
            Log.e(TAG, "start failed", e)
            VpnState.error.value = e.message ?: e.javaClass.simpleName
            stopVpn()
        }
    }

    private fun stopCore() {
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

    private fun goForeground(serverName: String?) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, L.t("notifChannel"), NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VoidRayVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_voidray)
            .setContentTitle(L.t("notifTitle"))
            .setContentText(serverName ?: "")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setUsesChronometer(true)
            .addAction(Notification.Action.Builder(null, L.t("notifDisconnect"), stop).build())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
