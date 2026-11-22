package com.farcast.agent.darkalgorithm.features

import android.content.Context
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient

/**
 * Ahmia (ahmia.fi) indexes and searches Tor hidden services, and — unlike
 * everything else wired so far — deliberately filters child-abuse content
 * out of its index before anything is searchable (documented at
 * ahmia.fi/documentation/indexing/). That curation is why it's the search
 * engine to use for this, not a reason to skip the usual caution: it's still
 * a dark-web index, and what it returns is metadata about hidden services,
 * not something to auto-visit.
 *
 * HONEST LIMITATION: Ahmia has no public JSON API. Its own documentation
 * only describes the HTML search form, and independent tooling built against
 * it notes the form "uses a rotating hidden token and a session cookie" —
 * i.e. this is screen-scraping a page that can change shape or add
 * anti-scraping friction at any time, not a stable contract like RDAP or
 * crt.sh. Treat parse failures as "the page changed, go check it," not as
 * a bug in this code.
 *
 * Querying ahmia.fi itself is a normal HTTPS clearnet request (ahmia.fi
 * is reachable without Tor) — but every RESULT is a .onion address, which
 * only resolves through Tor. This is exactly where privacy/TorGateway and
 * TorEnforcedHttpClient matter: this service queries over the "search"
 * branch's isolated circuit, and the app's onion-link opener (not built
 * here — that's a UI/intent-handling concern) should route through the
 * SOCKS proxy from TorGateway.awaitOn(), same as everything else that's
 * Tor-routed.
 */
class AhmiaOnionSearchService(private val context: Context) {

    data class OnionResult(val title: String, val onionUrl: String, val snippet: String)

    private val titleRegex = Regex("""<h4[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val snippetRegex = Regex("""<p[^>]*class="result-snippet"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private fun stripTags(s: String) = s.replace(Regex("<[^>]+>"), "").trim()

    suspend fun search(caseId: String, query: String, limit: Int = 20): List<OnionResult> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "onion-search")
        val url = "https://ahmia.fi/search/?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val html = FarcastHttp.getText(client, url)

        val titles = titleRegex.findAll(html).map { it.groupValues[1] to stripTags(it.groupValues[2]) }.toList()
        val snippets = snippetRegex.findAll(html).map { stripTags(it.groupValues[1]) }.toList()

        return titles.take(limit).mapIndexed { i, (href, title) ->
            // Ahmia's result links are redirect URLs (ahmia.fi/search/redirect?...)
            // wrapping the real onion URL as a query param — unwrap it rather
            // than returning a clearnet redirect link as if it were the target.
            val realUrl = Regex("""redirect_url=([^&"]+)""").find(href)?.groupValues?.get(1)
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: href
            OnionResult(title, realUrl, snippets.getOrElse(i) { "" })
        }
    }
}
