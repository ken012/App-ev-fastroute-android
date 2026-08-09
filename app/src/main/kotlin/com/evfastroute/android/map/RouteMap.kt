package com.evfastroute.android.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.evfastroute.core.LatLon
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// OpenFreeMap MapLibre style (free, no API key).
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private class MapState {
    var map: MapLibreMap? = null
    var style: Style? = null
    var route: List<LatLon> = emptyList()
    var chargers: List<LatLon> = emptyList()
    var endpoints: List<LatLon> = emptyList()
    var hasFitted = false
}

@Composable
fun RouteMap(
    routeGeometry: List<LatLon>,
    chargers: List<LatLon>,
    start: LatLon?,
    destination: LatLon?,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapViewWithLifecycle()
    val state = remember { MapState() }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.getMapAsync { map ->
                state.map = map
                map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                    state.style = style
                    applyRoute(style, map, state)
                }
            }
            mapView
        },
        update = {
            state.route = routeGeometry
            state.chargers = chargers
            state.endpoints = listOfNotNull(start, destination)
            state.style?.let { applyRoute(it, state.map, state) }
        },
    )
}

private fun applyRoute(style: Style, map: MapLibreMap?, state: MapState) {
    // Route polyline.
    val linePoints = state.route.map { Point.fromLngLat(it.longitude, it.latitude) }
    val routeCollection = if (linePoints.size > 1) {
        FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(linePoints)))
    } else {
        FeatureCollection.fromFeatures(emptyList())
    }
    upsertSource(style, "route", routeCollection)
    if (style.getLayer("route-line") == null) {
        style.addLayer(
            LineLayer("route-line", "route").withProperties(
                PropertyFactory.lineColor("#00B3A4"),
                PropertyFactory.lineWidth(5f),
            ),
        )
    }

    upsertSource(style, "chargers", pointCollection(state.chargers))
    if (style.getLayer("charger-points") == null) {
        style.addLayer(
            CircleLayer("charger-points", "chargers").withProperties(
                PropertyFactory.circleColor("#00B3A4"),
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
    }

    upsertSource(style, "endpoints", pointCollection(state.endpoints))
    if (style.getLayer("endpoint-points") == null) {
        style.addLayer(
            CircleLayer("endpoint-points", "endpoints").withProperties(
                PropertyFactory.circleColor("#1E6FEB"),
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
    }

    // Fit once to the whole route.
    val all = state.route + state.chargers + state.endpoints
    if (!state.hasFitted && all.size > 1 && map != null) {
        val bounds = LatLngBounds.Builder()
        all.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
        runCatching { map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)) }
            .onSuccess { state.hasFitted = true }
    }
}

private fun pointCollection(points: List<LatLon>): FeatureCollection =
    FeatureCollection.fromFeatures(
        points.map { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) },
    )

private fun upsertSource(style: Style, id: String, collection: FeatureCollection) {
    val existing = style.getSourceAs<GeoJsonSource>(id)
    if (existing != null) existing.setGeoJson(collection) else style.addSource(GeoJsonSource(id, collection))
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    MapLibre.getInstance(context) // must initialize before creating a MapView
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    return mapView
}
