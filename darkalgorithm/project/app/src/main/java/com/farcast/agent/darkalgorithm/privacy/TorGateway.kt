package com.farcast.agent.darkalgorithm.privacy

import android.content.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * TorGateway — integrates with Orbot (org.torproject.android) via its public
 * broadcast-intent API, rather than bundling a Tor binary into FARCAST's APK.
 * Verified against Orbot's actual Intent API (dev.guardianproject.info/projects/orbot/wiki/Orbot_Intent_API):
 *
 *   ACTION_START         = "org.torproject.android.intent.action.START"
 *   ACTION_STATUS        = "org.torproject.android.intent.action.STATUS"
 *   EXTRA_STATUS         = "org.torproject.android.intent.extra.STATUS"   // ON/OFF/STARTING/STOPPING
 *   EXTRA_PACKAGE_NAME   = "org.torproject.android.intent.extra.PACKAGE_NAME"
 *
 * Why Orbot instead of an embedded Tor binary: Section 6 of the spec asks us
 * to avoid permanently-loaded services and unnecessary bulk, and Section 4
 * says not to bundle every possible dependency into the core APK. Orbot is a
 * separate, independently-updated app the user installs once; FARCAST just
 * asks it to start and reads back the SOCKS proxy it's listening on. Nothing
 * Tor-related ships inside FARCAST's own APK.
 */
object TorGateway {
    const val ORBOT_PACKAGE = "org.torproject.android"
    private const val ACTION_START = "org.torproject.android.intent.action.START"
    private const val ACTION_STATUS = "org.torproject.android.intent.action.STATUS"
    private const val EXTRA_STATUS = "org.torproject.android.intent.extra.STATUS"
    private const val EXTRA_PACKAGE_NAME = "org.torproject.android.intent.extra.PACKAGE_NAME"
    private const val EXTRA_SOCKS_PROXY_HOST = "org.torproject.android.intent.extra.SOCKS_PROXY_HOST"
    private const val EXTRA_SOCKS_PROXY_PORT = "org.torproject.android.intent.extra.SOCKS_PROXY_PORT"

    sealed class TorStatus {
        data object Off : TorStatus()
        data object Starting : TorStatus()
        data class On(val socksHost: String, val socksPort: Int) : TorStatus()
        data object Stopping : TorStatus()
        data object OrbotNotInstalled : TorStatus()
    }

    fun isOrbotInstalled(context: Context): Boolean =
        try { context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0); true }
        catch (e: PackageManager.NameNotFoundException) { false }

    /** Cold flow of Orbot status changes. Collect this instead of polling. */
    fun statusUpdates(context: Context): Flow<TorStatus> = callbackFlow {
        if (!isOrbotInstalled(context)) {
            trySend(TorStatus.OrbotNotInstalled)
            awaitClose { }
            return@callbackFlow
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.getStringExtra(EXTRA_STATUS)) {
                    "ON" -> {
                        val host = intent.getStringExtra(EXTRA_SOCKS_PROXY_HOST) ?: "127.0.0.1"
                        val port = intent.getIntExtra(EXTRA_SOCKS_PROXY_PORT, 9050)
                        trySend(TorStatus.On(host, port))
                    }
                    "OFF" -> trySend(TorStatus.Off)
                    "STARTING" -> trySend(TorStatus.Starting)
                    "STOPPING" -> trySend(TorStatus.Stopping)
                }
            }
        }
        val filter = IntentFilter(ACTION_STATUS)
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Ask Orbot to start (no-op if already running) and to reply directly to us.
        val start = Intent(ACTION_START).apply {
            setPackage(ORBOT_PACKAGE)
            putExtra(EXTRA_PACKAGE_NAME, context.packageName)
        }
        context.sendBroadcast(start)

        awaitClose { context.unregisterReceiver(receiver) }
    }

    /** Suspends until Tor is confirmed ON (with a usable SOCKS proxy) or times out. */
    suspend fun awaitOn(context: Context, timeoutMs: Long = 20_000): TorStatus.On? =
        withTimeoutOrNull(timeoutMs) {
            statusUpdates(context).first { it is TorStatus.On } as TorStatus.On
        }
}
