package com.farcast.agent.darkalgorithm

/**
 * PhoenixLite — the DarkAlgorithm analogue of X's Phoenix model.
 *
 * Phoenix (real): a Grok-architecture transformer predicts P(like), P(reply),
 * P(repost), P(negative feedback)... per post, then a fixed set of weights in
 * code (xai-value-model/scoring.rs) combines them into one score. "Candidate
 * isolation" means a post's prediction can't leak information from other posts
 * in the same batch.
 *
 * PhoenixLite (this): no transformer, no GPU — this has to run on a low-memory
 * ARM32 phone per your Android-compatibility requirements (Section 6). Instead
 * of learned weights it uses declarative heuristic estimators per probability,
 * each pluggable/swappable, combined the exact same way Phoenix combines its
 * predictions: independent probabilities -> fixed weighted sum -> one score.
 * A learned model can be swapped in later as a plugin (Section 4) without
 * touching the weighting logic.
 */
class PhoenixLite(
    private val estimators: Map<String, ProbabilityEstimator>,
    private val weights: Map<String, Double>
) : LeadScorer {

    override suspend fun score(lead: Lead, ctx: RunContext): Lead {
        // Candidate isolation: each estimator sees only this one Lead + static
        // case context, never the batch — so leads can be scored in parallel
        // with no cross-contamination, same guarantee Phoenix makes.
        val probs = estimators.mapValues { (_, estimator) -> estimator.estimate(lead, ctx) }
        lead.probabilities.putAll(probs)

        val total = weights.entries.sumOf { (key, weight) -> weight * (probs[key] ?: 0.0) }
        lead.score = total
        lead.status = LeadStatus.SCORED
        return lead
    }
}

fun interface ProbabilityEstimator {
    fun estimate(lead: Lead, ctx: RunContext): Double  // must return a value in [0.0, 1.0]
}

/** Default estimator set + weights for DARK ALGORITHM. Swap/extend via plugins per Section 4. */
object DefaultPhoenixLite {

    /** P(this lead introduces information not already covered by an existing Evidence row). */
    private val novelty = ProbabilityEstimator { lead, ctx ->
        val existing = ctx.caseFile.evidence.map { it.finding.lowercase() }
        val tokens = lead.summary.lowercase().split(Regex("\\W+")).filter { it.length > 3 }.toSet()
        if (tokens.isEmpty()) return@ProbabilityEstimator 0.0
        val overlap = existing.count { finding -> tokens.any { finding.contains(it) } }
        (1.0 - overlap.toDouble() / existing.size.coerceAtLeast(1)).coerceIn(0.0, 1.0)
    }

    /** P(multiple independent sources agree), from the SourceCredibility corroboration graph. */
    private val corroboration = ProbabilityEstimator { lead, ctx ->
        val sources = ctx.caseFile.sources.filter { it.id in lead.sourceIds }
        if (sources.isEmpty()) return@ProbabilityEstimator 0.0
        val corroborated = sources.count { it.corroboratedBy.isNotEmpty() }
        (corroborated.toDouble() / sources.size).coerceIn(0.0, 1.0)
    }

    /** P(this lead, if explored, yields exportable Evidence rather than a dead end) —
     *  proxied by category + branch track record within this case. */
    private val actionability = ProbabilityEstimator { lead, ctx ->
        val base = when (lead.category) {
            LeadCategory.PUBLIC_RECORD -> 0.7
            LeadCategory.INFRASTRUCTURE_RECON -> 0.6
            LeadCategory.SELF_EXPOSURE_AUDIT -> 0.65
            LeadCategory.UNCLASSIFIED -> 0.3
            LeadCategory.FLAGGED -> 0.0
        }
        val priorSuccesses = ctx.caseFile.evidence.count { ev ->
            ctx.caseFile.leads.find { it.id == ev.leadId }?.branch == lead.branch
        }
        val priorTotal = ctx.caseFile.leads.count { it.branch == lead.branch }.coerceAtLeast(1)
        val trackRecord = priorSuccesses.toDouble() / priorTotal
        (0.5 * base + 0.5 * trackRecord).coerceIn(0.0, 1.0)
    }

    /** P(exploring this lead further would itself cross into disallowed territory).
     *  Mirrors X's "negative feedback" probability — a high value here pulls the score DOWN. */
    private val riskOfMisuse = ProbabilityEstimator { lead, _ ->
        if (lead.category == LeadCategory.FLAGGED) 1.0 else 0.0
    }

    val estimators: Map<String, ProbabilityEstimator> = mapOf(
        "novelty" to novelty,
        "corroboration" to corroboration,
        "actionability" to actionability,
        "riskOfMisuse" to riskOfMisuse
    )

    // Positive signals pull the score up; riskOfMisuse is weighted sharply negative,
    // the same way X down-weights predicted negative feedback.
    val weights: Map<String, Double> = mapOf(
        "novelty" to 0.30,
        "corroboration" to 0.30,
        "actionability" to 0.40,
        "riskOfMisuse" to -5.0
    )

    fun build() = PhoenixLite(estimators, weights)
}
