package com.farcast.agent.darkalgorithm.features

import android.content.Context
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import java.security.MessageDigest

/** #p-ps */
class PersonaIntelService(private val context: Context) {

    data class SiteHit(val site: String, val detail: String)

    /** The "quick 7" from the site: GitHub/Reddit/GitLab/Codeforces/HN/Dev.to/Keybase,
     *  each a single keyless API call. Fast enough to run as part of a normal
     *  DarkMixer pass, unlike the full Sherlock/Maigret scans below. */
    suspend fun quickSeven(caseId: String, username: String): List<SiteHit> = coroutineScope {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "persona")
        val checks = listOf<Pair<String, suspend () -> String?>>(
            "GitHub" to {
                FarcastHttp.getJsonOrNull(client, "https://api.github.com/users/$username")
                    ?.jsonObject?.get("public_repos")?.jsonPrimitive?.intOrNull?.let { "$it repos" }
            },
            "Reddit" to {
                FarcastHttp.getJsonOrNull(client, "https://www.reddit.com/user/$username/about.json")
                    ?.jsonObject?.get("data")?.jsonObject?.get("total_karma")?.jsonPrimitive?.intOrNull?.let { "karma $it" }
            },
            "GitLab" to {
                FarcastHttp.getJsonOrNull(client, "https://gitlab.com/api/v4/users?username=$username")
                    ?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull?.let { "id $it" }
            },
            "Codeforces" to {
                val o = FarcastHttp.getJsonOrNull(client, "https://codeforces.com/api/user.info?handles=$username")?.jsonObject
                if (o?.get("status")?.jsonPrimitive?.contentOrNull == "OK")
                    o["result"]?.jsonArray?.firstOrNull()?.jsonObject?.get("rank")?.jsonPrimitive?.contentOrNull ?: "unrated"
                else null
            },
            "HN" to {
                FarcastHttp.getJsonOrNull(client, "https://hn.algolia.com/api/v1/users/$username")
                    ?.jsonObject?.get("karma")?.jsonPrimitive?.intOrNull?.let { "karma $it" }
            },
            "Dev.to" to {
                FarcastHttp.getJsonOrNull(client, "https://dev.to/api/users/by_username?url=$username")
                    ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            },
            "Keybase" to {
                val o = FarcastHttp.getJsonOrNull(client, "https://keybase.io/_/api/1.0/user/lookup.json?usernames=$username")?.jsonObject
                if (o?.get("them")?.jsonArray?.firstOrNull() != null) "verified proofs" else null
            }
        )
        checks.map { (name, fn) -> async { name to runCatching { fn() }.getOrNull() } }
            .mapNotNull { it.await().let { (name, detail) -> detail?.let { d -> SiteHit(name, d) } } }
    }

    /** Mirrors loadSherlockData()/sherlockCheck() 1:1: fetch the canonical
     *  site-definition DB, probe each, report found/not-found/blocked. Runs
     *  with bounded concurrency like the site's worker-pool pattern (CONC=15). */
    suspend fun sherlockScan(caseId: String, username: String, concurrency: Int = 15,
                              onProgress: (done: Int, total: Int, found: Int) -> Unit = { _, _, _ -> }): List<SiteHit> = coroutineScope {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "persona")
        val data = FarcastHttp.getJson(client, "https://raw.githubusercontent.com/sherlock-project/sherlock/master/sherlock_project/resources/data.json").jsonObject
        val entries = data.entries.filter { it.key != "\$schema" }
        val found = mutableListOf<SiteHit>()
        var done = 0
        val chunks = entries.chunked((entries.size / concurrency).coerceAtLeast(1))
        chunks.map { chunk ->
            async {
                for ((name, def) in chunk) {
                    val d = def.jsonObject
                    val regex = d["regexCheck"]?.jsonPrimitive?.contentOrNull
                    if (regex != null && !Regex(regex).containsMatchIn(username)) { done++; continue }
                    val method = d["request_method"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"
                    if (method != "GET") { done++; continue }
                    val urlTemplate = d["urlProbe"]?.jsonPrimitive?.contentOrNull ?: d["url"]?.jsonPrimitive?.contentOrNull
                    val url = urlTemplate?.replace("{}", username) ?: continue
                    val probe = FarcastHttp.probe(client, url)
                    val isFound = when (d["errorType"]?.jsonPrimitive?.contentOrNull) {
                        "status_code" -> probe.statusCode != (d["errorCode"]?.jsonPrimitive?.intOrNull ?: 404)
                        "response_url" -> {
                            val errorUrl = d["errorUrl"]?.jsonPrimitive?.contentOrNull
                            !(errorUrl != null && probe.finalUrl.contains(errorUrl))
                        }
                        "message" -> {
                            val msgs = d["errorMsg"]?.let { if (it is JsonArray) it.mapNotNull { m -> m.jsonPrimitive.contentOrNull } else listOfNotNull(it.jsonPrimitive.contentOrNull) } ?: emptyList()
                            msgs.none { probe.body.contains(it) }
                        }
                        else -> false
                    }
                    done++
                    if (isFound && !probe.blocked) synchronized(found) { found += SiteHit(name, "found") }
                    onProgress(done, entries.size, found.size)
                }
            }
        }.forEach { it.await() }
        found
    }

    /** Mirrors loadMaigretData()/maigretCheck() — same shape, different field names. */
    suspend fun maigretScan(caseId: String, username: String, concurrency: Int = 15,
                             onProgress: (done: Int, total: Int, found: Int) -> Unit = { _, _, _ -> }): List<SiteHit> = coroutineScope {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "persona")
        val data = FarcastHttp.getJson(client, "https://raw.githubusercontent.com/soxoj/maigret/main/maigret/resources/data.json").jsonObject
        val entries = data.entries.toList()
        val found = mutableListOf<SiteHit>()
        var done = 0
        val chunks = entries.chunked((entries.size / concurrency).coerceAtLeast(1))
        chunks.map { chunk ->
            async {
                for ((name, def) in chunk) {
                    val d = def.jsonObject
                    if (d["disabled"]?.jsonPrimitive?.booleanOrNull == true) { done++; continue }
                    val url = d["url"]?.jsonPrimitive?.contentOrNull?.replace("{username}", username)
                    if (url.isNullOrBlank()) { done++; continue }
                    val probe = FarcastHttp.probe(client, url)
                    val isFound = when (d["checkType"]?.jsonPrimitive?.contentOrNull) {
                        "status_code" -> probe.statusCode != (d["errorCode"]?.jsonPrimitive?.intOrNull ?: 404)
                        "message" -> {
                            val presence = d["presenseStrs"]?.let { if (it is JsonArray) it.mapNotNull { m -> m.jsonPrimitive.contentOrNull } else listOfNotNull(it.jsonPrimitive.contentOrNull) }
                            presence?.any { probe.body.contains(it) } ?: false
                        }
                        "response_url" -> {
                            val errorUrl = d["errorUrl"]?.jsonPrimitive?.contentOrNull
                            !(errorUrl != null && probe.finalUrl.contains(errorUrl))
                        }
                        else -> false
                    }
                    done++
                    if (isFound && !probe.blocked) synchronized(found) { found += SiteHit(name, "found") }
                    onProgress(done, entries.size, found.size)
                }
            }
        }.forEach { it.await() }
        found
    }

    suspend fun emailCheck(caseId: String, email: String): String {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "persona")
        val domain = email.substringAfter("@", "")
        val mx = if (domain.isNotBlank()) {
            FarcastHttp.getJson(client, "https://dns.google/resolve?name=$domain&type=MX").jsonObject["Answer"]
                ?.jsonArray?.mapNotNull { it.jsonObject["data"]?.jsonPrimitive?.contentOrNull } ?: emptyList()
        } else emptyList()
        val hash = MessageDigest.getInstance("MD5").digest(email.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val hasGravatar = try {
            FarcastHttp.getText(client, "https://www.gravatar.com/avatar/$hash?d=404"); true
        } catch (_: Exception) { false }
        return "MX: ${mx.joinToString(" · ").ifBlank { "none" }} · Gravatar: ${if (hasGravatar) "yes" else "no"}"
    }
}
