package com.farcast.agent.darkalgorithm

import org.junit.Assert.*
import org.junit.Test

class CaseFileBuilderTest {

    @Test
    fun `domain-only profile seeds RDAP and crt sh sources, nothing persona-related`() {
        val case = CaseFileBuilder.generate("c1", "t", "example.com", TargetProfile(domain = "example.com"))
        val ids = case.sources.map { it.id }
        assertTrue("src-rdap" in ids)
        assertTrue("src-crtsh" in ids)
        assertFalse("src-sherlock" in ids)
    }

    @Test
    fun `username-only profile seeds sherlock, nothing domain-related`() {
        val case = CaseFileBuilder.generate("c1", "t", "example.com", TargetProfile(usernames = listOf("someuser")))
        val ids = case.sources.map { it.id }
        assertTrue("src-sherlock" in ids)
        assertFalse("src-rdap" in ids)
    }

    @Test
    fun `empty profile seeds no sources`() {
        val case = CaseFileBuilder.generate("c1", "t", "example.com", TargetProfile())
        assertTrue(case.sources.isEmpty())
    }

    @Test
    fun `the generated CaseFile carries the target profile forward for the autonomous runner to read`() {
        val profile = TargetProfile(domain = "example.com", usernames = listOf("u1"))
        val case = CaseFileBuilder.generate("c1", "t", "example.com", profile)
        assertEquals(profile, case.targetProfile)
    }

    @Test
    fun `exported json round-trips back into an equivalent CaseFile`() {
        val original = CaseFileBuilder.generate("c1", "t", "example.com", TargetProfile(domain = "example.com"))
        val json = DarkMixer.exportCase(original)
        val reimported = DarkMixer.importCase(json)
        assertEquals(original.caseId, reimported.caseId)
        assertEquals(original.targetProfile, reimported.targetProfile)
        assertEquals(original.sources.map { it.id }, reimported.sources.map { it.id })
    }
}
