package com.evfastroute.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evfastroute.android.net.PhotonClient
import com.evfastroute.core.EvCatalog
import com.evfastroute.core.EvPreset
import com.evfastroute.core.LatLon
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.NavigationPoint
import com.evfastroute.core.NavigationSession
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.PlaceRanker
import com.evfastroute.core.Region
import com.evfastroute.core.RouteOption
import com.evfastroute.core.orderedNavigationPoints
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One of the driver's intermediate stops, with its own search box state. Fields are Compose state
 * so the list can hold stable objects while each row edits independently. */
class WaypointField(val id: Int) {
    var text by mutableStateOf("")
    var selected by mutableStateOf<PlaceCandidate?>(null)
    var suggestions by mutableStateOf<List<PlaceCandidate>>(emptyList())
    var searchJob: Job? = null
}

class TripViewModel(application: Application) : AndroidViewModel(application) {

    private val planner = TripPlanner()
    private val settings = SettingsStore(application)

    // Persisted preferences (Settings screen). Region also decides connector standards + currency.
    var region by mutableStateOf(settings.region)
        private set
    var usesMiles by mutableStateOf(settings.usesMiles)
        private set
    var preferredNav by mutableStateOf(settings.preferredNav)
        private set
    var isEditingSettings by mutableStateOf(false)
        private set

    // An in-progress sequential handoff (multi-stop), restored across launches.
    var navSession by mutableStateOf(settings.navigationSession)
        private set

    /** True when live location suggests the driver has reached the current guided-trip point. */
    var arrivalSuggested by mutableStateOf(false)
        private set

    // The chosen car. Starts at a sensible default; the user can pick any catalog vehicle. The
    // planner only needs the physics ([EvPreset.toVehicle]); the preset keeps make/model for display.
    var selectedPreset by mutableStateOf(EvCatalog.default)
        private set
    private val vehicle get() = selectedPreset.toVehicle(european = region.isEuropean)

    var isPickingVehicle by mutableStateOf(false)
        private set

    var startText by mutableStateOf("")
        private set
    var destinationText by mutableStateOf("")
        private set
    var start: PlaceCandidate? by mutableStateOf(null)
        private set
    var destination: PlaceCandidate? by mutableStateOf(null)
        private set

    var startSuggestions by mutableStateOf<List<PlaceCandidate>>(emptyList())
        private set
    var destinationSuggestions by mutableStateOf<List<PlaceCandidate>>(emptyList())
        private set

    /** The driver's intermediate stops, in travel order (start → these → destination). */
    val waypoints = mutableStateListOf<WaypointField>()
    private var nextWaypointId = 0

    var currentSocPercent by mutableStateOf(80f)
    var arrivalBufferPercent by mutableStateOf(10f)

    /** Minutes from now the trip is planned to start; shifts the arrival-timeline clock. 0 = leave now.
     * (The free routing stack has no live-traffic model, so this moves the schedule, not the ETA.) */
    var departureOffsetMinutes by mutableStateOf(0)
        private set

    fun setDepartureOffset(minutes: Int) { departureOffsetMinutes = minutes }

    var options by mutableStateOf<List<RouteOption>>(emptyList())
        private set
    var selectedIndex by mutableStateOf(0)
        private set
    val selectedOption: RouteOption?
        get() = options.getOrNull(selectedIndex)
    var isPlanning by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set

    private var startSearchJob: Job? = null
    private var destinationSearchJob: Job? = null

    fun onStartTextChange(text: String) {
        startText = text
        start = null
        startSearchJob?.cancel()
        if (text.isBlank()) { startSuggestions = emptyList(); return }
        startSearchJob = viewModelScope.launch {
            startSuggestions = search(text)
        }
    }

    fun onDestinationTextChange(text: String) {
        destinationText = text
        destination = null
        destinationSearchJob?.cancel()
        if (text.isBlank()) { destinationSuggestions = emptyList(); return }
        destinationSearchJob = viewModelScope.launch {
            destinationSuggestions = search(text)
        }
    }

    fun selectStart(candidate: PlaceCandidate) {
        start = candidate
        startText = candidate.placeName
        startSuggestions = emptyList()
    }

    fun selectDestination(candidate: PlaceCandidate) {
        destination = candidate
        destinationText = candidate.placeName
        destinationSuggestions = emptyList()
    }

    fun selectOption(index: Int) {
        if (index in options.indices) selectedIndex = index
    }

    fun addWaypoint() { waypoints.add(WaypointField(nextWaypointId++)) }

    fun removeWaypoint(index: Int) {
        if (index in waypoints.indices) {
            waypoints[index].searchJob?.cancel()
            waypoints.removeAt(index)
        }
    }

    /** Moves the stop at [index] by [delta] positions (−1 up, +1 down), reordering the trip. */
    fun moveWaypoint(index: Int, delta: Int) {
        val target = index + delta
        if (index in waypoints.indices && target in waypoints.indices) {
            val moved = waypoints[index]
            waypoints[index] = waypoints[target]
            waypoints[target] = moved
        }
    }

