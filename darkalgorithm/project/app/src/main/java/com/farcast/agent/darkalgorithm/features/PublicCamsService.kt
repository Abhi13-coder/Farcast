package com.farcast.agent.darkalgorithm.features

import android.content.Context
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.json.*

data class PublicCam(val lat: Double, val lon: Double, val name: String, val imageUrl: String, val countryCode: String)

/** #p-cam */
class PublicCamsService(private val context: Context) {

    suspend fun londonTfl(caseId: String, limit: Int = 60): List<PublicCam> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "cams")
        val el = FarcastHttp.getJson(client, "https://api.tfl.gov.uk/Place/Type/JamCam")
        return el.jsonArray.take(limit).mapNotNull { p ->
            val o = p.jsonObject
            val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val imageUrl = o["additionalProperties"]?.jsonArray
                ?.firstOrNull { it.jsonObject["key"]?.jsonPrimitive?.contentOrNull == "imageUrl" }
                ?.jsonObject?.get("value")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            PublicCam(lat, lon, o["commonName"]?.jsonPrimitive?.contentOrNull ?: "TfL cam", imageUrl, "GB")
        }
    }

    /** The website hardcodes this list (see index.html ~line 757) rather than
     *  calling an API for it — ported verbatim, same curated set. */
    fun curatedWorldCams(): List<PublicCam> = listOf(
        PublicCam(51.5074, -0.1278, "UK London Trafalgar", "https://s3-eu-west-1.amazonaws.com/jamcams.tfl.gov.uk/00001.01253.jpg", "GB"),
        PublicCam(51.5155, -0.0922, "UK London Bank", "https://s3-eu-west-1.amazonaws.com/jamcams.tfl.gov.uk/00001.04532.jpg", "GB"),
        PublicCam(34.1583, -118.4686, "US CA US-101 Ventura", "https://cwwp2.dot.ca.gov/data/d7/cctv/image/us101atventurabl/us101atventurabl.jpg", "US"),
        PublicCam(37.8199, -122.4783, "US CA SF Bay Bridge", "https://cwwp2.dot.ca.gov/data/d4/cctv/image/sfbaybridge/sfbaybridge.jpg", "US"),
        PublicCam(47.6205, -122.3493, "US WA Seattle I-5", "https://images.wsdot.wa.gov/nw/005vc16508.jpg", "US"),
        PublicCam(60.1699, 24.9384, "FI Helsinki", "https://weathercam.digitraffic.fi/C04507.jpg", "FI"),
        PublicCam(48.2082, 16.3738, "AT Vienna", "https://www.asfinag.at/media/verkehrskameras/wien.jpg", "AT")
        // Full list in the source has ~20 entries; trimmed here to the ones
        // with stable, currently-working image URLs — several of the
        // site's entries (Rome/Paris/Singapore/Dubai/Sao Paulo/Atlanta) are
        // placeholder "---" URLs in the original that were never live, which
        // I'm not porting verbatim just to pad the count.
    )

    suspend fun windyClusters(caseId: String, apiKey: String, north: Double, south: Double, east: Double, west: Double): List<PublicCam> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "cams")
        val url = "https://api.windy.com/webcams/api/v3/map/clusters?north=$north&south=$south&east=$east&west=$west&limit=8"
        val el = FarcastHttp.getJson(client, url, mapOf("x-windy-api-key" to apiKey))
        // Windy's cluster response shape nests webcams per cluster cell; callers
        // needing individual webcam detail should follow up with the
        // /webcams/api/v3/webcams?limit=... endpoint, also wired below.
        return parseWindy(el)
    }

    suspend fun windyGlobal(caseId: String, apiKey: String, limit: Int = 20): List<PublicCam> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "cams")
        val url = "https://api.windy.com/webcams/api/v3/webcams?limit=$limit&offset=0"
        val el = FarcastHttp.getJson(client, url, mapOf("x-windy-api-key" to apiKey))
        return parseWindy(el)
    }

    private fun parseWindy(el: JsonElement): List<PublicCam> =
        (el.jsonObject["webcams"]?.jsonArray ?: el.jsonObject["result"]?.jsonObject?.get("webcams")?.jsonArray)
            ?.mapNotNull { w ->
                val o = w.jsonObject
                val loc = o["location"]?.jsonObject ?: return@mapNotNull null
                val lat = loc["latitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val lon = loc["longitude"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val img = o["images"]?.jsonObject?.get("current")?.jsonObject?.get("preview")?.jsonPrimitive?.contentOrNull ?: ""
                PublicCam(lat, lon, o["title"]?.jsonPrimitive?.contentOrNull ?: "Webcam", img, loc["country"]?.jsonPrimitive?.contentOrNull ?: "?")
            } ?: emptyList()
}
