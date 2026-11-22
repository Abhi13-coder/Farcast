package com.farcast.agent.darkalgorithm

/**
 * Ported directly from xai-org/x-algorithm's `candidate-pipeline` crate: the
 * six abstract stages that Home Mixer composes to go from a raw request to a
 * ranked response (Source -> Hydrator -> Filter -> Scorer -> Selector -> SideEffect).
 *
 * X built this as a reusable framework precisely so it ISN'T Twitter-specific —
 * that's what we're porting here. Nothing below predicts tweet engagement;
 * these are the same six roles doing investigation work instead of feed-ranking work.
 */

/** Context threaded through one pipeline run — the DarkAlgorithm equivalent of
 *  the hydrated "viewer context" Home Mixer builds at the start of a request. */
data class RunContext(
    val caseFile: CaseFile,
    val cancelled: () -> Boolean   // polled between stages for cooperative cancellation/recovery
)

/** Produces raw candidate Leads. Mirrors X's Thunder (in-network) / ML-discovery (out-of-network)
 *  sources — one branch generator per FARCAST feature module (geo, domain, persona, cam, ...). */
fun interface LeadSource {
    suspend fun fetch(ctx: RunContext): List<Lead>
}

/** Attaches extra data a Scorer will need — e.g. resolving a Source's corroboration graph,
 *  or fetching the parent Lead's probabilities for branch-expansion leads. */
fun interface LeadHydrator {
    suspend fun hydrate(lead: Lead, ctx: RunContext): Lead
}

/** Cheap pass/fail gate run before expensive scoring — the DarkAlgorithm analogue of
 *  X's pre-ranking filters (dedup, policy, eligibility) that run before Phoenix is invoked
 *  at all. A Filter here can also *classify+block*, which is how SafetyGate plugs in. */
fun interface LeadFilter {
    fun test(lead: Lead, ctx: RunContext): FilterResult
}

sealed class FilterResult {
    data object Pass : FilterResult()
    data class Reject(val reason: String) : FilterResult()
}

/** Assigns probabilities + a final weighted score. Mirrors Phoenix: predict several
 *  independent probabilities, then weight-sum into Lead.score. */
fun interface LeadScorer {
    suspend fun score(lead: Lead, ctx: RunContext): Lead
}

/** Sorts + truncates to the leads actually worth queuing for exploration this run.
 *  Mirrors X's final "sort by score, take top K" Selection stage. */
fun interface LeadSelector {
    fun select(scored: List<Lead>, limit: Int): List<Lead>
}

/** Fire-and-forget work after selection — logging, case-DB writes, metrics.
 *  Mirrors X's SideEffect stage (impression logging, etc.); never blocks the response. */
fun interface LeadSideEffect {
    suspend fun run(selected: List<Lead>, ctx: RunContext)
}