    fun onWaypointTextChange(field: WaypointField, text: String) {
        field.text = text
        field.selected = null
        field.searchJob?.cancel()
        if (text.isBlank()) { field.suggestions = emptyList(); return }
        field.searchJob = viewModelScope.launch { field.suggestions = search(text) }
    }

    fun selectWaypoint(field: WaypointField, candidate: PlaceCandidate) {
        field.selected = candidate
        field.text = candidate.placeName
        field.suggestions = emptyList()
    }

    fun showVehiclePicker() { isPickingVehicle = true }
    fun hideVehiclePicker() { isPickingVehicle = false }

    fun showSettings() { isEditingSettings = true }
    fun hideSettings() { isEditingSettings = false }

    fun updateRegion(value: Region) {
        region = value
        settings.region = value
        // If the user hasn't overridden units, follow the new region's convention.
        usesMiles = settings.usesMiles
        // Region changes connector standards + currency, so a prior plan may no longer be consistent.
        options = emptyList()
        selectedIndex = 0
    }

    fun updateUsesMiles(value: Boolean) {
        usesMiles = value
        settings.usesMiles = value
    }

    fun updatePreferredNav(value: NavigationApp) {
        preferredNav = value
        settings.preferredNav = value
    }

    // ---- Sequential guided handoff ----

    /** Begins a per-stop guided handoff through the route's stops to the destination. */
    fun startGuidedTrip(option: RouteOption, app: NavigationApp) {
        val dest = destination ?: return
        val stops = option.orderedNavigationPoints()
        val destinationPoint = NavigationPoint(dest.latitude, dest.longitude, dest.placeName, NavigationPoint.Kind.DESTINATION)
        navSession = NavigationSession.create(stops, destinationPoint, app, System.currentTimeMillis())
        arrivalSuggested = false
        settings.navigationSession = navSession
    }

    /** Records that the current point was just opened in the external app (starts the arrival clock). */
    fun recordSessionHandoff() {
        navSession = navSession?.recordHandoff(System.currentTimeMillis())
        arrivalSuggested = false // new handoff → a fresh arrival cycle for this point
        settings.navigationSession = navSession
    }

    /** Driver confirmed arrival at the current point; advance (and end when the destination is reached). */
    fun advanceGuidedTrip() {
        val advanced = navSession?.markCurrentPointComplete() ?: return
        navSession = if (advanced.isComplete) null else advanced
        arrivalSuggested = false
        settings.navigationSession = navSession
    }

    fun endGuidedTrip() {
        navSession = null
        arrivalSuggested = false
        settings.navigationSession = null
    }

    /**
     * Feeds a live location sample into the guided-trip arrival detector. When the sample is fresh,
     * accurate, settled and close to the current point, offers a confirm prompt exactly once (never
     * auto-advances — the driver still taps Arrived). Ported gate lives in [NavigationSession].
     */
    fun onLocationSample(latitude: Double, longitude: Double, accuracyMeters: Double?, sampleMillis: Long) {
        val session = navSession ?: return
        if (session.shouldSuggestArrival(latitude, longitude, accuracyMeters, sampleMillis, System.currentTimeMillis())) {
            arrivalSuggested = true
            navSession = session.recordArrivalPrompt() // mark so the same point isn't prompted repeatedly
            settings.navigationSession = navSession
        }
    }

    fun selectPreset(preset: EvPreset) {
        selectedPreset = preset
        isPickingVehicle = false
        // A different car changes the whole plan; clear stale options so nothing misleads.
        options = emptyList()
        selectedIndex = 0
    }

    fun plan() {
        val from = start
        val to = destination
        if (from == null || to == null) {
            errorMessage = "Pick a start and destination from search."
            return
        }
        if (waypoints.any { it.text.isNotBlank() && it.selected == null }) {
            errorMessage = "Pick each stop from its search results."
            return
        }
        val stops = waypoints.mapNotNull { it.selected }
        if (isPlanning) return
        isPlanning = true
        errorMessage = null
        options = emptyList()
        val startPoint = LatLon(from.latitude, from.longitude)
        val destinationPoint = LatLon(to.latitude, to.longitude)
        viewModelScope.launch {
            val result = planner.planThrough(
                start = startPoint,
                waypoints = stops,
                destination = destinationPoint,
                vehicle = vehicle,
                currentSOC = currentSocPercent.toDouble(),
                arrivalBufferPercent = arrivalBufferPercent.toDouble(),
            )
            when (result) {
                is TripPlanner.Result.Success -> {
                    options = result.options
                    selectedIndex = 0
                }
                is TripPlanner.Result.Error -> errorMessage = result.message
            }
            isPlanning = false
        }
    }

    private suspend fun search(query: String): List<PlaceCandidate> {
        val anchor = start ?: destination
        val raw = PhotonClient.search(query, anchor?.latitude, anchor?.longitude)
        return PlaceRanker.rank(raw, query, haveAnchor = anchor != null).take(6)
    }
}
