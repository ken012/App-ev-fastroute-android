package com.evfastroute.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.evfastroute.core.RangeEstimator
import com.evfastroute.core.Region
import com.evfastroute.core.RouteOption
import com.evfastroute.core.orderedNavigationPoints
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class WaypointField(val id: Int) {
    var text by mutableStateOf("")
    var selected by mutableStateOf<PlaceCandidate?>(null)
    var suggestions by mutableStateOf<List<PlaceCandidate>>(emptyList())
    var searchJob: Job? = null
}

enum class InformationPage { PRIVACY, TERMS, ABOUT }

private fun defaultGaragePresets(): List<EvPreset> {
    fun closest(make: String, modelPrefix: String, preferredYear: Int): EvPreset? {
        val candidates = EvCatalog.presets.filter {
            it.make.equals(make, ignoreCase = true) &&
                it.model.startsWith(modelPrefix, ignoreCase = true)
        }
        return candidates.firstOrNull { it.year == preferredYear }
            ?: candidates.maxByOrNull { it.year }
    }

    return listOfNotNull(
        closest("Tesla", "Model Y Long Range", 2026),
        closest("Hyundai", "IONIQ 5", 2026),
        closest("Ford", "Mustang Mach-E", 2025),
    ).distinctBy(EvPreset::catalogIdentifier).ifEmpty { listOf(EvCatalog.default) }
}

class TripViewModel(application: Application) : AndroidViewModel(application) {

    private val planner = TripPlanner()
    private val settings = SettingsStore(application)
    private val platformGeocoder = PlatformGeocoderClient(application)
    private var customVehicleRecords by mutableStateOf(settings.customVehicles)

    private fun presetForIdentifier(identifier: String?): EvPreset? =
        customVehicleRecords.firstOrNull { it.identifier == identifier }?.toPreset()
            ?: EvCatalog.preset(identifier)

    var hasOnboarded by mutableStateOf(settings.hasOnboarded)
        private set
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
    var informationPage by mutableStateOf<InformationPage?>(null)
        private set

    var navSession by mutableStateOf(settings.navigationSession)
        private set
    var arrivalSuggested by mutableStateOf(false)
        private set
    var isGuidedNavigationOpen by mutableStateOf(false)
        private set
    var activeNavigationRoute by mutableStateOf<RouteOption?>(null)
        private set
    var navigationOrigin by mutableStateOf<LatLon?>(null)
        private set
    var isRerouting by mutableStateOf(false)
        private set
    var navigationError by mutableStateOf<String?>(null)
        private set
    private var navigationBaselineBatteryPercent = 70.0
    private var navigationBaselineStartedAtMillis = 0L

    private val starterGarage = defaultGaragePresets()
    private val initialGarageIdentifiers = normalizeGarageVehicleIdentifiers(
        listOfNotNull(settings.selectedVehicleIdentifier) +
            settings.garageVehicleIdentifiers.ifEmpty { starterGarage.map(EvPreset::catalogIdentifier) },
    )

    var garageVehicleIdentifiers by mutableStateOf(initialGarageIdentifiers)
        private set
    val garageVehicles: List<EvPreset>
        get() = garageVehicleIdentifiers.mapNotNull(::presetForIdentifier)

    var selectedPreset by mutableStateOf(
        presetForIdentifier(settings.selectedVehicleIdentifier)
            ?: garageVehicles.firstOrNull()
            ?: EvCatalog.default,
    )
        private set
    private var selectedVehicleOverride by mutableStateOf(
        settings.vehicleOverride(selectedPreset.catalogIdentifier),
    )
    val configuredPreset: EvPreset
        get() = selectedVehicleOverride?.applyTo(selectedPreset)
            ?: selectedPreset.copy(connectorTypes = selectedPreset.connectorTypes(region.isEuropean))
    val batteryHealthPercent: Double
        get() = selectedVehicleOverride?.batteryHealthPercent ?: 100.0
    val defaultArrivalBufferPercent: Int
        get() = selectedVehicleOverride?.defaultArrivalBufferPercent ?: 15

