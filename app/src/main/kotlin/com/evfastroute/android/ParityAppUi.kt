package com.evfastroute.android

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evfastroute.android.map.RouteMap
import com.evfastroute.android.map.RouteMapCameraMode
import com.evfastroute.android.nav.LocationProvider
import com.evfastroute.android.nav.NavLauncher
import com.evfastroute.core.ChargerDataSource
import com.evfastroute.core.ChargerStatus
import com.evfastroute.core.ChargingStop
import com.evfastroute.core.EvCatalog
import com.evfastroute.core.EvPreset
import com.evfastroute.core.ConnectorType
import com.evfastroute.core.Geometry
import com.evfastroute.core.ItineraryStop
import com.evfastroute.core.LatLon
import com.evfastroute.core.ManeuverTracker
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.RangeDrivingStyle
import com.evfastroute.core.Region
import com.evfastroute.core.RouteObjective
import com.evfastroute.core.RouteOption
import com.evfastroute.core.Units
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

private enum class AppSection(val label: String, val icon: ImageVector) {
    PLAN("Plan", Icons.Filled.ElectricCar),
    ROUTE("Route", Icons.Filled.Map),
    GARAGE("Garage", Icons.Filled.DirectionsCar),
    SETTINGS("Settings", Icons.Filled.Tune),
}

private enum class SearchKind { START, DESTINATION, WAYPOINT }

private enum class NetworkPickerKind { PREFERRED, AVOIDED }

private data class ChargerBrowseTarget(val title: String, val center: LatLon)

private val weatherRangeChoices = listOf(
    0f to "Mild",
    8f to "Hot / strong A/C (−8%)",
    10f to "Cool or wet (−10%)",
    20f to "Cold (−20%)",
    35f to "Freezing (−35%)",
)

private val tripLoadChoices = listOf(
    0f to "Driver only",
    150f to "Passengers + bags",
    300f to "Full load",
    500f to "Very heavy",
)

private data class AddressSearchTarget(
    val kind: SearchKind,
    val waypointId: Int? = null,
    val createdNewWaypoint: Boolean = false,
) {
    val title: String
        get() = when (kind) {
            SearchKind.START -> "Starting Location"
            SearchKind.DESTINATION -> "Destination"
            SearchKind.WAYPOINT -> "Trip Stop"
        }
}

/**
 * Android's product shell, intentionally matched to the iOS information architecture and visual
 * language. Platform-owned permission dialogs, maps and external-navigation apps remain native.
 */
