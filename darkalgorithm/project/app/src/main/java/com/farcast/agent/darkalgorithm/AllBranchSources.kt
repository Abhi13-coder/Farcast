package com.farcast.agent.darkalgorithm

import android.content.Context
import com.farcast.agent.darkalgorithm.features.*
import java.util.UUID

/**
 * Every feature service built in the previous pass, wired into DarkMixer the
 * same way DomainRdapSource/PersonaUsernameSource already were: one LeadSource
 * per branch, each producing Leads that flow through SafetyGate -> PhoenixLite
 * -> selection like everything else. This is what "the dark algorithm adapts
 * to all these features" means concretely — nothing about DarkMixer itself
 * changes; it was already generic over LeadSource. What changed is that now
 * there's one implementation per branch instead of two.
 *
 * Live-data branches (aviation/orbital/quakes/iss/cams) don't carry the same
 * "investigation" framing as domain/persona — there's no suspicious/benign
 * axis to a satellite position. They're still modeled as Leads so they get
 * the same case-DB logging, Tor routing, and (for cams) can feed MomentFinder
 * once a cam snapshot has been through YOLO — see YoloHydrator.
 */

class AviationSource(private val context: Context) : LeadSource {
    override suspend fun fetch(ctx: RunContext): List<Lead> =
        AviationService(context).fetchPlanes(ctx.caseFile.caseId).map { v ->
            Lead(UUID.randomUUID().toString(), ctx.caseFile.caseId, "aviation",
                "${v.label} at ${v.lat}, ${v.lon}", listOf("src-adsb"), System.currentTimeMillis(),
                rawData = mapOf("lat" to v.lat.toString(), "lon" to v.lon.toString()),
                category = LeadCategory.PUBLIC_RECORD)
        }
}

class CelestrakSource(private val context: Context, private val group: String = "visual") : LeadSource {
    override suspend fun fetch(ctx: RunContext): List<Lead> =
        CelestrakService(context).fetchObjects(ctx.caseFile.caseId, group).map { name ->
            Lead(UUID.randomUUID().toString(), ctx.caseFile.caseId, "orbital",
                "Tracked object: $name", listOf("src-celestrak"), System.currentTimeMillis(),
                category = LeadCategory.PUBLIC_RECORD)
        }
}

class QuakeSource(private val context: Context, private val feed: String = "2.5_day") : LeadSource {
    override suspend fun fetch(ctx: RunContext): List<Lead> =
        UsgsQuakeService(context).fetchQuakes(ctx.caseFile.caseId, feed).map { q ->
            Lead(UUID.randomUUID().toString(), ctx.caseFile.caseId, "quakes",
                "M${q.mag} — ${q.place}", listOf("src-usgs"), System.currentTimeMillis(),
                rawData = mapOf("lat" to q.lat.toString(), "lon" to q.lon.toString(), "mag" to q.mag.toString()),
                category = LeadCategory.PUBLIC_RECORD)
        }
}

class IssSource(private val context: Context) : LeadSource {
    override suspend fun fetch(ctx: RunContext): List<Lead> {
        val pos = IssService(context).trackIss(ctx.caseFile.caseId)
        return listOf(
            Lead(UUID.randomUUID().toString(), ctx.caseFile.caseId, "iss",
                "ISS at ${pos.lat}, ${pos.lon} — ${pos.altitudeKm}km, ${pos.velocityKmh}km/h",
                listOf("src-wheretheiss"), System.currentTimeMillis(), category = LeadCategory.PUBLIC_RECORD)
        )
    }
}

/** Cam snapshots are the branch YoloHydrator attaches detections to — each
 *  Lead's rawData carries the still image URL for that. */
class PublicCamsSource(private val context: Context, private val mode: CamMode) : LeadSource {
    enum class CamMode { TFL, CURATED, WINDY }
    override suspend fun fetch(ctx: RunContext): List<Lead> {
        val svc = PublicCamsService(context)
        val cams = when (mode) {
            CamMode.TFL -> svc.londonTfl(ctx.caseFile.caseId)
            CamMode.CURATED -> svc.curatedWorldCams()
            CamMode.WINDY -> emptyList() // needs an API key — call svc.windyGlobal(caseId, key) from UI instead
        }
        return cams.map { c ->
            Lead(UUID.randomUUID().toString(), ctx.caseFile.caseId, "cams",
                "Cam: ${c.name}", listOf("src-cams"), System.currentTimeMillis(),
                rawData = mapOf("imageUrl" to c.imageUrl, "lat" to c.lat.toString(), "lon" to c.lon.toString()),
                category = LeadCategory.PUBLIC_RECORD)
        }
    }
}

class IpThreatSource(private val context: Context, private val ip: String?) : LeadSource {
    override suspend fun fetch(ctx: RunContext): List<Lead> {
        val svc = IpThreatService(context)
        val geo = svc.geo(ctx.caseFile.caseId, ip)
        val (ports, tags) = try { svc.shodanInternetDb(ctx.caseFile.caseId, geo.ip) } catch (_: Exception) { emptyList<String>() to emptyList() }
        return listOf(
            Lead(UUID.randomUUID().toString(), ctx.caseFile.caseId, "ip-threat",
                "${geo.ip} — ${geo.city}, ${geo.country} — ports ${ports.joinToString()} tags ${tags.joinToString()}",
                listOf("src-geojs", "src-shodan"), System.currentTimeMillis(),
                rawData = mapOf("ip" to geo.ip), category = LeadCategory.INFRASTRUCTURE_RECON)
        )
    }
}

/** OPSEC mirror results are about the OPERATOR, never a target — always
 *  SELF_EXPOSURE_AUDIT, which PhoenixLite and SafetyGate both already treat
 *  differently from INFRASTRUCTURE_RECON/PUBLIC_RECORD leads about a target. */
class OpsecMirrorSource(private val context: Context) : LeadSource {
    override suspend fun fetch(ctx: RunContext): List<Lead> {
        val svc = OpsecMirrorService(context)
        val dns = svc.dnsResolverLeakTest(ctx.caseFile.caseId)
        val summary = svc.runAll(ctx.caseFile.caseId, dns)
        return listOf(
            Lead(UUID.randomUUID().toString(), ctx.caseFile.caseId, "opsec-mirror",
                summary, listOf("src-self"), System.currentTimeMillis(),
                category = LeadCategory.SELF_EXPOSURE_AUDIT)
        )
    }
}

fun allBranchSources(context: Context, ip: String? = null): List<LeadSource> = listOf(
    AviationSource(context),
    CelestrakSource(context),
    QuakeSource(context),
    IssSource(context),
    PublicCamsSource(context, PublicCamsSource.CamMode.CURATED),
    IpThreatSource(context, ip),
    OpsecMirrorSource(context)
)
