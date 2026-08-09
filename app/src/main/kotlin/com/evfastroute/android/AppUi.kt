package com.evfastroute.android

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.evfastroute.core.NavigationLinks
import com.evfastroute.core.NavigationPoint
import com.evfastroute.core.NavigationSession
import com.evfastroute.core.orderedNavigationPoints
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.RangeDrivingStyle
import com.evfastroute.core.Region
import com.evfastroute.core.RouteOption
import com.evfastroute.core.Units

@Composable
fun PlannerApp(vm: TripViewModel = viewModel()) {
    if (vm.isViewingLicenses) {
        BackHandler(onBack = vm::hideLicenses)
        LicensesScreen(vm::hideLicenses)
        return
    }
    if (vm.isEditingSettings) {
        BackHandler(onBack = vm::hideSettings)
        SettingsScreen(vm)
        return
    }
    if (vm.isEditingVehicle) {
        BackHandler(onBack = vm::hideVehicleEditor)
        VehicleEditor(vm)
        return
    }
    if (vm.isPickingVehicle) {
        BackHandler(onBack = vm::hideVehiclePicker)
        VehiclePicker(
            current = vm.selectedPreset,
            usesMiles = vm.usesMiles,
            onSelect = vm::selectPreset,
            onClose = vm::hideVehiclePicker,
        )
        return
    }

    val context = LocalContext.current
    val plannerLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(plannerLifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshRoutesIfStale()
        }
        plannerLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { plannerLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun hasForegroundLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var hasLocationPermission by remember {
        mutableStateOf(hasForegroundLocationPermission())
    }
    var useLocationAsStartAfterGrant by remember { mutableStateOf(false) }
    val oneShotLocation = remember(context) { LocationProvider(context) }
    fun setStartFromDeviceLocation() {
        oneShotLocation.oneShot(
            onSample = { lat, lon, _, _ -> vm.useCurrentLocation(lat, lon) },
            onUnavailable = vm::reportLocationUnavailable,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasLocationPermission = grants.values.any { it } || hasForegroundLocationPermission()
        if (hasLocationPermission && useLocationAsStartAfterGrant) setStartFromDeviceLocation()
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
    // Trip-scoped, foreground-only live location for the arrival detector. Lives at the screen level
    // (not inside the scrollable banner item), so scrolling can't pause it; the lifecycle observer
    // stops updates when the app is backgrounded and resumes them (re-checking permission) on return.
    val activeSession = vm.navSession
    if (activeSession != null && activeSession.currentPoint != null && hasLocationPermission) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, activeSession.currentPoint) {
            val provider = LocationProvider(context)
            val feed: () -> Unit = { provider.start { lat, lon, acc, t -> vm.onLocationSample(lat, lon, acc, t) } }
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    // addObserver dispatches ON_RESUME immediately when already resumed, so this
                    // also covers the initial start — no separate initial feed() (which would
                    // double-register).
                    Lifecycle.Event.ON_RESUME -> {
                        hasLocationPermission = hasForegroundLocationPermission()
                        if (hasLocationPermission) feed()
                    }
                    Lifecycle.Event.ON_PAUSE -> provider.stop()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                provider.stop()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("planner_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("EV FastRoute", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = vm::showSettings) { Text("Settings") }
            }
        }

        vm.navSession?.let { session ->
            item {
                GuidedTripBanner(
                    session = session,
                    arrivalSuggested = vm.arrivalSuggested,
                    hasLocationPermission = hasLocationPermission,
                    onRequestLocationPermission = { requestLocationPermission(useAsStart = false) },
                    onHandoffRecorded = vm::recordSessionHandoff,
                    onArrived = vm::advanceGuidedTrip,
                    onEnd = vm::endGuidedTrip,
                )
            }
        }

        item {
            VehicleCard(
                preset = vm.configuredPreset,
                batteryHealthPercent = vm.batteryHealthPercent,
                usesMiles = vm.usesMiles,
                onChange = vm::showVehiclePicker,
                onEdit = vm::showVehicleEditor,
            )
        }
        item {
            SavedTripsCard(
                trips = vm.savedTrips,
                message = vm.savedTripMessage,
                onSave = vm::saveCurrentTrip,
                onLoad = vm::loadSavedTrip,
                onDelete = vm::deleteSavedTrip,
            )
        }

        item {
            AddressField(
                label = "Start",
                text = vm.startText,
                suggestions = vm.startSuggestions,
                onTextChange = vm::onStartTextChange,
                onSelect = vm::selectStart,
            )
            TextButton(onClick = { requestLocationPermission(useAsStart = true) }) {
                Text("Use current location")
            }
        }

        itemsIndexed(vm.waypoints, key = { _, field -> field.id }) { index, field ->
            WaypointRow(
                index = index,
                total = vm.waypoints.size,
                field = field,
                onTextChange = { vm.onWaypointTextChange(field, it) },
                onSelect = { vm.selectWaypoint(field, it) },
                onMoveUp = { vm.moveWaypoint(index, -1) },
                onMoveDown = { vm.moveWaypoint(index, 1) },
                onRemove = { vm.removeWaypoint(index) },
            )
        }
        item {
            TextButton(onClick = vm::addWaypoint, enabled = vm.canAddWaypoint) { Text("+ Add stop") }
            if (!vm.canAddWaypoint) {
                Text(
                    "Maximum $MAX_USER_WAYPOINTS visit stops. Charging stops are added automatically.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            AddressField(
                label = "Destination",
                text = vm.destinationText,
                suggestions = vm.destinationSuggestions,
                onTextChange = vm::onDestinationTextChange,
                onSelect = vm::selectDestination,
            )
        }

        item {
            SliderRow("Current battery", vm.currentSocPercent, 5f..100f, "%", vm::updateCurrentSoc)
        }
        item {
            SliderRow("Arrival buffer", vm.arrivalBufferPercent, 5f..40f, "%", vm::updateArrivalBuffer)
        }
        item { RangeAssumptionsCard(vm) }
        item { ChargerFiltersCard(vm) }

        vm.searchMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
        }

        item {
            Button(
                onClick = vm::plan,
                enabled = !vm.isPlanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.isPlanning) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Planning…")
                } else {
                    Text("Find Route")
                }
            }
        }

        vm.errorMessage?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        vm.selectedOption?.let { selected ->
            item {
                RouteMap(
                    routeGeometry = selected.geometry,
                    chargers = selected.chargingStops.map { LatLon(it.latitude, it.longitude) },
                    start = vm.start?.let { LatLon(it.latitude, it.longitude) },
                    destination = vm.destination?.let { LatLon(it.latitude, it.longitude) },
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    waypoints = selected.userWaypoints.map { LatLon(it.latitude, it.longitude) },
                )
            }
            vm.destination?.let { dest ->
                item {
                    DirectionsRow(
                        option = selected,
                        start = vm.start,
                        destination = dest,
                        preferredNav = vm.preferredNav,
                        onStartGuided = { vm.startGuidedTrip(selected, vm.preferredNav) },
                    )
                }
            }
            item {
                ArrivalTimeline(
                    option = selected,
                    destinationName = vm.destination?.placeName ?: "Destination",
                    departureOffsetMinutes = vm.departureOffsetMinutes,
                    onDepartureOffsetChange = vm::setDepartureOffset,
                )
            }
            item { RouteSafetyAndAttributionCard() }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val age = vm.lastPlanComputedAtMillis?.let { (System.currentTimeMillis() - it) / 60_000L }
                    Text(
                        if (age == null || age == 0L) "Route just updated" else "Route updated ${age}m ago",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = vm::refreshRoutes, enabled = !vm.isPlanning) { Text("Refresh") }
                }
            }
        }

        itemsIndexed(vm.options) { index, option ->
            RouteCard(
                option,
                selected = index == vm.selectedIndex,
                onClick = { vm.selectOption(index) },
            )
        }
    }
}

