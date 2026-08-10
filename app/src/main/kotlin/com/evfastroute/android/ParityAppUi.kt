package com.evfastroute.android

import android.Manifest
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.material3.Divider
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
import androidx.compose.ui.text.font.FontWeight
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
import com.evfastroute.android.nav.LocationProvider
import com.evfastroute.android.nav.NavLauncher
import com.evfastroute.core.EvCatalog
import com.evfastroute.core.EvPreset
import com.evfastroute.core.ConnectorType
import com.evfastroute.core.ItineraryStop
import com.evfastroute.core.LatLon
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.RangeDrivingStyle
import com.evfastroute.core.Region
import com.evfastroute.core.RouteOption
import com.evfastroute.core.Units

private enum class AppSection(val label: String, val icon: ImageVector) {
    PLAN("Plan", Icons.Filled.ElectricCar),
    ROUTE("Route", Icons.Filled.Map),
    GARAGE("Garage", Icons.Filled.DirectionsCar),
    SETTINGS("Settings", Icons.Filled.Tune),
}

private enum class SearchKind { START, DESTINATION, WAYPOINT }

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
    onThemeChanged: (Boolean) -> Unit = {},
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
    if (vm.isEditingVehicle) {
        BackHandler(onBack = vm::hideVehicleEditor)
        EvGradientBackground { ParityVehicleEditor(vm) }
        return
    }
    if (vm.isPickingVehicle) {
        BackHandler(onBack = vm::hideVehiclePicker)
        EvGradientBackground {
            ParityVehiclePicker(
                current = vm.selectedPreset,
                usesMiles = vm.usesMiles,
                onSelect = vm::selectPreset,
                onClose = vm::hideVehiclePicker,
            )
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

    fun setStartFromDeviceLocation() {
        oneShotLocation.oneShot(
            onSample = { lat, lon, _, _ -> vm.useCurrentLocation(lat, lon) },
            onUnavailable = vm::reportLocationUnavailable,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasLocationPermission = grants.values.any { it } || hasForegroundLocationPermission()
        if (useLocationAsStartAfterGrant) {
            if (hasLocationPermission) setStartFromDeviceLocation() else vm.reportLocationPermissionDenied()
        }
        useLocationAsStartAfterGrant = false
    }

    fun requestLocationPermission(useAsStart: Boolean) {
        useLocationAsStartAfterGrant = useAsStart
        if (hasForegroundLocationPermission()) {
            hasLocationPermission = true
            if (useAsStart) setStartFromDeviceLocation()
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
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        hasLocationPermission = hasForegroundLocationPermission()
                        if (hasLocationPermission) {
                            provider.start { lat, lon, accuracy, time ->
                                vm.onLocationSample(lat, lon, accuracy, time)
                            }
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> provider.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                provider.stop()
            }
        }
    }

    EvGradientBackground {
        if (vm.isGuidedNavigationOpen && vm.navSession != null && vm.selectedOption != null) {
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
                onThemeChanged = onThemeChanged,
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
    val option = vm.selectedOption ?: return
    val session = vm.navSession ?: return
    val destination = vm.destination

    Box(modifier = Modifier.fillMaxSize()) {
        RouteMap(
            routeGeometry = option.geometry,
            chargers = option.chargingStops.map { LatLon(it.latitude, it.longitude) },
            start = vm.start?.let { LatLon(it.latitude, it.longitude) },
            destination = destination?.let { LatLon(it.latitude, it.longitude) },
            waypoints = option.userWaypoints.map { LatLon(it.latitude, it.longitude) },
            userLocation = vm.currentLocation,
            followUser = hasLocationPermission && vm.currentLocation != null,
            fallbackCenter = vm.region.searchCenter,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent))),
        )
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
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
private fun AppShell(
    vm: TripViewModel,
    hasLocationPermission: Boolean,
    onRequestLocation: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onThemeChanged: (Boolean) -> Unit,
) {
    var selectedSection by rememberSaveable { mutableStateOf(AppSection.PLAN) }
    var searchTarget by remember { mutableStateOf<AddressSearchTarget?>(null) }
    var observedPlanning by remember { mutableStateOf(false) }

    LaunchedEffect(vm.isPlanning) {
        if (vm.isPlanning) {
            observedPlanning = true
        } else if (observedPlanning) {
            if (vm.options.isNotEmpty()) selectedSection = AppSection.ROUTE
            observedPlanning = false
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
                )
                AppSection.ROUTE -> ResultsScreen(vm, padding)
                AppSection.GARAGE -> GarageScreen(vm, padding)
                AppSection.SETTINGS -> SettingsTab(vm, padding, onThemeChanged)
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
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f),
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
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        background,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        background.copy(alpha = 0.98f),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(260.dp)
                .blur(70.dp)
                .background(EvCyan.copy(alpha = 0.18f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(280.dp)
                .blur(85.dp)
                .background(EvMint.copy(alpha = 0.11f), CircleShape),
        )
        content()
    }
}

@Composable
private fun OnboardingScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Spacer(Modifier.weight(0.7f))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
                    .background(EvCyan.copy(alpha = 0.18f), CircleShape),
            )
            Icon(
                Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = EvCyan,
                modifier = Modifier.size(106.dp),
            )
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = EvMint,
                modifier = Modifier.size(46.dp).padding(bottom = 14.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "EV FastRoute",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
            )
            Text(
                "Plan EV trips by total arrival time, including charging.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingFeature(Icons.Filled.Timer, "Optimizes total ETA", "Balances drive time, detours, queues, and charge speed.")
            OnboardingFeature(Icons.Filled.VerifiedUser, "Station-confidence aware", "Uses operational status, power, and site size as planning signals.")
            OnboardingFeature(Icons.Filled.Public, "International routing", "Supports North America and major European countries.")
        }
        Text(
            "Charging times, availability and pricing are estimates — always confirm in the operator's app before relying on them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
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
        Spacer(Modifier.weight(0.3f))
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
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = EvMint, modifier = Modifier.size(23.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                modifier = Modifier.fillMaxWidth().height(58.dp),
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
                    Icon(Icons.Filled.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

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
            )

            Divider(color = EvDivider)
            Text("Departure", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceRow(
                choices = listOf(0 to "Now", 30 to "+30m", 60 to "+1h", 120 to "+2h"),
                selected = vm.departureOffsetMinutes,
                onSelect = vm::setDepartureOffset,
            )

            Divider(color = EvDivider)
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
                range = 5f..40f,
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
            } else {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

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
                Text("Vehicle", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = vm::showVehiclePicker) {
                    Text("Change", color = EvMint)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = EvMint)
                }
            }
            Text(vehicle.displayName, style = MaterialTheme.typography.titleLarge)
            VehicleSpecRow(vehicle, vm.batteryHealthPercent)
            TextButton(onClick = vm::showVehicleEditor, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Edit vehicle specifications")
            }
        }
    }
}

