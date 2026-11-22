package com.farcast.agent.darkalgorithm

import android.content.Context
import com.farcast.agent.darkalgorithm.features.AhmiaOnionSearchService
import com.farcast.agent.darkalgorithm.features.DuckDuckGoSearchService
import com.farcast.agent.darkalgorithm.features.SearxngSearchService
import java.util.UUID

/**
 * query is whatever the case's current search term is (a name, handle, phrase
 * from another Lead's summary, etc.) — pass it in per-run rather than baking
 * one query into the Source, since a case's search term changes as the
 * investigation develops.
 *
 * SearXNG (searxngInstanceUrl) is the primary, sanctioned engine here — one
 * query gets general/image/video/music results together, through whatever
 * engines the configured instance aggregates, without FARCAST scraping
 * anything directly. Pass null to skip it (e.g. before the user has
 * configured an instance); Ahmia and DDG's instant-answer API still work
 * without it.
 */
class SearchBranchSource(
    private val context: Context,
    private val query: String,
    private val searxngInstanceUrl: String? = null,
    private val includeOnionSearch: Boolean = true
) : LeadSource {
    override suspend fun fetch(ctx: RunContext): List<Lead> {
        val leads = mutableListOf<Lead>()

        if (searxngInstanceUrl != null) {
            runCatching {
                val svc = SearxngSearchService(context, searxngInstanceUrl)
                svc.searchAllMedia(ctx.caseFile.caseId, query).forEach { (category, results) ->
                    results.forEach { r ->
                        leads += Lead(
                            UUID.randomUUID().toString(), ctx.caseFile.caseId, "search",
                            "SearXNG [${category.param}/${r.engine ?: "?"}] ${r.title} — ${r.url}",
                            listOf("src-searxng"), System.currentTimeMillis(),
                            rawData = buildMap {
                                put("url", r.url)
                                r.thumbnailUrl?.let { put("imageUrl", it) } // feeds YoloHydrator for free
                            },
                            category = LeadCategory.PUBLIC_RECORD
                        )
                    }
                }
            }
        } else {
            // Fallback when no SearXNG instance is configured yet.
            runCatching {
                val answer = DuckDuckGoSearchService(context).instantAnswer(ctx.caseFile.caseId, query)
                if (answer.abstractText.isNotBlank()) {
                    leads += Lead(
                        UUID.randomUUID().toString(), ctx.caseFile.caseId, "search",
                        "DDG instant answer (${answer.abstractSource}): ${answer.abstractText}",
                        listOf("src-ddg"), System.currentTimeMillis(), category = LeadCategory.PUBLIC_RECORD
                    )
                }
            }
        }

        if (includeOnionSearch) {
            runCatching {
                AhmiaOnionSearchService(context).search(ctx.caseFile.caseId, query, limit = 10).forEach { r ->
                    leads += Lead(
                        UUID.randomUUID().toString(), ctx.caseFile.caseId, "search",
                        "Ahmia: ${r.title} — ${r.onionUrl}",
                        listOf("src-ahmia"), System.currentTimeMillis(),
                        rawData = mapOf("onionUrl" to r.onionUrl),
                        category = LeadCategory.PUBLIC_RECORD
                    )
                }
            }
        }

        return leads
    }
}
