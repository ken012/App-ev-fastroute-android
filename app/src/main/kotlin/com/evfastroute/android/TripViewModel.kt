package com.evfastroute.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.evfastroute.android.net.PhotonClient
import com.evfastroute.android.net.PlatformGeocoderClient
import com.evfastroute.android.net.ServiceResult
import com.evfastroute.android.net.userMessage
import com.evfastroute.core.ConnectorType
import com.evfastroute.core.EvCatalog
import com.evfastroute.core.EvPreset
import com.evfastroute.core.Geometry
import com.evfastroute.core.LatLon
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.NavigationPoint
import com.evfastroute.core.NavigationSession
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.PlaceRanker
import com.evfastroute.core.RangeDrivingStyle
import com.evfastroute.core.Region
import com.evfastroute.core.RouteOption
import com.evfastroute.core.orderedNavigationPoints
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WaypointField(val id: Int) {
    var text by mutableStateOf("")
    var selected by mutableStateOf<PlaceCandidate?>(null)
    var suggestions by mutableStateOf<List<PlaceCandidate>>(emptyList())
    var searchJob: Job? = null
}

class TripViewModel(application: Application) : AndroidViewModel(application) {

    private val planner = TripPlanner()
    private val settings = SettingsStore(application)
    private val platformGeocoder = PlatformGeocoderClient(application)

    var region by mutableStateOf(settings.region)
        private set
    var usesMiles by mutableStateOf(settings.usesMiles)
        private set
    var preferredNav by mutableStateOf(settings.preferredNav)
        private set
    var isEditingSettings by mutableStateOf(false)
        private set
    var isViewingLicenses by mutableStateOf(false)
        private set

    var navSession by mutableStateOf(settings.navigationSession)
        private set
    var arrivalSuggested by mutableStateOf(false)
        private set

    var selectedPreset by mutableStateOf(
        EvCatalog.preset(settings.selectedVehicleIdentifier) ?: EvCatalog.default,
    )
        private set
    private var selectedVehicleOverride by mutableStateOf(
        settings.vehicleOverride(selectedPreset.catalogIdentifier),
    )
    val configuredPreset: EvPreset
        get() = selectedVehicleOverride?.applyTo(selectedPreset) ?: selectedPreset
    val batteryHealthPercent: Double
        get() = selectedVehicleOverride?.batteryHealthPercent ?: 100.0

    var isPickingVehicle by mutableStateOf(false)
        private set
    var isEditingVehicle by mutableStateOf(false)
        private set

    var weatherRangeLossPercent by mutableFloatStateOf(settings.weatherRangeLossPercent)
        private set
    var extraLoadKg by mutableFloatStateOf(settings.extraLoadKg)
        private set
    var drivingStyle by mutableStateOf(settings.drivingStyle)
        private set
    var minimumChargerSpeedKw by mutableFloatStateOf(settings.minimumChargerSpeedKw.toFloat())
        private set
    var avoidLowConfidenceStations by mutableStateOf(settings.avoidLowConfidenceStations)
        private set
    var preferredNetworks by mutableStateOf(settings.preferredNetworks)
        private set
    var avoidedNetworks by mutableStateOf(settings.avoidedNetworks)
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
    var searchMessage: String? by mutableStateOf(null)
        private set
    private var lastKnownLocation: LatLon? = null

    val waypoints = mutableStateListOf<WaypointField>()
    val canAddWaypoint: Boolean get() = waypoints.size < MAX_USER_WAYPOINTS
    private var nextWaypointId = 0

    var currentSocPercent by mutableFloatStateOf(80f)
        private set
    // Match the iOS safety default; the user can lower it explicitly for a specific trip.
    var arrivalBufferPercent by mutableFloatStateOf(15f)
        private set
    var departureOffsetMinutes by mutableIntStateOf(0)
        private set