@Composable
private fun VehicleSpecRow(vehicle: EvPreset, batteryHealth: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SpecCell("Usable pack", "${trimNumber(vehicle.batteryCapacityKwh * batteryHealth / 100)} kWh", Modifier.weight(1f))
        SpecCell("Max DC", "${vehicle.maxDcChargingKw} kW", Modifier.weight(1f))
        SpecCell("Plug", vehicle.connectorTypes.firstOrNull()?.name ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun SpecCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            Divider(color = EvDivider)
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
                valueRange = 0f..350f,
                steps = 13,
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
        Text(title, style = MaterialTheme.typography.headlineLarge)
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
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

@Composable
private fun AddressSearchScreen(
    vm: TripViewModel,
    target: AddressSearchTarget,
    onCancel: () -> Unit,
    onSelected: () -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    val waypoint = target.waypointId?.let { id -> vm.waypoints.firstOrNull { it.id == id } }
    val query = when (target.kind) {
        SearchKind.START -> vm.startText
        SearchKind.DESTINATION -> vm.destinationText
        SearchKind.WAYPOINT -> waypoint?.text.orEmpty()
    }
    val suggestions = when (target.kind) {
        SearchKind.START -> vm.startSuggestions
        SearchKind.DESTINATION -> vm.destinationSuggestions
        SearchKind.WAYPOINT -> waypoint?.suggestions.orEmpty()
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

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

    LaunchedEffect(target) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                target.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onCancel) { Text("Cancel", color = EvMint) }
        }

        OutlinedTextField(
            value = query,
            onValueChange = ::updateQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search address or place…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { updateQuery("") }) {
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
            if (target.kind == SearchKind.START && query.isBlank()) {
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

            items(suggestions) { candidate ->
                SearchResultRow(candidate = candidate, usesMiles = vm.usesMiles, onClick = { select(candidate) })
            }

            if (query.isNotBlank() && suggestions.isEmpty()) {
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
private fun SearchResultRow(candidate: PlaceCandidate, usesMiles: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(shape = CircleShape, color = EvMint.copy(alpha = 0.13f)) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = EvMint)
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
    Divider(color = EvDivider, modifier = Modifier.padding(start = 58.dp))
}

@Composable
private fun ResultsScreen(vm: TripViewModel, contentPadding: PaddingValues) {
    val selected = vm.selectedOption
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
            LargeScreenTitle("Fastest Route") {
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
            item { RouteSummaryCard(selected) }
            item { ArrivalTimelineCard(selected, vm.destination?.placeName ?: "Destination", vm.departureOffsetMinutes) }

            if (selected.userWaypoints.isNotEmpty()) {
                item { PlannedVisitsCard(selected) }
            }
            item { ChargingStopsCard(selected) }

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
private fun RouteSummaryCard(option: RouteOption) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(option.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(formatDuration(option.totalEtaMinutes), style = MaterialTheme.typography.headlineMedium, color = EvMint)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpecCell("Drive", formatDuration(option.drivingMinutes), Modifier.weight(1f))
                SpecCell("Charge", formatDuration(option.chargingMinutes), Modifier.weight(1f))
                SpecCell("Stops", option.chargingStops.size.toString(), Modifier.weight(1f))
                SpecCell("Arrive", "${option.arrivalBatteryPercent}%", Modifier.weight(1f))
            }
            Divider(color = EvDivider)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = EvMint, modifier = Modifier.size(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(option.objective.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val other = option.supportedObjectives - option.objective
                    if (other.isNotEmpty()) {
                        Text(
                            "Also optimal for: ${other.joinToString { it.mode }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EvCyan,
                        )
                    }
                }
            }
            option.estimatedCostText?.let { cost ->
                Divider(color = EvDivider)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Estimated charging cost", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(cost, style = MaterialTheme.typography.titleSmall, color = EvCyan)
                }
            }
        }
    }
}

@Composable
private fun ArrivalTimelineCard(option: RouteOption, destinationName: String, departureOffsetMinutes: Int) {
    val departure = remember(option.id, departureOffsetMinutes) {
        System.currentTimeMillis() + departureOffsetMinutes * 60_000L
    }
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Arrival timeline", style = MaterialTheme.typography.titleMedium)
            option.itinerary.forEach { stop ->
                TimelineItem(
                    icon = if (stop.kind == ItineraryStop.Kind.CHARGING) Icons.Filled.Bolt else Icons.Filled.LocationOn,
                    tint = if (stop.kind == ItineraryStop.Kind.CHARGING) EvMint else EvCyan,
                    name = stop.name,
                    detail = "arrive ${clockLabel(departure, stop.arrivalMinutesFromStart)} · ${stop.arrivalBatteryPercent}%",
                )
            }
            TimelineItem(
                icon = Icons.Filled.Flag,
                tint = Color(0xFF75E68A),
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
                    Surface(shape = CircleShape, color = Color(0xFF5969D8)) {
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
private fun ChargingStopsCard(option: RouteOption) {
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
                        modifier = Modifier.fillMaxWidth(),
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
                        }
                    }
                }
            }
        }
    }
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
                "Verify every charging stop, connector, access rule, and availability in the station operator's app. Range, traffic-free ETA, price, and status are estimates.",
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

