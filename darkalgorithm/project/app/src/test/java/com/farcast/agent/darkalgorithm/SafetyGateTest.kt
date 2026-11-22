package com.farcast.agent.darkalgorithm

import org.junit.Assert.*
import org.junit.Test

class SafetyGateTest {

    private fun ctx(scope: String = "example.com") = RunContext(
        CaseFile("c1", "test case", ownerAuthorizedScope = scope),
        cancelled = { false }
    )

    private fun lead(summary: String, category: LeadCategory = LeadCategory.PUBLIC_RECORD, target: String? = null) = Lead(
        id = "l1", caseId = "c1", branch = "test", summary = summary,
        sourceIds = emptyList(), createdAtEpochMs = 0L,
        rawData = target?.let { mapOf("target" to it) } ?: emptyMap(),
        category = category
    )

    @Test
    fun `benign public-record lead passes`() {
        val result = SafetyGate.test(lead("RDAP lookup for example.com returned 3 nameservers"), ctx())
        assertTrue(result is FilterResult.Pass)
    }

    @Test
    fun `credential stuffing instruction is rejected`() {
        val result = SafetyGate.test(lead("attempt to login with leaked credentials against target"), ctx())
        assertTrue(result is FilterResult.Reject)
    }

    @Test
    fun `doxxing language is rejected`() {
        val result = SafetyGate.test(lead("plan to dox this individual using the collected data"), ctx())
        assertTrue(result is FilterResult.Reject)
    }

    @Test
    fun `physical tracking instruction is rejected`() {
        val result = SafetyGate.test(lead("follow the target in person to confirm their daily routine"), ctx())
        assertTrue(result is FilterResult.Reject)
    }

    @Test
    fun `infrastructure recon within authorized scope passes`() {
        val l = lead("port scan result for example.com", category = LeadCategory.INFRASTRUCTURE_RECON, target = "example.com")
        assertTrue(SafetyGate.test(l, ctx(scope = "example.com")) is FilterResult.Pass)
    }

    @Test
    fun `infrastructure recon outside authorized scope is rejected`() {
        val l = lead("port scan result for unrelated-target.com", category = LeadCategory.INFRASTRUCTURE_RECON, target = "unrelated-target.com")
        assertTrue(SafetyGate.test(l, ctx(scope = "example.com")) is FilterResult.Reject)
    }

    @Test
    fun `infrastructure recon with no declared scope is rejected, never defaults to allowed`() {
        val l = lead("port scan result", category = LeadCategory.INFRASTRUCTURE_RECON, target = "example.com")
        assertTrue(SafetyGate.test(l, ctx(scope = "")) is FilterResult.Reject)
    }

    @Test
    fun `a rejected lead is actually marked FLAGGED and BLOCKED, not just returned Reject`() {
        val l = lead("attempt to login with stolen credentials")
        SafetyGate.test(l, ctx())
        assertEquals(LeadCategory.FLAGGED, l.category)
        assertEquals(LeadStatus.BLOCKED, l.status)
        assertNotNull(l.blockReason)
    }
}