    var options by mutableStateOf<List<RouteOption>>(emptyList())
        private set
    var selectedIndex by mutableIntStateOf(0)
        private set
    val selectedOption: RouteOption? get() = options.getOrNull(selectedIndex)
    var isPlanning by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var lastPlanComputedAtMillis: Long? by mutableStateOf(null)
        private set
    var savedTrips by mutableStateOf(settings.savedTrips)
        private set
    var savedTripMessage: String? by mutableStateOf(null)
        private set

    private var startSearchJob: Job? = null
    private var destinationSearchJob: Job? = null
    private var planJob: Job? = null
    private var planGeneration = 0L

    private val baseVehicle
        get() = configuredPreset.toVehicle(european = region.isEuropean)
            .copy(batteryHealthPercent = batteryHealthPercent)

    private val planningConditions
        get() = TripPlanner.Conditions(
            weatherRangeLossPercent = weatherRangeLossPercent.toDouble(),
            extraLoadKg = extraLoadKg.toDouble(),
            drivingStyle = drivingStyle,
        )

    private val planningPreferences
        get() = TripPlanner.Preferences(
            minimumChargerSpeedKw = minimumChargerSpeedKw.toInt(),
            preferredNetworks = preferredNetworks,
            avoidedNetworks = avoidedNetworks,
            avoidLowConfidenceStations = avoidLowConfidenceStations,
        )

