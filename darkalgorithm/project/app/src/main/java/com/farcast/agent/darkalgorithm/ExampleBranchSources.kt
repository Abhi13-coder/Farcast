package com.farcast.agent.darkalgorithm

import android.content.Context
import java.util.UUID

/**
 * Shows how an EXISTING FARCAST feature (Section 3.A) becomes a LeadSource —
 * the actual integration point DarkMixer needs from the rest of the app.
 * These two are stand-ins for the real DOMAIN/RDAP and PERSONA network calls
 * already implemented in the website (see index.html #p-dom, #p-ps): swap the
 * TODO-marked body for the real Retrofit/Ktor call and everything downstream
 * (hydrate/filter/score/select) works unchanged.
 *
 * Every LeadSource gets its OkHttpClient from TorEnforcedHttpClient instead of
 * building its own — that's the one-line change that puts a branch's traffic
 * through Tor with its own isolated circuit. If Orbot isn't running this
 * throws rather than quietly going out over clear-net.
 */

class DomainRdapSource(
    private val context: Context,
    private val targetDomain: String
) : LeadSource {
    override suspend fun fetch(ctx: RunContext): List<Lead> {
        val rdap = com.farcast.agent.darkalgorithm.features.DomainIntelService(context)
            .rdap(ctx.caseFile.caseId, targetDomain)
        val finding = "RDAP: ${rdap.ldhName} — status ${rdap.status.joinToString()} — NS ${rdap.nameservers.joinToString()}"
        return listOf(
            Lead(
                id = UUID.randomUUID().toString(),
                caseId = ctx.caseFile.caseId,
                branch = "domain",
                summary = finding,
                sourceIds = listOf("src-rdap"),
                createdAtEpochMs = System.currentTimeMillis(),
                rawData = mapOf("target" to targetDomain),
                category = LeadCategory.INFRASTRUCTURE_RECON
            )
        )
    }
}

class PersonaUsernameSource(
    private val context: Context,
    private val handle: String
) : LeadSource {
    /** Runs the fast "quick 7" per DarkMixer pass. The full Sherlock (400+ sites)
     *  and Maigret (500+ sites) scans are deliberately NOT run here — they take
     *  tens of seconds to minutes even at concurrency 15, same as on the website,
     *  and belong behind their own explicit UI action (as separate buttons on
     *  the site), not silently re-run on every pipeline tick. Call
     *  PersonaIntelService.sherlockScan()/maigretScan() directly from that UI action. */
    override suspend fun fetch(ctx: RunContext): List<Lead> {
        val hits = com.farcast.agent.darkalgorithm.features.PersonaIntelService(context)
            .quickSeven(ctx.caseFile.caseId, handle)
        if (hits.isEmpty()) return emptyList()
        return listOf(
            Lead(
                id = UUID.randomUUID().toString(),
                caseId = ctx.caseFile.caseId,
                branch = "persona",
                summary = "Quick-7 hits for $handle: " + hits.joinToString { "${it.site} (${it.detail})" },
                sourceIds = listOf("src-sherlock"),
                createdAtEpochMs = System.currentTimeMillis(),
                rawData = mapOf("handle" to handle),
                category = LeadCategory.PUBLIC_RECORD
            )
        )
    }
}

/** Default Source list wired for a case — extend as more branches are ported. */
fun defaultSourcesFor(context: Context, domain: String?, handle: String?): List<LeadSource> =
    listOfNotNull(
        domain?.let { DomainRdapSource(context, it) },
        handle?.let { PersonaUsernameSource(context, it) }
    )
