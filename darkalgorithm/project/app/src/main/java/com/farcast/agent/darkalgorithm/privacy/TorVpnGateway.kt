package com.farcast.agent.darkalgorithm.privacy

import android.content.Context
import android.content.Intent

/**
 * Whole-device routing, Path A: Orbot already has a VPN mode that tunnels
 * ALL device traffic through Tor. But Orbot's public Intent API (see
 * TorGateway.kt) only documents ACTION_START (start the Tor process) and
 * ACTION_STATUS (report ON/OFF/STARTING/STOPPING) — there is no documented
 * action for a third-party app to remotely flip Orbot's VPN toggle. That
 * toggle is gated behind Android's own VpnService.prepare() consent dialog,
 * and that consent is tied to whichever app's package requests it — Orbot's,
 * not FARCAST's, if we want Orbot to be the one holding the tunnel.
 *
 * So rather than fabricate an intent action that doesn't exist, this does the
 * honest thing: confirm Tor is reachable, then hand off to Orbot's own UI so
 * the user flips VPN mode on themselves, once, inside the app that actually
 * owns that permission grant.
 */
object TorVpnGateway {

    sealed class VpnPromptResult {
        data object OrbotNotInstalled : VpnPromptResult()
        data object LaunchedOrbotSettings : VpnPromptResult()
    }

    /** Opens Orbot so the user can enable its VPN mode. Does not (cannot, honestly)
     *  toggle it for them. Call this from a button like "Protect whole device". */
    fun promptEnableOrbotVpnMode(context: Context): VpnPromptResult {
        if (!TorGateway.isOrbotInstalled(context)) return VpnPromptResult.OrbotNotInstalled
        val launch = context.packageManager.getLaunchIntentForPackage(TorGateway.ORBOT_PACKAGE)
            ?: return VpnPromptResult.OrbotNotInstalled
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return VpnPromptResult.LaunchedOrbotSettings
    }
}