    fun onStartTextChange(text: String) {
        invalidatePlan()
        startText = text
        start = null
        startSearchJob?.cancel()
        if (text.isBlank()) {
            startSuggestions = emptyList()
            searchMessage = null
            return
        }
        startSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            startSuggestions = search(text)
        }
    }

    fun onDestinationTextChange(text: String) {
        invalidatePlan()
        destinationText = text
        destination = null
        destinationSearchJob?.cancel()
        if (text.isBlank()) {
            destinationSuggestions = emptyList()
            searchMessage = null
            return
        }
        destinationSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            destinationSuggestions = search(text)
        }
    }

    fun selectStart(candidate: PlaceCandidate) {
        startSearchJob?.cancel()
        startSearchJob = null
        invalidatePlan()
        start = candidate
        startText = candidate.placeName
        startSuggestions = emptyList()
        searchMessage = null
    }

    fun selectDestination(candidate: PlaceCandidate) {
        destinationSearchJob?.cancel()
        destinationSearchJob = null
        invalidatePlan()
        destination = candidate
        destinationText = candidate.placeName
        destinationSuggestions = emptyList()
        searchMessage = null
    }

    fun useCurrentLocation(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            searchMessage = "Your device returned an invalid location. Try again outdoors."
            return
        }
        lastKnownLocation = LatLon(latitude, longitude)
        selectStart(
            PlaceCandidate(
                placeName = "Current location",
                fullAddress = "Device location",
                latitude = latitude,
                longitude = longitude,
                distanceKm = 0.0,
            ),
        )
    }

    fun reportLocationUnavailable() {
        searchMessage = "Current location is unavailable. Check Location Services or type a start address."
    }

    fun selectOption(index: Int) {
        if (index in options.indices) selectedIndex = index
    }

    fun addWaypoint() {
        if (!canAddWaypoint) return
        invalidatePlan()
        waypoints.add(WaypointField(nextWaypointId++))
    }

    fun removeWaypoint(index: Int) {
        if (index in waypoints.indices) {
            invalidatePlan()
            waypoints[index].searchJob?.cancel()
            waypoints.removeAt(index)
        }
    }

    fun moveWaypoint(index: Int, delta: Int) {
        val target = index + delta
        if (index in waypoints.indices && target in waypoints.indices) {
            invalidatePlan()
            val moved = waypoints[index]
            waypoints[index] = waypoints[target]
            waypoints[target] = moved
        }
    }

    fun onWaypointTextChange(field: WaypointField, text: String) {
        invalidatePlan()
        field.text = text
        field.selected = null
        field.searchJob?.cancel()
        if (text.isBlank()) {
            field.suggestions = emptyList()
            return
        }
        field.searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            field.suggestions = search(text)
        }
    }

    fun selectWaypoint(field: WaypointField, candidate: PlaceCandidate) {
        field.searchJob?.cancel()
        field.searchJob = null
        invalidatePlan()
        field.selected = candidate
        field.text = candidate.placeName
        field.suggestions = emptyList()
        searchMessage = null
    }

    fun updateCurrentSoc(value: Float) {
        if (value == currentSocPercent) return
        currentSocPercent = value.coerceIn(5f, 100f)
        invalidatePlan()
    }

    fun updateArrivalBuffer(value: Float) {
        if (value == arrivalBufferPercent) return
        arrivalBufferPercent = value.coerceIn(5f, 40f)
        invalidatePlan()
    }

    fun setDepartureOffset(minutes: Int) {
        departureOffsetMinutes = minutes.coerceIn(0, 24 * 60)
    }

    fun showVehiclePicker() { isPickingVehicle = true }
    fun hideVehiclePicker() { isPickingVehicle = false }
    fun showVehicleEditor() { isEditingVehicle = true }
    fun hideVehicleEditor() { isEditingVehicle = false }
    fun showSettings() { isEditingSettings = true }
    fun hideSettings() { isEditingSettings = false }
    fun showLicenses() { isViewingLicenses = true }
    fun hideLicenses() { isViewingLicenses = false }

    fun updateRegion(value: Region) {
        if (region == value) return
        region = value
        settings.region = value
        usesMiles = settings.usesMiles
        invalidatePlan()
    }

    fun updateUsesMiles(value: Boolean) {
        usesMiles = value
        settings.usesMiles = value
    }

    fun updatePreferredNav(value: NavigationApp) {
        preferredNav = value
        settings.preferredNav = value
    }

    fun updateWeatherRangeLoss(value: Float) {
        weatherRangeLossPercent = value.coerceIn(0f, 45f)
        settings.weatherRangeLossPercent = weatherRangeLossPercent
        invalidatePlan()
    }

    fun updateExtraLoad(value: Float) {
        extraLoadKg = value.coerceIn(0f, 750f)
        settings.extraLoadKg = extraLoadKg
        invalidatePlan()
    }

    fun updateDrivingStyle(value: RangeDrivingStyle) {
        drivingStyle = value
        settings.drivingStyle = value
        invalidatePlan()
    }

    fun updateMinimumChargerSpeed(value: Float) {
        minimumChargerSpeedKw = value.coerceIn(0f, 350f)
        settings.minimumChargerSpeedKw = minimumChargerSpeedKw.toInt()
        invalidatePlan()
    }

    fun updateAvoidLowConfidence(value: Boolean) {
        avoidLowConfidenceStations = value
        settings.avoidLowConfidenceStations = value
        invalidatePlan()
    }

    fun updatePreferredNetworks(text: String) {
        preferredNetworks = parseNetworks(text)
        settings.preferredNetworks = preferredNetworks
        invalidatePlan()
    }

    fun updateAvoidedNetworks(text: String) {
        avoidedNetworks = parseNetworks(text)
        settings.avoidedNetworks = avoidedNetworks
        invalidatePlan()
    }

    fun selectPreset(preset: EvPreset) {
        selectedPreset = preset
        selectedVehicleOverride = settings.vehicleOverride(preset.catalogIdentifier)
        settings.selectedVehicleIdentifier = preset.catalogIdentifier
        isPickingVehicle = false
        invalidatePlan()
    }

    fun saveVehicleOverride(
        batteryCapacityKwh: Double,
        maxDcChargingKw: Int,
        efficiencyKwhPer100Km: Double,
        batteryHealthPercent: Double,
        connectors: Set<ConnectorType>,
    ): String? {
        val efficiency = efficiencyKwhPer100Km / 100.0
        val error = when {
            !batteryCapacityKwh.isFinite() || batteryCapacityKwh !in 10.0..300.0 -> "Battery capacity must be between 10 and 300 kWh."
            maxDcChargingKw !in 20..500 -> "Maximum DC charging must be between 20 and 500 kW."
            !efficiency.isFinite() || efficiency !in 0.05..0.60 -> "Consumption must be between 5 and 60 kWh/100 km."
            !batteryHealthPercent.isFinite() || batteryHealthPercent !in 50.0..100.0 -> "Battery health must be between 50% and 100%."
            connectors.isEmpty() -> "Select at least one compatible connector."
            else -> null
        }
        if (error != null) return error
        val override = VehicleOverride(
            batteryCapacityKwh = batteryCapacityKwh,
            maxDcChargingKw = maxDcChargingKw,
            efficiencyKwhPerKm = efficiency,
            connectorNames = connectors.map { it.name }.sorted(),
            batteryHealthPercent = batteryHealthPercent,
        )
        selectedVehicleOverride = override
        settings.setVehicleOverride(selectedPreset.catalogIdentifier, override)
        isEditingVehicle = false
        invalidatePlan()
        return null
    }

    fun resetVehicleOverride() {
        selectedVehicleOverride = null
        settings.setVehicleOverride(selectedPreset.catalogIdentifier, null)
        isEditingVehicle = false
        invalidatePlan()
    }

    fun saveCurrentTrip() {
        val from = start
        val to = destination
        if (from == null || to == null) {
            savedTripMessage = "Choose a start and destination before saving."
            return
        }
        val now = System.currentTimeMillis()
        val snapshot = SavedTripSnapshot(
            id = "trip-$now",
            name = "${from.placeName} → ${to.placeName}",
            start = from.toSavedPlace(),
            destination = to.toSavedPlace(),
            waypoints = waypoints.mapNotNull { it.selected?.toSavedPlace() },
            currentSocPercent = currentSocPercent,
            arrivalBufferPercent = arrivalBufferPercent,
            vehicleIdentifier = selectedPreset.catalogIdentifier,
            weatherRangeLossPercent = weatherRangeLossPercent,
            extraLoadKg = extraLoadKg,
            drivingStyle = drivingStyle.serialized,
            minimumChargerSpeedKw = minimumChargerSpeedKw.toInt(),
            preferredNetworks = preferredNetworks,
            avoidedNetworks = avoidedNetworks,
            avoidLowConfidenceStations = avoidLowConfidenceStations,
            createdAtMillis = now,
        )
        savedTrips = listOf(snapshot) + savedTrips
        settings.savedTrips = savedTrips
        savedTrips = settings.savedTrips
        savedTripMessage = "Trip saved on this device."
    }

    fun loadSavedTrip(snapshot: SavedTripSnapshot) {
        invalidatePlan()
        start = snapshot.start.toCandidate()
        startText = snapshot.start.name
        destination = snapshot.destination.toCandidate()
        destinationText = snapshot.destination.name
        startSuggestions = emptyList()
        destinationSuggestions = emptyList()
        waypoints.forEach { it.searchJob?.cancel() }
        waypoints.clear()
        snapshot.waypoints.take(MAX_USER_WAYPOINTS).forEach { place ->
            waypoints.add(
                WaypointField(nextWaypointId++).also { field ->
                    field.selected = place.toCandidate()
                    field.text = place.name
                },
            )
        }
        currentSocPercent = snapshot.currentSocPercent.coerceIn(5f, 100f)
        arrivalBufferPercent = snapshot.arrivalBufferPercent.coerceIn(5f, 40f)
        EvCatalog.preset(snapshot.vehicleIdentifier)?.let { preset ->
            selectedPreset = preset
            selectedVehicleOverride = settings.vehicleOverride(preset.catalogIdentifier)
            settings.selectedVehicleIdentifier = preset.catalogIdentifier
        }
        weatherRangeLossPercent = snapshot.weatherRangeLossPercent.coerceIn(0f, 45f)
        extraLoadKg = snapshot.extraLoadKg.coerceIn(0f, 750f)
        drivingStyle = RangeDrivingStyle.fromSerialized(snapshot.drivingStyle)
        minimumChargerSpeedKw = snapshot.minimumChargerSpeedKw.coerceIn(0, 350).toFloat()
        preferredNetworks = snapshot.preferredNetworks
        avoidedNetworks = snapshot.avoidedNetworks
        avoidLowConfidenceStations = snapshot.avoidLowConfidenceStations
        settings.weatherRangeLossPercent = weatherRangeLossPercent
        settings.extraLoadKg = extraLoadKg
        settings.drivingStyle = drivingStyle
        settings.minimumChargerSpeedKw = minimumChargerSpeedKw.toInt()
        settings.preferredNetworks = preferredNetworks
        settings.avoidedNetworks = avoidedNetworks
        settings.avoidLowConfidenceStations = avoidLowConfidenceStations
        savedTripMessage = "Loaded ${snapshot.name}. Tap Find Route for fresh live data."
    }

    fun deleteSavedTrip(snapshot: SavedTripSnapshot) {
        savedTrips = savedTrips.filterNot { it.id == snapshot.id }
        settings.savedTrips = savedTrips
        savedTripMessage = "Saved trip removed."
    }

    fun startGuidedTrip(option: RouteOption, app: NavigationApp) {
        val dest = destination ?: return
        val stops = option.orderedNavigationPoints()
        val destinationPoint = NavigationPoint(dest.latitude, dest.longitude, dest.placeName, NavigationPoint.Kind.DESTINATION)
        navSession = NavigationSession.create(stops, destinationPoint, app, System.currentTimeMillis())
        arrivalSuggested = false
        settings.navigationSession = navSession
    }

    fun recordSessionHandoff() {
        navSession = navSession?.recordHandoff(System.currentTimeMillis())
        arrivalSuggested = false
        settings.navigationSession = navSession
    }

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

    fun onLocationSample(latitude: Double, longitude: Double, accuracyMeters: Double?, sampleMillis: Long) {
        lastKnownLocation = LatLon(latitude, longitude)
        val session = navSession ?: return
        if (session.shouldSuggestArrival(latitude, longitude, accuracyMeters, sampleMillis, System.currentTimeMillis())) {
            arrivalSuggested = true
            navSession = session.recordArrivalPrompt()
            settings.navigationSession = navSession
        }
    }

    fun plan() {
        val from = start
        val to = destination
        if (from == null || to == null) {
            errorMessage = "Pick a start and destination from search."
            return
        }
        if (Geometry.haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude) < 50.0) {
            errorMessage = "Start and destination are the same place."
            return
        }
        if (currentSocPercent <= arrivalBufferPercent) {
            errorMessage = "Starting battery must be higher than the arrival buffer."
            return
        }
        if (waypoints.any { it.text.isNotBlank() && it.selected == null }) {
            errorMessage = "Pick each stop from its search results."
            return
        }

        val stops = waypoints.mapNotNull { it.selected }
        val requestedObjective = selectedOption?.objective
        val generation = ++planGeneration
        planJob?.cancel()
        isPlanning = true
        errorMessage = null
        options = emptyList()
        selectedIndex = 0
        val startPoint = LatLon(from.latitude, from.longitude)
        val destinationPoint = LatLon(to.latitude, to.longitude)
        val vehicleSnapshot = baseVehicle
        val conditionsSnapshot = planningConditions
        val preferencesSnapshot = planningPreferences
        val socSnapshot = currentSocPercent.toDouble()
        val bufferSnapshot = arrivalBufferPercent.toDouble()

        planJob = viewModelScope.launch {
            try {
                val result = planner.planThrough(
                    start = startPoint,
                    waypoints = stops,
                    destination = destinationPoint,
                    vehicle = vehicleSnapshot,
                    currentSOC = socSnapshot,
                    arrivalBufferPercent = bufferSnapshot,
                    conditions = conditionsSnapshot,
                    preferences = preferencesSnapshot,
                )
                if (generation != planGeneration) return@launch
                when (result) {
                    is TripPlanner.Result.Success -> {
                        options = result.options
                        selectedIndex = requestedObjective?.let { objective ->
                            result.options.indexOfFirst { option ->
                                option.objective == objective || objective in option.supportedObjectives
                            }.takeIf { it >= 0 }
                        } ?: 0
                        lastPlanComputedAtMillis = System.currentTimeMillis()
                    }
                    is TripPlanner.Result.Error -> errorMessage = result.message
                }
            } finally {
                if (generation == planGeneration) isPlanning = false
            }
        }
    }

    fun refreshRoutes() {
        if (!isPlanning && start != null && destination != null) plan()
    }

    fun refreshRoutesIfStale(nowMillis: Long = System.currentTimeMillis(), maxAgeMillis: Long = 5 * 60_000L) {
        val computedAt = lastPlanComputedAtMillis ?: return
        if (navSession == null && options.isNotEmpty() && !isPlanning && nowMillis - computedAt >= maxAgeMillis) plan()
    }

    private fun invalidatePlan() {
        planGeneration++
        planJob?.cancel()
        planJob = null
        isPlanning = false
        options = emptyList()
        selectedIndex = 0
        errorMessage = null
        lastPlanComputedAtMillis = null
    }

    private suspend fun search(query: String): List<PlaceCandidate> {
        val anchor = start?.let { LatLon(it.latitude, it.longitude) }
            ?: destination?.let { LatLon(it.latitude, it.longitude) }
            ?: lastKnownLocation
            ?: region.searchCenter

        val (deviceLocal, photonLocal) = coroutineScope {
            val device = async { platformGeocoder.search(query, anchor, limit = 15) }
            val photon = async { PhotonClient.search(query, anchor.latitude, anchor.longitude, limit = 20) }
            device.await() to photon.await()
        }
        val local = listOf(deviceLocal, photonLocal).flatMap { response ->
            when (response) {
                is ServiceResult.Success -> response.value
                is ServiceResult.Failure -> emptyList()
            }
        }
        if (local.isEmpty() && deviceLocal is ServiceResult.Failure && photonLocal is ServiceResult.Failure) {
            searchMessage = photonLocal.error.userMessage("Place search")
            return emptyList()
        }
        var combined = PlaceRanker.deduplicated(withDistances(local, anchor))
        if (PlaceRanker.shouldBroaden(combined, query)) {
            val (deviceBroad, photonBroad) = coroutineScope {
                val device = async { platformGeocoder.search(query, anchor = null, limit = 20) }
                val photon = async { PhotonClient.search(query, limit = 30) }
                device.await() to photon.await()
            }
            val broad = listOf(deviceBroad, photonBroad).flatMap { response ->
                if (response is ServiceResult.Success) response.value else emptyList()
            }
            combined = PlaceRanker.deduplicated(combined + withDistances(broad, anchor))
        }
        val ranked = PlaceRanker.rank(combined, query, haveAnchor = true).take(8)
        searchMessage = if (ranked.isEmpty()) "No places matched “$query”. Add a city, province/state, or postal code." else null
        return ranked
    }

    private fun withDistances(items: List<PlaceCandidate>, anchor: LatLon): List<PlaceCandidate> = items.map { item ->
        item.copy(
            distanceKm = Geometry.haversineMeters(
                anchor.latitude,
                anchor.longitude,
                item.latitude,
                item.longitude,
            ) / 1_000.0,
        )
    }

    private fun parseNetworks(text: String): Set<String> = text
        .split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

private fun PlaceCandidate.toSavedPlace(): SavedPlace = SavedPlace(
    name = placeName,
    address = fullAddress,
    latitude = latitude,
    longitude = longitude,
)

private fun SavedPlace.toCandidate(): PlaceCandidate = PlaceCandidate(
    placeName = name,
    fullAddress = address,
    latitude = latitude,
    longitude = longitude,
)
