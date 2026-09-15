package com.example.windowsbrowser

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * Skeleton VPN service for the WindowsBrowser app.
 * Establishes a TUN interface; packet routing/filtering logic is intentionally
 * left as a stub for future implementation (e.g., proxying or content filtering).
 */
class BrowserVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: read VPN configuration (server address, routes, DNS) from settings
        establishVpn()
        return START_STICKY
    }

    private fun establishVpn() {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress("10.0.0.2", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        vpnInterface?.close()
        vpnInterface = builder.establish()

        // TODO: spawn a worker thread reading from vpnInterface and forwarding packets
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }
}
