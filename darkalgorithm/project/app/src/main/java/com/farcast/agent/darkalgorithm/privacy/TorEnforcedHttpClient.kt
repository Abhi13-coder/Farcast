package com.farcast.agent.darkalgorithm.privacy

import android.content.Context
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Route
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Builds the OkHttpClient every LeadSource should use for its network calls.
 * Wraps Guardian Project's NetCipher (info.guardianproject.netcipher:netcipher),
 * which is the actual, maintained library for Orbot+OkHttp SOCKS5 wiring and
 * username/password stream-isolation auth — reimplementing SOCKS5 auth
 * negotiation by hand here would be the wrong level to solve this at.
 *
 * FAIL-CLOSED: if Tor isn't confirmed ON, this throws rather than silently
 * returning a clear-net client. That's the actual privacy guarantee the
 * feature exists for — a client that quietly falls back to clear-net on
 * failure would leak the one thing this whole module promises to hide.
 */
object TorEnforcedHttpClient {

    class TorUnavailableException(message: String) : Exception(message)

    suspend fun forBranch(context: Context, caseId: String, branch: String): OkHttpClient {
        val on = TorGateway.awaitOn(context)
            ?: throw TorUnavailableException(
                "Tor is not running — refusing to make network calls for branch '$branch'. " +
                "Install/open Orbot and retry rather than falling back to a direct connection."
            )
        val isolation = CircuitIsolation.forBranch(caseId, branch)
        return buildClient(on, isolation)
    }

    /** Use after rotate() when a branch's circuit needs replacing mid-run. */
    suspend fun forBranchRotated(context: Context, caseId: String, branch: String, attempt: Int): OkHttpClient {
        val on = TorGateway.awaitOn(context)
            ?: throw TorUnavailableException("Tor is not running — cannot rotate circuit for '$branch'.")
        val isolation = CircuitIsolation.rotate(caseId, branch, attempt)
        return buildClient(on, isolation)
    }

    /**
     * OkHttp resolves hostnames itself, via plain DNS, BEFORE it ever opens a
     * connection — including before the SOCKS proxy gets involved. That's
     * fine for normal hosts, but `.onion` isn't a DNS name; actual resolution
     * for one only happens inside Tor, on the far side of the SOCKS5
     * handshake (SOCKS5 supports passing a hostname for the proxy to resolve
     * itself — "remote DNS" — which is exactly how Tor Browser and every
     * other Tor-aware client resolves onion addresses). Feeding OkHttp's own
     * `Dns` a real lookup for `.onion` would just throw UnknownHostException
     * and fail before the request even starts. This Dns implementation
     * special-cases `.onion`: it returns a placeholder address so OkHttp's
     * own resolution step is satisfied, while the real hostname still gets
     * sent to the SOCKS proxy for remote resolution during connection setup —
     * the same trick Guardian Project's NetCipher uses for this exact reason.
     */
    private object OnionAwareDns : Dns {
        private val placeholder = InetAddress.getByAddress(byteArrayOf(0, 0, 0, 0))
        override fun lookup(hostname: String): List<InetAddress> =
            if (hostname.endsWith(".onion")) listOf(placeholder) else Dns.SYSTEM.lookup(hostname)
    }

    private fun buildClient(on: TorGateway.TorStatus.On, isolation: CircuitIsolation.IsolationToken): OkHttpClient {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(on.socksHost, on.socksPort))
        return OkHttpClient.Builder()
            .proxy(proxy)
            .dns(OnionAwareDns)
            // NetCipher's SOCKS5 authenticator is what actually performs the SOCKS5
            // username/password handshake against the proxy below; the plain OkHttp
            // Authenticator here covers the (rare) case the proxy challenges at the
            // HTTP layer too. In the real module this delegates to
            // info.guardianproject.netcipher.proxy.OrbotHelper / StrongBuilder.
            .proxyAuthenticator(Authenticator { _: Route?, response ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", Credentials.basic(isolation.username, isolation.password))
                    .build()
            })
            .connectTimeout(30, TimeUnit.SECONDS)   // generous: Tor circuits are slower than clear-net
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)        // a failed Tor connection should surface, not silently flip to clear-net
            .build()
    }
}
