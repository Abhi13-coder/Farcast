package com.farcast.agent.darkalgorithm.features

import android.content.Context
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.json.*

/** #p-ip. Each function is independent and keyless except abuseIpdb, matching
 *  the site's own "Keyless: ... Shodan DB · URLhaus · ThreatFox · GreyNoise ...
 *  AbuseIPDB [needs key]" note. */
class IpThreatService(private val context: Context) {

    data class GeoResult(val ip: String, val city: String?, val region: String?, val country: String?, val lat: Double?, val lon: Double?, val org: String?)

    suspend fun geo(caseId: String, ip: String? = null): GeoResult {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "ip-threat")
        val url = if (ip != null) "https://get.geojs.io/v1/ip/geo/$ip.json" else "https://get.geojs.io/v1/ip/geo.json"
        val o = FarcastHttp.getJson(client, url).jsonObject
        return GeoResult(
            ip = o["ip"]?.jsonPrimitive?.contentOrNull ?: ip.orEmpty(),
            city = o["city"]?.jsonPrimitive?.contentOrNull,
            region = o["region"]?.jsonPrimitive?.contentOrNull,
            country = o["country"]?.jsonPrimitive?.contentOrNull,
            lat = o["latitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
            lon = o["longitude"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(),
            org = o["organization"]?.jsonPrimitive?.contentOrNull
        )
    }

    suspend fun shodanInternetDb(caseId: String, ip: String): Pair<List<String>, List<String>> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "ip-threat")
        val o = FarcastHttp.getJson(client, "https://internetdb.shodan.io/$ip").jsonObject
        val ports = o["ports"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull?.toString() } ?: emptyList()
        val tags = o["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        return ports to tags
    }

    suspend fun urlhaus(caseId: String, url: String): Boolean {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "ip-threat")
        val target = if (url.startsWith("http")) url else "http://$url"
        val o = FarcastHttp.postForm(client, "https://urlhaus-api.abuse.ch/v1/url/", mapOf("url" to target)).jsonObject
        return o["query_status"]?.jsonPrimitive?.contentOrNull != "no_results"   // true = listed/malicious
    }

    suspend fun greynoise(caseId: String, ip: String): Pair<Boolean, String> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "ip-threat")
        val o = FarcastHttp.getJson(client, "https://api.greynoise.io/v3/community/$ip").jsonObject
        val noise = o["noise"]?.jsonPrimitive?.booleanOrNull ?: false
        val classification = o["classification"]?.jsonPrimitive?.contentOrNull ?: "—"
        return noise to classification
    }

    suspend fun threatfox(caseId: String, ioc: String): List<Pair<String, String>> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "ip-threat")
        val body = """{"query":"search_ioc","search_term":"${ioc.replace("\"", "")}"}"""
        val o = FarcastHttp.postJson(client, "https://threatfox-api.abuse.ch/api/v1/", body).jsonObject
        if (o["query_status"]?.jsonPrimitive?.contentOrNull != "ok") return emptyList()
        return o["data"]?.jsonArray?.take(4)?.mapNotNull { d ->
            val ioc_ = d.jsonObject["ioc"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val type = d.jsonObject["threat_type"]?.jsonPrimitive?.contentOrNull ?: "?"
            ioc_ to type
        } ?: emptyList()
    }

    /** Requires a free AbuseIPDB key, stored via SecureKeyVault. */
    suspend fun abuseIpdb(caseId: String, ip: String, apiKey: String): Pair<Int, Int> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "ip-threat")
        val o = FarcastHttp.getJson(
            client,
            "https://api.abuseipdb.com/api/v2/check?ipAddress=$ip&maxAgeInDays=90",
            mapOf("Key" to apiKey, "Accept" to "application/json")
        ).jsonObject["data"]?.jsonObject ?: return 0 to 0
        val score = o["abuseConfidenceScore"]?.jsonPrimitive?.intOrNull ?: 0
        val reports = o["totalReports"]?.jsonPrimitive?.intOrNull ?: 0
        return score to reports
    }
}
