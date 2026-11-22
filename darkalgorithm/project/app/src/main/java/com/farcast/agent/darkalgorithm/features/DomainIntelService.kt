package com.farcast.agent.darkalgorithm.features

import android.content.Context
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.json.*

/** #p-dom — real logic behind the earlier DomainRdapSource stub. */
class DomainIntelService(private val context: Context) {

    data class RdapResult(val ldhName: String, val status: List<String>, val nameservers: List<String>)

    suspend fun rdap(caseId: String, domain: String): RdapResult {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "domain")
        val o = FarcastHttp.getJson(client, "https://rdap.org/domain/$domain").jsonObject
        return RdapResult(
            ldhName = o["ldhName"]?.jsonPrimitive?.contentOrNull ?: domain,
            status = o["status"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            nameservers = o["nameservers"]?.jsonArray?.mapNotNull {
                it.jsonObject["ldhName"]?.jsonPrimitive?.contentOrNull
            } ?: emptyList()
        )
    }

    /** DNS-over-HTTPS via Google — A/AAAA/MX/NS/TXT, same record set the site queries. */
    suspend fun dns(caseId: String, domain: String): Map<String, List<String>> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "domain")
        val types = listOf("A", "AAAA", "MX", "NS", "TXT")
        return types.associateWith { type ->
            try {
                val o = FarcastHttp.getJson(client, "https://dns.google/resolve?name=$domain&type=$type").jsonObject
                o["Answer"]?.jsonArray?.mapNotNull { it.jsonObject["data"]?.jsonPrimitive?.contentOrNull } ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }
    }

    /** Certificate Transparency subdomain enumeration via crt.sh. */
    suspend fun ctSubdomains(caseId: String, domain: String, limit: Int = 60): List<String> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "domain")
        val text = FarcastHttp.getText(client, "https://crt.sh/?q=%25.$domain&output=json")
        val arr = Json { ignoreUnknownKeys = true }.parseToJsonElement(text).jsonArray
        return arr.flatMap { it.jsonObject["name_value"]?.jsonPrimitive?.contentOrNull.orEmpty().split("\n") }
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().take(limit)
    }

    /** Submits to urlscan.io; requires a free key (SecureKeyVault). */
    suspend fun urlscanSubmit(caseId: String, url: String, apiKey: String): String {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "domain")
        val target = if (url.startsWith("http")) url else "https://$url"
        val body = """{"url":"${target.replace("\"", "")}","public":"on"}"""
        val result = FarcastHttp.postJson(client, "https://urlscan.io/api/v1/scan/", body, mapOf("API-Key" to apiKey))
        return result.toString()
    }

    data class WaybackSnapshot(val timestamp: String, val url: String)

    suspend fun waybackClosest(caseId: String, url: String): WaybackSnapshot? {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "domain")
        val o = FarcastHttp.getJson(client, "https://archive.org/wayback/available?url=$url").jsonObject
        val snap = o["archived_snapshots"]?.jsonObject?.get("closest")?.jsonObject ?: return null
        return WaybackSnapshot(
            timestamp = snap["timestamp"]?.jsonPrimitive?.contentOrNull ?: "?",
            url = snap["url"]?.jsonPrimitive?.contentOrNull ?: "?"
        )
    }

    /** Simple typosquat check: generate near-miss variants, see which resolve.
     *  The site's btn-typo does this with a small fixed variant set; same idea here. */
    suspend fun typosquatCheck(caseId: String, domain: String): List<String> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "domain")
        val variants = generateVariants(domain)
        val hits = mutableListOf<String>()
        for (v in variants) {
            try {
                val o = FarcastHttp.getJson(client, "https://dns.google/resolve?name=$v&type=A").jsonObject
                val answer = o["Answer"]?.jsonArray
                if (!answer.isNullOrEmpty()) {
                    val ip = answer[0].jsonObject["data"]?.jsonPrimitive?.contentOrNull
                    hits += "$v → REGISTERED ($ip)"
                }
            } catch (_: Exception) { /* not registered or blocked, skip */ }
        }
        return hits
    }

    private fun generateVariants(domain: String): List<String> {
        val parts = domain.split(".")
        val name = parts.first()
        val rest = parts.drop(1).joinToString(".")
        val swaps = mapOf('o' to '0', 'l' to '1', 'i' to '1', 'e' to '3')
        val variants = mutableSetOf<String>()
        name.forEachIndexed { i, c ->
            swaps[c]?.let { variants += name.toCharArray().also { arr -> arr[i] = it }.concatToString() + "." + rest }
        }
        variants += "$name-$rest"  // e.g. example-com.* style confusables, trimmed set
        return variants.take(8)
    }
}