@Composable
fun ParityPlannerApp(
    vm: TripViewModel = viewModel(),
) {
    if (!vm.hasOnboarded) {
        EvGradientBackground {
            OnboardingScreen(onContinue = vm::completeOnboarding)
        }
        return
    }

    if (vm.isViewingLicenses) {
        BackHandler(onBack = vm::hideLicenses)
        EvGradientBackground { ParityLicensesScreen(vm::hideLicenses) }
        return
    }
    vm.informationPage?.let { page ->
        BackHandler(onBack = vm::hideInformation)
        EvGradientBackground {
            ParityInformationScreen(
                page = page,
                onClose = vm::hideInformation,
                onShowLicenses = {
                    vm.hideInformation()
                    vm.showLicenses()
                },
            )
        }
        return
    }
    if (vm.isEditingVehicle) {
        BackHandler(onBack = vm::hideVehicleEditor)
        EvGradientBackground { ParityVehicleEditor(vm) }
        return
    }
    if (vm.isPickingVehicle) {
        BackHandler(onBack = vm::hideVehiclePicker)
        EvGradientBackground {
            if (vm.pickerShowsGarageOnly) {
                GarageVehiclePicker(vm = vm, onClose = vm::hideVehiclePicker)
            } else {
                ParityVehiclePicker(
                    current = vm.selectedPreset,
                    usesMiles = vm.usesMiles,
                    onSelect = vm::selectPreset,
                    onCreateCustom = vm::showCustomVehicleEditor,
                    onClose = vm::hideVehiclePicker,
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshRoutesIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun hasForegroundLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var hasLocationPermission by remember { mutableStateOf(hasForegroundLocationPermission()) }
    var useLocationAsStartAfterGrant by remember { mutableStateOf(false) }
    val oneShotLocation = remember(context) { LocationProvider(context) }

    fun sampleDeviceLocation(useAsStart: Boolean) {
        oneShotLocation.oneShot(
            onSample = { lat, lon, accuracy, _ ->
                if (useAsStart) vm.useCurrentLocation(lat, lon, accuracy)
                else vm.updateCurrentLocationAnchor(lat, lon, accuracy)
            },
            onUnavailable = { if (useAsStart) vm.reportLocationUnavailable() },
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasLocationPermission = grants.values.any { it } || hasForegroundLocationPermission()
        if (hasLocationPermission) {
            sampleDeviceLocation(useLocationAsStartAfterGrant)
        } else if (useLocationAsStartAfterGrant) {
            vm.reportLocationPermissionDenied()
        }
        useLocationAsStartAfterGrant = false
    }

    fun requestLocationPermission(useAsStart: Boolean) {
        useLocationAsStartAfterGrant = useAsStart
        if (hasForegroundLocationPermission()) {
            hasLocationPermission = true
            sampleDeviceLocation(useAsStart)
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            )
        }
    }
    DisposableEffect(oneShotLocation) { onDispose { oneShotLocation.stop() } }

    val activeSession = vm.navSession
    if (activeSession != null && activeSession.currentPoint != null && hasLocationPermission) {
        DisposableEffect(lifecycleOwner, activeSession.currentPoint) {
            val provider = LocationProvider(context)
            fun startProvider() {
                provider.start { lat, lon, accuracy, time ->
                    vm.onLocationSample(lat, lon, accuracy, time)
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        hasLocationPermission = hasForegroundLocationPermission()
                        if (hasLocationPermission) startProvider()
                    }
                    Lifecycle.Event.ON_PAUSE -> provider.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            // Effects are commonly installed after the Activity is already resumed, in which
            // case no lifecycle event is replayed. Begin tracking immediately for a newly started
            // guided trip instead of waiting for a background/foreground cycle.
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                startProvider()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                provider.stop()
            }
        }
    }

    EvGradientBackground {
        if (vm.isGuidedNavigationOpen && vm.navSession != null && vm.activeNavigationRoute != null) {
            BackHandler(onBack = vm::dismissGuidedNavigation)
            GuidedNavigationScreen(
                vm = vm,
                hasLocationPermission = hasLocationPermission,
                onRequestLocation = { requestLocationPermission(false) },
            )
        } else {
            AppShell(
                vm = vm,
                hasLocationPermission = hasLocationPermission,
                onRequestLocation = { requestLocationPermission(false) },
                onUseCurrentLocation = { requestLocationPermission(true) },
            )
        }
    }
}

@Composable
private fun GuidedNavigationScreen(
    vm: TripViewModel,
    hasLocationPermission: Boolean,
    onRequestLocation: () -> Unit,
) {
    val option = vm.activeNavigationRoute ?: return
    val session = vm.navSession ?: return
    val destination = vm.destination
    var maneuverIndex by remember(option.id) { mutableIntStateOf(0) }
    var distanceToManeuver by remember(option.id) { mutableStateOf<Double?>(null) }
    var previousManeuverDistance by remember(option.id) { mutableStateOf<Double?>(null) }
    var offRouteSamples by remember(option.id) { mutableIntStateOf(0) }
    var cameraMode by remember(option.id) { mutableStateOf(RouteMapCameraMode.FOLLOWING) }
    var cameraActionToken by remember(option.id) { mutableIntStateOf(0) }
    val currentManeuver = option.routeSteps.getOrNull(maneuverIndex)

    LaunchedEffect(vm.currentLocation, option.id) {
        val location = vm.currentLocation ?: return@LaunchedEffect
        val progress = ManeuverTracker.current(
            user = location,
            maneuvers = option.routeSteps,
            lastReachedIndex = maneuverIndex,
            previousDistanceMeters = previousManeuverDistance,
        )
        if (progress != null) {
            maneuverIndex = progress.index
            distanceToManeuver = progress.distanceMeters
            previousManeuverDistance = progress.distanceMeters
        }

        // Match iOS: three consecutive accurate samples more than 120 m from the route trigger a
        // complete EV reroute (road geometry + chargers + battery plan), never a map-only patch.
        val accurateEnoughForOffRoute = vm.currentLocationAccuracyMeters?.let { it <= 100.0 } == true
        if (option.geometry.size > 1 && accurateEnoughForOffRoute) {
            val distanceToRoute = Geometry.distanceToPolylineMeters(location, option.geometry)
            offRouteSamples = if (distanceToRoute > 120.0) offRouteSamples + 1 else 0
            if (offRouteSamples >= 3 && !vm.isRerouting) {
                offRouteSamples = 0
                val segment = option.routeSteps.getOrNull(progress?.index ?: maneuverIndex)?.segmentIndex ?: 0
                vm.rerouteActiveTrip(segment)
            }
        } else {
            offRouteSamples = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RouteMap(
            routeGeometry = option.geometry,
            chargers = option.chargingStops.map { LatLon(it.latitude, it.longitude) },
            start = vm.navigationOrigin ?: vm.start?.let { LatLon(it.latitude, it.longitude) },
            destination = destination?.let { LatLon(it.latitude, it.longitude) },
            waypoints = option.userWaypoints.map { LatLon(it.latitude, it.longitude) },
            userLocation = vm.currentLocation,
            cameraMode = if (hasLocationPermission && vm.currentLocation != null) {
                cameraMode
            } else {
                RouteMapCameraMode.OVERVIEW
            },
            cameraActionToken = cameraActionToken,
            onManualInteraction = { cameraMode = RouteMapCameraMode.MANUAL },
            fallbackCenter = vm.region.searchCenter,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent))),
        )
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledIconButton(
                    onClick = vm::dismissGuidedNavigation,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.58f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close navigation map")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trip in progress", style = MaterialTheme.typography.labelLarge, color = EvMint)
                    Text(
                        session.currentPoint?.name ?: destination?.placeName.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.58f)) {
                    Text(
                        formatDuration(option.totalEtaMinutes),
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
            }
            if (vm.currentLocation != null && currentManeuver != null && distanceToManeuver != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.Navigation, contentDescription = null, tint = EvMint)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                ManeuverTracker.formatDistance(distanceToManeuver!!, vm.usesMiles),
                                style = MaterialTheme.typography.titleMedium,
                                color = EvMint,
                            )
                            Text(
                                currentManeuver.instruction,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (vm.isRerouting) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EvMint)
                        Text("Recalculating the complete EV route…", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            vm.navigationError?.let { message ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                ) {
                    Text(
                        message,
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NavigationMapControl(Icons.Filled.MyLocation, "Recenter") {
                cameraMode = RouteMapCameraMode.FOLLOWING
                cameraActionToken += 1
            }
            NavigationMapControl(Icons.Filled.Map, "Route overview") {
                cameraMode = RouteMapCameraMode.OVERVIEW
                cameraActionToken += 1
            }
            NavigationMapControl(Icons.Filled.Refresh, "Reroute") {
                vm.rerouteActiveTrip(currentManeuver?.segmentIndex ?: 0)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuidedTripBanner(
                session = session,
                arrivalSuggested = vm.arrivalSuggested,
                hasLocationPermission = hasLocationPermission,
                onRequestLocationPermission = onRequestLocation,
                onHandoffRecorded = vm::recordSessionHandoff,
                onArrived = vm::advanceGuidedTrip,
                onEnd = vm::endGuidedTrip,
            )
        }
    }
}

@Composable
private fun NavigationMapControl(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.68f),
            contentColor = Color.White,
        ),
    ) {
        Icon(icon, contentDescription = label)
    }
}

@Composable
private fun AppShell(
    vm: TripViewModel,
    hasLocationPermission: Boolean,
    onRequestLocation: () -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    var selectedSection by rememberSaveable { mutableStateOf(AppSection.PLAN) }
    var searchTarget by remember { mutableStateOf<AddressSearchTarget?>(null) }
    var chargerBrowseTarget by remember { mutableStateOf<ChargerBrowseTarget?>(null) }
    var requestedSearchAnchorLocation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(vm.successfulPlanRevision) {
        if (shouldOpenRouteResults(vm.successfulPlanRevision, vm.options.size)) {
            selectedSection = AppSection.ROUTE
        }
    }

    LaunchedEffect(searchTarget?.kind, vm.currentLocation, vm.start, vm.destination) {
        if (searchTarget != null &&
            vm.start == null && vm.destination == null &&
            vm.currentLocation == null &&
            !requestedSearchAnchorLocation
        ) {
            requestedSearchAnchorLocation = true
            onRequestLocation()
        }
    }

    searchTarget?.let { target ->
        BackHandler {
            removeUnusedNewWaypoint(vm, target)
            searchTarget = null
        }
        AddressSearchScreen(
            vm = vm,
            target = target,
            onCancel = {
                removeUnusedNewWaypoint(vm, target)
                searchTarget = null
            },
            onSelected = { searchTarget = null },
            onUseCurrentLocation = {
                onUseCurrentLocation()
                searchTarget = null
            },
        )
        return
    }

    chargerBrowseTarget?.let { target ->
        ChargerBrowseScreen(
            title = target.title,
            initialCenter = target.center,
            onBack = { chargerBrowseTarget = null },
        )
        return
    }

    BackHandler(enabled = selectedSection != AppSection.PLAN) {
        selectedSection = AppSection.PLAN
    }

    Column(modifier = Modifier.fillMaxSize()) {
        vm.navSession?.let { session ->
            if (session.currentPoint != null) {
                GuidedTripBanner(
                    session = session,
                    arrivalSuggested = vm.arrivalSuggested,
                    hasLocationPermission = hasLocationPermission,
                    onRequestLocationPermission = onRequestLocation,
                    onHandoffRecorded = vm::recordSessionHandoff,
                    onArrived = vm::advanceGuidedTrip,
                    onEnd = vm::endGuidedTrip,
                )
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                AppBottomBar(selected = selectedSection, onSelected = { selectedSection = it })
            },
        ) { padding ->
            when (selectedSection) {
                AppSection.PLAN -> PlannerScreen(
                    vm = vm,
                    contentPadding = padding,
                    onSearchStart = { searchTarget = AddressSearchTarget(SearchKind.START) },
                    onSearchDestination = { searchTarget = AddressSearchTarget(SearchKind.DESTINATION) },
                    onSearchWaypoint = { field ->
                        searchTarget = AddressSearchTarget(SearchKind.WAYPOINT, field.id)
                    },
                    onAddWaypoint = {
                        if (vm.canAddWaypoint) {
                            vm.addWaypoint()
                            vm.waypoints.lastOrNull()?.let { field ->
                                searchTarget = AddressSearchTarget(
                                    kind = SearchKind.WAYPOINT,
                                    waypointId = field.id,
                                    createdNewWaypoint = true,
                                )
                            }
                        }
                    },
                    onOpenChargingMap = {
                        if (vm.currentLocation == null) onRequestLocation()
                        val center = vm.currentLocation
                            ?: vm.start?.let { LatLon(it.latitude, it.longitude) }
                            ?: vm.region.defaultMapCenter
                        chargerBrowseTarget = ChargerBrowseTarget("Charging stations", center)
                    },
                    onOpenDestinationChargers = {
                        vm.destination?.let { destination ->
                            chargerBrowseTarget = ChargerBrowseTarget(
                                title = "Chargers at destination",
                                center = LatLon(destination.latitude, destination.longitude),
                            )
                        }
                    },
                )
                AppSection.ROUTE -> ResultsScreen(
                    vm = vm,
                    contentPadding = padding,
                    onEditTrip = { selectedSection = AppSection.PLAN },
                )
                AppSection.GARAGE -> GarageScreen(vm, padding)
                AppSection.SETTINGS -> SettingsTab(vm, padding)
            }
        }
    }
}

private fun removeUnusedNewWaypoint(vm: TripViewModel, target: AddressSearchTarget) {
    if (!target.createdNewWaypoint) return
    val index = vm.waypoints.indexOfFirst { it.id == target.waypointId }
    if (index >= 0 && vm.waypoints[index].selected == null && vm.waypoints[index].text.isBlank()) {
        vm.removeWaypoint(index)
    }
}

@Composable
private fun AppBottomBar(selected: AppSection, onSelected: (AppSection) -> Unit) {
    NavigationBar(
        containerColor = EvChrome.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
    ) {
        AppSection.entries.forEach { section ->
            NavigationBarItem(
                selected = section == selected,
                onClick = { onSelected(section) },
                icon = { Icon(section.icon, contentDescription = null) },
                label = { Text(section.label) },
                modifier = Modifier.testTag("tab_${section.label.lowercase()}"),
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = EvMint,
                    selectedTextColor = EvMint,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

@Composable
internal fun EvGradientBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        EvBackgroundTop,
                        EvBackgroundMiddle,
                        EvBackgroundBottom,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(260.dp)
                .offset(x = 90.dp, y = (-80).dp)
                .blur(70.dp)
                .background(EvCyan.copy(alpha = 0.20f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(280.dp)
                .offset(x = (-120).dp, y = 80.dp)
                .blur(85.dp)
                .background(EvMint.copy(alpha = 0.13f), CircleShape),
        )
        content()
    }
}

@Composable
private fun OnboardingScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
                    .background(EvCyan.copy(alpha = 0.18f), CircleShape),
            )
            Icon(
                Icons.Filled.ElectricCar,
                contentDescription = null,
                tint = EvCyan,
                modifier = Modifier.size(100.dp),
            )
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = EvMint,
                modifier = Modifier.size(42.dp).padding(bottom = 12.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "EV FastRoute",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 42.sp, lineHeight = 48.sp),
                textAlign = TextAlign.Center,
            )
            Text(
                "Plan EV trips by total arrival time, including charging.",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 25.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingFeature(Icons.Filled.Timer, "Optimizes total ETA", "Balances drive time, detours, charge speed, and station confidence.")
            OnboardingFeature(Icons.Filled.VerifiedUser, "Station-confidence aware", "Uses operational status, power, and site size as planning signals.")
            OnboardingFeature(Icons.Filled.Public, "International routing", "Supports North America and major European countries.")
        }
        Text(
            "Charging times and station confidence are estimates; free-stall availability may be unavailable and pricing incomplete. Always confirm in the operator's app.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EvMint, contentColor = Color(0xFF001F1E)),
        ) {
            Text("Get Started", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun OnboardingFeature(icon: ImageVector, title: String, subtitle: String) {
    GlassCard(contentPadding = 14.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = EvMint, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlannerScreen(
    vm: TripViewModel,
    contentPadding: PaddingValues,
    onSearchStart: () -> Unit,
    onSearchDestination: () -> Unit,
    onSearchWaypoint: (WaypointField) -> Unit,
    onAddWaypoint: () -> Unit,
    onOpenChargingMap: () -> Unit,
    onOpenDestinationChargers: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("planner_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 22.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 34.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { LargeScreenTitle("Trip Planner") }
        item { PlannerHeroMap(vm) }
        item {
            ChargingBrowseActions(
                hasDestination = vm.destination != null,
                onOpenChargingMap = onOpenChargingMap,
                onOpenDestinationChargers = onOpenDestinationChargers,
            )
        }
        item {
            PlannerTripCard(
                vm = vm,
                onSearchStart = onSearchStart,
                onSearchDestination = onSearchDestination,
                onSearchWaypoint = onSearchWaypoint,
                onAddWaypoint = onAddWaypoint,
            )
        }

        vm.errorMessage?.let { message ->
            item { InlineNotice(message, MaterialTheme.colorScheme.error) }
        }
        vm.searchMessage?.let { message ->
            item { InlineNotice(message, EvCyan) }
        }

        item { PlannerVehicleCard(vm) }
        item { PlannerChargingPreferences(vm) }
        item { RangeBand(vm) }

        if (vm.savedTrips.isNotEmpty() || (vm.start != null && vm.destination != null)) {
            item {
                SavedTripsCard(
                    trips = vm.savedTrips,
                    message = vm.savedTripMessage,
                    onSave = vm::saveCurrentTrip,
                    onLoad = vm::loadSavedTrip,
                    onDelete = vm::deleteSavedTrip,
                )
            }
        }

        item {
            Button(
                onClick = vm::plan,
                enabled = !vm.isPlanning,
                modifier = Modifier.fillMaxWidth().height(58.dp).testTag("find_route"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EvCyan,
                    contentColor = Color(0xFF001F29),
                    disabledContainerColor = EvCyan.copy(alpha = 0.35f),
                ),
            ) {
                if (vm.isPlanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color(0xFF001F29),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Finding routes…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Filled.Bolt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Find Fastest Route", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun ChargingBrowseActions(
    hasDestination: Boolean,
    onOpenChargingMap: () -> Unit,
    onOpenDestinationChargers: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onOpenChargingMap,
            modifier = Modifier.weight(1f).testTag("charging_map_button"),
            shape = RoundedCornerShape(15.dp),
        ) {
            Icon(Icons.Filled.Map, contentDescription = null, tint = EvMint)
            Spacer(Modifier.width(7.dp))
            Text("Charging map", color = MaterialTheme.colorScheme.onSurface)
        }
        OutlinedButton(
            onClick = onOpenDestinationChargers,
            enabled = hasDestination,
            modifier = Modifier.weight(1f).testTag("destination_chargers_button"),
            shape = RoundedCornerShape(15.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = EvCyan)
            Spacer(Modifier.width(7.dp))
            Text("At destination", maxLines = 1)
        }
    }
}

internal fun shouldOpenRouteResults(successfulPlanRevision: Long, optionCount: Int): Boolean =
    successfulPlanRevision > 0L && optionCount > 0

@Composable
private fun PlannerHeroMap(vm: TripViewModel) {
    val selected = vm.selectedOption
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        RouteMap(
            routeGeometry = selected?.geometry.orEmpty(),
            chargers = selected?.chargingStops.orEmpty().map { LatLon(it.latitude, it.longitude) },
            start = vm.start?.let { LatLon(it.latitude, it.longitude) },
            destination = vm.destination?.let { LatLon(it.latitude, it.longitude) },
            waypoints = vm.waypoints.mapNotNull { it.selected }.map { LatLon(it.latitude, it.longitude) },
            fallbackCenter = vm.region.searchCenter,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.48f), Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                    ),
                ),
        )
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Route intelligence",
                style = MaterialTheme.typography.labelLarge,
                color = EvMint,
            )
            Text("Fastest total arrival", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
    }
}

@Composable
private fun PlannerTripCard(
    vm: TripViewModel,
    onSearchStart: () -> Unit,
    onSearchDestination: () -> Unit,
    onSearchWaypoint: (WaypointField) -> Unit,
    onAddWaypoint: () -> Unit,
) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlannerAddressRow(
                label = "Start",
                value = vm.startText,
                icon = Icons.Filled.MyLocation,
                onClick = onSearchStart,
                onClear = vm::clearStart,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FilledIconButton(
                    onClick = vm::swapAddresses,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        contentColor = EvMint,
                    ),
                ) {
                    Icon(Icons.Filled.SwapVert, contentDescription = "Swap start and destination")
                }
            }

            vm.waypoints.forEachIndexed { index, field ->
                PlannerAddressRow(
                    label = "Stop ${index + 1}",
                    value = field.text,
                    icon = Icons.Filled.LocationOn,
                    onClick = { onSearchWaypoint(field) },
                    onRemove = { vm.removeWaypoint(index) },
                )
                if (vm.waypoints.size > 1) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { vm.moveWaypoint(index, -1) }, enabled = index > 0) { Text("Move up") }
                        TextButton(onClick = { vm.moveWaypoint(index, 1) }, enabled = index < vm.waypoints.lastIndex) { Text("Move down") }
                    }
                }
            }

            TextButton(
                onClick = onAddWaypoint,
                enabled = vm.canAddWaypoint,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    if (vm.canAddWaypoint) Icons.Filled.AddCircle else Icons.Filled.CheckCircle,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (vm.canAddWaypoint) "Add stop" else "Stop limit reached")
            }

            PlannerAddressRow(
                label = "Destination",
                value = vm.destinationText,
                icon = Icons.Filled.Flag,
                onClick = onSearchDestination,
                onClear = vm::clearDestination,
            )

            HorizontalDivider(color = EvDivider)
            ScheduledDepartureControl(vm)

            HorizontalDivider(color = EvDivider)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                BatteryDial(vm.currentSocPercent.toInt(), "Now", Modifier.weight(1f))
                BatteryDial(vm.arrivalBufferPercent.toInt(), "Arrival", Modifier.weight(1f))
            }
            BrandedSlider(
                label = "Current Battery",
                value = vm.currentSocPercent,
                range = 5f..100f,
                suffix = "%",
                onChange = vm::updateCurrentSoc,
            )
            BrandedSlider(
                label = "Arrival Buffer",
                value = vm.arrivalBufferPercent,
                range = 5f..35f,
                suffix = "%",
                accent = EvCyan,
                onChange = vm::updateArrivalBuffer,
            )
        }
    }
}

