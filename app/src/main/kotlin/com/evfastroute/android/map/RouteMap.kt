package com.evfastroute.android.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
internal const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

enum class RouteMapCameraMode { OVERVIEW, FOLLOWING, MANUAL }

private class MapState {
    var map: MapLibreMap? = null
    var style: Style? = null
    var route: List<LatLon> = emptyList()
    var chargers: List<LatLon> = emptyList()
    var waypoints: List<LatLon> = emptyList()
    var start: LatLon? = null
    var destination: LatLon? = null
    var userLocation: LatLon? = null
    var cameraMode = RouteMapCameraMode.OVERVIEW
    var cameraActionToken = 0
    var lastFollowedLocation: LatLon? = null
    var onManualInteraction: (() -> Unit)? = null
    var fallbackCenter = LatLon(37.7749, -122.4194)
    var hasFitted = false
    var loadState by mutableStateOf<MapLoadState>(MapLoadState.LOADING)
}

private enum class MapLoadState { LOADING, LOADED, FAILED }

@Composable
fun RouteMap(
    routeGeometry: List<LatLon>,
    chargers: List<LatLon>,
    start: LatLon?,
    destination: LatLon?,
    modifier: Modifier = Modifier,
    waypoints: List<LatLon> = emptyList(),
    fallbackCenter: LatLon = LatLon(37.7749, -122.4194),
    userLocation: LatLon? = null,
    cameraMode: RouteMapCameraMode = RouteMapCameraMode.OVERVIEW,
    cameraActionToken: Int = 0,
    onManualInteraction: (() -> Unit)? = null,
) {
    val mapView = rememberMapViewWithLifecycle()
    val state = remember { MapState() }
    state.onManualInteraction = onManualInteraction

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.addOnDidFailLoadingMapListener { state.loadState = MapLoadState.FAILED }
                mapView.addOnDidFinishLoadingMapListener { state.loadState = MapLoadState.LOADED }
                mapView.getMapAsync { map ->
                    state.map = map
                    map.addOnCameraMoveStartedListener { reason ->
                        if (
                            reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE &&
                            state.cameraMode != RouteMapCameraMode.MANUAL &&
                            state.onManualInteraction != null
                        ) {
                            // Stop live GPS updates from yanking the map back while the driver
                            // looks ahead. Recenter explicitly resumes follow mode.
                            state.cameraMode = RouteMapCameraMode.MANUAL
                            state.onManualInteraction?.invoke()
                        }
                    }
                    map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                        state.style = style
                        applyRoute(style, map, state)
                    }
                }
                mapView
            },
            update = {
                if (
                    routeGeometry != state.route || chargers != state.chargers ||
                    waypoints != state.waypoints || start != state.start ||
                    destination != state.destination || fallbackCenter != state.fallbackCenter
                ) {
                    state.hasFitted = false
                }
                state.route = routeGeometry
                state.chargers = chargers
                state.waypoints = waypoints
                state.start = start
                state.destination = destination
                state.fallbackCenter = fallbackCenter
                state.userLocation = userLocation
                if (cameraMode != state.cameraMode || cameraActionToken != state.cameraActionToken) {
                    state.cameraMode = cameraMode
                    state.cameraActionToken = cameraActionToken
                    when (cameraMode) {
                        RouteMapCameraMode.OVERVIEW -> state.hasFitted = false
                        RouteMapCameraMode.FOLLOWING -> state.lastFollowedLocation = null
                        RouteMapCameraMode.MANUAL -> Unit
                    }
                }
                state.style?.let { applyRoute(it, state.map, state) }
            },
        )
        when (state.loadState) {
            MapLoadState.LOADING -> Text(
                "Loading map…",
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            MapLoadState.FAILED -> Text(
                "The map tiles couldn't load. Your verified route details and navigation handoff are still available below.",
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            MapLoadState.LOADED -> Unit
        }
    }
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
                PropertyFactory.lineColor("#5BE3DC"),
                PropertyFactory.lineWidth(5f),
            ),
        )
    }

    upsertSource(style, "chargers", pointCollection(state.chargers))
    if (style.getLayer("charger-points") == null) {
        style.addLayer(
            CircleLayer("charger-points", "chargers").withProperties(
                PropertyFactory.circleColor("#5BE3DC"),
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
    }

    upsertSource(style, "waypoints", pointCollection(state.waypoints))
    if (style.getLayer("waypoint-points") == null) {
        style.addLayer(
            CircleLayer("waypoint-points", "waypoints").withProperties(
                PropertyFactory.circleColor("#5969D8"), // shared visit-stop indigo
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
    }

    upsertSource(style, "start-point", pointCollection(listOfNotNull(state.start)))
    if (style.getLayer("start-point-layer") == null) {
        style.addLayer(
            CircleLayer("start-point-layer", "start-point").withProperties(
                PropertyFactory.circleColor("#5BE3DC"),
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
    }

    upsertSource(style, "destination-point", pointCollection(listOfNotNull(state.destination)))
    if (style.getLayer("destination-point-layer") == null) {
        style.addLayer(
            CircleLayer("destination-point-layer", "destination-point").withProperties(
                PropertyFactory.circleColor("#59C9F3"),
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
    }

    upsertSource(style, "user-location", pointCollection(listOfNotNull(state.userLocation)))
    if (style.getLayer("user-location-layer") == null) {
        style.addLayer(
            CircleLayer("user-location-layer", "user-location").withProperties(
                PropertyFactory.circleColor("#FFFFFF"),
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleStrokeColor("#2979FF"),
                PropertyFactory.circleStrokeWidth(4f),
            ),
        )
    }

    if (state.cameraMode == RouteMapCameraMode.FOLLOWING) {
        state.userLocation?.takeIf { it != state.lastFollowedLocation }?.let { point ->
            map?.easeCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 14.5),
            )
            state.lastFollowedLocation = point
            state.hasFitted = true
        }
    }

    // Fit once to the whole route.
    val all = state.route + state.chargers + state.waypoints + listOfNotNull(state.start, state.destination)
    if (state.cameraMode == RouteMapCameraMode.OVERVIEW && !state.hasFitted && map != null) {
        when {
            all.size > 1 -> {
                val bounds = LatLngBounds.Builder()
                all.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
                runCatching { map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80)) }
                    .onSuccess { state.hasFitted = true }
            }
            all.size == 1 -> {
                val point = all.first()
                map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 11.0))
                state.hasFitted = true
            }
            else -> {
                // Match the iOS planner's neutral pre-search hero while avoiding MapLibre's
                // default whole-world camera. The first selected/current point replaces it.
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(state.fallbackCenter.latitude, state.fallbackCenter.longitude),
                        9.5,
                    ),
                )
                state.hasFitted = true
            }
        }
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
internal fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    MapLibre.getInstance(context) // must initialize before creating a MapView
    val mapView = remember { MapView(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var destroyed = false
        fun destroyOnce() {
            if (!destroyed) {
                destroyed = true
                mapView.onDestroy()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> destroyOnce()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            destroyOnce()
        }
    }
    return mapView
}
