package win.enderr.voidray

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import win.enderr.voidray.i18n.L
import win.enderr.voidray.ui.MainViewModel
import win.enderr.voidray.ui.VoidRayApp
import win.enderr.voidray.vpn.VpnState
import win.enderr.voidray.vpn.VpnStatus

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) vm.startVpn() else vm.showToast(L.t("errPermission"), error = true)
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val status by VpnState.status.collectAsStateWithLifecycle()
            VoidRayApp(vm, isSystemInDarkTheme(), status, onToggleVpn = ::toggleVpn)
        }
    }

    private fun toggleVpn() {
        when (VpnState.status.value) {
            VpnStatus.Connected -> vm.stopVpn()
            VpnStatus.Disconnected -> {
                val consent = VpnService.prepare(this)
                if (consent != null) vpnPermission.launch(consent) else vm.startVpn()
            }
            else -> Unit
        }
    }
}
