package com.evfastroute.android

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.evfastroute.android.map.RouteMap
import com.evfastroute.android.nav.NavLauncher
import com.evfastroute.core.EvCatalog
import com.evfastroute.core.EvPreset
import com.evfastroute.core.ItineraryStop
import com.evfastroute.core.LatLon
import com.evfastroute.core.NavigationApp
import com.evfastroute.core.NavigationLinks
import com.evfastroute.core.NavigationPoint
import com.evfastroute.core.PlaceCandidate
import com.evfastroute.core.Region
import com.evfastroute.core.RouteOption
import com.evfastroute.core.Units

@Composable
fun PlannerApp(vm: TripViewModel = viewModel()) {
    if (vm.isEditingSettings) {
        SettingsScreen(vm)
        return
    }
    if (vm.isPickingVehicle) {
        VehiclePicker(
            current = vm.selectedPreset,
            usesMiles = vm.usesMiles,
            onSelect = vm::selectPreset,
            onClose = vm::hideVehiclePicker,
        )
        return
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
                Text("EV FastRoute", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = vm::showSettings) { Text("Settings") }
            }
        }

        item { VehicleCard(preset = vm.selectedPreset, usesMiles = vm.usesMiles, onClick = vm::showVehiclePicker) }

        item {
            AddressField(
                label = "Start",
                text = vm.startText,
                suggestions = vm.startSuggestions,
                onTextChange = vm::onStartTextChange,
                onSelect = vm::selectStart,
            )
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
            TextButton(onClick = vm::addWaypoint) { Text("+ Add stop") }
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
            SliderRow("Current battery", vm.currentSocPercent, 5f..100f) { vm.currentSocPercent = it }
        }
        item {
            SliderRow("Arrival buffer", vm.arrivalBufferPercent, 5f..40f) { vm.arrivalBufferPercent = it }
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
                item { DirectionsRow(option = selected, destination = dest, preferredNav = vm.preferredNav) }
            }
            item {
                ArrivalTimeline(
                    option = selected,
                    destinationName = vm.destination?.placeName ?: "Destination",
                )
            }
        }

        itemsIndexed(vm.options) { index, option ->
            RouteCard(
                option,
                selected = index == vm.selectedIndex,
                currencySymbol = vm.region.currencySymbol,
                onClick = { vm.selectOption(index) },
            )
        }
    }
}

@Composable
private fun ArrivalTimeline(option: RouteOption, destinationName: String) {
    // Absolute clock times are anchored to "now" as the departure moment (stable per option).
    val departureMillis = remember(option) { System.currentTimeMillis() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Trip timeline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            TimelineRow(marker = "◉", title = "Depart", time = clockLabel(departureMillis, 0), trailing = "now")
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
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${value.toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun RouteCard(option: RouteOption, selected: Boolean, currencySymbol: String, onClick: () -> Unit) {
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
            option.estimatedChargingCostValue?.takeIf { it > 0 }?.let { cost ->
                Text("Est. charging cost ~$currencySymbol${"%.2f".format(cost)}", style = MaterialTheme.typography.bodySmall)
            }
            option.itinerary.forEach { stop ->
                Text(
                    "• ${stop.name} — ${formatMinutes(stop.arrivalMinutesFromStart)} in, ${stop.arrivalBatteryPercent}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DirectionsRow(option: RouteOption, destination: PlaceCandidate, preferredNav: NavigationApp) {
    val context = LocalContext.current
    var note by remember(option, destination) { mutableStateOf<String?>(null) }

    val stops = option.chargingStops.map {
        NavigationPoint(it.latitude, it.longitude, it.name, NavigationPoint.Kind.CHARGING)
    }
    val destPoint = NavigationPoint(
        destination.latitude, destination.longitude, destination.placeName, NavigationPoint.Kind.DESTINATION,
    )

    fun launch(app: NavigationApp) {
        val plan = NavigationLinks.handoff(app, origin = null, stops = stops, destination = destPoint)
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
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shortNavLabel(app: NavigationApp): String = when (app) {
    NavigationApp.GOOGLE_MAPS -> "Google"
    NavigationApp.WAZE -> "Waze"
    NavigationApp.DEFAULT -> "Default"
}

@Composable
private fun VehicleCard(preset: EvPreset, usesMiles: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
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
                Text("Change", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(preset.displayName, style = MaterialTheme.typography.titleMedium)
            Text(presetSpecLine(preset, usesMiles), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    return "${minutes / 60}h ${minutes % 60}m"
}