    var isPickingVehicle by mutableStateOf(false)
        private set
    var isEditingVehicle by mutableStateOf(false)
        private set
    var isCreatingVehicle by mutableStateOf(false)
        private set
    var pickerShowsGarageOnly by mutableStateOf(false)
        private set
    var editorHasCatalogSeed by mutableStateOf(false)
        private set
    var editorSeedRevision by mutableIntStateOf(0)
        private set
    private var editorOriginalIdentifier: String? = null
    private var editorReturnPreset: EvPreset? = null
    private var editorReturnOverride: VehicleOverride? = null
    private var editorRestoresSelectionAfterSave = false
    private var pickerReturnsToEditor = false

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
    var currentLocation by mutableStateOf<LatLon?>(null)
        private set
    var currentLocationAccuracyMeters by mutableStateOf<Double?>(null)
        private set

    val waypoints = mutableStateListOf<WaypointField>()
    val canAddWaypoint: Boolean get() = waypoints.size < MAX_USER_WAYPOINTS
    private var nextWaypointId = 0

    var currentSocPercent by mutableFloatStateOf(70f)
        private set
    // Match the iOS safety default; the user can lower it explicitly for a specific trip.
    var arrivalBufferPercent by mutableFloatStateOf(settings.arrivalBufferPercent)
        private set
    var useScheduledDeparture by mutableStateOf(false)
        private set
    var scheduledDepartureMillis by mutableLongStateOf(System.currentTimeMillis() + 5 * 60_000L)
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
    var successfulPlanRevision by mutableLongStateOf(0L)
        private set
    var savedTrips by mutableStateOf(settings.savedTrips)
        private set
    var savedTripMessage: String? by mutableStateOf(null)
        private set

    private var startSearchJob: Job? = null
    private var destinationSearchJob: Job? = null
    private var planJob: Job? = null
    private var rerouteJob: Job? = null
    private var planGeneration = 0L

    init {
        if (settings.garageVehicleIdentifiers != garageVehicleIdentifiers) {
            settings.garageVehicleIdentifiers = garageVehicleIdentifiers
        }
        if (settings.selectedVehicleIdentifier != selectedPreset.catalogIdentifier) {
            settings.selectedVehicleIdentifier = selectedPreset.catalogIdentifier
        }
    }

    private val baseVehicle
        // `configuredPreset` already contains region-mapped catalog connectors or the driver's
        // explicit override. Mapping a second time would silently rewrite a user-selected adapter.
        get() = configuredPreset.toVehicle(european = false)
            .copy(batteryHealthPercent = batteryHealthPercent)

    val estimatedRange: RangeEstimator.Estimate
        get() = RangeEstimator.estimate(
            vehicle = baseVehicle,
            currentBatteryPercent = currentSocPercent.toDouble(),
            arrivalBufferPercent = arrivalBufferPercent.toDouble(),
            weatherRangeLossPercent = weatherRangeLossPercent.toDouble(),
            extraLoadKg = extraLoadKg.toDouble(),
            drivingStyle = drivingStyle,
        )

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
        searchMessage = null
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

    fun clearStart() = onStartTextChange("")

    /** Rehydrates suggestions when an already-selected field is reopened. Previously the search
     * sheet showed an endless spinner until the user changed the old place name. */
    fun refreshStartSuggestions() {
        val query = startText.trim()
        if (query.isEmpty()) return
        startSearchJob?.cancel()
        searchMessage = null
        startSearchJob = viewModelScope.launch { startSuggestions = search(query) }
    }