private val departureChoices = listOf(0 to "Now", 30 to "+30m", 60 to "+1h", 120 to "+2h")

@Composable
private fun ArrivalTimeline(
    option: RouteOption,
    destinationName: String,
    departureOffsetMinutes: Int,
    onDepartureOffsetChange: (Int) -> Unit,
) {
    // "Now" is captured once per option; the chosen departure offset shifts the whole clock.
    val nowMillis = remember(option) { System.currentTimeMillis() }
    val departureMillis = nowMillis + departureOffsetMinutes * 60_000L
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Trip timeline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            Text("Depart", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                departureChoices.forEach { (minutes, label) ->
                    ToggleButton(label, selected = departureOffsetMinutes == minutes, modifier = Modifier.weight(1f)) {
                        onDepartureOffsetChange(minutes)
                    }
                }
            }

            TimelineRow(
                marker = "◉",
                title = "Depart",
                time = clockLabel(departureMillis, 0),
                trailing = if (departureOffsetMinutes == 0) "now" else "scheduled",
            )
            option.itinerary.forEach { stop ->
                val isCharge = stop.kind == ItineraryStop.Kind.CHARGING
                TimelineRow(
                    marker = if (isCharge) "⚡" else "◍",
                    title = stop.name,
                    time = clockLabel(departureMillis, stop.arrivalMinutesFromStart),
                    trailing = "${if (isCharge) "Charge" else "Stop"} · ${stop.arrivalBatteryPercent}%",
                )
            }
            TimelineRow(
                marker = "◉",
                title = destinationName,
                time = clockLabel(departureMillis, option.totalEtaMinutes),
                trailing = "Arrive · ${option.arrivalBatteryPercent}%",
            )
            Text(
                "Free-flow estimates — live traffic isn't included on the free routing stack.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TimelineRow(marker: String, title: String, time: String, trailing: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(marker, style = MaterialTheme.typography.bodyMedium)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            trailing?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(time, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun clockLabel(departureMillis: Long, offsetMinutes: Int): String {
    val time = java.time.Instant.ofEpochMilli(departureMillis + offsetMinutes * 60_000L)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    return time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
}

@Composable
private fun SavedTripsCard(
    trips: List<SavedTripSnapshot>,
    message: String?,
    onSave: () -> Unit,
    onLoad: (SavedTripSnapshot) -> Unit,
    onDelete: (SavedTripSnapshot) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Saved trips", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onSave) { Text("Save current") }
            }
            trips.forEach { trip ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onLoad(trip) }, modifier = Modifier.weight(1f)) {
                        Text(trip.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = { onDelete(trip) }) { Text("Remove") }
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RangeAssumptionsCard(vm: TripViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Real-world range assumptions", style = MaterialTheme.typography.titleMedium)
            Text(
                "The planner combines these with your vehicle, battery health, route speed, and a conservative uncertainty margin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SliderRow(
                "Weather range loss",
                vm.weatherRangeLossPercent,
                0f..45f,
                "%",
                vm::updateWeatherRangeLoss,
            )
            SliderRow("Passengers and cargo", vm.extraLoadKg, 0f..750f, " kg", vm::updateExtraLoad)
            Text("Driving style", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RangeDrivingStyle.entries.forEach { style ->
                    ToggleButton(
                        label = style.name.lowercase().replaceFirstChar { it.uppercase() },
                        selected = vm.drivingStyle == style,
                        modifier = Modifier.weight(1f),
                    ) { vm.updateDrivingStyle(style) }
                }
            }
        }
    }
}

