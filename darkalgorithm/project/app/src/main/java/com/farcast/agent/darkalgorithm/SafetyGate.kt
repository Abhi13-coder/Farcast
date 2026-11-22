package com.farcast.agent.darkalgorithm

/**
 * SafetyGate — ported from x-algorithm's Post-Selection Filtering stage
 * (the layer that strips spam/violence/gore/deleted posts right before the
 * ranked feed is returned) and its abuse-enforcement-service (category
 * classification + policy enforcement).
 *
 * This is the literal, non-negotiable implementation of the spec's rule:
 *   "Do not implement functionality that converts personal information about
 *    an individual into targeted intrusion, account compromise, physical
 *    tracking, or surveillance instructions."
 *
 * It runs twice in the pipeline, same as X runs filtering both pre-scoring
 * (cheap, catches obvious cases early) and post-selection (catches anything
 * that slipped through, right before output) — see DarkMixer.
 *
 * This is a LeadFilter: deterministic, auditable, never model-based. A Lead
 * that matches a rule is reclassified FLAGGED and hard-excluded — it is never
 * auto-explored, never included in an Evidence export, and never passed to a
 * plugin. A human can still review FLAGGED leads manually in the UI; the gate
 * blocks AUTOMATED pipeline action on them, not visibility of the raw input.
 */
object SafetyGate : LeadFilter {

    /**
     * Each rule targets the ACTION a lead's text implies, not the data it was
     * built from. Looking up a public DNS/RDAP record is fine; a lead whose
     * summary reads like an instruction to act on a specific individual
     * ("call [number] and pretend to be...", "go to [address] and...",
     * "attempt login with...") is not, regardless of which branch produced it.
     */
    private val disallowedPatterns: List<Regex> = listOf(
        Regex("""\b(brute[- ]?force|credential[- ]?stuff|password[- ]?spray)\b""", RegexOption.IGNORE_CASE),
        Regex("""\battempt (to )?(log ?in|sign ?in|access) (as|with|using)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(physically )?(follow|tail|stake ?out|show up at|intercept)\b.*\b(person|individual|target|them|him|her)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(swat|dox|doxx)\w*\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(sim[- ]?swap|social engineer|impersonate)\b.*\b(carrier|bank|support|victim)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bexploit\b.*\b(cve|vulnerab|rce|injection)\b""", RegexOption.IGNORE_CASE),
        Regex("""\binstall(ed)? (spyware|stalkerware|keylogger)\b""", RegexOption.IGNORE_CASE)
    )

    /**
     * Scope check: a lead whose branch is INFRASTRUCTURE_RECON must stay
     * inside the case's own stated authorized scope. This doesn't try to be
     * clever about parsing the scope string — it's a simple substring/domain
     * containment check, intentionally conservative: anything ambiguous gets
     * rejected rather than guessed into "probably fine."
     */
    private fun withinAuthorizedScope(lead: Lead, ctx: RunContext): Boolean {
        if (lead.category != LeadCategory.INFRASTRUCTURE_RECON) return true
        val scope = ctx.caseFile.ownerAuthorizedScope.trim()
        if (scope.isEmpty()) return false
        val target = lead.rawData["target"] ?: return false
        return target.contains(scope, ignoreCase = true) || scope.contains(target, ignoreCase = true)
    }

    override fun test(lead: Lead, ctx: RunContext): FilterResult {
        for (pattern in disallowedPatterns) {
            if (pattern.containsMatchIn(lead.summary)) {
                lead.category = LeadCategory.FLAGGED
                lead.status = LeadStatus.BLOCKED
                lead.blockReason = "matched disallowed-action pattern: ${pattern.pattern}"
                return FilterResult.Reject(lead.blockReason!!)
            }
        }
        if (!withinAuthorizedScope(lead, ctx)) {
            lead.category = LeadCategory.FLAGGED
            lead.status = LeadStatus.BLOCKED
            lead.blockReason = "infrastructure-recon target outside caseFile.ownerAuthorizedScope"
            return FilterResult.Reject(lead.blockReason!!)
        }
        return FilterResult.Pass
    }
}
