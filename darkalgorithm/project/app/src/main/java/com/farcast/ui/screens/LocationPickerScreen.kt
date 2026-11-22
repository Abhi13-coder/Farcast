package com.farcast.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import com.farcast.ui.theme.FarcastAmber
import com.farcast.ui.theme.FarcastCyan
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class MapMarker(val lat: Double, val lon: Double, val label: String, val isLive: Boolean)

/**
 * #p-pk, the one item from the original feature inventory that was always
 * explicitly "Section 5, not a service to port" — this is that UI, finally.
 * OSMDroid rather than Google Maps Compose: no API key, no Google Play
 * Services dependency, consistent with an app whose whole premise is
 * minimizing what the user has to trust. Tile source is OSM's standard
 * Mapnik rendering; the site's other tile sources (NASA GIBS, Esri World
 * Imagery) are swappable TileSources if those specific looks matter later —
 * not wired here to keep this piece honestly scoped to "the map works,"
 * not "every tile source from the site is reproduced."
 */
@Composable
fun LocationPickerScreen(markers: List<MapMarker>, initialCenter: GeoPoint = GeoPoint(51.5074, -0.1278)) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
            Configuration.getInstance().userAgentValue = context.packageName
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(4.0)
                controller.setCenter(initialCenter)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            markers.forEach { m ->
                val marker = Marker(mapView).apply {
                    position = GeoPoint(m.lat, m.lon)
                    title = m.label
                    // Live telemetry vs. static recon data stays visually
                    // distinguishable here too — same cyan-only-means-live
                    // rule as everywhere else in the app (Color.kt).
                    // (Real pin icon tinting needs a drawable per color —
                    // left as the default pin here; wire a tinted drawable
                    // using FarcastCyan/FarcastAmber once an icon asset exists.)
                }
                mapView.overlays.add(marker)
            }
            mapView.invalidate()
        }
    )
}

/** Call from the hosting Activity/Fragment's onPause/onResume — OSMDroid
 *  needs this for its tile cache lifecycle, it's not optional cleanup. */
@Composable
fun rememberMapViewLifecycle(context: Context) {
    DisposableEffect(Unit) {
        onDispose { Configuration.getInstance().save(context, PreferenceManager.getDefaultSharedPreferences(context)) }
    }
}
