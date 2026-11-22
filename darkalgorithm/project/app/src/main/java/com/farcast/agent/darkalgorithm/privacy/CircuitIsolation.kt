package com.farcast.agent.darkalgorithm.privacy

/**
 * Tor builds a fresh, isolated circuit per distinct SOCKS5 username/password
 * pair (IsolateSOCKSAuth, on by default in Tor's torrc). That's the real
 * mechanism behind what the spec calls "multi-tunnel": not several VPN
 * tunnels, but several independent 3-hop Tor circuits that don't share an
 * exit relay, so a lookup on one FARCAST branch can't be correlated with a
 * lookup on another branch by anything watching exit traffic.
 *
 * One isolation token per branch, stable for the lifetime of a case so a
 * branch's requests stay on one circuit (avoids needlessly cycling circuits
 * mid-investigation) but distinct across branches and across cases.
 */
object CircuitIsolation {

    /** SOCKS5 username/password Tor will use as the isolation key. Values are
     *  opaque tokens, not secrets — they never leave the device, they only
     *  tell Tor "treat these two connections as unrelated." */
    data class IsolationToken(val username: String, val password: String)

    fun forBranch(caseId: String, branch: String): IsolationToken {
        val key = "$caseId:$branch"
        return IsolationToken(username = "farcast-${key.hashCode().toUInt()}", password = "x")
    }

    /** Call when a branch's current circuit looks compromised/blocked (e.g. the exit
     *  node got rate-limited) to force Tor onto a brand-new circuit for that branch only. */
    fun rotate(caseId: String, branch: String, attempt: Int): IsolationToken {
        val key = "$caseId:$branch:$attempt"
        return IsolationToken(username = "farcast-${key.hashCode().toUInt()}", password = "x")
    }
}
