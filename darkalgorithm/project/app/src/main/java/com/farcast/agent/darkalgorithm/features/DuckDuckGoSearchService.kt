package com.farcast.agent.darkalgorithm.features

import android.content.Context
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.json.*

/**
 * Two genuinely different things both called "DuckDuckGo search," kept
 * separate on purpose:
 *
 *  1. instantAnswer() — DuckDuckGo's own documented, official API
 *     (api.duckduckgo.com/?format=json). No key, no ToS gray area. The
 *     honest limitation: it's an Instant-Answer/infobox API (think
 *     Wikipedia-style summaries, disambiguation, definitions), NOT a
 *     general web-search index — most ordinary queries return an empty
 *     AbstractText. Good for "who/what is X" style lookups, not for
 *     "find pages mentioning X".
 *
 *  2. htmlSearchScrape() — scrapes html.duckduckgo.com/html/, the
 *     no-JS version of DDG's actual web search, to get real web results.
 *     THIS IS NOT AN OFFICIAL API. DuckDuckGo's terms restrict automated
 *     querying of their search results; this method exists because the
 *     Sherlock/Maigret-style OSINT community routes around that constantly,
 *     and the spec explicitly asked for "DuckDuckGo integration," but it's
 *     labeled loudly rather than presented as equivalent to (1). Off by
 *     default (`enableHtmlScrape` must be passed true) so nobody wires it
 *     in by accident thinking it's the sanctioned API.
 */
class DuckDuckGoSearchService(private val context: Context) {

    data class InstantAnswer(val abstractText: String, val abstractSource: String, val relatedTopics: List<String>)

    suspend fun instantAnswer(caseId: String, query: String): InstantAnswer {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "web-search")
        val url = "https://api.duckduckgo.com/?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&format=json&no_html=1&skip_disambig=1"
        val o = FarcastHttp.getJson(client, url).jsonObject
        return InstantAnswer(
            abstractText = o["AbstractText"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            abstractSource = o["AbstractSource"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            relatedTopics = o["RelatedTopics"]?.jsonArray?.mapNotNull {
                it.jsonObject["Text"]?.jsonPrimitive?.contentOrNull
            } ?: emptyList()
        )
    }

    data class WebResult(val title: String, val url: String, val snippet: String)

    /** Not called from anywhere else in this codebase by default — must be
     *  invoked explicitly with enableHtmlScrape=true, and that argument
     *  should be surfaced in the UI as a setting with the ToS caveat shown,
     *  not silently flipped on. */
    suspend fun htmlSearchScrape(caseId: String, query: String, enableHtmlScrape: Boolean, limit: Int = 15): List<WebResult> {
        require(enableHtmlScrape) {
            "DuckDuckGo's HTML endpoint isn't an official search API and sits outside their " +
            "documented terms for automated use. Pass enableHtmlScrape=true only after the user " +
            "has explicitly opted in, same as any other ToS-adjacent feature in this app."
        }
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "web-search")
        val url = "https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val html = FarcastHttp.getText(client, url)
        val resultRegex = Regex(
            """<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>.*?<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
            RegexOption.DOT_MATCHES_ALL
        )
        fun strip(s: String) = s.replace(Regex("<[^>]+>"), "").trim()
        return resultRegex.findAll(html).take(limit).map { m ->
            val rawUrl = m.groupValues[1]
            // DDG's html endpoint wraps result URLs in a redirect
            // (//duckduckgo.com/l/?uddg=<encoded-real-url>) — unwrap it.
            val real = Regex("""uddg=([^&]+)""").find(rawUrl)?.groupValues?.get(1)
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: rawUrl
            WebResult(strip(m.groupValues[2]), real, strip(m.groupValues[3]))
        }.toList()
    }
}