@Composable
private fun PlannerAddressRow(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.Black.copy(alpha = 0.18f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = EvMint) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color(0xFF001F1E), modifier = Modifier.size(19.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value.ifBlank { "Tap to search…" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove $label") }
            } else if (onClear != null && value.isNotBlank()) {
                IconButton(onClick = onClear) { Icon(Icons.Filled.Close, contentDescription = "Clear $label") }
            } else {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScheduledDepartureControl(vm: TripViewModel) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.testTag("schedule_departure"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Schedule departure",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (vm.useScheduledDeparture) "Set the trip's planned arrival clock times"
                    else "Leave now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = vm.useScheduledDeparture,
                onCheckedChange = vm::updateUseScheduledDeparture,
                modifier = Modifier.testTag("schedule_departure_toggle"),
                colors = brandedSwitchColors(),
            )
        }

        if (vm.useScheduledDeparture) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        showDeparturePicker(
                            context = context,
                            selectedMillis = vm.scheduledDepartureMillis,
                            onSelected = vm::updateScheduledDeparture,
                        )
                    }
                    .testTag("leave_at_picker"),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = EvMint)
                    Text(
                        "Leave at",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        departureDateTimeLabel(vm.scheduledDepartureMillis),
                        style = MaterialTheme.typography.titleSmall,
                        color = EvMint,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

private fun showDeparturePicker(
    context: android.content.Context,
    selectedMillis: Long,
    onSelected: (Long) -> Unit,
) {
    val nowMillis = System.currentTimeMillis()
    val selected = Calendar.getInstance().apply { timeInMillis = selectedMillis.coerceAtLeast(nowMillis) }
    val chosen = Calendar.getInstance().apply { timeInMillis = selected.timeInMillis }

    val dateDialog = DatePickerDialog(
        context,
        { _, year, month, day ->
            chosen.set(Calendar.YEAR, year)
            chosen.set(Calendar.MONTH, month)
            chosen.set(Calendar.DAY_OF_MONTH, day)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    chosen.set(Calendar.HOUR_OF_DAY, hour)
                    chosen.set(Calendar.MINUTE, minute)
                    chosen.set(Calendar.SECOND, 0)
                    chosen.set(Calendar.MILLISECOND, 0)
                    onSelected(chosen.timeInMillis.coerceAtLeast(System.currentTimeMillis()))
                },
                selected.get(Calendar.HOUR_OF_DAY),
                selected.get(Calendar.MINUTE),
                android.text.format.DateFormat.is24HourFormat(context),
            ).show()
        },
        selected.get(Calendar.YEAR),
        selected.get(Calendar.MONTH),
        selected.get(Calendar.DAY_OF_MONTH),
    )
    dateDialog.datePicker.minDate = nowMillis
    dateDialog.show()
}

private fun departureDateTimeLabel(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault()))

@Composable
private fun BatteryDial(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
            CircularProgressIndicator(
                progress = { value.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxSize(),
                color = EvMint,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                strokeWidth = 10.dp,
            )
            Text("$value%", style = MaterialTheme.typography.headlineSmall)
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BrandedSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    accent: Color = EvMint,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${value.toInt()}$suffix", style = MaterialTheme.typography.titleMedium, color = accent)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun ChoiceRow(
    choices: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        choices.forEach { (value, label) ->
            val active = selected == value
            Surface(
                modifier = Modifier.weight(1f).clickable { onSelect(value) },
                shape = RoundedCornerShape(12.dp),
                color = if (active) EvMint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(vertical = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) Color(0xFF001F1E) else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PlannerVehicleCard(vm: TripViewModel) {
    val vehicle = vm.configuredPreset
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Vehicle",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = vm::showVehiclePicker) {
                    Text("Change", color = EvMint)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = EvMint)
                }
            }
            Text(
                vehicle.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            VehicleSpecRow(vehicle, vm.batteryHealthPercent, vm.routingConnectorTypes)
            if (vm.requiresCcs1AdapterConfirmation) {
                InlineNotice(
                    "CCS1 routing is paused. Open Garage and confirm that this vehicle supports CCS1 and that you carry the adapter.",
                    MaterialTheme.colorScheme.tertiary,
                )
            }
            TextButton(onClick = vm::showVehicleEditor, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Edit vehicle specifications")
            }
        }
    }
}