    fun onDestinationTextChange(text: String) {
        invalidatePlan()
        destinationText = text
        destination = null
        destinationSearchJob?.cancel()
        searchMessage = null
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

    fun clearDestination() = onDestinationTextChange("")

    fun refreshDestinationSuggestions() {
        val query = destinationText.trim()
        if (query.isEmpty()) return
        destinationSearchJob?.cancel()
        searchMessage = null
        destinationSearchJob = viewModelScope.launch { destinationSuggestions = search(query) }
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

    fun useCurrentLocation(latitude: Double, longitude: Double, accuracyMeters: Double? = null) {
        if (!updateCurrentLocationAnchor(latitude, longitude, accuracyMeters)) {
            searchMessage = "Your device returned an invalid location. Try again outdoors."
            return
        }
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

    /** Stores an optional foreground location only as a search-ranking/navigation anchor. It does
     * not change either address field. */
    fun updateCurrentLocationAnchor(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double?,
    ): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            return false
        }
        currentLocation = LatLon(latitude, longitude)
        currentLocationAccuracyMeters = accuracyMeters?.takeIf { it.isFinite() && it >= 0.0 }
        return true
    }

    fun reportLocationUnavailable() {
        searchMessage = "Current location is unavailable. Check Location Services or type a start address."
    }

    fun reportLocationPermissionDenied() {
        searchMessage = "Location permission wasn't granted. You can still type a starting address."
    }

    fun swapAddresses() {
        startSearchJob?.cancel()
        destinationSearchJob?.cancel()
        invalidatePlan()
        val previousStart = start
        val previousStartText = startText
        start = destination
        startText = destinationText
        destination = previousStart
        destinationText = previousStartText
        startSuggestions = emptyList()
        destinationSuggestions = emptyList()
        searchMessage = null
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
        searchMessage = null
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

    fun refreshWaypointSuggestions(field: WaypointField) {
        val query = field.text.trim()
        if (query.isEmpty()) return
        field.searchJob?.cancel()
        searchMessage = null
        field.searchJob = viewModelScope.launch { field.suggestions = search(query) }
    }

    fun updateCurrentSoc(value: Float) {
        if (value == currentSocPercent) return
        currentSocPercent = value.coerceIn(5f, 100f)
        invalidatePlan()
    }

    fun updateArrivalBuffer(value: Float) {
        if (value == arrivalBufferPercent) return
        arrivalBufferPercent = value.coerceIn(5f, 35f)
        settings.arrivalBufferPercent = arrivalBufferPercent
        invalidatePlan()
    }

    fun updateUseScheduledDeparture(enabled: Boolean) {
        if (useScheduledDeparture == enabled) return
        useScheduledDeparture = enabled
        if (enabled && scheduledDepartureMillis <= System.currentTimeMillis()) {
            scheduledDepartureMillis = System.currentTimeMillis() + 5 * 60_000L
        }
        invalidatePlan()
    }

    fun updateScheduledDeparture(millis: Long) {
        val earliest = System.currentTimeMillis()
        scheduledDepartureMillis = millis.coerceAtLeast(earliest)
        useScheduledDeparture = true
        invalidatePlan()
    }

    fun effectiveDepartureMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        if (useScheduledDeparture) scheduledDepartureMillis.coerceAtLeast(nowMillis) else nowMillis

    fun completeOnboarding() {
        hasOnboarded = true
        settings.hasOnboarded = true
    }

    fun showVehiclePicker() {
        pickerReturnsToEditor = false
        pickerShowsGarageOnly = true
        isCreatingVehicle = false
        isPickingVehicle = true
    }
    fun hideVehiclePicker() {
        isPickingVehicle = false
        pickerShowsGarageOnly = false
        if (pickerReturnsToEditor) {
            pickerReturnsToEditor = false
            isEditingVehicle = true
        }
    }
    fun showVehicleEditor() {
        editorOriginalIdentifier = selectedPreset.catalogIdentifier
        editorReturnPreset = selectedPreset
        editorReturnOverride = selectedVehicleOverride
        editorRestoresSelectionAfterSave = false
        editorHasCatalogSeed = !selectedPreset.catalogIdentifier.startsWith("custom:")
        isCreatingVehicle = false
        isEditingVehicle = true
    }

    /** Opens a Garage profile for editing without selecting it as the trip-planning vehicle.
     * iOS treats the pencil and the card tap as separate actions; Android must do the same. */
    fun showVehicleEditor(preset: EvPreset) {
        val currentPreset = selectedPreset
        val currentOverride = selectedVehicleOverride
        editorOriginalIdentifier = preset.catalogIdentifier
        editorReturnPreset = currentPreset
        editorReturnOverride = currentOverride
        editorRestoresSelectionAfterSave = preset.catalogIdentifier != currentPreset.catalogIdentifier
        selectedPreset = preset
        selectedVehicleOverride = settings.vehicleOverride(preset.catalogIdentifier)
        editorHasCatalogSeed = !preset.catalogIdentifier.startsWith("custom:")
        isCreatingVehicle = false
        isEditingVehicle = true
    }
    fun showCustomVehicleEditor() {
        pickerShowsGarageOnly = false
        if (pickerReturnsToEditor) {
            editorReturnPreset?.let { selectedPreset = it }
            selectedVehicleOverride = editorReturnOverride
            pickerReturnsToEditor = false
            isPickingVehicle = false
            editorHasCatalogSeed = !isCreatingVehicle && !selectedPreset.catalogIdentifier.startsWith("custom:")
            isEditingVehicle = true
            return
        }
        editorOriginalIdentifier = null
        editorReturnPreset = selectedPreset
        editorReturnOverride = selectedVehicleOverride
        editorRestoresSelectionAfterSave = false
        editorHasCatalogSeed = false
        isPickingVehicle = false
        isCreatingVehicle = true
        isEditingVehicle = true
    }
    fun hideVehicleEditor() {
        editorReturnPreset?.let { selectedPreset = it }
        selectedVehicleOverride = editorReturnOverride
        clearVehicleEditorState()
    }
    private fun clearVehicleEditorState() {
        isEditingVehicle = false
        isPickingVehicle = false
        isCreatingVehicle = false
        editorHasCatalogSeed = false
        pickerShowsGarageOnly = false
        editorOriginalIdentifier = null
        editorReturnPreset = null
        editorReturnOverride = null
        editorRestoresSelectionAfterSave = false
        pickerReturnsToEditor = false
    }
    fun replaceVehicleFromEditor() {
        pickerShowsGarageOnly = false
        pickerReturnsToEditor = true
        isPickingVehicle = true
    }
    fun showSettings() { isEditingSettings = true }
    fun hideSettings() { isEditingSettings = false }
    fun showLicenses() { isViewingLicenses = true }
    fun hideLicenses() { isViewingLicenses = false }
    fun showInformation(page: InformationPage) { informationPage = page }
    fun hideInformation() { informationPage = null }

    fun updateRegion(value: Region) {
        if (region == value) return
        region = value
        settings.region = value
        usesMiles = settings.usesMiles
        preferredNetworks = value.defaultNetworks
        avoidedNetworks = emptySet()
        settings.preferredNetworks = preferredNetworks
        settings.avoidedNetworks = avoidedNetworks
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
        minimumChargerSpeedKw = value.coerceIn(50f, 350f)
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

    fun updatePreferredNetworks(value: Set<String>) {
        preferredNetworks = value.intersect(region.defaultNetworks)
        settings.preferredNetworks = preferredNetworks
        invalidatePlan()
    }

    fun updateAvoidedNetworks(text: String) {
        avoidedNetworks = parseNetworks(text)
        settings.avoidedNetworks = avoidedNetworks
        invalidatePlan()
    }

    fun updateAvoidedNetworks(value: Set<String>) {
        avoidedNetworks = value.intersect(region.defaultNetworks)
        settings.avoidedNetworks = avoidedNetworks
        invalidatePlan()
    }

    fun resetAllToDefaults() {
        startSearchJob?.cancel()
        startSearchJob = null
        destinationSearchJob?.cancel()
        destinationSearchJob = null
        waypoints.forEach { it.searchJob?.cancel() }
        rerouteJob?.cancel()
        rerouteJob = null
        invalidatePlan()
        settings.resetAll()

        region = settings.region
        usesMiles = region.usesImperialByDefault
        preferredNav = settings.preferredNav
        customVehicleRecords = emptyList()
        garageVehicleIdentifiers = starterGarage.map(EvPreset::catalogIdentifier)
        selectedPreset = garageVehicles.firstOrNull() ?: EvCatalog.default
        selectedVehicleOverride = null
        currentSocPercent = 70f
        arrivalBufferPercent = 15f
        useScheduledDeparture = false
        scheduledDepartureMillis = System.currentTimeMillis() + 5 * 60_000L
        weatherRangeLossPercent = 0f
        extraLoadKg = 0f
        drivingStyle = RangeDrivingStyle.BALANCED
        minimumChargerSpeedKw = 50f
        avoidLowConfidenceStations = false
        preferredNetworks = region.defaultNetworks
        avoidedNetworks = emptySet()
        waypoints.clear()
        startSuggestions = emptyList()
        destinationSuggestions = emptyList()
        searchMessage = null
        savedTrips = emptyList()
        savedTripMessage = "Settings and saved data were reset."
        navSession = null
        arrivalSuggested = false
        isGuidedNavigationOpen = false
        activeNavigationRoute = null
        navigationOrigin = null
        navigationBaselineBatteryPercent = 70.0
        navigationBaselineStartedAtMillis = 0L
        isRerouting = false
        navigationError = null
        clearVehicleEditorState()
        isViewingLicenses = false
        informationPage = null

        // Keep onboarding completed just as iOS does, then persist the fresh Garage identities.
        hasOnboarded = true
        settings.hasOnboarded = true
        settings.garageVehicleIdentifiers = garageVehicleIdentifiers
        settings.selectedVehicleIdentifier = selectedPreset.catalogIdentifier
    }

    fun selectPreset(preset: EvPreset) {
        if (pickerReturnsToEditor) {
            selectedPreset = preset
            selectedVehicleOverride = null
            editorHasCatalogSeed = true
            editorSeedRevision += 1
            pickerReturnsToEditor = false
            isPickingVehicle = false
            pickerShowsGarageOnly = false
            isEditingVehicle = true
            return
        }
        if (preset.catalogIdentifier !in garageVehicleIdentifiers) {
            garageVehicleIdentifiers = normalizeGarageVehicleIdentifiers(
                listOf(preset.catalogIdentifier) + garageVehicleIdentifiers,
            )
            settings.garageVehicleIdentifiers = garageVehicleIdentifiers
        }
        selectedPreset = preset
        selectedVehicleOverride = settings.vehicleOverride(preset.catalogIdentifier)
        settings.selectedVehicleIdentifier = preset.catalogIdentifier
        isPickingVehicle = false
        pickerShowsGarageOnly = false
        editorHasCatalogSeed = false
        editorOriginalIdentifier = null
        editorReturnPreset = null
        editorReturnOverride = null
        invalidatePlan()
    }

    fun configuredPresetFor(preset: EvPreset): EvPreset =
        settings.vehicleOverride(preset.catalogIdentifier)?.applyTo(preset)
            ?: preset.copy(connectorTypes = preset.connectorTypes(region.isEuropean))

    fun batteryHealthFor(preset: EvPreset): Double =
        settings.vehicleOverride(preset.catalogIdentifier)?.batteryHealthPercent ?: 100.0

    fun removeGarageVehicle(preset: EvPreset) {
        if (garageVehicleIdentifiers.size <= 1) return
        val updated = garageVehicleIdentifiers.filterNot { it == preset.catalogIdentifier }
        if (updated.size == garageVehicleIdentifiers.size) return
        garageVehicleIdentifiers = updated
        settings.garageVehicleIdentifiers = updated
        settings.setVehicleOverride(preset.catalogIdentifier, null)
        if (preset.catalogIdentifier.startsWith("custom:")) {
            customVehicleRecords = customVehicleRecords.filterNot { it.identifier == preset.catalogIdentifier }
            settings.customVehicles = customVehicleRecords
        }
        if (selectedPreset.catalogIdentifier == preset.catalogIdentifier) {
            val replacement = updated.firstNotNullOfOrNull(::presetForIdentifier) ?: EvCatalog.default
            selectedPreset = replacement
            selectedVehicleOverride = settings.vehicleOverride(replacement.catalogIdentifier)
            settings.selectedVehicleIdentifier = replacement.catalogIdentifier
            invalidatePlan()
        }
    }

    fun deleteVehicleBeingEdited() {
        val identifier = editorOriginalIdentifier ?: return
        val original = presetForIdentifier(identifier) ?: return
        val restorePreset = editorReturnPreset
        val shouldRestoreSelection = editorRestoresSelectionAfterSave
        selectedPreset = original
        selectedVehicleOverride = settings.vehicleOverride(identifier)
        removeGarageVehicle(original)
        if (shouldRestoreSelection && restorePreset != null) {
            presetForIdentifier(restorePreset.catalogIdentifier)?.let { restored ->
                selectedPreset = restored
                selectedVehicleOverride = settings.vehicleOverride(restored.catalogIdentifier)
                settings.selectedVehicleIdentifier = restored.catalogIdentifier
            }
        }
        clearVehicleEditorState()
    }

    fun saveVehicle(
        make: String,
        model: String,
        year: Int,
        batteryCapacityKwh: Double,
        maxDcChargingKw: Int,
        efficiencyKwhPer100Km: Double,
        batteryHealthPercent: Double,
        defaultArrivalBufferPercent: Int,
        connectors: Set<ConnectorType>,
    ): String? {
        val normalizedMake = make.trim()
        val normalizedModel = model.trim()
        val efficiency = efficiencyKwhPer100Km / 100.0
        val error = when {
            normalizedMake.isEmpty() -> "Vehicle make is required."
            normalizedModel.isEmpty() -> "Vehicle model is required."
            normalizedMake.length > 100 || normalizedModel.length > 150 -> "Vehicle name is too long."
            year !in 2010..(Calendar.getInstance().get(Calendar.YEAR) + 1) -> "Enter a valid model year."
            !batteryCapacityKwh.isFinite() || batteryCapacityKwh !in 15.0..300.0 -> "Battery capacity must be between 15 and 300 kWh."
            maxDcChargingKw !in 25..500 -> "Maximum DC charging must be between 25 and 500 kW."
            !efficiency.isFinite() || efficiency <= 0.05 || efficiency > 0.50 -> "Consumption must be above 5 and no more than 50 kWh/100 km."
            !batteryHealthPercent.isFinite() || batteryHealthPercent !in 60.0..100.0 -> "Battery health must be between 60% and 100%."
            defaultArrivalBufferPercent !in 5..35 -> "Arrival buffer must be between 5% and 35%."
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
            defaultArrivalBufferPercent = defaultArrivalBufferPercent,
        )
        val sourceCatalogIdentifier = when {
            !selectedPreset.catalogIdentifier.startsWith("custom:") -> selectedPreset.catalogIdentifier
            else -> customVehicleRecords.firstOrNull {
                it.identifier == selectedPreset.catalogIdentifier
            }?.sourceCatalogIdentifier
        }
        val sourcePreset = presetForIdentifier(sourceCatalogIdentifier)
        val sourceIdentityStillMatches = sourcePreset != null &&
            normalizedMake.equals(sourcePreset.make, ignoreCase = true) &&
            normalizedModel.equals(sourcePreset.model, ignoreCase = true) &&
            year == sourcePreset.year
        val mustCreateIndependentProfile = isCreatingVehicle || (
            editorOriginalIdentifier != null &&
                editorHasCatalogSeed &&
                editorOriginalIdentifier != selectedPreset.catalogIdentifier
            )
        val identityStillMatchesCatalog = !mustCreateIndependentProfile &&
            !selectedPreset.catalogIdentifier.startsWith("custom:") &&
            normalizedMake.equals(selectedPreset.make, ignoreCase = true) &&
            normalizedModel.equals(selectedPreset.model, ignoreCase = true) &&
            year == selectedPreset.year
        val identifier = if (identityStillMatchesCatalog) {
            selectedPreset.catalogIdentifier
        } else {
            editorOriginalIdentifier?.takeIf {
                !editorHasCatalogSeed && it.startsWith("custom:")
            } ?: "custom:${UUID.randomUUID()}"
        }

        var persistedPreset = selectedPreset
        if (identifier.startsWith("custom:")) {
            val record = CustomVehicleRecord(
                identifier = identifier,
                make = normalizedMake,
                model = normalizedModel,
                year = year,
                batteryCapacityKwh = batteryCapacityKwh,
                maxDcChargingKw = maxDcChargingKw,
                efficiencyKwhPerKm = efficiency,
                connectorNames = connectors.map { it.name }.sorted(),
                sourceCatalogIdentifier = sourceCatalogIdentifier.takeIf { sourceIdentityStillMatches },
            )
            customVehicleRecords = (listOf(record) + customVehicleRecords.filterNot { it.identifier == identifier })
                .take(MAX_GARAGE_VEHICLES)
            settings.customVehicles = customVehicleRecords
            persistedPreset = record.toPreset()
        } else {
            persistedPreset = presetForIdentifier(identifier) ?: selectedPreset
        }

        val previousIdentifier = editorOriginalIdentifier
        garageVehicleIdentifiers = replacingGarageVehicleIdentifier(
            values = garageVehicleIdentifiers,
            original = previousIdentifier,
            replacement = identifier,
        )
        settings.garageVehicleIdentifiers = garageVehicleIdentifiers
        if (previousIdentifier != null && previousIdentifier != identifier) {
            if (previousIdentifier.startsWith("custom:")) {
                customVehicleRecords = customVehicleRecords.filterNot { it.identifier == previousIdentifier }
                settings.customVehicles = customVehicleRecords
            }
            settings.setVehicleOverride(previousIdentifier, null)
        }

        val restorePreset = editorReturnPreset
        val shouldRestoreSelection = editorRestoresSelectionAfterSave
        settings.setVehicleOverride(identifier, override)
        if (shouldRestoreSelection && restorePreset != null) {
            selectedPreset = presetForIdentifier(restorePreset.catalogIdentifier) ?: restorePreset
            selectedVehicleOverride = settings.vehicleOverride(selectedPreset.catalogIdentifier)
            settings.selectedVehicleIdentifier = selectedPreset.catalogIdentifier
        } else {
            selectedPreset = persistedPreset
            selectedVehicleOverride = override
            settings.selectedVehicleIdentifier = identifier
        }
        clearVehicleEditorState()
        invalidatePlan()
        return null
    }

    fun resetVehicleOverride() {
        if (selectedPreset.catalogIdentifier.startsWith("custom:")) {
            // A manually entered profile has no external catalog baseline to restore.
            isEditingVehicle = false
            isCreatingVehicle = false
            return
        }
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
        arrivalBufferPercent = snapshot.arrivalBufferPercent.coerceIn(5f, 35f)
        settings.arrivalBufferPercent = arrivalBufferPercent
        presetForIdentifier(snapshot.vehicleIdentifier)?.let { preset ->
            if (preset.catalogIdentifier !in garageVehicleIdentifiers) {
                garageVehicleIdentifiers = (garageVehicleIdentifiers + preset.catalogIdentifier).distinct()
                settings.garageVehicleIdentifiers = garageVehicleIdentifiers
            }
            selectedPreset = preset
            selectedVehicleOverride = settings.vehicleOverride(preset.catalogIdentifier)
            settings.selectedVehicleIdentifier = preset.catalogIdentifier
        }
        weatherRangeLossPercent = snapshot.weatherRangeLossPercent.coerceIn(0f, 45f)
        extraLoadKg = snapshot.extraLoadKg.coerceIn(0f, 750f)
        drivingStyle = RangeDrivingStyle.fromSerialized(snapshot.drivingStyle)
        minimumChargerSpeedKw = snapshot.minimumChargerSpeedKw.coerceIn(50, 350).toFloat()
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
        val now = System.currentTimeMillis()
        navSession = NavigationSession.create(stops, destinationPoint, app, now)
        activeNavigationRoute = option
        navigationOrigin = start?.let { LatLon(it.latitude, it.longitude) }
        navigationBaselineBatteryPercent = currentSocPercent.toDouble()
        navigationBaselineStartedAtMillis = now
        navigationError = null
        arrivalSuggested = false
        isGuidedNavigationOpen = true
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
        if (navSession == null) {
            isGuidedNavigationOpen = false
            activeNavigationRoute = null
            navigationOrigin = null
        }
        arrivalSuggested = false
        settings.navigationSession = navSession
    }

    fun endGuidedTrip() {
        rerouteJob?.cancel()
        rerouteJob = null
        navSession = null
        activeNavigationRoute = null
        navigationOrigin = null
        isRerouting = false
        navigationError = null
        arrivalSuggested = false
        isGuidedNavigationOpen = false
        settings.navigationSession = null
    }

    fun dismissGuidedNavigation() {
        isGuidedNavigationOpen = false
    }

    /** Rebuilds the complete EV plan from the live position, including charging stops and SOC.
     * This matches iOS rerouting behavior; it never swaps only the blue map line while leaving an
     * unsafe charging plan behind. */
    fun rerouteActiveTrip(currentSegmentIndex: Int) {
        val current = currentLocation
        val destinationSnapshot = destination
        val previousRoute = activeNavigationRoute
        if (current == null || destinationSnapshot == null || previousRoute == null) {
            navigationError = "A current location and active route are required to reroute."
            return
        }
        if (isRerouting) return

        val remainingWaypoints = previousRoute.userWaypoints.indices.mapNotNull { index ->
            val segment = previousRoute.userWaypointSegmentIndices.getOrNull(index) ?: Int.MAX_VALUE
            previousRoute.userWaypoints[index].takeIf { segment >= currentSegmentIndex }
        }
        val now = System.currentTimeMillis()
        val elapsedMinutes = if (navigationBaselineStartedAtMillis > 0L) {
            ((now - navigationBaselineStartedAtMillis).coerceAtLeast(0L) / 60_000.0)
        } else {
            0.0
        }
        val progress = if (previousRoute.totalEtaMinutes > 0) {
            (elapsedMinutes / previousRoute.totalEtaMinutes).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val estimatedBattery = (
            navigationBaselineBatteryPercent +
                (previousRoute.arrivalBatteryPercent - navigationBaselineBatteryPercent) * progress
            ).coerceIn(5.0, 100.0)
        val requestedObjective = previousRoute.objective
        val app = navSession?.app ?: preferredNav

        rerouteJob?.cancel()
        isRerouting = true
        navigationError = null
        rerouteJob = viewModelScope.launch {
            try {
                when (
                    val result = planner.planThrough(
                        start = current,
                        waypoints = remainingWaypoints,
                        destination = LatLon(destinationSnapshot.latitude, destinationSnapshot.longitude),
                        vehicle = baseVehicle,
                        currentSOC = estimatedBattery,
                        arrivalBufferPercent = arrivalBufferPercent.toDouble(),
                        conditions = planningConditions,
                        preferences = planningPreferences,
                    )
                ) {
                    is TripPlanner.Result.Success -> {
                        val route = result.options.firstOrNull { option ->
                            option.objective == requestedObjective || requestedObjective in option.supportedObjectives
                        } ?: result.options.firstOrNull()
                        if (route == null) {
                            navigationError = "No safe updated charging route is available."
                            return@launch
                        }
                        val updated = route.copy(plannedDepartureMillis = now)
                        activeNavigationRoute = updated
                        navigationOrigin = current
                        navigationBaselineBatteryPercent = estimatedBattery
                        navigationBaselineStartedAtMillis = now
                        navSession = NavigationSession.create(
                            updated.orderedNavigationPoints(),
                            NavigationPoint(
                                destinationSnapshot.latitude,
                                destinationSnapshot.longitude,
                                destinationSnapshot.placeName,
                                NavigationPoint.Kind.DESTINATION,
                            ),
                            app,
                            now,
                        )
                        settings.navigationSession = navSession
                        arrivalSuggested = false
                    }
                    is TripPlanner.Result.Error -> navigationError = result.message
                }
            } finally {
                isRerouting = false
            }
        }
    }

    fun onLocationSample(latitude: Double, longitude: Double, accuracyMeters: Double?, sampleMillis: Long) {
        if (!updateCurrentLocationAnchor(latitude, longitude, accuracyMeters)) return
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
        if (currentSocPercent <= arrivalBufferPercent) {
            errorMessage = "Starting battery must be higher than the arrival buffer."
            return
        }
        if (waypoints.any { it.text.isNotBlank() && it.selected == null }) {
            errorMessage = "Pick each stop from its search results."
            return
        }

        val stops = waypoints.mapNotNull { it.selected }
        val itinerary = listOf(from) + stops + to
        itineraryValidationError(itinerary)?.let { validationError ->
            errorMessage = validationError
            return
        }
        val requestedObjective = selectedOption?.objective
        val departureSnapshot = effectiveDepartureMillis()
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
                        options = result.options.map { option ->
                            option.copy(plannedDepartureMillis = departureSnapshot)
                        }
                        selectedIndex = requestedObjective?.let { objective ->
                            options.indexOfFirst { option ->
                                option.objective == objective || objective in option.supportedObjectives
                            }.takeIf { it >= 0 }
                        } ?: 0
                        lastPlanComputedAtMillis = System.currentTimeMillis()
                        // A cached replan can finish before Compose observes the transient
                        // isPlanning=true state. Publish a durable completion revision so every
                        // successful button press can present its fresh results.
                        successfulPlanRevision = nextSuccessfulPlanRevision(successfulPlanRevision)
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
            ?: currentLocation
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

internal fun nextSuccessfulPlanRevision(current: Long): Long =
    if (current == Long.MAX_VALUE) 1L else current + 1L

internal fun itineraryValidationError(itinerary: List<PlaceCandidate>): String? {
    val start = itinerary.firstOrNull()
    val destination = itinerary.lastOrNull()
    if (itinerary.size > 1 && start != null && destination != null &&
        start.latitude == destination.latitude && start.longitude == destination.longitude
    ) {
        return "Start and destination cannot be the same."
    }
    val unsupported = itinerary.firstOrNull { !Region.supports(it.countryCode) }
    if (unsupported != null) {
        return "${unsupported.placeName} is in ${unsupported.countryCode ?: "an unsupported country"}, where EV FastRoute does not yet support charging plans."
    }
    if (itinerary.zipWithNext().any { (left, right) ->
            Geometry.haversineMeters(
                left.latitude, left.longitude, right.latitude, right.longitude,
            ) < 20.0
        }
    ) {
        return "Two consecutive trip locations are the same. Remove or replace the duplicate stop."
    }
    return null
}

private fun PlaceCandidate.toSavedPlace(): SavedPlace = SavedPlace(
    name = placeName,
    address = fullAddress,
    latitude = latitude,
    longitude = longitude,
    countryCode = countryCode,
)

private fun SavedPlace.toCandidate(): PlaceCandidate = PlaceCandidate(
    placeName = name,
    fullAddress = address,
    latitude = latitude,
    longitude = longitude,
    countryCode = countryCode,
)
