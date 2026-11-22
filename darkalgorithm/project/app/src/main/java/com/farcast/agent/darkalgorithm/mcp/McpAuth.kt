package com.farcast.agent.darkalgorithm.mcp

import java.security.SecureRandom

/**
 * Pairing-token auth for the MCP server. Off by default; the user has to
 * explicitly turn this on in Settings and pair a client before any AI
 * assistant can reach case data — matching Section 8's "do not expose
 * private case data to third parties without explicit user authorization."
 * A locally-run AI assistant the user deliberately pairs isn't a third
 * party in that sense, but the explicit-authorization bar still applies:
 * nothing is reachable until the user generates and hands over a token.
 */
object McpAuth {

    data class PairingToken(val value: String, val issuedAtEpochMs: Long)

    private var current: PairingToken? = null

    /** Call when the user taps "Enable AI access" in Settings. Shown as text
     *  to copy or a QR code; invalidates any previously issued token. */
    fun issueNewToken(): PairingToken {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        return PairingToken(token, System.currentTimeMillis()).also { current = it }
    }

    fun revoke() { current = null }

    fun isEnabled(): Boolean = current != null

    fun isValid(presented: String?): Boolean {
        val token = current ?: return false
        if (presented.isNullOrBlank()) return false
        // Constant-time compare to avoid timing side-channels on token guessing.
        if (presented.length != token.value.length) return false
        var diff = 0
        for (i in presented.indices) diff = diff or (presented[i].code xor token.value[i].code)
        return diff == 0
    }
}

/** Binding policy: loopback-only by default. LAN binding is a separate,
 *  explicitly-labeled opt-in with its own warning, never a side effect of
 *  turning the server on. */
enum class McpBindScope { LOOPBACK_ONLY, LOCAL_NETWORK }
