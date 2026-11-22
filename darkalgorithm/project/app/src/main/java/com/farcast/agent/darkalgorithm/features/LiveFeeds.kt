package com.farcast.agent.darkalgorithm.features

import android.content.Context
import com.farcast.agent.darkalgorithm.privacy.TorEnforcedHttpClient
import kotlinx.serialization.json.*
import java.net.URLEncoder

data class MapVector(val lat: Double, val lon: Double, val label: String, val kind: String, val meta: Map<String, String> = emptyMap())

/** #p-av — real endpoints: OpenSky via allorigins CORS proxy (irrelevant on
 *  Android, no CORS — called directly), falling back to adsb.lol like the site does. */
class AviationService(private val context: Context) {
    suspend fun fetchPlanes(caseId: String, limit: Int = 60): List<MapVector> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "aviation")
        return try {
            val el = FarcastHttp.getJson(client, "https://opensky-network.org/api/states/all")
            parseOpenSky(el, limit)
        } catch (_: Exception) {
            val el = FarcastHttp.getJson(client, "https://api.adsb.lol/v2/ladd")
            parseAdsbLol(el, limit)
        }
    }

    private fun parseOpenSky(el: JsonElement, limit: Int): List<MapVector> =
        el.jsonObject["states"]?.jsonArray?.take(limit)?.mapNotNull { s ->
            val arr = s.jsonArray
            val lon = arr.getOrNull(5)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lat = arr.getOrNull(6)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val callsign = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            MapVector(lat, lon, callsign.ifBlank { "AC" }, "aircraft", mapOf("callsign" to callsign))
        } ?: emptyList()

    private fun parseAdsbLol(el: JsonElement, limit: Int): List<MapVector> =
        el.jsonObject["ac"]?.jsonArray?.take(limit)?.mapNotNull { a ->
            val o = a.jsonObject
            val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lon = o["lon"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val flight = o["flight"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            MapVector(lat, lon, flight.ifBlank { "AC" }, "aircraft", mapOf("callsign" to flight))
        } ?: emptyList()
}

/** #p-or — celestrak.org GP element sets. */
class CelestrakService(private val context: Context) {
    suspend fun fetchObjects(caseId: String, group: String = "visual", limit: Int = 60): List<String> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "orbital")
        val el = FarcastHttp.getJson(client, "https://celestrak.org/NORAD/elements/gp.php?GROUP=$group&FORMAT=JSON")
        return el.jsonArray.take(limit).mapNotNull {
            it.jsonObject["OBJECT_NAME"]?.jsonPrimitive?.contentOrNull
        }
    }
}

/** #p-qk — USGS GeoJSON earthquake feeds. */
class UsgsQuakeService(private val context: Context) {
    data class Quake(val lat: Double, val lon: Double, val mag: Double, val place: String)

    suspend fun fetchQuakes(caseId: String, feed: String = "2.5_day", limit: Int = 60): List<Quake> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "quakes")
        val el = FarcastHttp.getJson(client, "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/$feed.geojson")
        return el.jsonObject["features"]?.jsonArray?.take(limit)?.mapNotNull { f ->
            val coords = f.jsonObject["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
            val props = f.jsonObject["properties"]?.jsonObject
            Quake(
                lat = coords[1].jsonPrimitive.double,
                lon = coords[0].jsonPrimitive.double,
                mag = props?.get("mag")?.jsonPrimitive?.doubleOrNull ?: 0.0,
                place = props?.get("place")?.jsonPrimitive?.contentOrNull ?: "?"
            )
        } ?: emptyList()
    }
}

/** #p-iss — position keyless via wheretheiss.at; crew via open-notify (HTTP-only,
 *  website proxies it through allorigins/corsproxy for the browser's benefit —
 *  Android has no such restriction and can call it directly). */
class IssService(private val context: Context) {
    data class Position(val lat: Double, val lon: Double, val altitudeKm: Double, val velocityKmh: Double, val visibility: String)

    suspend fun trackIss(caseId: String): Position {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "iss")
        val el = FarcastHttp.getJson(client, "https://api.wheretheiss.at/v1/satellites/25544").jsonObject
        return Position(
            lat = el["latitude"]!!.jsonPrimitive.double,
            lon = el["longitude"]!!.jsonPrimitive.double,
            altitudeKm = el["altitude"]!!.jsonPrimitive.double,
            velocityKmh = el["velocity"]!!.jsonPrimitive.double,
            visibility = el["visibility"]?.jsonPrimitive?.contentOrNull ?: "—"
        )
    }

    suspend fun crew(caseId: String): List<String> {
        val client = TorEnforcedHttpClient.forBranch(context, caseId, "iss")
        val el = FarcastHttp.getJson(client, "http://api.open-notify.org/astros.json").jsonObject
        return el["people"]?.jsonArray
            ?.filter { it.jsonObject["craft"]?.jsonPrimitive?.contentOrNull == "ISS" }
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?: emptyList()
    }
}
