package com.farcast.agent.darkalgorithm

import kotlinx.serialization.Serializable

/**
 * DARK ALGORITHM data model — ported from the role "Post" plays in X's
 * xai-org/x-algorithm Candidate Pipeline. A Lead is the unit that flows
 * through Source -> Hydrator -> Filter -> Scorer -> Selector -> SideEffect,
 * exactly like a Post does in Home Mixer.
 */
@Serializable
data class Lead(
    val id: String,
    val caseId: String,
    val branch: String,                 // which FARCAST module produced it: "domain","persona","geo","cam"...
    val summary: String,
    val sourceIds: List<String>,
    val createdAtEpochMs: Long,
    val rawData: Map<String, String> = emptyMap(),
    val parentLeadId: String? = null,   // non-null if spawned while exploring another lead (branch expansion)
    var category: LeadCategory = LeadCategory.UNCLASSIFIED,

    // Hydrated/scored progressively by the pipeline — mirrors Phoenix's per-action
    // probabilities before they're weight-summed into Post.score in x-algorithm.
    var probabilities: MutableMap<String, Double> = mutableMapOf(),
    var score: Double? = null,
    var status: LeadStatus = LeadStatus.PENDING,
    var blockReason: String? = null
)

enum class LeadCategory {
    UNCLASSIFIED,
    PUBLIC_RECORD,          // RDAP/WHOIS, DNS, CT logs, public cams — passive, already-public data
    SELF_EXPOSURE_AUDIT,    // the OPSEC-mirror "turn the lens on yourself" findings
    INFRASTRUCTURE_RECON,   // domain/IP/cert recon on infra the case owner controls or is authorized on
    FLAGGED                 // matched a SafetyGate rule — never auto-explored further, surfaced for human review only
}

enum class LeadStatus { PENDING, HYDRATED, SCORED, SELECTED, EXPLORED, BLOCKED, DISCARDED, CANCELLED }

/** A data source a Lead cites. Mirrors the account-level prior ("base score") in x-algorithm. */
@Serializable
data class Source(
    val id: String,
    val name: String,          // "RDAP", "crt.sh", "Sherlock", "Windy Webcams"...
    val kind: SourceKind,
    val corroboratedBy: List<String> = emptyList() // ids of other Sources agreeing with this one
)

enum class SourceKind { PUBLIC_RECORD, ACTIVE_RECON, THIRD_PARTY_API, SELF_COLLECTED }

/**
 * What the operator actually knows about a target going in — the "generate
 * JSON" feature's input. Every non-empty field tells
 * PluginAwarePipeline/CaseFileBuilder which branches are worth activating:
 * a domain present means run DomainRdapSource, a username means run
 * PersonaUsernameSource, and so on — this is what makes a generated case
 * file self-describing enough for DarkMixer to run autonomously off of it
 * with no further manual wiring once it's fed in.
 */
@Serializable
data class TargetProfile(
    val domain: String? = null,
    val usernames: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val ips: List<String> = emptyList(),
    val freeTextQuery: String? = null,   // fed to SearchBranchSource (name, alias, phrase)
    val notes: String = ""
)

/** One case = one investigation. Mirrors a single user's request context in Home Mixer,
 *  but persists across many pipeline runs (FARCAST's local SQLite case DB). */
@Serializable
data class CaseFile(
    val caseId: String,
    val title: String,
    val ownerAuthorizedScope: String,   // operator's own statement of what they're authorized to examine
    val targetProfile: TargetProfile? = null,
    val leads: MutableList<Lead> = mutableListOf(),
    val sources: MutableList<Source> = mutableListOf(),
    val evidence: MutableList<Evidence> = mutableListOf()
)

/** What actually gets written to the case DB / JSON export once a Lead clears the pipeline. */
@Serializable
data class Evidence(
    val id: String,
    val leadId: String,
    val finding: String,
    val confidence: Double,
    val producedAtEpochMs: Long
)
