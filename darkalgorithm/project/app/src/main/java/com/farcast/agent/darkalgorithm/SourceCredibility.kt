package com.farcast.agent.darkalgorithm

import kotlin.math.min

/**
 * SourceCredibility — ported from x-algorithm's "base score": before a post
 * is evaluated for quality, the posting account gets a base score (verified
 * tier vs. unverified ceiling). DarkAlgorithm has no verification tiers, so
 * the prior here is keyed off SourceKind + how many independent sources
 * corroborate it — closer in spirit to the older tweepcred PageRank-over-
 * follows idea, just simplified to a fixed-iteration propagation since a
 * case's source graph is tiny (tens of nodes, not hundreds of millions).
 *
 * This prior is read by PhoenixLite's `corroboration` estimator via
 * caseFile.sources — this class is what KEEPS that data current as new
 * Sources are added mid-case.
 */
object SourceCredibility {

    private val basePrior: Map<SourceKind, Double> = mapOf(
        SourceKind.PUBLIC_RECORD to 0.7,
        SourceKind.THIRD_PARTY_API to 0.5,
        SourceKind.ACTIVE_RECON to 0.4,
        SourceKind.SELF_COLLECTED to 0.3
    )

    /** Fixed-iteration propagation, not a true eigenvector solve — fine at case-graph scale
     *  and avoids pulling in a linear-algebra dependency for a few dozen nodes. */
    fun recompute(sources: List<Source>, iterations: Int = 8): Map<String, Double> {
        val byId = sources.associateBy { it.id }
        var scores = sources.associate { it.id to (basePrior[it.kind] ?: 0.4) }.toMutableMap()

        repeat(iterations) {
            val next = scores.toMutableMap()
            for (source in sources) {
                if (source.corroboratedBy.isEmpty()) continue
                val incoming = source.corroboratedBy.mapNotNull { byId[it] }
                if (incoming.isEmpty()) continue
                val boost = incoming.sumOf { scores[it.id] ?: 0.0 } / incoming.size
                next[source.id] = min(1.0, ((scores[source.id] ?: 0.0) * 0.6) + (boost * 0.4))
            }
            scores = next
        }
        return scores
    }
}