@Composable
private fun VehicleSpecRow(
    vehicle: EvPreset,
    batteryHealth: Double,
    routingConnectors: List<ConnectorType>,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SpecCell("Usable pack", "${trimNumber(vehicle.batteryCapacityKwh * batteryHealth / 100)} kWh", Modifier.weight(1f))
        SpecCell("Max DC", "${vehicle.maxDcChargingKw} kW", Modifier.weight(1f))
        SpecCell(
            "Plug",
            routingConnectors.joinToString(", ") { it.displayLabel }.ifEmpty { "—" },
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpecCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlannerChargingPreferences(vm: TripViewModel) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = EvMint)
                Text("Avoid low-confidence stations (est.)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = vm.avoidLowConfidenceStations,
                    onCheckedChange = vm::updateAvoidLowConfidence,
                    colors = brandedSwitchColors(),
                )
            }
            HorizontalDivider(color = EvDivider)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = EvMint)
                Spacer(Modifier.width(10.dp))
                Text("Min charger speed", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text("${vm.minimumChargerSpeedKw.toInt()} kW", style = MaterialTheme.typography.titleSmall, color = EvMint)
            }
            Slider(
                value = vm.minimumChargerSpeedKw,
                onValueChange = vm::updateMinimumChargerSpeed,
                valueRange = 50f..350f,
                steps = 11,
            )
        }
    }
}

