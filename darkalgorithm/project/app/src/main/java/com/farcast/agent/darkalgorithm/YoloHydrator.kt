package com.farcast.agent.darkalgorithm

import android.content.Context
import com.farcast.agent.darkalgorithm.features.MomentFinderService
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import okhttp3.Request
import java.io.File

/**
 * Plugs Moment Finder into DarkMixer as a Hydrator — the stage that runs
 * between Source and Scorer. Any Lead carrying rawData["imageUrl"] (cam
 * snapshots from PublicCamsSource, for instance) gets downloaded, run
 * through YOLO, and its detected labels appended to the Lead's own summary
 * — which means PhoenixLite's novelty/actionability estimators see them too,
 * same as any other text in a Lead. Nothing about DarkMixer changed to
 * support this; LeadHydrator was already a generic stage.
 *
 * Fails soft: no image, model not loaded yet, or a download error just
 * means the Lead passes through unmodified — auto-labeling is an
 * enhancement to a Lead, never a requirement for it to flow through the
 * pipeline (a cam snapshot FARCAST couldn't fetch is still a valid Lead).
 */
class YoloHydrator(
    private val context: Context,
    private val momentFinder: MomentFinderService,
    private val imageCacheDir: File
) : LeadHydrator {

    override suspend fun hydrate(lead: Lead, ctx: RunContext): Lead {
        val imageUrl = lead.rawData["imageUrl"] ?: return lead
        val localFile = runCatching { downloadImage(ctx.caseFile.caseId, imageUrl) }.getOrNull() ?: return lead

        val detections = runCatching {
            momentFinder.processImage(lead.caseId, lead.id, localFile)
        }.getOrNull() ?: return lead

        if (detections.isEmpty()) return lead
        val labelSummary = detections.joinToString { "${it.label} (${(it.confidence * 100).toInt()}%)" }
        return lead.copy(summary = "${lead.summary} — detected: $labelSummary")
    }

    /** Routed through the same Tor-isolated "cams" circuit the cam Lead
     *  itself came from — an image download is still network activity that
     *  should stay inside the privacy module's guarantees, not bypass them. */
    private suspend fun downloadImage(caseId: String, url: String): File {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "cams")
        val file = File(imageCacheDir, url.hashCode().toString() + ".jpg")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body ?: error("empty image response")
            file.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        return file
    }
}
