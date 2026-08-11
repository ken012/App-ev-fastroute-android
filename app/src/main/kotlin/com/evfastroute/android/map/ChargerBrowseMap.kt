package com.evfastroute.android.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.evfastroute.android.net.OcmBounds
import com.evfastroute.core.Charger
import com.evfastroute.core.ChargerStatus
import com.evfastroute.core.LatLon
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private enum class BrowseMapLoadState { LOADING, LOADED, FAILED }

private val statusLayers = listOf(
    ChargerStatus.AVAILABLE to "browse-operational-points",
    ChargerStatus.BUSY to "browse-busy-points",
    ChargerStatus.LIMITED to "browse-unknown-points",
    ChargerStatus.OFFLINE to "browse-offline-points",
)

private class BrowseMapState {
    var map: MapLibreMap? = null
    var style: Style? = null
    var chargers: List<Charger> = emptyList()
    var initialCenter: LatLon? = null
    var hasCentered = false
    var onViewportChanged: (OcmBounds) -> Unit = {}
    var onChargerSelected: (Charger) -> Unit = {}
    var loadState by mutableStateOf(BrowseMapLoadState.LOADING)
}

/** Interactive, trip-independent station map. MapLibre keeps map rendering key-free. */
@Composable
internal fun ChargerBrowseMap(
    chargers: List<Charger>,
    initialCenter: LatLon,
    onViewportChanged: (OcmBounds) -> Unit,
    onChargerSelected: (Charger) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapViewWithLifecycle()
    val state = remember { BrowseMapState() }
    state.onViewportChanged = onViewportChanged
    state.onChargerSelected = onChargerSelected

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.addOnDidFailLoadingMapListener { state.loadState = BrowseMapLoadState.FAILED }
                mapView.addOnDidFinishLoadingMapListener { state.loadState = BrowseMapLoadState.LOADED }
                mapView.getMapAsync { map ->
                    state.map = map
                    map.addOnCameraIdleListener {
                        val bounds = map.projection.visibleRegion.latLngBounds
                        state.onViewportChanged(
                            OcmBounds(
                                minLat = bounds.latSouth,
                                minLon = bounds.lonWest,
                                maxLat = bounds.latNorth,
                                maxLon = bounds.lonEast,
                            ),
                        )
                    }
                    map.addOnMapClickListener { point ->
                        val screenPoint = map.projection.toScreenLocation(point)
                        val features = map.queryRenderedFeatures(
                            screenPoint,
                            *statusLayers.map { it.second }.toTypedArray(),
                        )
                        val id = features.firstOrNull()?.getStringProperty("chargerId")
                        val selected = state.chargers.firstOrNull { it.id == id }
                        selected?.let(state.onChargerSelected)
                        selected != null
                    }
                    map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                        state.style = style
                        applyBrowseChargers(style, state.chargers)
                        if (!state.hasCentered) {
                            val center = state.initialCenter ?: initialCenter
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(center.latitude, center.longitude),
                                    10.0,
                                ),
                            )
                            state.hasCentered = true
                        }
                    }
                }
                mapView
            },
            update = {
                state.initialCenter = initialCenter
                state.chargers = chargers
                state.style?.let { applyBrowseChargers(it, chargers) }
            },
        )
        when (state.loadState) {
            BrowseMapLoadState.LOADING -> Text(
                "Loading map…",
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
            BrowseMapLoadState.FAILED -> Text(
                "The map tiles couldn't load. Check your connection and try again.",
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                color = MaterialTheme.colorScheme.error,
            )
            BrowseMapLoadState.LOADED -> Unit
        }
    }
}

private fun applyBrowseChargers(style: Style, chargers: List<Charger>) {
    statusLayers.forEach { (status, layerId) ->
        val sourceId = "$layerId-source"
        val features = chargers.filter { it.status == status }.map { charger ->
            Feature.fromGeometry(Point.fromLngLat(charger.longitude, charger.latitude)).apply {
                addStringProperty("chargerId", charger.id)
            }
        }
        val collection = FeatureCollection.fromFeatures(features)
        val existing = style.getSourceAs<GeoJsonSource>(sourceId)
        if (existing != null) existing.setGeoJson(collection)
        else style.addSource(GeoJsonSource(sourceId, collection))

        if (style.getLayer(layerId) == null) {
            style.addLayer(
                CircleLayer(layerId, sourceId).withProperties(
                    PropertyFactory.circleColor(statusColor(status)),
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(2f),
                ),
            )
        }
    }
}

private fun statusColor(status: ChargerStatus): String = when (status) {
    ChargerStatus.AVAILABLE -> "#5BE3DC"
    ChargerStatus.BUSY -> "#FF9800"
    ChargerStatus.LIMITED -> "#FBC02D"
    ChargerStatus.OFFLINE -> "#E53935"
}
