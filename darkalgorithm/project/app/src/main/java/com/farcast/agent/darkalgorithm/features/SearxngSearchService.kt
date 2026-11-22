package com.farcast.agent.darkalgorithm.features

import android.content.Context
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.json.*

/**
 * SearXNG (github.com/searxng/searxng, AGPL-3.0 — fine, FARCAST is open
 * source) is the real answer to "DuckDuckGo integration" and "image/video/
 * audio search" together: it's an open-source metasearch engine that
 * aggregates dozens of backend engines — including DuckDuckGo, Google
 * Images, YouTube, SoundCloud, and many more — behind one official,
 * documented JSON API, so FARCAST never has to scrape a search engine
 * directly. The instance operator's engine config carries that
 * responsibility instead of FARCAST's client code, which is the standard
 * shape every privacy-respecting search client (not just this one) uses.
 *
 * "Embed Tor search," concretely: self-host a SearXNG instance (Docker image
 * in their repo) and put a Tor hidden service in front of it (a few lines in
 * torrd/torrc — HiddenServiceDir + HiddenServicePort pointing at the local
 * port SearXNG listens on). Point `instanceUrl` at that `.onion` address and
 * every query here already routes through TorEnforcedHttpClient's SOCKS
 * proxy with the OnionAwareDns fix — no extra plumbing needed. Equally valid:
 * point it at any public instance from searx.space over its clearnet address,
 * still Tor-routed for your own IP, just not onion-to-onion.
 *
 * REQUIRES instanceUrl. There is deliberately no default instance baked in —
 * SearXNG's own docs say the same (no hardcoded default), because a baked-in
 * default becomes a single point of trust/failure and an unannounced
 * dependency on someone else's server and policies. Pick one or self-host.
 *
 * REQUIRES the instance to have JSON output enabled (`formats: [html, json]`
 * in its settings.yml) — disabled by default on fresh installs and on most
 * public instances as an anti-abuse measure. A self-hosted instance you
 * control is the reliable path; a public one may or may not have it on.
 */
class SearxngSearchService(private val context: Context, private val instanceUrl: String) {

    enum class Category(val param: String) {
        GENERAL("general"), IMAGES("images"), VIDEOS("videos"),
        MUSIC("music"), FILES("files"), NEWS("news"), SCIENCE("science")
    }

    data class SearxResult(
        val title: String,
        val url: String,
        val content: String,
        val category: Category,
        val thumbnailUrl: String? = null,   // populated for IMAGES/VIDEOS when the instance's engines provide one
        val engine: String? = null
    )

    suspend fun search(caseId: String, query: String, categories: Set<Category> = setOf(Category.GENERAL)): List<SearxResult> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "search")
        val categoryParam = categories.joinToString(",") { it.param }
        val url = "${instanceUrl.trimEnd('/')}/search?q=" +
            java.net.URLEncoder.encode(query, "UTF-8") +
            "&format=json&categories=$categoryParam"

        val body = FarcastHttp.getText(client, url)
        val root = runCatching { Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject }
            .getOrElse {
                throw IllegalStateException(
                    "Instance returned non-JSON — most likely JSON output isn't enabled on " +
                    "$instanceUrl (see settings.yml: search.formats). Raw response started with: " +
                    body.take(120)
                )
            }

        return root["results"]?.jsonArray?.mapNotNull { r ->
            val o = r.jsonObject
            val cat = categories.firstOrNull { c ->
                o["category"]?.jsonPrimitive?.contentOrNull == c.param
            } ?: categories.first()
            SearxResult(
                title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                content = o["content"]?.jsonPrimitive?.contentOrNull ?: "",
                category = cat,
                thumbnailUrl = o["thumbnail_src"]?.jsonPrimitive?.contentOrNull
                    ?: o["img_src"]?.jsonPrimitive?.contentOrNull,
                engine = o["engine"]?.jsonPrimitive?.contentOrNull
            )
        } ?: emptyList()
    }

    /** Convenience: everything at once, one result list per category requested. */
    suspend fun searchAllMedia(caseId: String, query: String): Map<Category, List<SearxResult>> {
        val wanted = setOf(Category.GENERAL, Category.IMAGES, Category.VIDEOS, Category.MUSIC)
        val results = search(caseId, query, wanted)
        return wanted.associateWith { cat -> results.filter { it.category == cat } }
    }
}