private fun clockLabel(departureMillis: Long, offsetMinutes: Int): String {
    val time = java.time.Instant.ofEpochMilli(departureMillis + offsetMinutes * 60_000L)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    return time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
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
                IconButton(onClick = vm::showVehiclePicker) {
                    Icon(Icons.Filled.AddCircle, contentDescription = "Add vehicle", tint = EvMint, modifier = Modifier.size(32.dp))
                }
            }
        }
        items(vm.garageVehicles, key = { it.catalogIdentifier }) { preset ->
            val configured = vm.configuredPresetFor(preset)
            GarageVehicleCard(
                vehicle = configured,
                batteryHealth = vm.batteryHealthFor(preset),
                selected = preset.catalogIdentifier == vm.selectedPreset.catalogIdentifier,
                canRemove = vm.garageVehicles.size > 1,
                onSelect = { vm.selectPreset(preset) },
                onEdit = {
                    vm.selectPreset(preset)
                    vm.showVehicleEditor()
                },
                onRemove = { vm.removeGarageVehicle(preset) },
            )
        }
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = vm::showVehiclePicker),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
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
    selected: Boolean,
    canRemove: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    var showRemoveConfirmation by remember { mutableStateOf(false) }
    if (showRemoveConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            title = { Text("Remove vehicle?") },
            text = { Text("Remove ${vehicle.displayName} from your Garage? Catalog data will remain available if you add it again.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirmation = false
                    onRemove()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRemoveConfirmation = false }) { Text("Cancel") } },
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassCard(modifier = Modifier.weight(1f).clickable(onClick = onSelect)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${vehicle.year} ${vehicle.make}", style = MaterialTheme.typography.labelLarge, color = EvMint)
                    Spacer(Modifier.weight(1f))
                    if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = EvMint)
                }
                Text(vehicle.model, style = MaterialTheme.typography.headlineSmall)
                VehicleSpecRow(vehicle, batteryHealth)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit ${vehicle.displayName}", tint = EvMint)
            }
            if (canRemove) {
                IconButton(onClick = { showRemoveConfirmation = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove ${vehicle.displayName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParityVehicleEditor(vm: TripViewModel) {
    val preset = vm.configuredPreset
    var capacity by remember(preset) { mutableStateOf(trimNumber(preset.batteryCapacityKwh)) }
    var maxPower by remember(preset) { mutableStateOf(preset.maxDcChargingKw.toString()) }
    var consumption by remember(preset) { mutableStateOf("%.1f".format(preset.efficiencyKwhPerKm * 100.0)) }
    var health by remember(preset) { mutableStateOf(trimNumber(vm.batteryHealthPercent)) }
    var connectors by remember(preset) { mutableStateOf(preset.connectorTypes.toSet()) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    fun save() {
        val parsedCapacity = capacity.replace(',', '.').toDoubleOrNull()
        val parsedPower = maxPower.toIntOrNull()
        val parsedConsumption = consumption.replace(',', '.').toDoubleOrNull()
        val parsedHealth = health.replace(',', '.').toDoubleOrNull()
        validationMessage = if (
            parsedCapacity == null || parsedPower == null ||
            parsedConsumption == null || parsedHealth == null
        ) {
            "Enter valid numbers in every field."
        } else {
            vm.saveVehicleOverride(
                batteryCapacityKwh = parsedCapacity,
                maxDcChargingKw = parsedPower,
                efficiencyKwhPer100Km = parsedConsumption,
                batteryHealthPercent = parsedHealth,
                connectors = connectors,
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
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Close vehicle editor")
                }
                Text(
                    "Edit Vehicle",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = ::save) { Text("Save", color = EvMint) }
            }
        }

        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(preset.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Catalog values are a starting point. Update them for your exact trim, wheels, usable battery, adapters, and current battery health.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    Text("Replace from vehicle catalog", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        label = { Text("Usable battery capacity (kWh)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = maxPower,
                        onValueChange = { maxPower = it },
                        label = { Text("Maximum DC charging (kW)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = consumption,
                        onValueChange = { consumption = it },
                        label = { Text("Reference consumption (kWh/100 km)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = health,
                        onValueChange = { health = it },
                        label = { Text("Battery health (%)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Battery health reduces usable capacity without changing the capacity-when-new value.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionLabel("CONNECTOR TYPES", Modifier.padding(top = 6.dp)) }
        item {
            GlassCard(contentPadding = 0.dp) {
                Column {
                    ConnectorType.entries.filter { it != ConnectorType.OTHER }.forEachIndexed { index, connector ->
                        SettingsToggleRow(
                            label = connector.name,
                            checked = connector in connectors,
                            onCheckedChange = { checked ->
                                connectors = if (checked) connectors + connector else connectors - connector
                            },
                        )
                        if (index != ConnectorType.entries.filter { it != ConnectorType.OTHER }.lastIndex) {
                            Divider(color = EvDivider)
                        }
                    }
                }
            }
        }

        preset.sourceName?.let { source ->
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
        item {
            OutlinedButton(
                onClick = vm::resetVehicleOverride,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Restore catalog specifications")
            }
        }
    }
}

@Composable
private fun ParityVehiclePicker(
    current: EvPreset,
    usesMiles: Boolean,
    onSelect: (EvPreset) -> Unit,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { EvCatalog.search(query) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(results, key = { it.catalogIdentifier }) { preset ->
                val selected = preset.catalogIdentifier == current.catalogIdentifier
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(preset) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) EvMint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
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
                IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "Close") }
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
        "${preset.connectorTypes.joinToString("/") { it.name }}$range"
}

@Composable
private fun SettingsTab(
    vm: TripViewModel,
    contentPadding: PaddingValues,
    onThemeChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var preferredNetworks by remember { mutableStateOf(vm.preferredNetworks.sorted().joinToString(", ")) }
    var avoidedNetworks by remember { mutableStateOf(vm.avoidedNetworks.sorted().joinToString(", ")) }
    var regionMenuExpanded by remember { mutableStateOf(false) }

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
                        range = 0f..350f,
                        step = 25f,
                        onChange = vm::updateMinimumChargerSpeed,
                    )
                    Divider(color = EvDivider)
                    StepperRow(
                        label = "Arrival battery buffer",
                        value = vm.arrivalBufferPercent,
                        suffix = "%",
                        range = 5f..40f,
                        step = 1f,
                        onChange = vm::updateArrivalBuffer,
                    )
                    Divider(color = EvDivider)
                    SettingsToggleRow("Use miles (mi)", vm.usesMiles, vm::updateUsesMiles)
                    Divider(color = EvDivider)
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
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = preferredNetworks,
                        onValueChange = {
                            preferredNetworks = it
                            vm.updatePreferredNetworks(it)
                        },
                        label = { Text("Preferred networks") },
                        placeholder = { Text("Tesla, Electrify America…") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = avoidedNetworks,
                        onValueChange = {
                            avoidedNetworks = it
                            vm.updateAvoidedNetworks(it)
                        },
                        label = { Text("Avoided networks") },
                        placeholder = { Text("Comma-separated") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Text(
                        "Network preferences influence ranking. They never make an incompatible or unreachable charger valid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionLabel("REAL-WORLD RANGE", Modifier.padding(top = 10.dp)) }
        item {
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrandedSlider(
                        label = "Weather range loss",
                        value = vm.weatherRangeLossPercent,
                        range = 0f..45f,
                        suffix = "%",
                        onChange = vm::updateWeatherRangeLoss,
                    )
                    BrandedSlider(
                        label = "Passengers and cargo",
                        value = vm.extraLoadKg,
                        range = 0f..750f,
                        suffix = " kg",
                        accent = EvCyan,
                        onChange = vm::updateExtraLoad,
                    )
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

        item { SectionLabel("APPEARANCE", Modifier.padding(top = 10.dp)) }
        item {
            GlassCard(contentPadding = 0.dp) {
                SettingsToggleRow("Dark mode", vm.prefersDarkMode) { enabled ->
                    vm.updateDarkMode(enabled)
                    onThemeChanged(enabled)
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
                    SettingsLink("Privacy Policy") { NavLauncher.open(context, BuildConfig.PRIVACY_POLICY_URL) }
                    SettingsLink("Support") { NavLauncher.open(context, BuildConfig.SUPPORT_URL) }
                    SettingsLink("Data sources and licenses", vm::showLicenses)
                    SettingsLink("Open Charge Map") { NavLauncher.open(context, "https://openchargemap.org/") }
                    SettingsLink("openrouteservice by HeiGIT") { NavLauncher.open(context, "https://openrouteservice.org/") }
                    Divider(color = EvDivider, modifier = Modifier.padding(vertical = 8.dp))
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
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
        Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
