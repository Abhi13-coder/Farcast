package com.farcast.agent.darkalgorithm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PhoenixLiteTest {

    private fun ctx(caseFile: CaseFile = CaseFile("c1", "t", "example.com")) =
        RunContext(caseFile) { false }

    private fun lead(category: LeadCategory = LeadCategory.PUBLIC_RECORD) = Lead(
        id = "l1", caseId = "c1", branch = "domain", summary = "a fresh unrelated finding about something new",
        sourceIds = emptyList(), createdAtEpochMs = 0L, category = category
    )

    @Test
    fun `score is the declared weighted sum of the estimators, not something else`() = runTest {
        val estimators = mapOf<String, ProbabilityEstimator>(
            "a" to ProbabilityEstimator { _, _ -> 1.0 },
            "b" to ProbabilityEstimator { _, _ -> 0.5 }
        )
        val weights = mapOf("a" to 2.0, "b" to -1.0)
        val scorer = PhoenixLite(estimators, weights)

        val scored = scorer.score(lead(), ctx())

        // expected = 2.0*1.0 + (-1.0)*0.5 = 1.5
        assertEquals(1.5, scored.score!!, 1e-9)
        assertEquals(LeadStatus.SCORED, scored.status)
    }

    @Test
    fun `a FLAGGED lead's riskOfMisuse estimator returns 1_0, pulling score down via its negative weight`() = runTest {
        val scorer = DefaultPhoenixLite.build()
        val flagged = lead(category = LeadCategory.FLAGGED)
        val scored = scorer.score(flagged, ctx())
        assertEquals(1.0, scored.probabilities["riskOfMisuse"]!!, 1e-9)
        // riskOfMisuse's weight is sharply negative (-5.0) — its presence
        // should make a FLAGGED lead's score clearly lower than a PUBLIC_RECORD
        // lead with otherwise-identical inputs.
        val benignScored = scorer.score(lead(category = LeadCategory.PUBLIC_RECORD), ctx())
        assertTrue(scored.score!! < benignScored.score!!)
    }

    @Test
    fun `candidate isolation - scoring one lead does not read or mutate another lead in the case`() = runTest {
        val caseFile = CaseFile("c1", "t", "example.com")
        val leadA = lead().copy(id = "a")
        val leadB = lead().copy(id = "b")
        caseFile.leads += listOf(leadA, leadB)

        val scorer = DefaultPhoenixLite.build()
        val scoredA = scorer.score(leadA, ctx(caseFile))

        assertEquals("b", leadB.id) // leadB untouched
        assertNull(leadB.score)     // scoring A never assigned a score to B
    }
}