@Composable
private fun RangeBand(vm: TripViewModel) {
    val estimate = vm.estimatedRange
    val lower = Units.formatDistance(estimate.conservativeRangeKm, vm.usesMiles)
    val upper = Units.formatDistance(estimate.expectedRangeKm, vm.usesMiles)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Speed, contentDescription = null, tint = EvMint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                "Real-world range above reserve",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text("$lower–$upper", style = MaterialTheme.typography.labelMedium, color = EvMint)
        }
        Text(
            "Includes battery health, weather, load, driving style, and uncertainty.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun InlineNotice(message: String, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f)),
    ) {
        Text(message, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun LargeScreenTitle(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        action?.invoke()
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = EvGlass.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
private fun brandedSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = EvMint,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
    uncheckedBorderColor = Color.Transparent,
)

private fun trimNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private val ConnectorType.displayLabel: String
    get() = when (this) {
        ConnectorType.CCS -> "CCS1"
        ConnectorType.CCS2 -> "CCS2"
        ConnectorType.NACS -> "NACS/Tesla"
        ConnectorType.CHADEMO -> "CHAdeMO"
        ConnectorType.TYPE2 -> "Type 2"
        ConnectorType.J1772 -> "J1772"
    }

@Composable
private fun AddressSearchScreen(
    vm: TripViewModel,
    target: AddressSearchTarget,
    onCancel: () -> Unit,
    onSelected: () -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    val waypoint = target.waypointId?.let { id -> vm.waypoints.firstOrNull { it.id == id } }
    val suggestions = when (target.kind) {
        SearchKind.START -> vm.startSuggestions
        SearchKind.DESTINATION -> vm.destinationSuggestions
        SearchKind.WAYPOINT -> waypoint?.suggestions.orEmpty()
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var refreshedForFirstLocation by remember(target) {
        mutableStateOf(vm.currentLocation != null)
    }
    var queryValue by remember(target) {
        // Match iOS: every search opens on the default actions/recents screen instead of
        // immediately re-searching the currently selected planner value.
        mutableStateOf(TextFieldValue("", selection = TextRange(0, 0)))
    }

    fun updateQuery(value: String) {
        when (target.kind) {
            SearchKind.START -> vm.onStartTextChange(value)
            SearchKind.DESTINATION -> vm.onDestinationTextChange(value)
            SearchKind.WAYPOINT -> waypoint?.let { vm.onWaypointTextChange(it, value) }
        }
    }

    fun select(candidate: PlaceCandidate) {
        when (target.kind) {
            SearchKind.START -> vm.selectStart(candidate)
            SearchKind.DESTINATION -> vm.selectDestination(candidate)
            SearchKind.WAYPOINT -> waypoint?.let { vm.selectWaypoint(it, candidate) }
        }
        keyboard?.hide()
        onSelected()
    }

    LaunchedEffect(target) {
        focusRequester.requestFocus()
    }

    // Search can open before the optional foreground location fix arrives. Re-run an existing
    // query once when that first fix becomes available so nearby places are ranked from the
    // driver's position instead of the broad regional fallback. Later GPS ticks deliberately do
    // not trigger more network searches.
    LaunchedEffect(target, vm.currentLocation) {
        if (!refreshedForFirstLocation && vm.currentLocation != null && queryValue.text.isNotBlank()) {
            refreshedForFirstLocation = true
            when (target.kind) {
                SearchKind.START -> vm.refreshStartSuggestions()
                SearchKind.DESTINATION -> vm.refreshDestinationSuggestions()
                SearchKind.WAYPOINT -> waypoint?.let(vm::refreshWaypointSuggestions)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                target.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onCancel) { Text("Cancel", color = EvMint) }
        }

        OutlinedTextField(
            value = queryValue,
            onValueChange = { value ->
                queryValue = value
                updateQuery(value.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search address or place…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (queryValue.text.isNotEmpty()) {
                    IconButton(onClick = {
                        queryValue = TextFieldValue("")
                        updateQuery("")
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EvMint.copy(alpha = 0.55f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
        ) {
            if (target.kind == SearchKind.START && queryValue.text.isBlank()) {
                item {
                    SearchActionRow(
                        icon = Icons.Filled.MyLocation,
                        title = "Current Location",
                        subtitle = "Use your GPS position",
                        onClick = onUseCurrentLocation,
                    )
                }
                item {
                    SectionLabel("SAVED LOCATIONS", Modifier.padding(top = 22.dp, bottom = 8.dp))
                    Text(
                        "No saved locations yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Text(
                        "Saved Home and Work locations will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
            }

            if (queryValue.text.isBlank() && vm.recentLocations.isNotEmpty()) {
                item {
                    SectionLabel(
                        "RECENT",
                        Modifier.padding(top = 22.dp, bottom = 8.dp).testTag("recent_locations_header"),
                    )
                }
                items(vm.recentLocations) { candidate ->
                    SearchResultRow(
                        candidate = candidate,
                        usesMiles = vm.usesMiles,
                        icon = Icons.Filled.History,
                        onClick = { select(candidate) },
                    )
                }
            }

            if (queryValue.text.isNotBlank()) {
                items(suggestions) { candidate ->
                    SearchResultRow(candidate = candidate, usesMiles = vm.usesMiles, onClick = { select(candidate) })
                }
            }

            if (queryValue.text.isNotBlank() && suggestions.isEmpty()) {
                item {
                    val message = vm.searchMessage
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (message == null) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = EvMint, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(
                            message ?: "Searching nearby and broader matches…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(shape = CircleShape, color = EvCyan.copy(alpha = 0.14f)) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = EvCyan)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SearchResultRow(
    candidate: PlaceCandidate,
    usesMiles: Boolean,
    icon: ImageVector = Icons.Filled.LocationOn,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(shape = CircleShape, color = EvMint.copy(alpha = 0.13f)) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = EvMint)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(candidate.placeName, style = MaterialTheme.typography.titleMedium)
            if (candidate.fullAddress.isNotBlank() && candidate.fullAddress != candidate.placeName) {
                Text(
                    candidate.fullAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            candidate.distanceKm?.let { distance ->
                Text(
                    "${Units.formatDistance(distance, usesMiles)} away",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvMint,
                )
            }
        }
    }
    HorizontalDivider(color = EvDivider, modifier = Modifier.padding(start = 58.dp))
}

@Composable
private fun ResultsScreen(
    vm: TripViewModel,
    contentPadding: PaddingValues,
    onEditTrip: () -> Unit,
) {
    val selected = vm.selectedOption
    var selectedChargerStop by remember(selected?.id) { mutableStateOf<ChargingStop?>(null) }
    selectedChargerStop?.let { stop ->
        BackHandler { selectedChargerStop = null }
        ChargerDetailScreen(
            stop = stop,
            contentPadding = contentPadding,
            onBack = { selectedChargerStop = null },
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("route_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 22.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 34.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LargeScreenTitle("Route options") {
                Row {
                    IconButton(onClick = vm::refreshRoutes, enabled = !vm.isPlanning && vm.options.isNotEmpty()) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh routes", tint = EvMint)
                    }
                    IconButton(
                        onClick = { selected?.let { vm.startGuidedTrip(it, vm.preferredNav) } },
                        enabled = selected != null,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Start trip", tint = EvMint, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onEditTrip,
                modifier = Modifier.fillMaxWidth().testTag("edit_trip"),
                border = BorderStroke(1.dp, EvMint.copy(alpha = 0.45f)),
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Edit trip and search again")
            }
        }

        if (vm.isPlanning) {
            item {
                GlassCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = EvMint)
                        Text("Finding fastest EV route…", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Verifying road segments, charger power, and station confidence",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        } else if (selected == null) {
            item { EmptyRouteState(vm.errorMessage) }
        } else {
            item { ResultsMapCard(vm, selected) }
            vm.lastPlanComputedAtMillis?.let { computedAt ->
                item {
                    val ageMinutes = ((System.currentTimeMillis() - computedAt).coerceAtLeast(0L) / 60_000L)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(
                            if (ageMinutes == 0L) "ETA updated just now · tap refresh to update"
                            else "ETA updated ${ageMinutes}m ago · tap refresh to update",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item { RouteSummaryCard(selected, vm.usesMiles) }
            item { ArrivalTimelineCard(selected, vm.destination?.placeName ?: "Destination") }

            if (selected.userWaypoints.isNotEmpty()) {
                item { PlannedVisitsCard(selected) }
            }
            item { ChargingStopsCard(selected, onSelect = { selectedChargerStop = it }) }

            if (vm.options.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Alternate routes", style = MaterialTheme.typography.titleMedium)
                        vm.options.forEachIndexed { index, option ->
                            AlternateRouteCard(
                                option = option,
                                selected = index == vm.selectedIndex,
                                onClick = { vm.selectOption(index) },
                            )
                        }
                    }
                }
            }

            vm.destination?.let { destination ->
                item {
                    GlassCard {
                        DirectionsRow(
                            option = selected,
                            start = vm.start,
                            destination = destination,
                            preferredNav = vm.preferredNav,
                            onStartGuided = { vm.startGuidedTrip(selected, vm.preferredNav) },
                        )
                    }
                }
            }
            item { BeforeYouDriveCard() }
        }
    }
}

@Composable
private fun EmptyRouteState(error: String?) {
    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                if (error == null) Icons.Filled.Map else Icons.Filled.Security,
                contentDescription = null,
                tint = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(54.dp),
            )
            Text(if (error == null) "No routes found yet" else "Route error", style = MaterialTheme.typography.titleLarge)
            Text(
                error ?: "Go to the Planner tab, enter your start and destination, then tap Find Fastest Route.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ResultsMapCard(vm: TripViewModel, option: RouteOption) {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(24.dp)),
    ) {
        RouteMap(
            routeGeometry = option.geometry,
            chargers = option.chargingStops.map { LatLon(it.latitude, it.longitude) },
            start = vm.start?.let { LatLon(it.latitude, it.longitude) },
            destination = vm.destination?.let { LatLon(it.latitude, it.longitude) },
            waypoints = option.userWaypoints.map { LatLon(it.latitude, it.longitude) },
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.62f),
        ) {
            Text(
                "${option.mode} • ${formatDuration(option.totalEtaMinutes)} ETA",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun RouteSummaryCard(option: RouteOption, usesMiles: Boolean) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(option.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(formatDuration(option.totalEtaMinutes), style = MaterialTheme.typography.headlineMedium, color = EvMint)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecCell("Drive", formatDuration(option.drivingMinutes), Modifier.weight(1f))
                SpecCell("Charge", formatDuration(option.chargingMinutes), Modifier.weight(1f))
                SpecCell("Charge stops", option.chargingStops.size.toString(), Modifier.weight(1f))
                SpecCell("Arrive", "${option.arrivalBatteryPercent}%", Modifier.weight(1f))
            }
            if (option.chargingStops.isNotEmpty()) {
                HorizontalDivider(color = EvDivider)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = EvMint, modifier = Modifier.size(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(option.objective.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val other = RouteObjective.plannerCases.filter {
                            it != option.objective && it in option.supportedObjectives
                        }
                        if (other.isNotEmpty()) {
                            Text(
                                "Also optimal for: ${other.joinToString { it.mode }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EvCyan,
                            )
                        }
                    }
                }
            }
            option.estimatedCostText?.let { cost ->
                HorizontalDivider(color = EvDivider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Est. charging cost", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("~$cost", style = MaterialTheme.typography.titleSmall, color = EvCyan)
                }
            }
            option.estimatedConsumptionKwhPer100Km?.let { consumption ->
                HorizontalDivider(color = EvDivider)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Conservative route consumption",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        Units.formatConsumption(consumption, usesMiles),
                        style = MaterialTheme.typography.titleSmall,
                        color = EvCyan,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArrivalTimelineCard(option: RouteOption, destinationName: String) {
    val departure = option.plannedDepartureMillis ?: System.currentTimeMillis()
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Arrival timeline", style = MaterialTheme.typography.titleMedium)
            option.itinerary.forEach { stop ->
                TimelineItem(
                    icon = if (stop.kind == ItineraryStop.Kind.CHARGING) Icons.Filled.Bolt else Icons.Filled.LocationOn,
                    tint = if (stop.kind == ItineraryStop.Kind.CHARGING) EvMint else EvIndigo,
                    name = stop.name,
                    detail = "arrive ${clockLabel(departure, stop.arrivalMinutesFromStart)} · ${stop.arrivalBatteryPercent}%",
                )
            }
            TimelineItem(
                icon = Icons.Filled.Flag,
                tint = EvSuccess,
                name = destinationName,
                detail = "arrive ${clockLabel(departure, option.totalEtaMinutes)} · ${option.arrivalBatteryPercent}%",
            )
        }
    }
}

@Composable
private fun TimelineItem(icon: ImageVector, tint: Color, name: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(23.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlannedVisitsCard(option: RouteOption) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("Planned visits", style = MaterialTheme.typography.titleMedium)
            option.userWaypoints.forEachIndexed { index, waypoint ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = EvIndigo) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(waypoint.placeName, style = MaterialTheme.typography.titleSmall)
                        if (waypoint.fullAddress.isNotBlank() && waypoint.fullAddress != waypoint.placeName) {
                            Text(
                                waypoint.fullAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargingStopsCard(option: RouteOption, onSelect: (ChargingStop) -> Unit) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (option.chargingStops.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EvMint)
                    Text(
                        "No charging stop needed — you can reach your destination with your arrival buffer intact.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text("Recommended charging stops", style = MaterialTheme.typography.titleMedium)
                option.chargingStops.forEachIndexed { index, stop ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(stop) },
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(shape = CircleShape, color = EvMint.copy(alpha = 0.13f)) {
                                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = EvMint)
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${index + 1}. ${stop.name}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Charge ${stop.chargeDurationMinutes} min: ${stop.arrivalBatteryPercent}% → ${stop.targetBatteryPercent}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EvCyan,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "View ${stop.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargerDetailScreen(
    stop: ChargingStop,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("charger_detail"),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 34.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to route")
                }
                Text("Charger", style = MaterialTheme.typography.headlineLarge)
            }
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stop.network.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = EvMint,
                    )
                    Text(stop.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChargerStatusPill(stop.status)
                        Text(
                            "• ${chargerRegionName(stop.region)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChargerMetricTile(
                        title = "Max speed",
                        value = "${stop.maxKw} kW",
                        icon = Icons.Filled.Bolt,
                        modifier = Modifier.weight(1f),
                    )
                    ChargerMetricTile(
                        title = "Stalls",
                        value = stop.availableStalls?.let { "$it/${stop.numberOfStalls} free" }
                            ?: "${stop.numberOfStalls} stalls",
                        icon = Icons.Filled.ElectricCar,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChargerMetricTile(
                        title = "Station confidence (est.)",
                        value = "~${stop.reliabilityScore.toInt()}%",
                        icon = Icons.Filled.VerifiedUser,
                        modifier = Modifier.weight(1f),
                    )
                    ChargerMetricTile(
                        title = "Detour (est.)",
                        value = "~${stop.detourMinutes} min",
                        icon = Icons.Filled.SwapVert,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChargerMetricTile(
                        title = "Price",
                        value = chargerPriceDisplay(stop),
                        icon = Icons.Filled.Bolt,
                        modifier = Modifier.weight(1f),
                    )
                    ChargerMetricTile(
                        title = "Source",
                        value = if (stop.dataSource == ChargerDataSource.OPEN_CHARGE_MAP) "Open Charge Map" else "Sample",
                        icon = Icons.Filled.Public,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Recommended session", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Most EVs charge fastest between roughly 10% and 80% state of charge — plan to arrive low and leave before the curve tapers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Plug types")
                        Text(
                            stop.connectorTypes.joinToString(", ") { it.displayLabel }.ifEmpty { "Unavailable" },
                            color = EvMint,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFC107).copy(alpha = 0.08f),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        chargerAttribution(stop),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChargerMetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = EvGlass.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = EvMint, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChargerStatusPill(status: ChargerStatus) {
    val (label, color) = when (status) {
        ChargerStatus.AVAILABLE -> "Operational" to EvMint
        ChargerStatus.BUSY -> "Busy" to Color(0xFFFFC107)
        ChargerStatus.LIMITED -> "Status unknown" to Color(0xFFFFC107)
        ChargerStatus.OFFLINE -> "Offline" to MaterialTheme.colorScheme.error
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

private fun chargerRegionName(regionCode: String): String =
    if (regionCode.isBlank()) "Region unavailable" else Region.from(regionCode).displayName

private fun chargerPriceDisplay(stop: ChargingStop): String {
    stop.usageCostText?.takeIf { it.isNotBlank() }?.let { return it }
    val price = stop.pricePerKwh ?: return "Price unavailable"
    val currency = stop.priceCurrencyCode ?: return "Price unavailable"
    val amount = "%.2f".format(Locale.US, price)
    return when (currency) {
        "USD", "CAD", "AUD", "NZD" -> "\$$amount $currency/kWh"
        "EUR" -> "€$amount/kWh"
        "GBP" -> "£$amount/kWh"
        "NOK", "SEK" -> "$amount $currency/kWh"
        "CHF" -> "CHF $amount/kWh"
        else -> "$amount $currency/kWh"
    }
}

private fun chargerAttribution(stop: ChargingStop): String {
    val source = if (stop.dataSource == ChargerDataSource.OPEN_CHARGE_MAP) "Open Charge Map" else "sample data"
    val provider = stop.dataProviderTitle?.takeIf { it.isNotBlank() }?.let { " Provider: $it." }.orEmpty()
    val license = stop.dataProviderLicense?.takeIf { it.isNotBlank() }?.let { " License: $it." }.orEmpty()
    return "Free-stall availability may be unavailable; station confidence is estimated; and provider pricing may be incomplete or out of date. Always confirm in the network's own app. Source: $source.$provider$license"
}

@Composable
private fun AlternateRouteCard(option: RouteOption, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) EvMint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    option.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) Color(0xFF001F1E) else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${option.chargingStops.size} charge stops • charge ${option.chargingMinutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) Color(0xFF001F1E).copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatDuration(option.totalEtaMinutes),
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) Color(0xFF001F1E) else EvMint,
            )
        }
    }
}

@Composable
private fun BeforeYouDriveCard() {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Before you drive", style = MaterialTheme.typography.titleMedium)
            Text(
                "Verify every charging stop, connector, access rule, availability, and price in the station operator's app. Range, traffic-free ETA, charge time, and station confidence are estimates; free-stall availability may be unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "Routing © openrouteservice.org by HeiGIT · Map and search data © OpenStreetMap contributors.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun clockLabel(
    departureMillis: Long,
    offsetMinutes: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val departure = Instant.ofEpochMilli(departureMillis).atZone(zoneId)
    val arrival = Instant.ofEpochMilli(departureMillis + offsetMinutes * 60_000L).atZone(zoneId)
    val pattern = if (arrival.toLocalDate() == departure.toLocalDate()) "h:mm a" else "MMM d, h:mm a"
    return arrival.format(DateTimeFormatter.ofPattern(pattern, locale))
}

private fun formatDuration(minutes: Int): String =
    if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"

@Composable
private fun GarageScreen(vm: TripViewModel, contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("garage_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 22.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 34.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LargeScreenTitle("Garage") {
                IconButton(onClick = vm::showCustomVehicleEditor) {
                    Icon(Icons.Filled.Add, contentDescription = "Add vehicle", tint = EvMint, modifier = Modifier.size(28.dp))
                }
            }
        }
        items(vm.garageVehicles, key = { it.catalogIdentifier }) { preset ->
            val configured = vm.configuredPresetFor(preset)
            GarageVehicleCard(
                vehicle = configured,
                batteryHealth = vm.batteryHealthFor(preset),
                routingConnectors = vm.routingConnectorTypesFor(preset),
                requiresAdapterConfirmation = vm.requiresCcs1AdapterConfirmationFor(preset),
                selected = preset.catalogIdentifier == vm.selectedPreset.catalogIdentifier,
                onSelect = { vm.selectPreset(preset) },
                onEdit = { vm.showVehicleEditor(preset) },
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = vm::showCustomVehicleEditor),
                color = EvGlass.copy(alpha = 0.70f),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null, tint = EvMint)
                    Spacer(Modifier.width(9.dp))
                    Text("Add Vehicle", style = MaterialTheme.typography.titleMedium, color = EvMint)
                }
            }
        }
    }
}

@Composable
private fun GarageVehicleCard(
    vehicle: EvPreset,
    batteryHealth: Double,
    routingConnectors: List<ConnectorType>,
    requiresAdapterConfirmation: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassCard(modifier = Modifier.weight(1f).clickable(onClick = onSelect)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${vehicle.year} ${vehicle.make}", style = MaterialTheme.typography.labelLarge, color = EvMint)
                    Spacer(Modifier.weight(1f))
                    if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = EvMint)
                }
                Text(vehicle.model, style = MaterialTheme.typography.headlineSmall)
                VehicleSpecRow(vehicle, batteryHealth, routingConnectors)
                if (requiresAdapterConfirmation) {
                    Text(
                        "Confirm CCS1 adapter to enable CCS routing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_vehicle")) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit ${vehicle.displayName}", tint = EvMint)
            }
        }
    }
}

@Composable
private fun ParityVehicleEditor(vm: TripViewModel) {
    val preset = vm.configuredPreset
    val isBlankNewVehicle = vm.isCreatingVehicle && !vm.editorHasCatalogSeed
    val canDelete = !vm.isCreatingVehicle && vm.garageVehicles.size > 1
    val latestVehicleYear = Calendar.getInstance().get(Calendar.YEAR) + 1
    var make by remember(preset, vm.isCreatingVehicle, vm.editorHasCatalogSeed, vm.editorSeedRevision) {
        mutableStateOf(if (isBlankNewVehicle) "" else preset.make)
    }
    var model by remember(preset, vm.isCreatingVehicle, vm.editorHasCatalogSeed, vm.editorSeedRevision) {
        mutableStateOf(if (isBlankNewVehicle) "" else preset.model)
    }
    var year by remember(preset, vm.isCreatingVehicle, vm.editorHasCatalogSeed, vm.editorSeedRevision) {
        mutableIntStateOf(
            if (isBlankNewVehicle) {
                Calendar.getInstance().get(Calendar.YEAR)
            } else {
                preset.year
            },
        )
    }
    var capacity by remember(preset, isBlankNewVehicle, vm.editorSeedRevision) {
        mutableStateOf(if (isBlankNewVehicle) "75" else trimNumber(preset.batteryCapacityKwh))
    }
    var maxPower by remember(preset, isBlankNewVehicle, vm.editorSeedRevision) {
        mutableStateOf(if (isBlankNewVehicle) "250" else preset.maxDcChargingKw.toString())
    }
    var consumption by remember(preset, isBlankNewVehicle, vm.editorSeedRevision) {
        mutableStateOf(if (isBlankNewVehicle) "17.0" else "%.1f".format(preset.efficiencyKwhPerKm * 100.0))
    }
    var batteryHealth by remember(preset, isBlankNewVehicle, vm.editorSeedRevision) {
        mutableIntStateOf(if (isBlankNewVehicle) 100 else vm.batteryHealthPercent.toInt().coerceIn(60, 100))
    }
    var defaultArrivalBuffer by remember(preset, isBlankNewVehicle, vm.editorSeedRevision) {
        mutableIntStateOf(if (isBlankNewVehicle) 15 else vm.defaultArrivalBufferPercent)
    }
    var connectors by remember(preset, isBlankNewVehicle, vm.editorSeedRevision) {
        // A hand-entered vehicle starts with no assumed hardware. Catalog selection remains the
        // fastest safe path and fills published connectors without guessing the driver's inlet.
        mutableStateOf(if (isBlankNewVehicle) emptySet<ConnectorType>() else preset.connectorTypes.toSet())
    }
    var ccs1AdapterAvailable by remember(preset, isBlankNewVehicle, vm.editorSeedRevision) {
        mutableStateOf(if (isBlankNewVehicle) null else vm.ccs1AdapterAvailability)
    }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (vm.isPickingVehicle) {
        BackHandler(onBack = vm::hideVehiclePicker)
        ParityVehiclePicker(
            current = vm.selectedPreset,
            usesMiles = vm.usesMiles,
            onSelect = vm::selectPreset,
            onCreateCustom = vm::showCustomVehicleEditor,
            onClose = vm::hideVehiclePicker,
        )
        return
    }

    if (showDeleteConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete this vehicle?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        vm.deleteVehicleBeingEdited()
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    fun save() {
        val parsedCapacity = capacity.replace(',', '.').toDoubleOrNull()
        val parsedPower = maxPower.toIntOrNull()
        val parsedConsumption = consumption.replace(',', '.').toDoubleOrNull()
        validationMessage = if (
            parsedCapacity == null || parsedPower == null ||
            parsedConsumption == null
        ) {
            "Enter valid numbers in every field."
        } else {
            vm.saveVehicle(
                make = make,
                model = model,
                year = year,
                batteryCapacityKwh = parsedCapacity,
                maxDcChargingKw = parsedPower,
                efficiencyKwhPer100Km = parsedConsumption,
                batteryHealthPercent = batteryHealth.toDouble(),
                defaultArrivalBufferPercent = defaultArrivalBuffer,
                connectors = connectors,
                ccs1AdapterAvailable = ccs1AdapterAvailable,
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::hideVehicleEditor) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close vehicle editor")
                }
                Text(
                    if (vm.isCreatingVehicle) "Add Vehicle" else "Edit Vehicle",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = ::save) { Text("Save", color = EvMint) }
            }
        }

        item { SectionLabel("CATALOG") }
        item {
            GlassCard(
                modifier = Modifier.clickable(onClick = vm::replaceVehicleFromEditor),
                contentPadding = 0.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = EvMint)
                    Text(
                        if (vm.isCreatingVehicle && !vm.editorHasCatalogSeed) "Choose from vehicle catalog"
                        else "Replace from vehicle catalog",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { SectionLabel("VEHICLE INFO") }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = make,
                        onValueChange = { make = it },
                        label = { Text("Make") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model / trim") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StepperRow(
                        label = "Model year",
                        value = year.toFloat(),
                        suffix = "",
                        range = 2010f..latestVehicleYear.toFloat(),
                        step = 1f,
                        onChange = { year = it.toInt() },
                        modifier = Modifier.testTag("vehicle_year"),
                    )
                }
            }
        }

        item { SectionLabel("VEHICLE SPECIFICATIONS", Modifier.padding(top = 6.dp)) }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = capacity,
                        onValueChange = { capacity = it },
                        label = { Text("Battery capacity (kWh)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = maxPower,
                        onValueChange = { maxPower = it },
                        label = { Text("Max DC charge speed (kW)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = consumption,
                        onValueChange = { consumption = it },
                        label = { Text("Reference use (kWh/100 km)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StepperRow(
                        label = "Battery health",
                        value = batteryHealth.toFloat(),
                        suffix = "%",
                        range = 60f..100f,
                        step = 1f,
                        onChange = { batteryHealth = it.toInt() },
                    )
                    Text(
                        "Battery health reduces usable capacity without changing the capacity-when-new value.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StepperRow(
                        label = "Default arrival buffer",
                        value = defaultArrivalBuffer.toFloat(),
                        suffix = "%",
                        range = 5f..35f,
                        step = 1f,
                        onChange = { defaultArrivalBuffer = it.toInt() },
                    )
                }
            }
        }

        item { SectionLabel("CONNECTOR TYPES", Modifier.padding(top = 6.dp)) }
        item {
            GlassCard(contentPadding = 0.dp) {
                Column {
                    val showsCcs1Adapter = !vm.region.isEuropean && ConnectorType.NACS in connectors
                    if (showsCcs1Adapter) {
                        SettingsToggleRow(
                            label = "CCS1 supported + adapter available",
                            checked = ccs1AdapterAvailable == true,
                            onCheckedChange = { available ->
                                ccs1AdapterAvailable = available
                                connectors = if (available) {
                                    connectors + ConnectorType.CCS
                                } else {
                                    connectors - ConnectorType.CCS
                                }
                            },
                        )
                        HorizontalDivider(color = EvDivider)
                        if (ccs1AdapterAvailable == null && ConnectorType.CCS in connectors) {
                            Text(
                                "CCS routing is paused until you confirm support and that the adapter is in the car.",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            HorizontalDivider(color = EvDivider)
                        }
                    }
                    val editableConnectors = ConnectorType.entries.filter {
                        !showsCcs1Adapter || it != ConnectorType.CCS
                    }
                    editableConnectors.forEachIndexed { index, connector ->
                        SettingsToggleRow(
                            label = connector.displayLabel,
                            checked = connector in connectors,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (connector == ConnectorType.NACS && ConnectorType.CCS in connectors) {
                                        ccs1AdapterAvailable = true
                                    }
                                    connectors = connectors + connector
                                } else {
                                    connectors = connectors - connector
                                    if (connector == ConnectorType.NACS) ccs1AdapterAvailable = null
                                }
                            },
                        )
                        if (index != editableConnectors.lastIndex) {
                            HorizontalDivider(color = EvDivider)
                        }
                    }
                    if (showsCcs1Adapter) {
                        HorizontalDivider(color = EvDivider)
                        Text(
                            "Only enable CCS1 after confirming your vehicle supports it and you will carry the required adapter.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (!vm.isCreatingVehicle || vm.editorHasCatalogSeed) preset.sourceName?.let { source ->
            item { SectionLabel("STARTING SPECIFICATION", Modifier.padding(top = 6.dp)) }
            item {
                GlassCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Catalog source: $source", style = MaterialTheme.typography.bodyMedium)
                        preset.ratedRangeKm?.let { range ->
                            Text(
                                "Catalog range reference: ${Units.formatDistance(range, vm.usesMiles)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        preset.sourceUrl?.let { url ->
                            val context = LocalContext.current
                            TextButton(onClick = { NavLauncher.open(context, url) }) {
                                Text("View source", color = EvMint)
                            }
                        }
                    }
                }
            }
        }

        validationMessage?.let { message -> item { InlineNotice(message, MaterialTheme.colorScheme.error) } }

        item {
            Button(
                onClick = ::save,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EvMint, contentColor = Color(0xFF001F1E)),
            ) {
                Text("Save vehicle specifications", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (canDelete) {
            item {
                OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete vehicle", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ParityVehiclePicker(
    current: EvPreset,
    usesMiles: Boolean,
    onSelect: (EvPreset) -> Unit,
    onCreateCustom: () -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { EvCatalog.search(query) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Choose your car", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = EvMint) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            placeholder = { Text("Search ${EvCatalog.makeCount} makes") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EvMint.copy(alpha = 0.55f),
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            ),
        )
        Text(
            "Catalog specifications are starting values. You can update your exact trim, wheels, battery health, and adapters after adding a vehicle.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp),
        )
        OutlinedButton(
            onClick = onCreateCustom,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Enter a vehicle manually")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.catalogIdentifier }) { preset ->
                val selected = preset.catalogIdentifier == current.catalogIdentifier
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(preset) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) EvMint.copy(alpha = 0.18f) else EvGlass.copy(alpha = 0.74f),
                    border = BorderStroke(
                        1.dp,
                        if (selected) EvMint.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(preset.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                pickerSpecLine(preset, usesMiles),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = EvMint)
                    }
                }
            }
        }
    }
}

@Composable
private fun GarageVehiclePicker(vm: TripViewModel, onClose: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("garage_vehicle_picker"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Select vehicle", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = EvMint) }
            }
        }
        item {
            Text(
                "Choose a saved vehicle. Add or edit profiles from Garage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(vm.garageVehicles, key = { it.catalogIdentifier }) { preset ->
            val vehicle = vm.configuredPresetFor(preset)
            val selected = preset.catalogIdentifier == vm.selectedPreset.catalogIdentifier
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { vm.selectPreset(preset) },
                shape = RoundedCornerShape(18.dp),
                color = if (selected) EvMint.copy(alpha = 0.18f)
                else EvGlass.copy(alpha = 0.74f),
                border = BorderStroke(
                    1.dp,
                    if (selected) EvMint.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(vehicle.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            pickerSpecLine(vehicle, vm.usesMiles),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = EvMint)
                }
            }
        }
    }
}

private data class InformationSection(val title: String, val body: String)

@Composable
private fun ParityInformationScreen(
    page: InformationPage,
    onClose: () -> Unit,
    onShowLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val title: String
    val sections: List<InformationSection>
    when (page) {
        InformationPage.PRIVACY -> {
            title = "Privacy Policy"
            sections = listOf(
                InformationSection(
                    "Your privacy",
                    "EV FastRoute has no account, ads, analytics, or tracking SDKs and does not sell personal information. Vehicle profiles, preferences, saved trips, and active-trip progress stay in app-private storage on this device.",
                ),
                InformationSection(
                    "Location & search",
                    "Optional foreground location ranks nearby search results, can set the starting point, follows in-app trip progress, supports off-route rerouting, and offers arrival prompts. Typed searches and an approximate anchor are sent to the device geocoder and Photon. Background location is not requested.",
                ),
                InformationSection(
                    "Routes, chargers & maps",
                    "Selected trip and charging-stop coordinates are sent to openrouteservice for traffic-independent road routing. Route-area boxes are sent to Open Charge Map for station records. OpenFreeMap receives map-tile requests. These providers also receive ordinary network information such as an IP address.",
                ),
                InformationSection(
                    "Control",
                    "You can deny location, remove saved trips, reset local data in Settings, clear app storage, or uninstall the app. Location samples are not retained after the active operation unless coordinates are part of a trip you explicitly save.",
                ),
            )
        }
        InformationPage.TERMS -> {
            title = "Terms of Use"
            sections = listOf(
                InformationSection(
                    "Planning estimates",
                    "Routes, range, battery state, charge time, station status, pricing, and arrival times are estimates. Weather, speed, battery condition, traffic, closures, queues, and provider data can differ from the plan.",
                ),
                InformationSection(
                    "Vehicle specifications",
                    "Catalog values are published references, not a live connection to your vehicle. Confirm the exact trim and edit battery capacity, consumption, charging power, model year, health, reserve, and connectors when your car differs.",
                ),
                InformationSection(
                    "Charging stations",
                    "Open Charge Map is community-maintained and may be incomplete or stale. Sample stations are never used for a navigable route. Verify every planned stop, connector, status, and price in the charging operator's app before relying on it.",
                ),
                InformationSection(
                    "Navigation & safety",
                    "EV FastRoute provides route overview and basic maneuver guidance, not voice, lane, or live-traffic navigation. Do not interact with the app while driving. Obey road signs, traffic laws, vehicle warnings, and safe driving judgment.",
                ),
                InformationSection(
                    "No warranty",
                    "The app is provided as-is without a guarantee that a route or station will be usable. To the maximum extent permitted by law, the publisher is not liable for delays, low charge, or reliance on inaccurate third-party data.",
                ),
            )
        }
        InformationPage.ABOUT -> {
            title = "About EV FastRoute"
            sections = listOf(
                InformationSection(
                    "EV FastRoute",
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}). Plan electric road trips by total arrival time, including conservative real-world range and charging sessions.",
                ),
                InformationSection(
                    "Data sources",
                    "Vehicle references include OpenEV Data v1.24.0. Charging data comes from Open Charge Map. Road routing comes from openrouteservice by HeiGIT. Search uses Photon and the Android device geocoder. Maps use OpenFreeMap and OpenStreetMap data.",
                ),
                InformationSection(
                    "Attribution",
                    "OpenEV Data is used under CDLA-Permissive-2.0. Charging data © Open Charge Map contributors and listed providers. Routing © openrouteservice.org by HeiGIT; map/search data © OpenStreetMap contributors.",
                ),
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close $title")
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = EvMint) }
            }
        }
        items(sections, key = { it.title }) { section ->
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(section.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (page == InformationPage.PRIVACY) {
                        SettingsLink("Open hosted privacy policy") {
                            NavLauncher.open(context, BuildConfig.PRIVACY_POLICY_URL)
                        }
                    }
                    SettingsLink("Support") { NavLauncher.open(context, BuildConfig.SUPPORT_URL) }
                    SettingsLink("Data sources and licenses", onShowLicenses)
                }
            }
        }
    }
}

@Composable
private fun ParityLicensesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val noticeText = remember(context) {
        listOf("THIRD_PARTY_NOTICES.txt", "Apache-2.0.txt", "CDLA-Permissive-2.0.txt")
            .joinToString("\n\n") { name ->
                runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }
                    .getOrElse { "Unable to load $name." }
            }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close") }
                Text("Licenses and notices", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = EvMint) }
            }
        }
        item {
            GlassCard {
                Text(
                    noticeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun pickerSpecLine(preset: EvPreset, usesMiles: Boolean): String {
    val range = preset.ratedRangeKm?.let { " · ~${Units.formatDistance(it, usesMiles)}" }.orEmpty()
    return "${trimNumber(preset.batteryCapacityKwh)} kWh · ${preset.maxDcChargingKw} kW DC · " +
        "${preset.connectorTypes.joinToString("/") { it.displayLabel }}$range"
}

@Composable
private fun SettingsTab(
    vm: TripViewModel,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    var regionMenuExpanded by remember { mutableStateOf(false) }
    var weatherMenuExpanded by remember { mutableStateOf(false) }
    var loadMenuExpanded by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var networkPicker by remember { mutableStateOf<NetworkPickerKind?>(null) }

    networkPicker?.let { kind ->
        NetworkPickerDialog(
            title = if (kind == NetworkPickerKind.PREFERRED) "Preferred Networks" else "Avoided Networks",
            available = vm.region.defaultNetworks.sorted(),
            selected = if (kind == NetworkPickerKind.PREFERRED) vm.preferredNetworks else vm.avoidedNetworks,
            onToggle = { network ->
                val selected = if (kind == NetworkPickerKind.PREFERRED) vm.preferredNetworks else vm.avoidedNetworks
                val updated = if (network in selected) selected - network else selected + network
                if (kind == NetworkPickerKind.PREFERRED) {
                    vm.updatePreferredNetworks(updated)
                } else {
                    vm.updateAvoidedNetworks(updated)
                }
            },
            onClose = { networkPicker = null },
        )
    }

    if (showResetConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset all settings?") },
            text = {
                Text("Restores default vehicles and preferences and clears saved trips and active-trip progress. This can't be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        vm.resetAllToDefaults()
                    },
                ) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("settings_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 22.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 34.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LargeScreenTitle("Settings") }

        item { SectionLabel("ROUTING") }
        item {
            GlassCard(contentPadding = 0.dp) {
                Column {
                    StepperRow(
                        label = "Minimum charger speed",
                        value = vm.minimumChargerSpeedKw,
                        suffix = " kW",
                        range = 50f..350f,
                        step = 25f,
                        onChange = vm::updateMinimumChargerSpeed,
                    )
                    HorizontalDivider(color = EvDivider)
                    StepperRow(
                        label = "Arrival battery buffer",
                        value = vm.arrivalBufferPercent,
                        suffix = "%",
                        range = 5f..35f,
                        step = 1f,
                        onChange = vm::updateArrivalBuffer,
                    )
                    HorizontalDivider(color = EvDivider)
                    SettingsToggleRow(
                        "Use miles (${if (vm.usesMiles) "mi" else "km"})",
                        vm.usesMiles,
                        vm::updateUsesMiles,
                    )
                    HorizontalDivider(color = EvDivider)
                    SettingsToggleRow(
                        "Avoid low-confidence chargers",
                        vm.avoidLowConfidenceStations,
                        vm::updateAvoidLowConfidence,
                    )
                }
            }
        }

        item { SectionLabel("CHARGING NETWORKS", Modifier.padding(top = 10.dp)) }
        item {
            GlassCard(contentPadding = 0.dp) {
                Column {
                    SettingsSelectionRow(
                        label = "Preferred networks",
                        value = "${vm.preferredNetworks.size} selected",
                        onClick = { networkPicker = NetworkPickerKind.PREFERRED },
                    )
                    HorizontalDivider(color = EvDivider)
                    SettingsSelectionRow(
                        label = "Avoided networks",
                        value = "${vm.avoidedNetworks.size} selected",
                        onClick = { networkPicker = NetworkPickerKind.AVOIDED },
                    )
                    HorizontalDivider(color = EvDivider)
                    Text(
                        "Network preferences influence ranking. They never make an incompatible or unreachable charger valid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        item { SectionLabel("REAL-WORLD RANGE", Modifier.padding(top = 10.dp)) }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box {
                        SettingsDropdownRow(
                            label = "Expected weather",
                            value = weatherRangeChoices.firstOrNull { it.first == vm.weatherRangeLossPercent }?.second
                                ?: "${vm.weatherRangeLossPercent.toInt()}% range loss",
                            onClick = { weatherMenuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = weatherMenuExpanded,
                            onDismissRequest = { weatherMenuExpanded = false },
                        ) {
                            weatherRangeChoices.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        vm.updateWeatherRangeLoss(value)
                                        weatherMenuExpanded = false
                                    },
                                    trailingIcon = {
                                        if (vm.weatherRangeLossPercent == value) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EvMint)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = EvDivider)
                    Box {
                        SettingsDropdownRow(
                            label = "Trip load",
                            value = tripLoadChoices.firstOrNull { it.first == vm.extraLoadKg }?.second
                                ?: "${vm.extraLoadKg.toInt()} kg",
                            onClick = { loadMenuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = loadMenuExpanded,
                            onDismissRequest = { loadMenuExpanded = false },
                        ) {
                            tripLoadChoices.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        vm.updateExtraLoad(value)
                                        loadMenuExpanded = false
                                    },
                                    trailingIcon = {
                                        if (vm.extraLoadKg == value) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EvMint)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = EvDivider)
                    Text("Driving style", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        RangeDrivingStyle.entries.forEach { style ->
                            val active = vm.drivingStyle == style
                            Surface(
                                modifier = Modifier.weight(1f).clickable { vm.updateDrivingStyle(style) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (active) EvMint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            ) {
                                Text(
                                    style.name.lowercase().replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) Color(0xFF001F1E) else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    Text(
                        "Efficient assumes gentler acceleration; Balanced is neutral; Brisk adds an energy margin for faster driving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionLabel("REGION", Modifier.padding(top = 10.dp)) }
        item {
            Box {
                GlassCard(
                    modifier = Modifier.clickable { regionMenuExpanded = true },
                    contentPadding = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 17.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Region", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Text("${vm.region.flag} ${vm.region.displayName}", style = MaterialTheme.typography.bodyLarge, color = EvMint)
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = EvMint)
                    }
                }
                DropdownMenu(expanded = regionMenuExpanded, onDismissRequest = { regionMenuExpanded = false }) {
                    Region.entries.forEach { region ->
                        DropdownMenuItem(
                            text = { Text("${region.flag} ${region.displayName}") },
                            onClick = {
                                vm.updateRegion(region)
                                regionMenuExpanded = false
                            },
                            trailingIcon = {
                                if (region == vm.region) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EvMint)
                            },
                        )
                    }
                }
            }
        }

        item { SectionLabel("NAVIGATION", Modifier.padding(top = 10.dp)) }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Preferred navigation app", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        NavigationApp.entries.forEach { app ->
                            val active = vm.preferredNav == app
                            Surface(
                                modifier = Modifier.weight(1f).clickable { vm.updatePreferredNav(app) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (active) EvMint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            ) {
                                Text(
                                    shortNavigationName(app),
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) Color(0xFF001F1E) else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { SectionLabel("INFORMATION", Modifier.padding(top = 10.dp)) }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SettingsLink("Privacy Policy") { vm.showInformation(InformationPage.PRIVACY) }
                    SettingsLink("Terms of Use") { vm.showInformation(InformationPage.TERMS) }
                    SettingsLink("About EV FastRoute") { vm.showInformation(InformationPage.ABOUT) }
                    SettingsLink("Support") { NavLauncher.open(context, BuildConfig.SUPPORT_URL) }
                    SettingsLink("Data sources and licenses", vm::showLicenses)
                    SettingsLink("Open Charge Map") { NavLauncher.open(context, "https://openchargemap.org/") }
                    SettingsLink("openrouteservice by HeiGIT") { NavLauncher.open(context, "https://openrouteservice.org/") }
                    HorizontalDivider(color = EvDivider, modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Search terms, route coordinates, and charger-area queries are sent to configured providers. Location is foreground-only and optional.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }

        item { SectionLabel("DATA", Modifier.padding(top = 10.dp)) }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Your vehicles, saved trips, and preferences are stored only on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { showResetConfirmation = true },
                        modifier = Modifier.testTag("reset_all_settings"),
                    ) {
                        Text("Reset all settings", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Float,
    suffix: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("${value.toInt()}$suffix", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onChange((value - step).coerceAtLeast(range.start)) }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Box(modifier = Modifier.width(1.dp).height(30.dp).background(EvDivider))
                TextButton(onClick = { onChange((value + step).coerceAtMost(range.endInclusive)) }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = brandedSwitchColors())
    }
}

@Composable
private fun SettingsLink(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSelectionRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsDropdownRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NetworkPickerDialog(
    title: String,
    available: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClose: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column {
                available.forEach { network ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(network) }.padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(network, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (network in selected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = EvMint)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done", color = EvMint) } },
    )
}

@Composable
private fun SectionLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
    )
}

private fun shortNavigationName(app: NavigationApp): String = when (app) {
    NavigationApp.GOOGLE_MAPS -> "Google"
    NavigationApp.WAZE -> "Waze"
    NavigationApp.DEFAULT -> "Default"
}
