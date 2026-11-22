package com.farcast.agent.darkalgorithm

/**
 * "Generate JSON": turn a few typed-in facts about a target into a complete,
 * valid CaseFile — the thing a "Generate JSON" button in the UI calls, and
 * what DarkMixer.exportCase() then turns into text for the "feed JSON"
 * button to pick back up (possibly after the operator reviews/edits it).
 *
 * Deliberately asks for ownerAuthorizedScope up front rather than defaulting
 * it to the domain/target itself — SafetyGate checks a lead's target against
 * this field for every INFRASTRUCTURE_RECON lead, so an empty or wrong scope
 * here means real leads get rejected later, not a formality to skip.
 */
object CaseFileBuilder {

    fun generate(
        caseId: String,
        title: String,
        ownerAuthorizedScope: String,
        profile: TargetProfile
    ): CaseFile {
        val sources = buildList {
            if (profile.domain != null) {
                add(Source("src-rdap", "RDAP", SourceKind.PUBLIC_RECORD))
                add(Source("src-crtsh", "crt.sh", SourceKind.PUBLIC_RECORD))
            }
            if (profile.usernames.isNotEmpty()) {
                add(Source("src-sherlock", "Sherlock", SourceKind.THIRD_PARTY_API, corroboratedBy = listOfNotNull(
                    if (profile.domain != null) "src-rdap" else null
                )))
            }
            if (profile.ips.isNotEmpty()) {
                add(Source("src-geojs", "GeoJS", SourceKind.PUBLIC_RECORD))
                add(Source("src-shodan", "Shodan InternetDB", SourceKind.THIRD_PARTY_API))
            }
            if (profile.freeTextQuery != null) {
                add(Source("src-searxng", "SearXNG", SourceKind.THIRD_PARTY_API))
                add(Source("src-ahmia", "Ahmia", SourceKind.THIRD_PARTY_API))
            }
        }

        return CaseFile(
            caseId = caseId,
            title = title,
            ownerAuthorizedScope = ownerAuthorizedScope,
            targetProfile = profile,
            sources = sources.toMutableList()
        )
    }
}
