package com.farcast.agent.darkalgorithm.privacy

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

/**
 * Whole-device routing, Path B: FARCAST owns the VPN tunnel directly instead
 * of relying on Orbot's VPN mode. This is the heavier option the README's
 * previous note flagged — it means FARCAST itself holds the always-on
 * VpnService permission grant and shows in Android's VPN status icon, which
 * is a bigger ask of the user than Path A and runs against Section 6's
 * "avoid unnecessary background services" guidance unless the user
 * specifically wants FARCAST (not Orbot) to be the thing holding the tunnel.
 *
 * What's real in this file: the Android lifecycle — permission request via
 * VpnService.prepare(), the Builder config, running as a foreground service
 * with a visible notification (required on Android 8+ for a long-lived
 * service, and the right thing to do for something intercepting all device
 * traffic — it should never be invisible to the user).
 *
 * What's NOT faked: packet forwarding. A VpnService gives you a raw tun file
 * descriptor — individual IP packets in, IP packets out. Turning that into
 * working TCP/UDP connections proxied through Tor's SOCKS port is exactly
 * what tun2socks implementations exist to do (this is literally the same
 * component Orbot's own VPN mode uses internally — badvpn's tun2socks, or
 * the newer go-tun2socks via gomobile bindings, both with ARM32 builds).
 * Hand-rolling TCP/IP packet parsing here instead of using one of those
 * would produce code that looks complete and silently drops/corrupts
 * traffic — worse than not having the feature. The integration point below
 * is marked explicitly; wire in the native lib's JNI/gomobile binding there.
 */
class FarcastVpnService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        tunFd = Builder()
            .setSession("FARCAST Privacy Tunnel")
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)          // route everything
            .addDnsServer("8.8.8.8")        // tunneled through Tor, same default Orbot's VPN mode uses
            .setMtu(1500)
            .establish()

        val fd = tunFd ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        // --- INTEGRATION POINT ---
        // Hand `fd.fileDescriptor` (the raw tun fd) to a native tun2socks
        // implementation here, pointed at the local SOCKS proxy from
        // TorGateway.awaitOn() (Orbot's advertised SOCKS host/port). e.g.:
        //
        //   Tun2socks.start(
        //       tunFd = fd.fileDescriptor,
        //       socksServerAddr = "${torStatus.socksHost}:${torStatus.socksPort}",
        //       mtu = 1500
        //   )
        //
        // Nothing stands in for that call here — see the file header.
        // -------------------------

        return START_STICKY
    }

    override fun onDestroy() {
        tunFd?.close()
        tunFd = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "FARCAST Privacy Tunnel", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FARCAST is routing device traffic through Tor")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "farcast_vpn"
        private const val NOTIFICATION_ID = 42

        /** Call from an Activity; if this returns non-null you must
         *  startActivityForResult(it, ...) before starting the service —
         *  that's the one-time system VPN consent dialog. */
        fun prepareIntent(context: android.content.Context): Intent? = prepare(context)
    }
}