@Composable
private fun ChargerFiltersCard(vm: TripViewModel) {
    var preferredText by remember { mutableStateOf(vm.preferredNetworks.sorted().joinToString(", ")) }
    var avoidedText by remember { mutableStateOf(vm.avoidedNetworks.sorted().joinToString(", ")) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Charging preferences", style = MaterialTheme.typography.titleMedium)
            SliderRow(
                "Minimum compatible speed",
                vm.minimumChargerSpeedKw,
                0f..350f,
                " kW",
                vm::updateMinimumChargerSpeed,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Avoid low-confidence station records", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "May remove rural options; confidence is data completeness, not live uptime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.avoidLowConfidenceStations, onCheckedChange = vm::updateAvoidLowConfidence)
            }
            OutlinedTextField(
                value = preferredText,
                onValueChange = { preferredText = it; vm.updatePreferredNetworks(it) },
                label = { Text("Preferred networks (comma-separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = avoidedText,
                onValueChange = { avoidedText = it; vm.updateAvoidedNetworks(it) },
                label = { Text("Avoid networks (comma-separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsScreen(vm: TripViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = vm::hideSettings) { Text("Close") }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Distance units", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton("Kilometers", selected = !vm.usesMiles, modifier = Modifier.weight(1f)) { vm.updateUsesMiles(false) }
                    ToggleButton("Miles", selected = vm.usesMiles, modifier = Modifier.weight(1f)) { vm.updateUsesMiles(true) }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Preferred navigation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NavigationApp.entries.forEach { app ->
                        ToggleButton(shortNavLabel(app), selected = vm.preferredNav == app, modifier = Modifier.weight(1f)) {
                            vm.updatePreferredNav(app)
                        }
                    }
                }
            }
        }

        item {
            Text("Region", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        items(Region.entries) { region ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { vm.updateRegion(region) },
                colors = if (region == vm.region) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(region.flag, style = MaterialTheme.typography.titleMedium)
                    Text(region.displayName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        item {
            val context = LocalContext.current
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Privacy, safety, and data", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Search terms, route coordinates, and charger-area queries are sent to the configured routing providers. " +
                            "EV FastRoute does not create an account or sell personal data. Location is foreground-only and optional.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Always verify a station in its operator app before committing to it. Station status, price, and availability may be missing or stale.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { NavLauncher.open(context, BuildConfig.PRIVACY_POLICY_URL) }) {
                        Text("Read privacy policy")
                    }
                    Text("Data sources and open-source components", style = MaterialTheme.typography.labelMedium)
                    listOf(
                        "Open Charge Map" to "https://openchargemap.org/",
                        "openrouteservice by HeiGIT" to "https://openrouteservice.org/",
                        "Photon / OpenStreetMap search" to "https://photon.komoot.io/",
                        "MapLibre and OpenFreeMap" to "https://maplibre.org/",
                        "OpenEV vehicle catalog" to "https://github.com/chargeprice/open-ev-data",
                        "OpenEV data license (CDLA 2.0)" to "https://cdla.dev/permissive-2-0/",
                        "Support and issue reporting" to BuildConfig.SUPPORT_URL,
                    ).forEach { (label, url) ->
                        TextButton(onClick = { NavLauncher.open(context, url) }) { Text(label) }
                    }
                    TextButton(onClick = vm::showLicenses) { Text("View licenses and notices in app") }
                    Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LicensesScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val noticeText = remember(context) {
        val files = listOf("THIRD_PARTY_NOTICES.txt", "Apache-2.0.txt", "CDLA-Permissive-2.0.txt")
        files.joinToString("\n\n") { name ->
            runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }
                .getOrElse { "Unable to load $name." }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Licenses and notices", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
        item { Text(noticeText, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ToggleButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun WaypointRow(
    index: Int,
    total: Int,
    field: WaypointField,
    onTextChange: (String) -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AddressField(
            label = "Stop ${index + 1}",
            text = field.text,
            suggestions = field.suggestions,
            onTextChange = onTextChange,
            onSelect = onSelect,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("↑ Up") }
            TextButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.weight(1f)) { Text("↓ Down") }
            TextButton(onClick = onRemove, modifier = Modifier.weight(1f)) { Text("Remove") }
        }
    }
}

@Composable
private fun AddressField(
    label: String,
    text: String,
    suggestions: List<PlaceCandidate>,
    onTextChange: (String) -> Unit,
    onSelect: (PlaceCandidate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        suggestions.forEach { candidate ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(candidate) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Text(candidate.placeName, style = MaterialTheme.typography.bodyLarge)
                if (candidate.fullAddress.isNotBlank()) {
                    Text(
                        candidate.fullAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                candidate.distanceKm?.let { distance ->
                    Text(
                        if (distance < 1.0) "${(distance * 1_000).toInt()} m away" else "${"%.1f".format(distance)} km away",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Text("$label: ${value.toInt()}$suffix", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun RouteCard(option: RouteOption, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option.title, style = MaterialTheme.typography.titleMedium)
                Text(formatMinutes(option.totalEtaMinutes), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "${option.chargingStops.size} charge stop${if (option.chargingStops.size == 1) "" else "s"} · " +
                    "${option.drivingMinutes}m drive + ${option.chargingMinutes}m charge · arrive ${option.arrivalBatteryPercent}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            option.estimatedChargingCostValue?.let { cost ->
                val costText = if (cost == 0.0) {
                    "Known energy charge: free"
                } else {
                    "Est. energy cost ~${formatCurrency(cost, option.estimatedChargingCostCurrencyCode)}"
                }
                Text(costText, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                option.objective.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val otherObjectives = option.supportedObjectives - option.objective
            if (otherObjectives.isNotEmpty()) {
                Text(
                    "Also best for: ${otherObjectives.joinToString { it.mode }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            option.itinerary.forEach { stop ->
                Text(
                    "• ${stop.name} — ${formatMinutes(stop.arrivalMinutesFromStart)} in, ${stop.arrivalBatteryPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val providerNotices = option.chargingStops
                .mapNotNull { stop ->
                    val title = stop.dataProviderTitle ?: return@mapNotNull null
                    Triple(title, stop.dataProviderLicense, stop.dataProviderWebsiteUrl)
                }
                .distinct()
            if (providerNotices.isNotEmpty()) {
                Text(
                    "Charging-station data attribution",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                providerNotices.forEach { (title, license, url) ->
                    if (url != null) {
                        TextButton(onClick = { NavLauncher.open(context, url) }) { Text(title) }
                    } else {
                        Text(title, style = MaterialTheme.typography.bodySmall)
                    }
                    license?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (option.chargingStops.isNotEmpty()) {
                Text(
                    "Charging-station data © Open Charge Map contributors and listed data providers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RouteSafetyAndAttributionCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Before you drive", style = MaterialTheme.typography.titleSmall)
            Text(
                "Verify every charging stop, connector, access rule, and availability in the station operator's app. " +
                    "Range, traffic-free ETA, price, and status are estimates and may be incomplete or stale.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "Routing © openrouteservice.org by HeiGIT · Map and search data © OpenStreetMap contributors.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DirectionsRow(
    option: RouteOption,
    start: PlaceCandidate?,
    destination: PlaceCandidate,
    preferredNav: NavigationApp,
    onStartGuided: () -> Unit,
) {
    val context = LocalContext.current
    var note by remember(option, destination) { mutableStateOf<String?>(null) }

    // Interleave the driver's own waypoints AND charging stops in travel order (matches iOS), so a
    // one-tap handoff never silently skips a stop the user added.
    val stops = option.orderedNavigationPoints()
    val destPoint = NavigationPoint(
        destination.latitude, destination.longitude, destination.placeName, NavigationPoint.Kind.DESTINATION,
    )
    val originPoint = start?.let {
        NavigationPoint(it.latitude, it.longitude, it.placeName, NavigationPoint.Kind.VISIT)
    }
    val hasStops = stops.isNotEmpty()

    fun launch(app: NavigationApp) {
        val plan = NavigationLinks.handoff(app, origin = originPoint, stops = stops, destination = destPoint)
        if (plan == null) { note = "Couldn't build a ${app.displayName} link."; return }
        val opened = NavLauncher.open(context, plan.url)
        note = if (!opened) "No app on this device could open ${app.displayName}." else plan.note
    }

    // The preferred app leads and reads as the primary action; the others stay available.
    val apps = listOf(preferredNav) + (NavigationApp.entries - preferredNav)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Directions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            apps.forEach { app ->
                if (app == preferredNav) {
                    Button(onClick = { launch(app) }, modifier = Modifier.weight(1f)) { Text(shortNavLabel(app)) }
                } else {
                    OutlinedButton(onClick = { launch(app) }, modifier = Modifier.weight(1f)) { Text(shortNavLabel(app)) }
                }
            }
        }
        // Capability-accurate wording for the preferred app (e.g. "Full trip in Google Maps" vs
        // "Next stop in Waze") — signals up front when a single link carries only the next stop.
        Text(
            NavigationLinks.actionLabel(preferredNav, intermediateStopCount = stops.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasStops) {
            OutlinedButton(onClick = onStartGuided, modifier = Modifier.fillMaxWidth()) {
                Text("Start guided trip (stop by stop)")
            }
        }
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuidedTripBanner(
    session: NavigationSession,
    arrivalSuggested: Boolean,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onHandoffRecorded: () -> Unit,
    onArrived: () -> Unit,
    onEnd: () -> Unit,
) {
    val context = LocalContext.current
    val point = session.currentPoint ?: return
    val position = session.completedPointCount + 1
    val isFinal = session.remainingPointCount <= 1

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Guided trip · stop $position of ${session.totalPointCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Next: ${point.name}", style = MaterialTheme.typography.titleMedium)
            if (arrivalSuggested) {
                Text(
                    "You seem to have arrived at ${point.name}. Confirm to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val plan = NavigationLinks.handoff(session.app, origin = null, stops = emptyList(), destination = point)
                        if (plan != null && NavLauncher.open(context, plan.url)) onHandoffRecorded()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Open in ${session.app.displayName}") }
                // Emphasise the confirm action once arrival is suggested.
                if (arrivalSuggested) {
                    Button(onClick = onArrived, modifier = Modifier.weight(1f)) {
                        Text(if (isFinal) "Arrived" else "Arrived · next")
                    }
                } else {
                    OutlinedButton(onClick = onArrived, modifier = Modifier.weight(1f)) {
                        Text(if (isFinal) "Arrived" else "Arrived · next")
                    }
                }
            }
            if (!hasLocationPermission) {
                TextButton(onClick = onRequestLocationPermission) {
                    Text("Enable location for automatic arrival")
                }
            }
            TextButton(onClick = onEnd) { Text("End guided trip") }
        }
    }
}

private fun shortNavLabel(app: NavigationApp): String = when (app) {
    NavigationApp.GOOGLE_MAPS -> "Google"
    NavigationApp.WAZE -> "Waze"
    NavigationApp.DEFAULT -> "Default"
}

@Composable
private fun VehicleCard(
    preset: EvPreset,
    batteryHealthPercent: Double,
    usesMiles: Boolean,
    onChange: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Vehicle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row {
                    TextButton(onClick = onEdit) { Text("Edit specs") }
                    TextButton(onClick = onChange) { Text("Change") }
                }
            }
            Text(preset.displayName, style = MaterialTheme.typography.titleMedium)
            Text(presetSpecLine(preset, usesMiles), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Battery health: ${batteryHealthPercent.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VehicleEditor(vm: TripViewModel) {
    val preset = vm.configuredPreset
    var capacity by remember(preset) { mutableStateOf(trimDecimal(preset.batteryCapacityKwh)) }
    var maxPower by remember(preset) { mutableStateOf(preset.maxDcChargingKw.toString()) }
    var consumption by remember(preset) { mutableStateOf("%.1f".format(preset.efficiencyKwhPerKm * 100.0)) }
    var health by remember(preset) { mutableStateOf(trimDecimal(vm.batteryHealthPercent)) }
    var connectors by remember(preset) { mutableStateOf(preset.connectorTypes.toSet()) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Vehicle specifications", style = MaterialTheme.typography.headlineSmall)
                    Text(preset.displayName, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = vm::hideVehicleEditor) { Text("Close") }
            }
        }
        item {
            Text(
                "Catalog values are a starting point. Update them for your exact trim, wheels, usable battery, adapters, and current battery health.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = capacity,
                onValueChange = { capacity = it },
                label = { Text("Usable battery capacity (kWh)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = maxPower,
                onValueChange = { maxPower = it },
                label = { Text("Maximum DC charging (kW)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = consumption,
                onValueChange = { consumption = it },
                label = { Text("Reference consumption (kWh/100 km)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = health,
                onValueChange = { health = it },
                label = { Text("Battery health (%)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text("Compatible connectors", style = MaterialTheme.typography.labelMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ConnectorType.entries.filter { it != ConnectorType.OTHER }.forEach { connector ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(connector.name)
                        Switch(
                            checked = connector in connectors,
                            onCheckedChange = { checked ->
                                connectors = if (checked) connectors + connector else connectors - connector
                            },
                        )
                    }
                }
            }
        }
        validationMessage?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Button(
                onClick = {
                    val parsedCapacity = capacity.replace(',', '.').toDoubleOrNull()
                    val parsedPower = maxPower.toIntOrNull()
                    val parsedConsumption = consumption.replace(',', '.').toDoubleOrNull()
                    val parsedHealth = health.replace(',', '.').toDoubleOrNull()
                    validationMessage = if (
                        parsedCapacity == null || parsedPower == null || parsedConsumption == null || parsedHealth == null
                    ) {
                        "Enter valid numbers in every field."
                    } else {
                        vm.saveVehicleOverride(
                            parsedCapacity,
                            parsedPower,
                            parsedConsumption,
                            parsedHealth,
                            connectors,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save vehicle specifications") }
        }
        item {
            OutlinedButton(onClick = vm::resetVehicleOverride, modifier = Modifier.fillMaxWidth()) {
                Text("Restore catalog specifications")
            }
        }
        preset.sourceName?.let { source ->
            item {
                Text(
                    "Catalog source: $source",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VehiclePicker(current: EvPreset, usesMiles: Boolean, onSelect: (EvPreset) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { EvCatalog.search(query) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Choose your car", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onClose) { Text("Close") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search ${EvCatalog.makeCount} makes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (results.isEmpty()) {
                item {
                    Text(
                        "No matching vehicles. Try a make or model.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            itemsIndexed(results) { _, preset ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(preset) },
                    colors = if (preset.catalogIdentifier == current.catalogIdentifier) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        CardDefaults.cardColors()
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(preset.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(presetSpecLine(preset, usesMiles), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun presetSpecLine(preset: EvPreset, usesMiles: Boolean): String {
    val battery = "${trimDecimal(preset.batteryCapacityKwh)} kWh"
    val power = "${preset.maxDcChargingKw} kW DC"
    val connectors = preset.connectorTypes.joinToString("/") { it.name }
    val range = preset.ratedRangeKm?.let { " · ~${Units.formatDistance(it, usesMiles)}" } ?: ""
    return "$battery · $power · $connectors$range"
}

private fun trimDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun formatCurrency(value: Double, currencyCode: String?): String {
    if (currencyCode == null) return "${"%.2f".format(value)} (currency unknown)"
    return runCatching {
        val formatter = java.text.NumberFormat.getCurrencyInstance()
        formatter.currency = java.util.Currency.getInstance(currencyCode)
        formatter.format(value)
    }.getOrElse { "$currencyCode ${"%.2f".format(value)}" }
}

private fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    return "${minutes / 60}h ${minutes % 60}m"
}
