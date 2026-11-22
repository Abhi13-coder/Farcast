package com.farcast.agent.darkalgorithm.features

import android.content.Context
import android.os.Build
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.json.*
import java.util.Locale

/**
 * #p-mir. Two of the site's three checks are browser-specific and don't have
 * an Android equivalent — ported honestly rather than faked:
 *
 *  - WEBRTC LEAK: tests whether RTCPeerConnection ICE candidate gathering
 *    reveals your real local IP despite a VPN/proxy. There's no
 *    RTCPeerConnection involved in a native app with no WebRTC stack, so
 *    there's nothing to leak this way — not "ported", genuinely not applicable.
 *  - FINGERPRINT: the site means browser/canvas/WebGL fingerprinting
 *    (entropy from how your specific browser+GPU renders a test image).
 *    Android has no canvas to fingerprint; what's below instead reports
 *    which of YOUR OWN device signals an app could use to fingerprint you,
 *    which is the OPSEC-relevant analogue — knowing your own exposure.
 *
 *  - DNS RESOLVERS: ports directly and for real — it's just HTTP calls to
 *    three DoH endpoints, platform-agnostic.
 */
class OpsecMirrorService(private val context: Context) {

    data class DnsResolverResult(val name: String, val ok: Boolean, val answer: String)

    suspend fun dnsResolverLeakTest(caseId: String): List<DnsResolverResult> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "opsec-mirror")
        val targets = listOf(
            Triple("Cloudflare", "https://cloudflare-dns.com/dns-query?name=example.com&type=A", mapOf("Accept" to "application/dns-json")),
            Triple("Google", "https://dns.google/resolve?name=example.com&type=A", emptyMap()),
            Triple("Quad9", "https://dns.quad9.net:5053/dns-query?name=example.com&type=A", mapOf("Accept" to "application/dns-json"))
        )
        return targets.map { (name, url, headers) ->
            try {
                val o = FarcastHttp.getJson(client, url, headers).jsonObject
                val answer = o["Answer"]?.jsonArray?.firstOrNull()?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull ?: "no answer"
                DnsResolverResult(name, true, answer)
            } catch (e: Exception) {
                DnsResolverResult(name, false, e.message ?: "blocked")
            }
        }
    }

    data class DeviceExposure(val signal: String, val value: String, val fingerprintRisk: String)

    /** Not browser fingerprinting — an inventory of what an app on THIS device
     *  could read about you without special permissions, i.e. your baseline
     *  exposure before any OSINT tool even runs. */
    fun deviceFingerprintExposure(): List<DeviceExposure> = listOf(
        DeviceExposure("Device model", "${Build.MANUFACTURER} ${Build.MODEL}", "low — common models share this value with millions of others"),
        DeviceExposure("OS version", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})", "low"),
        DeviceExposure("Locale", Locale.getDefault().toString(), "low, but combines with other signals"),
        DeviceExposure("Screen density bucket", context.resources.displayMetrics.densityDpi.toString(), "low"),
        DeviceExposure(
            "Advertising ID availability",
            "check Settings > Privacy > Ads — resettable, should be reset or opted out for OPSEC-sensitive use",
            "high if not reset — this one is DESIGNED to be a stable cross-app identifier"
        )
    )

    fun runAll(caseId: String, dnsResults: List<DnsResolverResult>): String {
        val exposure = deviceFingerprintExposure()
        return buildString {
            appendLine("DNS resolvers reachable: ${dnsResults.count { it.ok }}/${dnsResults.size}")
            appendLine("Device exposure signals: ${exposure.size} (see deviceFingerprintExposure() for detail)")
            appendLine("WebRTC leak check: not applicable — no WebRTC stack in a native app")
        }
    }
}
