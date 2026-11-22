package com.farcast.agent.darkalgorithm.features

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.json.*
import java.io.File

/**
 * "Search by image" — the spec's PERSONA/DOMAIN panels only ever took text
 * input; this is new. Reverse image search as a category has a real
 * limitation worth being upfront about: there's no free, officially-
 * sanctioned, general-purpose reverse-image-search API. What's actually
 * available:
 *
 *  1. LOCAL YOLO LABELING (always on, needs no key, no network call to a
 *     third party at all) — MomentFinderService already does this. Running
 *     it on the query image and turning the detected labels into a SearXNG/
 *     Ahmia text query is a real, working way to go from "image in" to
 *     "search results out" without touching anyone's reverse-image API.
 *  2. GOOGLE CLOUD VISION WEB DETECTION — official, documented, keyed, paid
 *     per use (not free, but it's a real sanctioned API, same shape as
 *     urlscan/AbuseIPDB elsewhere in this app: user supplies their own key
 *     via SecureKeyVault). Finds actual pages containing a visually similar
 *     or identical image — the thing people usually mean by "reverse image
 *     search."
 *  3. Nothing else wired here. TinEye has a real API but its request-signing
 *     scheme needs verifying against TinEye's current docs before trusting
 *     a from-memory implementation of it; Yandex's reverse-image search has
 *     no public API at all, only an HTML page to scrape — same ToS-gray
 *     territory as DuckDuckGo's HTML endpoint, not added here to avoid
 *     quietly duplicating that problem a second time. Add either later the
 *     same way DuckDuckGoSearchService.htmlSearchScrape() was: real, but
 *     hard-gated behind an explicit opt-in with the caveat shown in the UI.
 */
class ImageQueryService(private val context: Context) {

    data class ImageSearchResult(
        val detectedLabels: List<String>,          // from local YOLO — always populated if a model is loaded
        val suggestedTextQuery: String,            // labels joined, ready to hand to SearxngSearchService/Ahmia
        val matchingPages: List<MatchingPage>       // only populated if a Vision API key was supplied
    )

    data class MatchingPage(val url: String, val pageTitle: String?)

    suspend fun searchByImage(
        caseId: String,
        imageFile: File,
        momentFinder: MomentFinderService,
        googleVisionApiKey: String? = null
    ): ImageSearchResult {
        // Step 1 — always: local YOLO, free, no network call to a third party.
        val detections = runCatching {
            momentFinder.processImage(caseId, leadId = "query-image-${System.currentTimeMillis()}", imageFile)
        }.getOrElse { emptyList() }
        val labels = detections.map { it.label }.distinct()

        // Step 2 — optional: Google Cloud Vision Web Detection, only if the
        // user supplied their own key (SecureKeyVault), same pattern as
        // urlscan/AbuseIPDB — never called with a baked-in key.
        val matches = if (googleVisionApiKey != null) {
            runCatching { googleVisionWebDetection(caseId, imageFile, googleVisionApiKey) }.getOrElse { emptyList() }
        } else emptyList()

        return ImageSearchResult(
            detectedLabels = labels,
            suggestedTextQuery = labels.joinToString(" "),
            matchingPages = matches
        )
    }

    private suspend fun googleVisionWebDetection(caseId: String, imageFile: File, apiKey: String): List<MatchingPage> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "image-search")
        val base64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
        val body = buildJsonObject {
            put("requests", buildJsonArray {
                addJsonObject {
                    put("image", buildJsonObject { put("content", base64) })
                    put("features", buildJsonArray { addJsonObject { put("type", "WEB_DETECTION") } })
                }
            })
        }.toString()

        val url = "https://vision.googleapis.com/v1/images:annotate?key=$apiKey"
        val response = FarcastHttp.postJson(client, url, body).jsonObject
        val webDetection = response["responses"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("webDetection")?.jsonObject ?: return emptyList()

        val pages = (webDetection["pagesWithMatchingImages"]?.jsonArray ?: JsonArray(emptyList())) +
            (webDetection["fullMatchingImages"]?.jsonArray ?: JsonArray(emptyList()))

        return pages.mapNotNull { p ->
            val url_ = p.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            MatchingPage(url_, p.jsonObject["pageTitle"]?.jsonPrimitive?.contentOrNull)
        }.distinctBy { it.url }
    }
}
